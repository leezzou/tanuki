#!/usr/bin/env bash
# 回归测试：clonefile 失败留下部分临时目录时，普通复制回退不得嵌套或残留。
# 只加载脚本里的复制辅助函数，不执行环境准备，也不写 worktree 的 .build。
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
work=$(mktemp -d "${TMPDIR:-/tmp}/tanuki-ao-env-test.XXXXXX")
trap 'rm -rf "$work"' EXIT

export TANUKI_ENV_LIB_ONLY=1
# shellcheck source=scripts/ao-worktree-env.sh
source "$root/scripts/ao-worktree-env.sh"
unset TANUKI_ENV_LIB_ONLY

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

# 模拟 macOS 的 cp：-cRL / -c 先创建部分目标再失败；plain_copy_fails=1 时 -RL 也失败。
real_cp=$(command -v cp)
plain_copy_fails=0
cp() {
  case "$1" in
    -cRL)
      mkdir -p "$3/partial-clone"
      printf 'partial\n' > "$3/partial-clone/junk"
      return 1
      ;;
    -c)
      printf 'partial\n' > "$3"
      return 1
      ;;
    -RL)
      if [[ "$plain_copy_fails" == "1" ]]; then
        mkdir -p "$3/partial-plain"
        printf 'partial\n' > "$3/partial-plain/junk"
        return 1
      fi
      ;;
  esac
  "$real_cp" "$@"
}

# 1. 目录：clone 失败并留下 partial tmp，回退复制后布局必须正确且无残留。
mkdir -p "$work/src/sub"
printf 'data\n' > "$work/src/sub/file.txt"
tanuki_env_copy_entry "$work/src" "$work/dst-dir"
[[ -f "$work/dst-dir/sub/file.txt" ]] || fail "目录复制缺少文件"
[[ ! -e "$work/dst-dir/src" ]] || fail "目录复制发生嵌套"
[[ ! -e "$work/dst-dir/partial-clone" ]] || fail "目录复制混入 clone 残留"

# 2. 文件：clone 失败并留下 partial tmp，回退复制必须覆盖为完整文件。
printf 'whole-file\n' > "$work/source.txt"
tanuki_env_copy_entry "$work/source.txt" "$work/dst-file.txt"
cmp -s "$work/source.txt" "$work/dst-file.txt" || fail "文件复制内容不完整"

# 3. 顶层符号链接：解析后复制为独立实体，不能把外部链接带进 worktree。
mkdir -p "$work/external-dir"
printf 'external-dir\n' > "$work/external-dir/file.txt"
ln -s "$work/external-dir" "$work/link-dir"
tanuki_env_copy_entry "$work/link-dir" "$work/dst-link-dir"
[[ -d "$work/dst-link-dir" && ! -L "$work/dst-link-dir" ]] || fail "目录链接未实体化"
[[ -f "$work/dst-link-dir/file.txt" ]] || fail "目录链接目标缺少文件"

printf 'external-file\n' > "$work/external-file.txt"
ln -s external-file.txt "$work/link-file"
tanuki_env_copy_entry "$work/link-file" "$work/dst-link-file"
[[ -f "$work/dst-link-file" && ! -L "$work/dst-link-file" ]] || fail "文件链接未实体化"
cmp -s "$work/external-file.txt" "$work/dst-link-file" || fail "文件链接目标内容不完整"

# 4. 目录内的绝对/逃逸链接必须拒绝，且不留下目标。
mkdir -p "$work/outside" "$work/src-escape-abs" "$work/src-escape-rel"
printf 'secret\n' > "$work/outside/secret.txt"
ln -s "$work/outside/secret.txt" "$work/src-escape-abs/absolute-link"
if tanuki_env_copy_entry "$work/src-escape-abs" "$work/dst-escape-abs" 2>/dev/null; then
  fail "绝对符号链接应被拒绝"
fi
[[ ! -e "$work/dst-escape-abs" ]] || fail "拒绝绝对链接后不应留下目标"

ln -s ../outside/secret.txt "$work/src-escape-rel/relative-link"
if tanuki_env_copy_entry "$work/src-escape-rel" "$work/dst-escape-rel" 2>/dev/null; then
  fail "逃出源码树的相对符号链接应被拒绝"
fi
[[ ! -e "$work/dst-escape-rel" ]] || fail "拒绝逃逸链接后不应留下目标"

# 4b. 先逃出源根再返回的链接：复制后仍会指向源目录，必须拒绝。
mkdir -p "$work/src-escape-round/data"
printf 'round\n' > "$work/src-escape-round/data/file.txt"
ln -s ../src-escape-round/data/file.txt "$work/src-escape-round/link.txt"
if tanuki_env_copy_entry "$work/src-escape-round" "$work/dst-escape-round" 2>/dev/null; then
  fail "先逃出源根再返回的链接应被拒绝"
fi
[[ ! -e "$work/dst-escape-round" ]] || fail "拒绝绕回链接后不应留下目标"

# 4c. 循环链接必须拒绝，且不留下目标。
mkdir -p "$work/src-cycle/sub"
ln -s .. "$work/src-cycle/sub/loop"
if tanuki_env_copy_entry "$work/src-cycle" "$work/dst-cycle" 2>/dev/null; then
  fail "循环符号链接应被拒绝"
fi
[[ ! -e "$work/dst-cycle" ]] || fail "拒绝循环链接后不应留下目标"

# 5. 源/目标目录名不同：内部安全链接被 dereference，写副本不能修改源。
mkdir -p "$work/src-original/data" "$work/src-original/sub"
printf 'source\n' > "$work/src-original/data/file.txt"
ln -s ../data/file.txt "$work/src-original/sub/link.txt"
tanuki_env_copy_entry "$work/src-original" "$work/dst-renamed"
[[ -f "$work/dst-renamed/sub/link.txt" && ! -L "$work/dst-renamed/sub/link.txt" ]] || fail "内部链接应 dereference 为普通文件"
[[ "$(cat "$work/dst-renamed/sub/link.txt")" == "source" ]] || fail "dereference 后内容不完整"
printf 'changed\n' > "$work/dst-renamed/sub/link.txt"
[[ "$(cat "$work/src-original/data/file.txt")" == "source" ]] || fail "写副本文件修改了源"
[[ "$(cat "$work/dst-renamed/sub/link.txt")" == "changed" ]] || fail "写副本文件未生效"

# 5b. 中间目录链接 + 链式 .. 绕回：dereference 后写副本不得修改源。
mkdir -p "$work/src-chain/d" "$work/src-chain/data" "$work/src-chain/deep"
printf 'chain\n' > "$work/src-chain/data/file.txt"
ln -s ../d "$work/src-chain/deep/to-d"
ln -s to-d/../../src-chain/data/file.txt "$work/src-chain/deep/link.txt"
tanuki_env_copy_entry "$work/src-chain" "$work/dst-chain"
[[ -f "$work/dst-chain/deep/link.txt" && ! -L "$work/dst-chain/deep/link.txt" ]] || fail "链式链接应 dereference 为普通文件"
[[ "$(cat "$work/dst-chain/deep/link.txt")" == "chain" ]] || fail "链式链接 dereference 内容不完整"
[[ -z "$(find "$work/dst-chain" -type l -print -quit)" ]] || fail "副本不应保留任何符号链接"
printf 'chain-changed\n' > "$work/dst-chain/deep/link.txt"
[[ "$(cat "$work/src-chain/data/file.txt")" == "chain" ]] || fail "写链式副本修改了源"
[[ "$(cat "$work/dst-chain/deep/link.txt")" == "chain-changed" ]] || fail "写链式副本未生效"

# 6. 回退也失败时必须返回非 0，且清理目标与临时目录。
plain_copy_fails=1
if tanuki_env_copy_entry "$work/src" "$work/dst-failed"; then
  fail "普通复制失败时应返回非 0"
fi
[[ ! -e "$work/dst-failed" ]] || fail "失败后不应留下目标目录"
if compgen -G "$work/dst-failed.tmp.*" >/dev/null; then
  fail "失败后不应留下临时目录"
fi
plain_copy_fails=0

# 7. 正常路径（真实 cp）仍工作。
unset -f cp
tanuki_env_copy_entry "$work/src" "$work/dst-ok"
[[ -f "$work/dst-ok/sub/file.txt" ]] || fail "正常复制缺少文件"

printf 'PASS: ao-worktree-env copy regression\n'

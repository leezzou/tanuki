#!/usr/bin/env bash
# 回归测试：可写缓存/源码树复制严格跳过符号链接，且 clone 失败回退不嵌套。
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

# 模拟 macOS 的 cp：-cR / -c 先创建部分目标再失败；plain_copy_fails=1 时 -R 也失败。
real_cp=$(command -v cp)
plain_copy_fails=0
cp() {
  case "$1" in
    -cR)
      mkdir -p "$3/partial-clone"
      printf 'partial\n' > "$3/partial-clone/junk"
      return 1
      ;;
    -c)
      printf 'partial\n' > "$3"
      return 1
      ;;
    -R)
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

# 3. 顶层符号链接：一律跳过，不复制、不跟随、不产生目标。
mkdir -p "$work/external-dir"
printf 'external-dir\n' > "$work/external-dir/file.txt"
ln -s "$work/external-dir" "$work/link-dir"
tanuki_env_copy_entry "$work/link-dir" "$work/dst-link-dir"
[[ ! -e "$work/dst-link-dir" ]] || fail "顶层目录链接不应产生目标"

printf 'external-file\n' > "$work/external-file.txt"
ln -s external-file.txt "$work/link-file"
tanuki_env_copy_entry "$work/link-file" "$work/dst-link-file"
[[ ! -e "$work/dst-link-file" ]] || fail "顶层文件链接不应产生目标"

# 3b. 顶层链接指向含逃逸链接的目录：也直接跳过，不递归。
mkdir -p "$work/external-escape" "$work/outside-escape"
printf 'secret2\n' > "$work/outside-escape/secret.txt"
ln -s "$work/outside-escape/secret.txt" "$work/external-escape/escape"
ln -s "$work/external-escape" "$work/link-escape-dir"
tanuki_env_copy_entry "$work/link-escape-dir" "$work/dst-link-escape"
[[ ! -e "$work/dst-link-escape" ]] || fail "顶层链接目录不应产生目标"

# 4. 目录内的链接全部跳过，其他文件正常复制，目标树不得有任何符号链接。
mkdir -p "$work/outside" "$work/src-links/data" "$work/src-links/deep" "$work/src-links/cycle"
printf 'secret\n' > "$work/outside/secret.txt"
printf 'source\n' > "$work/src-links/data/file.txt"
ln -s data/file.txt "$work/src-links/safe-link"
ln -s "$work/outside/secret.txt" "$work/src-links/absolute-link"
ln -s ../outside/secret.txt "$work/src-links/relative-link"
ln -s ../d "$work/src-links/deep/to-d"
ln -s to-d/../../src-links/data/file.txt "$work/src-links/deep/chain-link"
ln -s .. "$work/src-links/cycle/loop"
tanuki_env_copy_entry "$work/src-links" "$work/dst-links"
[[ -f "$work/dst-links/data/file.txt" ]] || fail "目录复制缺少普通文件"
[[ -z "$(find "$work/dst-links" -type l -print -quit)" ]] || fail "目标树不应保留符号链接"
for skipped in safe-link absolute-link relative-link deep/to-d deep/chain-link cycle/loop; do
  [[ ! -e "$work/dst-links/$skipped" && ! -L "$work/dst-links/$skipped" ]] || fail "链接未被跳过: $skipped"
done
printf 'changed\n' > "$work/dst-links/data/file.txt"
[[ "$(cat "$work/src-links/data/file.txt")" == "source" ]] || fail "写目标文件修改了源"
[[ "$(cat "$work/dst-links/data/file.txt")" == "changed" ]] || fail "写目标文件未生效"

# 5. 源/目标目录名不同 + 中间目录链接 + 链式 .. 绕回：链接跳过，写目标不影响源。
mkdir -p "$work/src-chain/d" "$work/src-chain/data" "$work/src-chain/deep"
printf 'chain\n' > "$work/src-chain/data/file.txt"
ln -s ../d "$work/src-chain/deep/to-d"
ln -s to-d/../../src-chain/data/file.txt "$work/src-chain/deep/link.txt"
tanuki_env_copy_entry "$work/src-chain" "$work/dst-chain"
[[ -f "$work/dst-chain/data/file.txt" ]] || fail "链式源缺少普通文件"
[[ ! -e "$work/dst-chain/deep/to-d" && ! -L "$work/dst-chain/deep/to-d" ]] || fail "目录链接未被跳过"
[[ ! -e "$work/dst-chain/deep/link.txt" && ! -L "$work/dst-chain/deep/link.txt" ]] || fail "链式链接未被跳过"
[[ -z "$(find "$work/dst-chain" -type l -print -quit)" ]] || fail "链式目标不应保留符号链接"
printf 'chain-changed\n' > "$work/dst-chain/data/file.txt"
[[ "$(cat "$work/src-chain/data/file.txt")" == "chain" ]] || fail "写链式目标修改了源"
[[ "$(cat "$work/dst-chain/data/file.txt")" == "chain-changed" ]] || fail "写链式目标未生效"

# 6. 已存在的目标树也要清理链接，保证重复 source 后仍无符号链接。
mkdir -p "$work/existing-target"
printf 'old\n' > "$work/existing-target/old.txt"
ln -s "$work/outside/secret.txt" "$work/existing-target/old-link"
tanuki_env_copy_entry "$work/src" "$work/existing-target"
[[ -f "$work/existing-target/old.txt" ]] || fail "已有目标文件被误删"
[[ -z "$(find "$work/existing-target" -type l -print -quit)" ]] || fail "已有目标树仍有符号链接"

# 7. 回退也失败时必须返回非 0，且清理目标与临时目录。
plain_copy_fails=1
if tanuki_env_copy_entry "$work/src" "$work/dst-failed"; then
  fail "普通复制失败时应返回非 0"
fi
[[ ! -e "$work/dst-failed" ]] || fail "失败后不应留下目标目录"
if compgen -G "$work/dst-failed.tmp.*" >/dev/null; then
  fail "失败后不应留下临时目录"
fi
plain_copy_fails=0

# 8. 正常路径（真实 cp）仍工作。
unset -f cp
tanuki_env_copy_entry "$work/src" "$work/dst-ok"
[[ -f "$work/dst-ok/sub/file.txt" ]] || fail "正常复制缺少文件"

printf 'PASS: ao-worktree-env copy regression\n'

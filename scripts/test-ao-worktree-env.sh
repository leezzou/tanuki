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

# 3. 回退也失败时必须返回非 0，且清理目标与临时目录。
plain_copy_fails=1
if tanuki_env_copy_entry "$work/src" "$work/dst-failed"; then
  fail "普通复制失败时应返回非 0"
fi
[[ ! -e "$work/dst-failed" ]] || fail "失败后不应留下目标目录"
if compgen -G "$work/dst-failed.tmp.*" >/dev/null; then
  fail "失败后不应留下临时目录"
fi
plain_copy_fails=0

# 4. 正常路径（真实 cp）仍工作。
unset -f cp
tanuki_env_copy_entry "$work/src" "$work/dst-ok"
[[ -f "$work/dst-ok/sub/file.txt" ]] || fail "正常复制缺少文件"

printf 'PASS: ao-worktree-env copy regression\n'

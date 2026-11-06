#!/usr/bin/env bash
# 为 AO 独立 worktree 准备可写的构建环境（用 source 加载，不修改全局 Java/SDK/Shell）。
# 仅写入当前 worktree 内的 .build/ 与 local.properties；绝不写 ~/.gradle。
#
# 用法：
#   source scripts/ao-worktree-env.sh
#
# 可用环境变量：
#   TANUKI_JDK            指定 JDK 17 home（优先级最高）
#   TANUKI_SDK            指定 Android SDK（否则用 ANDROID_HOME/ANDROID_SDK_ROOT，再否则 ~/Library/Android/sdk）
#   TANUKI_MAIN_REPO      指定主仓库路径（默认由 git --git-common-dir 推导）
#   TANUKI_SKIP_GRADLE_CACHE_COPY=1     跳过依赖缓存克隆（Gradle 将联网下载）
#   TANUKI_COPY_GRADLE_VERSION_CACHE=1  额外克隆 caches/<版本>（含 transforms，首次更慢）
#   TANUKI_ANDROID_USER_HOME            覆盖默认的 worktree 内 ANDROID_USER_HOME
#   TANUKI_SKIP_ANDROID_USER_HOME=1     不重定向 ANDROID_USER_HOME（assemble/lint 会因写 ~/.android 被沙箱拒绝）

# 解析路径；优先 readlink -f，旧系统退回 realpath。
tanuki_env_realpath() {
  local path="$1"
  if readlink -f "$path" >/dev/null 2>&1; then
    readlink -f "$path"
  elif command -v realpath >/dev/null 2>&1; then
    realpath "$path"
  else
    return 1
  fi
}

# 可写缓存/源码树拒绝绝对链接、逃出源树或先出根再返回的链接；内部安全链接在复制时 dereference。
tanuki_env_validate_tree_links() {
  local src="$1" src_real link raw resolved
  local link_rel link_dir depth part
  local -a parts
  src_real=$(tanuki_env_realpath "$src") || return 1
  src="$src_real"
  while IFS= read -r -d '' link; do
    raw=$(readlink "$link") || return 1
    if [[ "$raw" == /* ]]; then
      printf '[ao-worktree-env] 错误: 源码树含绝对符号链接，拒绝复制: %s -> %s\n' "$link" "$raw" >&2
      return 1
    fi
    # 仅检查源位置的 realpath 不够：链接文本会原样复制，必须保证搬迁后仍从副本根内解析。
    # 从链接所在目录按词法累计深度，任何 .. 越过源根（即使随后返回）都会指向源目录或断链。
    link_rel="${link#"$src_real"/}"
    link_dir="${link_rel%/*}"
    [[ "$link_dir" == "$link_rel" ]] && link_dir=""
    depth=0
    IFS='/' read -r -a parts <<< "$link_dir"
    parts+=("")
    for part in "${parts[@]}"; do
      case "$part" in
        ''|.) ;;
        ..) depth=$((depth - 1)); (( depth >= 0 )) || { printf '[ao-worktree-env] 错误: 符号链接路径异常: %s\n' "$link" >&2; return 1; } ;;
        *) depth=$((depth + 1)) ;;
      esac
    done
    IFS='/' read -r -a parts <<< "$raw"
    parts+=("")
    for part in "${parts[@]}"; do
      case "$part" in
        ''|.) ;;
        ..)
          depth=$((depth - 1))
          if (( depth < 0 )); then
            printf '[ao-worktree-env] 错误: 符号链接先逃出源码根，搬迁后会指向源目录或断链: %s -> %s\n' "$link" "$raw" >&2
            return 1
          fi
          ;;
        *) depth=$((depth + 1)) ;;
      esac
    done
    resolved=$(tanuki_env_realpath "$link") || {
      printf '[ao-worktree-env] 错误: 无法解析符号链接: %s -> %s\n' "$link" "$raw" >&2
      return 1
    }
    if [[ ! -e "$resolved" ]]; then
      printf '[ao-worktree-env] 错误: 符号链接目标不存在: %s -> %s\n' "$link" "$raw" >&2
      return 1
    fi
    case "$resolved" in
      "$src_real"/*) ;;
      *)
        printf '[ao-worktree-env] 错误: 符号链接逃出源码树，拒绝复制: %s -> %s\n' "$link" "$raw" >&2
        return 1
        ;;
    esac
  done < <(find "$src" -type l -print0 2>/dev/null)
  return 0
}

# 通用复制：目录/文件/符号链接；优先 APFS clonefile（写时复制，快且省盘），失败退回普通复制。
# 独立定义，便于 scripts/test-ao-worktree-env.sh 在 TANUKI_ENV_LIB_ONLY=1 下做回归测试。
tanuki_env_copy_entry() {
  local src="$1" dst="$2" tmp target leftover
  if [[ -e "$dst" || -L "$dst" ]]; then return 0; fi
  mkdir -p "$(dirname "$dst")" || return 1
  tmp="${dst}.tmp.$$"
  rm -rf "$tmp"
  if [[ -L "$src" ]]; then
    # 顶层链接先解析，再把目标复制为独立实体；绝不把外部/相对链接原样带进 worktree。
    target=$(tanuki_env_realpath "$src") || {
      printf '[ao-worktree-env] 错误: 无法解析符号链接: %s\n' "$src" >&2
      return 1
    }
    if [[ ! -e "$target" ]]; then
      printf '[ao-worktree-env] 错误: 符号链接目标不存在: %s -> %s\n' "$src" "$(readlink "$src")" >&2
      return 1
    fi
    tanuki_env_copy_entry "$target" "$dst" || return 1
    return 0
  fi
  if [[ -d "$src" ]]; then
    tanuki_env_validate_tree_links "$src" || return 1
    # -L 完全 dereference 链接，worktree 内不保留任何链接文本；循环链接由 cp 拒绝。
    if ! cp -cRL "$src" "$tmp" 2>/dev/null; then
      # clone 失败可能已创建部分 $tmp；不清理会让普通复制嵌套成 $tmp/<basename>。
      rm -rf "$tmp"
      if ! cp -RL "$src" "$tmp"; then
        rm -rf "$tmp"
        return 1
      fi
    fi
    leftover=$(find "$tmp" -type l -print -quit 2>/dev/null)
    if [[ -n "$leftover" ]]; then
      printf '[ao-worktree-env] 错误: 复制后仍残留符号链接，拒绝使用: %s\n' "$leftover" >&2
      rm -rf "$tmp"
      return 1
    fi
    if mv "$tmp" "$dst"; then return 0; fi
    rm -rf "$tmp"; return 1
  fi
  if ! cp -c "$src" "$tmp" 2>/dev/null; then
    rm -f "$tmp"
    if ! cp -p "$src" "$tmp"; then
      rm -f "$tmp"
      return 1
    fi
  fi
  if mv "$tmp" "$dst"; then return 0; fi
  rm -f "$tmp"; return 1
}

# 只加载辅助函数（回归测试用）；正常 source 继续执行环境准备。
if [[ "${TANUKI_ENV_LIB_ONLY:-0}" == "1" ]]; then
  if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    printf '[ao-worktree-env] 错误: TANUKI_ENV_LIB_ONLY=1 只能 source 使用\n' >&2
    exit 2
  fi
  return 0
fi

tanuki_ao_worktree_env() {
  local project_root main_repo build_dir gradle_user_home android_user_home jdk sdk
  local wrapper_version dist_source dist_target entry name
  local cache_source cache_target version_cache
  local start_seconds elapsed
  local -a jdk_candidates
  local missing=()

  start_seconds=$(date +%s)
  project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P) || return 1
  build_dir="$project_root/.build"

  tanuki_env_note() { printf '[ao-worktree-env] %s\n' "$*"; }
  tanuki_env_fail() { printf '[ao-worktree-env] 错误: %s\n' "$*" >&2; return 1; }

  # 解析主仓库：linked worktree 的 --git-common-dir 指向主仓库 .git。
  main_repo="${TANUKI_MAIN_REPO:-}"
  if [[ -z "$main_repo" ]]; then
    local common_dir
    if common_dir=$(git -C "$project_root" rev-parse --git-common-dir 2>/dev/null) && [[ -n "$common_dir" ]]; then
      common_dir=$(cd "$project_root" && cd "$common_dir" && pwd -P) || common_dir=""
      [[ -n "$common_dir" ]] && main_repo=$(dirname "$common_dir")
    fi
  fi
  if [[ -n "$main_repo" ]]; then
    main_repo=$(cd "$main_repo" 2>/dev/null && pwd -P) || main_repo=""
  fi
  export TANUKI_MAIN_REPO="$main_repo"

  # 选择 JDK 17：TANUKI_JDK > worktree .build/jdk > 主仓库 .build/jdk。
  jdk=""
  jdk_candidates=()
  [[ -n "${TANUKI_JDK:-}" ]] && jdk_candidates+=("$TANUKI_JDK")
  for entry in "$project_root"/.build/jdk/*/Contents/Home; do
    [[ -x "$entry/bin/java" ]] && jdk_candidates+=("$entry")
  done
  if [[ -n "$main_repo" && "$main_repo" != "$project_root" ]]; then
    for entry in "$main_repo"/.build/jdk/*/Contents/Home; do
      [[ -x "$entry/bin/java" ]] && jdk_candidates+=("$entry")
    done
  fi
  for entry in "${jdk_candidates[@]}"; do
    [[ -x "$entry/bin/java" ]] || continue
    if [[ -f "$entry/release" ]]; then
      grep -q 'JAVA_VERSION="17' "$entry/release" || continue
    elif ! "$entry/bin/java" -version 2>&1 | grep -q 'version "17'; then
      continue
    fi
    jdk="$entry"
    break
  done
  if [[ -z "$jdk" ]]; then
    tanuki_env_fail "未找到 JDK 17。请设置 TANUKI_JDK=/path/to/jdk-17，或先在主仓库执行 scripts/prepare-build-tools.sh 下载到 .build/jdk（需网络，本脚本不自动安装）。当前主仓库: ${main_repo:-未识别}"
    return 1
  fi

  # 选择 Android SDK：TANUKI_SDK > 现有 ANDROID_HOME/ANDROID_SDK_ROOT > ~/Library/Android/sdk。
  sdk="${TANUKI_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}}"
  [[ -d "$sdk" ]] || { tanuki_env_fail "Android SDK 目录不存在: ${sdk}（可用 TANUKI_SDK 指定）"; return 1; }
  sdk=$(cd "$sdk" && pwd -P) || return 1

  [[ -d "$sdk/platforms/android-36" ]] || missing+=("platforms/android-36")
  [[ -d "$sdk/build-tools/35.0.0" ]] || missing+=("build-tools/35.0.0")
  [[ -d "$sdk/ndk/27.2.12479018" ]] || missing+=("ndk/27.2.12479018")
  if (( ${#missing[@]} > 0 )); then
    tanuki_env_fail "Android SDK 缺少组件: ${missing[*]}。请在 SDK: $sdk 中通过 sdkmanager 安装（本脚本不自动安装）。"
    return 1
  fi

  # per-worktree GRADLE_USER_HOME：沙箱只允许写当前 worktree，绝不能落回 ~/.gradle。
  gradle_user_home="$build_dir/gradle-user-home"
  case "$gradle_user_home" in
    "$project_root"/.build/*) ;;
    *) tanuki_env_fail "GRADLE_USER_HOME 解析异常（不在 worktree .build 内）: $gradle_user_home"; return 1 ;;
  esac
  if [[ "$gradle_user_home" == "$HOME/.gradle" || "$gradle_user_home" == "$HOME/.gradle/"* ]]; then
    tanuki_env_fail "拒绝使用全局 Gradle 目录: $gradle_user_home"
    return 1
  fi
  if ! mkdir -p "$gradle_user_home/caches" "$gradle_user_home/wrapper/dists"; then
    tanuki_env_fail "无法创建 per-worktree Gradle 目录: ${gradle_user_home}（检查沙箱写权限）"
    return 1
  fi

  # $HOME/.android 同样不可写：assemble/lint 需要 debug keystore 与指标目录，重定向到 worktree。
  # 注意：设备/模拟器任务应改用 scripts/env.sh，否则会看不到 ~/.android/avd 下的既有 AVD。
  android_user_home=""
  if [[ "${TANUKI_SKIP_ANDROID_USER_HOME:-0}" != "1" ]]; then
    android_user_home="${TANUKI_ANDROID_USER_HOME:-$build_dir/android-user-home}"
    case "$android_user_home" in
      "$project_root"/*) ;;
      *) tanuki_env_fail "ANDROID_USER_HOME 必须位于当前 worktree 内: $android_user_home"; return 1 ;;
    esac
    if ! mkdir -p "$android_user_home"; then
      tanuki_env_fail "无法创建 ANDROID_USER_HOME: $android_user_home"
      return 1
    fi
  fi

  # 复用已有 Gradle 发行版，避免 Wrapper 重新下载；只读复制到 worktree。
  wrapper_version=$(sed -n 's#^distributionUrl=.*gradle-\([0-9][0-9.]*\)-\(bin\|all\)\.zip$#\1#p' \
    "$project_root/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | head -n 1)
  [[ -n "$wrapper_version" ]] || wrapper_version=8.13
  dist_source="$HOME/.gradle/wrapper/dists/gradle-${wrapper_version}-bin"
  dist_target="$gradle_user_home/wrapper/dists/gradle-${wrapper_version}-bin"
  local dist_reused=0
  if [[ -d "$dist_source" ]]; then
    for entry in "$dist_source"/*/; do
      [[ -d "$entry" ]] || continue
      name=$(basename "$entry")
      [[ -x "$dist_target/$name/gradle-${wrapper_version}/bin/gradle" ]] && continue
      tanuki_env_copy_entry "${entry%/}" "$dist_target/$name" || {
        tanuki_env_fail "复用 Gradle 发行版失败: ${entry%/}"
        return 1
      }
      dist_reused=1
    done
  fi
  local dist_ready=0
  for entry in "$dist_target"/*/gradle-${wrapper_version}/bin/gradle; do
    [[ -x "$entry" ]] && dist_ready=1 && break
  done
  if (( dist_ready == 1 )); then
    tanuki_env_note "Gradle ${wrapper_version} 发行版已就绪（复用 ~/.gradle/wrapper/dists，未写全局目录）"
  else
    tanuki_env_note "警告: 未找到可复用的 Gradle ${wrapper_version} 发行版，Wrapper 将下载到 ${gradle_user_home}（需要网络）"
  fi

  # 复用依赖缓存（modules-2/jars-9/generated-gradle-jars），避免重复下载；不复制锁与 journal。
  local cache_reused=0 cache_skipped=0
  if [[ "${TANUKI_SKIP_GRADLE_CACHE_COPY:-0}" == "1" ]]; then
    cache_skipped=1
  else
    cache_source="$HOME/.gradle/caches"
    version_cache="$gradle_user_home/caches/$wrapper_version"
    for entry in "$cache_source/modules-2" "$cache_source/jars-9" "$cache_source/$wrapper_version/generated-gradle-jars"; do
      [[ -e "$entry" ]] || continue
      if [[ "$entry" == "$cache_source/$wrapper_version/generated-gradle-jars" ]]; then
        name="$version_cache/generated-gradle-jars"
      else
        name="$gradle_user_home/caches/$(basename "$entry")"
      fi
      [[ -e "$name" ]] && continue
      tanuki_env_copy_entry "$entry" "$name" || {
        tanuki_env_fail "复制 Gradle 缓存失败: $entry"
        return 1
      }
      cache_reused=1
    done
    if [[ "${TANUKI_COPY_GRADLE_VERSION_CACHE:-0}" == "1" && -d "$cache_source/$wrapper_version" ]]; then
      for entry in "$cache_source/$wrapper_version"/*; do
        [[ -e "$entry" ]] || continue
        name="$version_cache/$(basename "$entry")"
        [[ -e "$name" ]] && continue
        tanuki_env_copy_entry "$entry" "$name" || {
          tanuki_env_fail "复制 Gradle 版本缓存失败: $entry"
          return 1
        }
        cache_reused=1
      done
    fi
  fi
  if [[ "$cache_skipped" == "1" ]]; then
    tanuki_env_note "已按 TANUKI_SKIP_GRADLE_CACHE_COPY=1 跳过依赖缓存复制"
  elif [[ "$cache_reused" == "1" ]]; then
    tanuki_env_note "已复用 Gradle 依赖缓存（modules-2/jars-9，APFS 可用时为写时复制）"
  else
    tanuki_env_note "Gradle 依赖缓存已存在，跳过复制"
  fi

  # 复用原生构建源码/下载/中间产物，避免 build-native-runtime.sh 重新联网抓 PRoot/talloc。
  if [[ -n "$main_repo" && "$main_repo" != "$project_root" ]]; then
    local native_src sub
    for sub in sources downloads native; do
      native_src="$main_repo/.build/$sub"
      [[ -d "$native_src" ]] || continue
      mkdir -p "$build_dir/$sub" || return 1
      for entry in "$native_src"/*; do
        [[ -e "$entry" || -L "$entry" ]] || continue
        name=$(basename "$entry")
        tanuki_env_copy_entry "$entry" "$build_dir/$sub/$name" || {
          tanuki_env_fail "复制原生构建缓存失败: $entry"
          return 1
        }
      done
    done
    if [[ -f "$build_dir/sources/talloc-2.4.3/talloc.c" && -d "$build_dir/sources/termux-proot/.git" ]]; then
      tanuki_env_note "原生构建源码已就绪（复用主仓库 .build/sources、.build/downloads）"
    fi
  fi

  # local.properties 只在缺失时生成；必须被 .gitignore 覆盖，避免误提交本机路径。
  if [[ ! -e "$project_root/local.properties" ]]; then
    printf 'sdk.dir=%s\n' "$sdk" > "$project_root/local.properties" || {
      tanuki_env_fail "无法写入 local.properties"
      return 1
    }
  fi
  if ! git -C "$project_root" check-ignore -q local.properties 2>/dev/null; then
    tanuki_env_fail "local.properties 未被 .gitignore 忽略，拒绝继续（请检查 .gitignore）"
    return 1
  fi

  export TANUKI_PROJECT="$project_root"
  export TANUKI_JDK="$jdk"
  export TANUKI_SDK="$sdk"
  export JAVA_HOME="$jdk"
  export ANDROID_HOME="$sdk"
  export ANDROID_SDK_ROOT="$sdk"
  export GRADLE_USER_HOME="$gradle_user_home"
  if [[ -n "$android_user_home" ]]; then
    export ANDROID_USER_HOME="$android_user_home"
  fi
  case ":$PATH:" in
    *":$JAVA_HOME/bin:"*) ;;
    *) export PATH="$JAVA_HOME/bin:$PATH" ;;
  esac

  elapsed=$(( $(date +%s) - start_seconds ))
  tanuki_env_note "worktree: $project_root"
  tanuki_env_note "JDK17: $JAVA_HOME"
  tanuki_env_note "Android SDK: $ANDROID_HOME"
  tanuki_env_note "GRADLE_USER_HOME: $GRADLE_USER_HOME"
  if [[ -n "$android_user_home" ]]; then
    tanuki_env_note "ANDROID_USER_HOME: ${ANDROID_USER_HOME}（assemble/lint 用；设备测试请改用 scripts/env.sh）"
  else
    tanuki_env_note "ANDROID_USER_HOME: 未重定向（assemble/lint 可能因写 ~/.android 失败）"
  fi
  tanuki_env_note "准备完成（${elapsed}s）。可用: ./gradlew --version"
  return 0
}

if tanuki_ao_worktree_env; then
  unset -f tanuki_ao_worktree_env tanuki_env_note tanuki_env_fail tanuki_env_copy_entry tanuki_env_realpath tanuki_env_validate_tree_links
else
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
    return 1
  fi
  exit 1
fi

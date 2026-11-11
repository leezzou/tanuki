#!/bin/bash
# Tanuki 远程连接一键启动：证书、钉扎、中继、主机端 agent 和配对提示。
#
# 默认在 ~/.tanuki-relay 生成/复用自签名证书，后台启动 relay.py 与
# host_agent.py（自动带 --cert-pin），把配对链接复制到剪贴板并在终端
# 大字显示。Ctrl+C 会同时停止两个子进程，不留孤儿。
#
# 用法：bash scripts/remote-relay/quickstart.sh [选项]
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB="${HERE}/quickstart_lib.py"
GEN_CERT="${HERE}/gen-cert.sh"

PORT=8443
LISTEN=0.0.0.0
PUBLIC_HOST=""
STATE_DIR="${HOME}/.tanuki-relay"
CERT=""
KEY=""
CERT_SET=0
KEY_SET=0
SSH_HOST=127.0.0.1
SSH_PORT=22
PRINT_ONLY=0
COPY_CLIPBOARD=1
READY_TIMEOUT=10

RELAY_PID=""
AGENT_PID=""
TAIL_PID=""
PID_FILE=""
STARTED=0
CLEANED=0

usage() {
  cat <<'EOF'
Tanuki 远程连接一键启动

用法：
  bash scripts/remote-relay/quickstart.sh [选项]

默认行为：
  1. 检查 python3/openssl，必要时在 ~/.tanuki-relay 生成自签名证书；
  2. 自动计算中继证书钉扎（sha256-...），不需要手工抄写；
  3. 后台启动 TLS 中继并等待端口监听，然后启动 host_agent 并自动带 --cert-pin；
  4. 把配对链接复制到剪贴板，并在终端显示链接、6 位核对码和手机端步骤；
  5. Ctrl+C 同时停止中继与主机端 agent。

选项：
  --port N              中继端口（默认 8443）
  --listen ADDR         中继监听地址（默认 0.0.0.0）
  --public-host HOST    手机访问中继使用的地址/域名；默认自动探测局域网 IPv4
  --state-dir DIR       证书、host-id、配对文件和日志目录（默认 ~/.tanuki-relay）
  --cert PATH           使用指定证书（需与 --key 同时提供；缺少时自动生成）
  --key PATH            使用指定私钥（需与 --cert 同时提供）
  --ssh-host HOST       本机 sshd 地址（默认 127.0.0.1）
  --ssh-port N          本机 sshd 端口（默认 22）
  --print-only          只打印解析后的参数和将要执行的命令，不启动任何进程
  --no-clipboard        不自动复制配对链接（改为提示手动复制 pairing.txt）
  --ready-timeout N     等待中继/配对文件就绪的秒数（默认 10）
  -h, --help            显示本帮助

示例：
  # 同一 Wi-Fi 下，自动探测局域网地址
  bash scripts/remote-relay/quickstart.sh

  # 手机走公网域名/地址，端口 9443
  bash scripts/remote-relay/quickstart.sh --port 9443 --public-host relay.example.com

  # 只排查参数，不启动
  bash scripts/remote-relay/quickstart.sh --print-only
EOF
}

fail() {
  printf '错误：%s\n' "$*" >&2
  exit 1
}

need_value() {
  if [[ $# -lt 2 || -z "${2:-}" ]]; then
    fail "选项 $1 需要一个值"
  fi
}

expand_path() {
  case "${1:-}" in
    "~") printf '%s\n' "${HOME}" ;;
    "~/"*) printf '%s\n' "${HOME}/${1#\~/}" ;;
    *) printf '%s\n' "${1:-}" ;;
  esac
}

absolute_path() {
  case "${1:-}" in
    /*) printf '%s\n' "${1}" ;;
    *) printf '%s\n' "${PWD}/${1}" ;;
  esac
}

format_endpoint() {
  # 与 quickstart_lib.format_endpoint 保持单一实现，避免 IPv6 方括号规则分叉。
  "${PYTHON}" "${LIB}" format-endpoint --host "$1" --port "$2"
}

stop_pid() {
  local pid="$1" i
  [[ -n "${pid}" ]] || return 0
  if kill -0 "${pid}" 2>/dev/null; then
    kill -TERM "${pid}" 2>/dev/null || true
    for ((i = 0; i < 30; i++)); do
      kill -0 "${pid}" 2>/dev/null || break
      sleep 0.1
    done
    if kill -0 "${pid}" 2>/dev/null; then
      kill -KILL "${pid}" 2>/dev/null || true
    fi
  fi
  wait "${pid}" 2>/dev/null || true
}

cleanup() {
  local status=$?
  # 先摘掉所有 trap，避免 stop_pid/exit 再次触发本函数。
  trap - EXIT INT TERM HUP
  if [[ "${CLEANED}" -eq 0 ]]; then
    CLEANED=1
    if [[ -n "${TAIL_PID}" ]]; then
      kill "${TAIL_PID}" 2>/dev/null || true
    fi
    # 先停 agent，避免它在 relay 退出后继续重连。
    stop_pid "${AGENT_PID}"
    stop_pid "${RELAY_PID}"
    if [[ -n "${PID_FILE}" && -f "${PID_FILE}" ]]; then
      rm -f "${PID_FILE}"
    fi
    if [[ "${STARTED}" -eq 1 ]]; then
      printf '\n中继与主机端 agent 已停止。\n' >&2
    fi
  fi
  exit "${status}"
}

# 信号退出码显式固定，避免 trap 里的 $? 受上一条命令影响而错误返回 0。
on_int() { exit 130; }
on_term() { exit 143; }
on_hup() { exit 129; }
trap cleanup EXIT
trap on_int INT
trap on_term TERM
trap on_hup HUP

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port) need_value "$1" "${2:-}"; PORT="$2"; shift 2 ;;
    --port=*) PORT="${1#*=}"; shift ;;
    --listen) need_value "$1" "${2:-}"; LISTEN="$2"; shift 2 ;;
    --listen=*) LISTEN="${1#*=}"; shift ;;
    --public-host) need_value "$1" "${2:-}"; PUBLIC_HOST="$2"; shift 2 ;;
    --public-host=*) PUBLIC_HOST="${1#*=}"; shift ;;
    --state-dir) need_value "$1" "${2:-}"; STATE_DIR="$2"; shift 2 ;;
    --state-dir=*) STATE_DIR="${1#*=}"; shift ;;
    --cert) need_value "$1" "${2:-}"; CERT="$2"; CERT_SET=1; shift 2 ;;
    --cert=*) CERT="${1#*=}"; CERT_SET=1; shift ;;
    --key) need_value "$1" "${2:-}"; KEY="$2"; KEY_SET=1; shift 2 ;;
    --key=*) KEY="${1#*=}"; KEY_SET=1; shift ;;
    --ssh-host) need_value "$1" "${2:-}"; SSH_HOST="$2"; shift 2 ;;
    --ssh-host=*) SSH_HOST="${1#*=}"; shift ;;
    --ssh-port) need_value "$1" "${2:-}"; SSH_PORT="$2"; shift 2 ;;
    --ssh-port=*) SSH_PORT="${1#*=}"; shift ;;
    --ready-timeout) need_value "$1" "${2:-}"; READY_TIMEOUT="$2"; shift 2 ;;
    --ready-timeout=*) READY_TIMEOUT="${1#*=}"; shift ;;
    --print-only) PRINT_ONLY=1; shift ;;
    --no-clipboard) COPY_CLIPBOARD=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "未知参数：$1（用 --help 查看用法）" ;;
  esac
done

case "${PORT}" in ''|*[!0-9]*) fail "--port 必须是整数" ;; esac
[[ "${PORT}" -ge 1 && "${PORT}" -le 65535 ]] || fail "--port 必须在 1-65535 之间"
case "${SSH_PORT}" in ''|*[!0-9]*) fail "--ssh-port 必须是整数" ;; esac
[[ "${SSH_PORT}" -ge 1 && "${SSH_PORT}" -le 65535 ]] || fail "--ssh-port 必须在 1-65535 之间"
case "${READY_TIMEOUT}" in ''|*[!0-9]*) fail "--ready-timeout 必须是整数" ;; esac
[[ "${READY_TIMEOUT}" -ge 1 && "${READY_TIMEOUT}" -le 120 ]] || fail "--ready-timeout 必须在 1-120 之间"
[[ -n "${LISTEN}" ]] || fail "--listen 不能为空"
[[ -n "${SSH_HOST}" ]] || fail "--ssh-host 不能为空"
if [[ "${CERT_SET}" -ne "${KEY_SET}" ]]; then
  fail "--cert 与 --key 必须同时提供"
fi

STATE_DIR="$(absolute_path "$(expand_path "${STATE_DIR}")")"
if [[ "${CERT_SET}" -eq 1 ]]; then
  CERT="$(absolute_path "$(expand_path "${CERT}")")"
else
  CERT="${STATE_DIR%/}/relay-cert.pem"
fi
if [[ "${KEY_SET}" -eq 1 ]]; then
  KEY="$(absolute_path "$(expand_path "${KEY}")")"
else
  KEY="${STATE_DIR%/}/relay-key.pem"
fi
[[ "${STATE_DIR}" != "/" ]] || fail "--state-dir 不能是 /"

# python3 是硬前提；openssl 只在需要生成证书时使用。
command -v python3 >/dev/null 2>&1 || fail "未找到 python3；请先安装 Python 3.9+"
PYTHON="$(command -v python3)"
if ! "${PYTHON}" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 9) else 1)' 2>/dev/null; then
  fail "python3 版本过低（$("${PYTHON}" --version 2>&1)）；需要 3.9+"
fi

PUBLIC_HOST_AUTO=0
if [[ -z "${PUBLIC_HOST}" ]]; then
  PUBLIC_HOST_AUTO=1
  DETECTED_HOST="$("${PYTHON}" "${LIB}" detect-lan-ip || true)"
  if [[ -n "${DETECTED_HOST}" ]]; then
    PUBLIC_HOST="${DETECTED_HOST}"
  else
    PUBLIC_HOST=127.0.0.1
  fi
fi
"${PYTHON}" "${LIB}" check-endpoint --host "${PUBLIC_HOST}" --port "${PORT}" \
  || fail "手机可达地址无效：${PUBLIC_HOST}:${PORT}"
PAIRING_ENDPOINT="$(format_endpoint "${PUBLIC_HOST}" "${PORT}")"

if [[ "${PRINT_ONLY}" -eq 1 ]]; then
  # 只排查参数：不建目录、不生成证书、不启动进程。
  PIN=""
  if [[ -f "${CERT}" ]]; then
    PIN="$("${PYTHON}" "${LIB}" cert-pin --cert "${CERT}")"
  fi
  printf '以下为解析后的参数；--print-only 不会启动任何进程。\n\n'
  "${PYTHON}" "${LIB}" plan \
    --python "${PYTHON}" \
    --state-dir "${STATE_DIR}" \
    --cert "${CERT}" \
    --key "${KEY}" \
    --listen "${LISTEN}" \
    --port "${PORT}" \
    --public-host "${PUBLIC_HOST}" \
    --ssh-host "${SSH_HOST}" \
    --ssh-port "${SSH_PORT}" \
    --pairing-file "${STATE_DIR%/}/pairing.txt" \
    --cert-pin "${PIN}"
  exit 0
fi

case "${LISTEN}" in
  127.*|localhost|::1|"[::1]")
    case "${PUBLIC_HOST}" in
      127.*|localhost|::1|"[::1]") ;;
      *) fail "--listen 只监听回环地址，但 --public-host 是 ${PUBLIC_HOST}；请改用 --listen 0.0.0.0 或 --public-host 127.0.0.1" ;;
    esac
    ;;
esac

# 先查端口再生成证书：端口被占用时快速失败，不留下半套文件。
"${PYTHON}" "${LIB}" port-free --host "${LISTEN}" --port "${PORT}" 2>/dev/null \
  || fail "端口 ${PORT} 已被占用；请用 --port 换一个端口，或停止占用该端口的进程"

mkdir -p "${STATE_DIR}"
chmod 700 "${STATE_DIR}" 2>/dev/null || true
STATE_DIR="$(cd "${STATE_DIR}" && pwd)"
if [[ "${CERT_SET}" -eq 0 ]]; then CERT="${STATE_DIR}/relay-cert.pem"; fi
if [[ "${KEY_SET}" -eq 0 ]]; then KEY="${STATE_DIR}/relay-key.pem"; fi
PAIRING_FILE="${STATE_DIR}/pairing.txt"
PID_FILE="${STATE_DIR}/quickstart.pids"
RELAY_LOG="${STATE_DIR}/relay.log"
AGENT_LOG="${STATE_DIR}/agent.log"

if ! command -v openssl >/dev/null 2>&1; then
  if [[ -f "${CERT}" && -f "${KEY}" ]]; then
    printf '提示：未找到 openssl，继续使用已有证书 %s\n' "${CERT}" >&2
  else
    fail "未找到 openssl；首次运行需要它生成自签名证书（或用 --cert/--key 指定现有证书）"
  fi
fi

if [[ -f "${CERT}" && -f "${KEY}" ]]; then
  [[ -s "${CERT}" && -s "${KEY}" ]] || fail "证书或私钥为空：${CERT} / ${KEY}"
elif [[ -f "${CERT}" || -f "${KEY}" ]]; then
  fail "只找到证书或私钥之一（${CERT} / ${KEY}）；请删除 ${STATE_DIR} 后重试，或同时用 --cert/--key 指定"
elif [[ "${CERT_SET}" -eq 1 || "${KEY_SET}" -eq 1 ]]; then
  fail "指定的证书或私钥不存在：${CERT} / ${KEY}"
else
  printf '首次运行：在 %s 生成中继自签名证书（私钥仅本机可读）\n' "${STATE_DIR}"
  GEN_LOG="${STATE_DIR}/gen-cert.log"
  if ! bash "${GEN_CERT}" "${STATE_DIR}" >"${GEN_LOG}" 2>&1; then
    printf '生成证书失败；最近输出：\n' >&2
    tail -n 20 "${GEN_LOG}" >&2 || true
    fail "证书生成失败（${CERT}）"
  fi
  rm -f "${GEN_LOG}"
  [[ -s "${CERT}" && -s "${KEY}" ]] || fail "证书生成后仍未找到 ${CERT} / ${KEY}"
fi

KEY_PERM="$(stat -f '%Lp' "${KEY}" 2>/dev/null || stat -c '%a' "${KEY}" 2>/dev/null || true)"
case "${KEY_PERM}" in
  ''|400|600) ;;
  *) printf '警告：私钥权限为 %s，建议执行 chmod 600 %s\n' "${KEY_PERM}" "${KEY}" >&2 ;;
esac

PIN="$("${PYTHON}" "${LIB}" cert-pin --cert "${CERT}")"

STARTED=1
: > "${RELAY_LOG}"
: > "${AGENT_LOG}"
printf '启动中继：%s:%s（日志 %s）\n' "${LISTEN}" "${PORT}" "${RELAY_LOG}"
"${PYTHON}" "${LIB}" run-relay \
  --python "${PYTHON}" \
  --listen "${LISTEN}" \
  --port "${PORT}" \
  --cert "${CERT}" \
  --key "${KEY}" >>"${RELAY_LOG}" 2>&1 &
RELAY_PID=$!
printf 'relay=%s\n' "${RELAY_PID}" > "${PID_FILE}"
chmod 600 "${PID_FILE}" 2>/dev/null || true

if ! "${PYTHON}" "${LIB}" wait-port --host "${LISTEN}" --port "${PORT}" --timeout "${READY_TIMEOUT}"; then
  printf '中继未在 %s 秒内监听；最近日志：\n' "${READY_TIMEOUT}" >&2
  tail -n 20 "${RELAY_LOG}" >&2 || true
  fail "中继启动失败（端口 ${PORT}）"
fi
if ! kill -0 "${RELAY_PID}" 2>/dev/null; then
  printf '中继进程已退出；最近日志：\n' >&2
  tail -n 20 "${RELAY_LOG}" >&2 || true
  fail "中继启动失败"
fi

# 旧配对文件可能来自上次运行，先删除，避免读取过期链接。
rm -f "${PAIRING_FILE}"
printf '启动主机端 agent：连接 127.0.0.1:%s，配对地址 %s\n' "${PORT}" "${PAIRING_ENDPOINT}"
"${PYTHON}" "${LIB}" run-agent \
  --python "${PYTHON}" \
  --relay "127.0.0.1:${PORT}" \
  --ssh-host "${SSH_HOST}" \
  --ssh-port "${SSH_PORT}" \
  --state-dir "${STATE_DIR}" \
  --cert-pin "${PIN}" \
  --pairing-relay "${PAIRING_ENDPOINT}" >>"${AGENT_LOG}" 2>&1 &
AGENT_PID=$!
printf 'relay=%s\nagent=%s\n' "${RELAY_PID}" "${AGENT_PID}" > "${PID_FILE}"

PAIRING_READY=0
for ((i = 0; i < READY_TIMEOUT * 10; i++)); do
  if [[ -s "${PAIRING_FILE}" ]]; then
    PAIRING_READY=1
    break
  fi
  kill -0 "${AGENT_PID}" 2>/dev/null || break
  sleep 0.1
done
if [[ "${PAIRING_READY}" -eq 0 ]]; then
  printf '主机端 agent 未生成配对文件；最近日志：\n' >&2
  tail -n 20 "${AGENT_LOG}" >&2 || true
  fail "配对文件生成失败：${PAIRING_FILE}"
fi

PAIRING_LINK="$("${PYTHON}" "${LIB}" read-pairing --path "${PAIRING_FILE}" --field link)"
PAIRING_CODE="$("${PYTHON}" "${LIB}" read-pairing --path "${PAIRING_FILE}" --field code)"

# 等中继注册日志出现（最多 3 秒）；失败不阻塞，只在横幅里提示排查。
REGISTERED=0
for ((i = 0; i < 30; i++)); do
  if grep -q 'registered room' "${AGENT_LOG}" 2>/dev/null; then
    REGISTERED=1
    break
  fi
  kill -0 "${AGENT_PID}" 2>/dev/null || break
  sleep 0.1
done

BOLD=""
DIM=""
RESET=""
if [[ -t 1 ]]; then
  BOLD=$'\033[1m'
  DIM=$'\033[2m'
  RESET=$'\033[0m'
fi
printf '\n%s\n' "${BOLD}============================================================${RESET}"
printf '%s\n' "${BOLD}  Tanuki 远程连接已就绪${RESET}"
printf '%s\n\n' "${BOLD}============================================================${RESET}"
printf '%s\n' "  配对链接（复制到手机；含一次性密钥，勿外传）："
printf '%s\n\n' "  ${PAIRING_LINK}"
printf '%s\n\n' "  ${BOLD}6 位核对码：  >>>  ${PAIRING_CODE}  <<<${RESET}"
printf '%s\n' "  手机端下一步："
printf '%s\n' "    1. 打开 Tanuki → 设置 → 远程主机 → 新增远程连接"
printf '%s\n' "    2. 选“配对链接”：手机一般会自动读取剪贴板，读不到时粘贴上面的链接"
printf '%s\n\n' "    3. 核对手机算出的 6 位码与上方一致并确认；首次 SSH 主机密钥确认后进入远程 DSH"
printf '%s\n' "  中继监听：${LISTEN}:${PORT}（主机端连接 127.0.0.1:${PORT}）"
printf '%s\n' "  配对地址：${PAIRING_ENDPOINT}"
printf '%s\n' "  配对文件：${PAIRING_FILE}"
printf '%s\n' "  日志：${AGENT_LOG}"
if [[ "${PUBLIC_HOST_AUTO}" -eq 1 ]]; then
  case "${PUBLIC_HOST}" in
    127.*|localhost|::1|"[::1]")
      printf '%s\n' "  提示：未探测到局域网地址，配对地址是 127.0.0.1，仅本机可用；"
      printf '%s\n' "        跨设备请加 --public-host <局域网/公网地址或域名> 重新运行。"
      ;;
    *)
      printf '%s\n' "  提示：配对地址是自动探测的局域网地址；手机不在同一网络时，"
      printf '%s\n' "        请加 --public-host <公网地址或域名> 重新运行。"
      ;;
  esac
fi
if [[ "${REGISTERED}" -eq 0 ]]; then
  printf '%s\n' "  注意：主机端尚未确认注册；若手机提示 host_offline，请查看 ${AGENT_LOG}"
fi
printf '%s\n\n' "  Ctrl+C 会同时停止中继与主机端 agent"
printf '%s\n\n' "${DIM}============================================================${RESET}"

COPY_OK=0
if [[ "${COPY_CLIPBOARD}" -eq 1 ]]; then
  if command -v pbcopy >/dev/null 2>&1 && printf '%s' "${PAIRING_LINK}" | pbcopy 2>/dev/null; then
    COPY_OK=1
  elif [[ -n "${WAYLAND_DISPLAY:-}" ]] && command -v wl-copy >/dev/null 2>&1 \
    && printf '%s' "${PAIRING_LINK}" | wl-copy 2>/dev/null; then
    COPY_OK=1
  elif command -v xclip >/dev/null 2>&1 \
    && printf '%s' "${PAIRING_LINK}" | xclip -selection clipboard 2>/dev/null; then
    COPY_OK=1
  elif command -v xsel >/dev/null 2>&1 \
    && printf '%s' "${PAIRING_LINK}" | xsel --clipboard --input 2>/dev/null; then
    COPY_OK=1
  fi
fi
if [[ "${COPY_OK}" -eq 1 ]]; then
  printf '配对链接已复制到剪贴板。\n'
elif [[ "${COPY_CLIPBOARD}" -eq 1 ]]; then
  printf '未能自动复制到剪贴板；请手动打开 %s 复制。\n' "${PAIRING_FILE}"
else
  printf '已跳过剪贴板；请手动打开 %s 复制。\n' "${PAIRING_FILE}"
fi

if [[ -t 1 ]]; then
  printf '实时日志（Ctrl+C 退出）：\n'
else
  printf '运行中；Ctrl+C 停止。\n'
fi
tail -n 0 -F "${RELAY_LOG}" "${AGENT_LOG}" 2>/dev/null &
TAIL_PID=$!

while kill -0 "${AGENT_PID}" 2>/dev/null && kill -0 "${RELAY_PID}" 2>/dev/null; do
  sleep 0.5
done

if ! kill -0 "${RELAY_PID}" 2>/dev/null; then
  printf '中继已退出；最近日志：\n' >&2
  tail -n 20 "${RELAY_LOG}" >&2 || true
elif ! kill -0 "${AGENT_PID}" 2>/dev/null; then
  printf '主机端 agent 已退出；最近日志：\n' >&2
  tail -n 20 "${AGENT_LOG}" >&2 || true
fi
fail "中继或主机端 agent 已退出"

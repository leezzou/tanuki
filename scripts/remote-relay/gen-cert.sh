#!/bin/bash
# 生成中继自签名 TLS 证书（仅用于自建中继；私钥只留在主机）。
set -euo pipefail

out_dir="${1:-.}"
mkdir -p "$out_dir"
chmod 700 "$out_dir"
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 365 \
  -subj "/CN=tanuki-relay" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
  -keyout "$out_dir/relay-key.pem" -out "$out_dir/relay-cert.pem"
chmod 600 "$out_dir/relay-key.pem"
printf 'cert=%s\nkey=%s\n' "$out_dir/relay-cert.pem" "$out_dir/relay-key.pem"

#!/usr/bin/env bash
# 生成 release 签名密钥，并写出 keystore.properties。
# 两者都不会入库（.gitignore 已排除），请自行妥善备份 ——
# 一旦丢失，已发布的 APK 将无法用同一签名更新。
set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE="${1:-wuyou-release.jks}"
ALIAS="${KEY_ALIAS:-wuyou}"

if [ -f "$KEYSTORE" ]; then
    echo "错误：$KEYSTORE 已存在。如需重新生成，请先手工移走旧文件。" >&2
    exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
    echo "错误：找不到 keytool，请先安装 JDK 17 并确保它在 PATH 里。" >&2
    exit 1
fi

read -rsp "设置密钥库密码（至少 6 位）：" STORE_PASS; echo
read -rsp "再次输入：                    " STORE_PASS2; echo

if [ "$STORE_PASS" != "$STORE_PASS2" ]; then
    echo "错误：两次输入不一致。" >&2
    exit 1
fi
if [ "${#STORE_PASS}" -lt 6 ]; then
    echo "错误：密码至少 6 位。" >&2
    exit 1
fi

keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
    -dname "CN=Wuyou IME, OU=WuyouAI Lab, O=WuyouAI Lab, L=Beijing, ST=Beijing, C=CN"

cat > keystore.properties <<PROPEOF
storeFile=$KEYSTORE
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$STORE_PASS
PROPEOF

chmod 600 keystore.properties "$KEYSTORE" 2>/dev/null || true

echo ""
echo "完成："
echo "  密钥库      $KEYSTORE"
echo "  配置文件    keystore.properties"
echo ""
echo "这两个文件已在 .gitignore 中，不会提交。请离线备份密钥库 —— 丢失后无法更新已发布应用。"

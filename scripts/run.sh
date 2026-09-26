#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

JAVA_EXE="./runtime/bin/java"
if [ ! -x "$JAVA_EXE" ]; then
    echo "[ERROR] 同梱のJavaランタイムが見つかりません。runtimeフォルダの内容を確認してください。"
    read -r -p "Enterキーで終了します..." _
    exit 1
fi

JAR_FILE=$(find . -maxdepth 1 -name '*.jar' | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "[ERROR] 実行可能jarファイルが見つかりません。"
    read -r -p "Enterキーで終了します..." _
    exit 1
fi

# 失敗時（終了コード2等）もset -eで中断せず、結果のメッセージを読めるよう終了前に一時停止する
status=0
"$JAVA_EXE" -jar "$JAR_FILE" || status=$?

echo
read -r -p "Enterキーで終了します..." _
exit "$status"

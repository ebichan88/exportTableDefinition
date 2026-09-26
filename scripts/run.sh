#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# このスクリプト自体のエラーも、ツールの失敗と同じく終了コード2で返す（終了コード2以上＝失敗。1は--checkの差分あり）

JAVA_EXE="./runtime/bin/java"
if [ ! -x "$JAVA_EXE" ]; then
    echo "[ERROR] 同梱のJavaランタイムが見つかりません。runtimeフォルダの内容を確認してください。"
    read -r -p "Enterキーで終了します..." _
    exit 2
fi

JAR_FILE=$(find . -maxdepth 1 -name '*.jar' | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "[ERROR] 実行可能jarファイルが見つかりません。"
    read -r -p "Enterキーで終了します..." _
    exit 2
fi

# javaが0以外で終了した場合（失敗・JVMの起動エラー等）もset -eで中断せず、結果のメッセージを読めるよう終了前に一時停止する
status=0
"$JAVA_EXE" -jar "$JAR_FILE" || status=$?

echo
read -r -p "Enterキーで終了します..." _
exit "$status"

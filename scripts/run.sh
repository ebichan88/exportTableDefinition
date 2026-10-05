#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# このスクリプト自体のエラーも、ツールの失敗と同じく終了コード2で返す（終了コード2以上＝失敗。1は--checkの差分あり）

# 一時停止は端末から実行したときだけ行う。CI等で標準入力が端末でない場合、readが入力の終端で失敗し、
# set -eによって終了コードが1（--checkの差分あり）に化けるため
pause_if_interactive() {
    if [ -t 0 ]; then
        read -r -p "Enterキーで終了します..." _ || true
    fi
}

JAVA_EXE="./runtime/bin/java"
if [ ! -x "$JAVA_EXE" ]; then
    echo "[ERROR] 同梱のJavaランタイムが見つかりません。runtimeフォルダの内容を確認してください。"
    pause_if_interactive
    exit 2
fi

JAR_FILE=$(find . -maxdepth 1 -name '*.jar' | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "[ERROR] 実行可能jarファイルが見つかりません。"
    pause_if_interactive
    exit 2
fi

# javaが0以外で終了した場合（失敗・JVMの起動エラー等）もset -eで中断せず、結果のメッセージを読めるよう終了前に一時停止する
status=0
# 引数（--check・--output-path=... 等）はそのままツールへ渡す
"$JAVA_EXE" -jar "$JAR_FILE" "$@" || status=$?

echo
pause_if_interactive
exit "$status"

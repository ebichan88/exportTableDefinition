@echo off
setlocal

cd /d "%~dp0"

set "JAVA_EXE=%~dp0runtime\bin\java.exe"
if not exist "%JAVA_EXE%" (
    echo [ERROR] 同梱のJavaランタイムが見つかりません。runtimeフォルダの内容を確認してください。
    pause
    exit /b 2
)

set "JAR_FILE=%~dp0dbxray.jar"
if not exist "%JAR_FILE%" (
    echo [ERROR] 実行可能jarファイル（dbxray.jar）が見つかりません。
    pause
    exit /b 2
)

rem 引数（--check・--output-path=... 等）はそのままツールへ渡す
"%JAVA_EXE%" -jar "%JAR_FILE%" %*
set "STATUS=%ERRORLEVEL%"

echo.
pause
rem ツールの終了コード（1は--checkの差分あり、2以上は失敗）を呼び出し元へ返す。%STATUS%はendlocalより前に展開される
endlocal & exit /b %STATUS%

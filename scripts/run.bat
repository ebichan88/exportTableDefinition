@echo off
setlocal

cd /d "%~dp0"

set "JAVA_EXE=%~dp0runtime\bin\java.exe"
if not exist "%JAVA_EXE%" (
    echo [ERROR] 同梱のJavaランタイムが見つかりません。runtimeフォルダの内容を確認してください。
    pause
    exit /b 2
)

set "JAR_FILE="
for %%f in ("%~dp0*.jar") do set "JAR_FILE=%%~ff"

if not defined JAR_FILE (
    echo [ERROR] 実行可能jarファイルが見つかりません。
    pause
    exit /b 2
)

rem 引数（--check・--output-path=... 等）はそのままツールへ渡す
"%JAVA_EXE%" -jar "%JAR_FILE%" %*

echo.
pause
endlocal

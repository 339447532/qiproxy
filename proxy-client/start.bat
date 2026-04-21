@echo off
:: ============================================
:: qiproxy-client 启动脚本 (Windows)
:: ============================================

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo ========================================
echo   qiproxy-client 启动脚本
echo ========================================
echo.

:: 检查 Node.js 是否安装
where node >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%i in ('node --version') do set NODE_VERSION=%%i
    echo [INFO] Node.js 已安装: !NODE_VERSION!
    goto :start_client
)

echo [WARN] 未检测到 Node.js，开始自动安装...
echo.

:: 检测系统架构
if "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
    set "ARCH=x64"
    set "NODE_URL=https://npmmirror.com/mirrors/node/v20.10.0/node-v20.10.0-win-x64.zip"
) else if "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    set "ARCH=arm64"
    set "NODE_URL=https://npmmirror.com/mirrors/node/v20.10.0/node-v20.10.0-win-arm64.zip"
) else (
    echo [ERROR] 不支持的系统架构: %PROCESSOR_ARCHITECTURE%
    pause
    exit /b 1
)

set "TMP_DIR=%TEMP%\qiproxy-node-install"
mkdir "!TMP_DIR!" 2>nul

echo [INFO] 下载 Node.js...
powershell -Command "Invoke-WebRequest -Uri '!NODE_URL!' -OutFile '!TMP_DIR!\node.zip'"

echo [INFO] 安装 Node.js...
powershell -Command "Expand-Archive -Path '!TMP_DIR!\node.zip' -DestinationPath 'C:\' -Force"
move /y "C:\node-v20.10.0-win-%ARCH%" "C:\nodejs" >nul 2>&1

set "PATH=C:\nodejs;%PATH%"
echo [INFO] Node.js 安装完成: !NODE_VERSION!

:: 清理
del /f /q "!TMP_DIR!\node.zip" >nul 2>&1
rmdir "!TMP_DIR!" 2>nul

:start_client
echo.
echo [INFO] 启动 qiproxy-client...
echo.
node src\index.js

pause

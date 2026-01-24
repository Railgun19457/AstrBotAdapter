@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: AstrbotAdapter 构建脚本
:: 用法: build.bat [all|bukkit|folia|velocity]

set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=all"

set "OUTPUT_DIR=releases"
set "VERSION=2.0.0"

echo.
echo ========================================
echo    AstrbotAdapter 多平台构建脚本
echo ========================================
echo.

:: 检查 Maven
where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未找到 Maven，请确保已安装并添加到 PATH
    exit /b 1
)

:: 创建输出目录
if not exist "%OUTPUT_DIR%" (
    mkdir "%OUTPUT_DIR%"
    echo [INFO] 已创建输出目录: %OUTPUT_DIR%
)

:: 根据目标决定构建平台
if /i "%TARGET%"=="all" (
    call :build_platform bukkit
    call :build_platform folia
    call :build_platform velocity
) else if /i "%TARGET%"=="bukkit" (
    call :build_platform bukkit
) else if /i "%TARGET%"=="folia" (
    call :build_platform folia
) else if /i "%TARGET%"=="velocity" (
    call :build_platform velocity
) else (
    echo [ERROR] 未知目标: %TARGET%
    echo 用法: build.bat [all^|bukkit^|folia^|velocity]
    exit /b 1
)

echo.
echo ========================================
echo    构建完成!
echo    输出目录: %OUTPUT_DIR%
echo ========================================
echo.

dir /b "%OUTPUT_DIR%\*.jar" 2>nul
exit /b 0

:build_platform
set "PLATFORM=%~1"
set "PLATFORM_CAP=%PLATFORM%"

:: 首字母大写
for %%a in (Bukkit Folia Velocity) do (
    if /i "%PLATFORM%"=="%%a" set "PLATFORM_CAP=%%a"
)

echo [INFO] 正在构建 %PLATFORM_CAP% 版本...

call mvn package -P %PLATFORM% -DskipTests -q
if errorlevel 1 (
    echo [ERROR] %PLATFORM_CAP% 构建失败!
    exit /b 1
)

set "JAR_NAME=AstrbotAdaptor-%VERSION%-%PLATFORM_CAP%.jar"
if exist "target\%JAR_NAME%" (
    copy /y "target\%JAR_NAME%" "%OUTPUT_DIR%\" >nul
    echo [OK] %JAR_NAME%
) else (
    echo [ERROR] 找不到构建产物: target\%JAR_NAME%
)
exit /b 0

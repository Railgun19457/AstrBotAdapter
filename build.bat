@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

echo.
echo =========================================
echo   AstrbotAdaptor Release Build Script
echo =========================================
echo.

:: 项目路径
set "PROJECT_ROOT=%~dp0"
set "RELEASES_DIR=%PROJECT_ROOT%releases"
set "TARGET_DIR=%PROJECT_ROOT%target"

:: 读取版本号
for /f "tokens=3 delims=<>" %%a in ('findstr /r "<version>[0-9]" "%PROJECT_ROOT%pom.xml"') do (
    set "VERSION=%%a"
    goto :got_version
)
:got_version
echo [INFO] 版本号: %VERSION%

:: 确保releases目录存在
if not exist "%RELEASES_DIR%" mkdir "%RELEASES_DIR%"

:: 清理
echo [INFO] 清理旧构建文件...
if exist "%TARGET_DIR%" rmdir /s /q "%TARGET_DIR%"
del /q "%RELEASES_DIR%\AstrbotAdaptor-%VERSION%-*.jar" 2>nul
echo [SUCCESS] 清理完成
echo.

:: 构建Bukkit版本
echo ----------------------------------------
echo   构建 Bukkit 版本
echo ----------------------------------------
call mvn clean package -Pbukkit -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Bukkit 构建失败
    goto :error
)
copy "%TARGET_DIR%\AstrbotAdaptor-%VERSION%-Bukkit.jar" "%RELEASES_DIR%\" >nul
echo [SUCCESS] Bukkit 构建成功
echo.

:: 构建Folia版本
echo ----------------------------------------
echo   构建 Folia 版本
echo ----------------------------------------
call mvn clean package -Pfolia -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Folia 构建失败
    goto :error
)
copy "%TARGET_DIR%\AstrbotAdaptor-%VERSION%-Folia.jar" "%RELEASES_DIR%\" >nul
echo [SUCCESS] Folia 构建成功
echo.

:: 构建Velocity版本
echo ----------------------------------------
echo   构建 Velocity 版本
echo ----------------------------------------
call mvn clean package -Pvelocity -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Velocity 构建失败
    goto :error
)
copy "%TARGET_DIR%\AstrbotAdaptor-%VERSION%-Velocity.jar" "%RELEASES_DIR%\" >nul
echo [SUCCESS] Velocity 构建成功
echo.

:: 构建摘要
echo =========================================
echo   构建摘要
echo =========================================
echo.
echo 版本: %VERSION%
echo 输出目录: %RELEASES_DIR%
echo.
echo 生成的文件:
dir /b "%RELEASES_DIR%\AstrbotAdaptor-%VERSION%-*.jar"
echo.
echo [SUCCESS] 所有平台构建成功！
goto :end

:error
echo.
echo [ERROR] 构建过程中发生错误
exit /b 1

:end
endlocal

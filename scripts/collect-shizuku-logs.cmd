@echo off
setlocal
set "FTA_ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "FTA_LOG=%~dp0..\shizuku-log.txt"

if not exist "%FTA_ADB%" (
    echo Android SDK adb.exe was not found at:
    echo %FTA_ADB%
    echo Set FTA_ADB in this script to your Android SDK platform-tools path.
    exit /b 1
)

"%FTA_ADB%" devices -l
"%FTA_ADB%" get-state >nul 2>&1
if errorlevel 1 (
    echo Connect exactly one device and accept its USB debugging prompt, then retry.
    exit /b 1
)

echo.
echo Open FTA on the phone and tap the Shizuku authorize / reconnect button.
echo Wait until the connection timeout is displayed, then immediately return here.
pause

> "%FTA_LOG%" echo FTA Shizuku connection diagnostic
>> "%FTA_LOG%" echo Captured at %DATE% %TIME%
>> "%FTA_LOG%" echo [Android version]
"%FTA_ADB%" shell getprop ro.build.version.release >> "%FTA_LOG%" 2>&1
"%FTA_ADB%" shell getprop ro.build.version.sdk >> "%FTA_LOG%" 2>&1
>> "%FTA_LOG%" echo [Device model]
"%FTA_ADB%" shell getprop ro.product.manufacturer >> "%FTA_LOG%" 2>&1
"%FTA_ADB%" shell getprop ro.product.model >> "%FTA_LOG%" 2>&1
>> "%FTA_LOG%" echo [FTA package]
"%FTA_ADB%" shell dumpsys package com.fta.senior 2>&1 | findstr /i "versionCode versionName targetSdk codePath flags" >> "%FTA_LOG%"
>> "%FTA_LOG%" echo [Shizuku package]
"%FTA_ADB%" shell dumpsys package moe.shizuku.privileged.api 2>&1 | findstr /i "versionCode versionName" >> "%FTA_LOG%"
>> "%FTA_LOG%" echo [Related processes]
"%FTA_ADB%" shell ps -A 2>&1 | findstr /i "shizuku com.fta.senior" >> "%FTA_LOG%"
>> "%FTA_LOG%" echo [Recent startup context - all priorities, including warning exception stacks]
rem Shizuku's service-loader tag varies between versions. Its startup exceptions
rem are logged as warnings, so a fixed tag list with *:E can hide the root cause.
"%FTA_ADB%" logcat -d -b main -b system -b crash -v threadtime -t 10000 "*:V" >> "%FTA_LOG%" 2>&1
if errorlevel 1 (
    echo Log collection failed. Details were saved to %FTA_LOG%
    exit /b 1
)
echo.
echo Saved: %FTA_LOG%
echo Logs may contain package names and device information. Keep this file local.
endlocal

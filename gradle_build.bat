@echo off
cd %~dp0

REM Redirect both stdout and stderr to log.txt
gradle clean build > build.log 2>&1

IF %ERRORLEVEL% NEQ 0 (
    echo.
    echo Gradle build FAILED! See build.log for details.
) ELSE (
    echo.
    echo Gradle build succeeded! See build.log for details.
)
echo.
pause

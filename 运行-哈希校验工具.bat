@echo off
rem ============================================================
rem  Local File Integrity Hash Checker - Windows Launcher
rem  Uses javaw (no console window) when available.
rem  (The GUI itself is fully in Chinese.)
rem ============================================================
setlocal

set "JAR=%~dp0target\hash-check-tool-1.0.0.jar"

if not exist "%JAR%" (
    echo [ERROR] Executable jar not found:
    echo         %JAR%
    echo Please run "mvn clean package" in the project root first.
    pause
    exit /b 1
)

where javaw >nul 2>nul
if %errorlevel%==0 (
    start "" javaw -jar "%JAR%"
    exit /b 0
)

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java was not found. Please install JRE 8 or later and add it to PATH.
    pause
    exit /b 1
)

java -jar "%JAR%"
endlocal

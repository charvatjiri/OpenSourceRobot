@echo off
set "SCRIPT_DIR=%~dp0"
call "%SCRIPT_DIR%..\client\gradlew.bat" -p "%SCRIPT_DIR%" %*

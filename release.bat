@echo off
rem Pubblica una nuova versione di Money su GitHub.
rem   release.bat          aumenta la patch   (1.2.3 -> 1.2.4)
rem   release.bat minor    aumenta la minor   (1.2.3 -> 1.3.0)
rem   release.bat major    aumenta la major   (1.2.3 -> 2.0.0)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\release.ps1" %*
set EXITCODE=%ERRORLEVEL%
echo.
pause
exit /b %EXITCODE%

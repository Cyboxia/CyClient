@echo off
REM Cy Client: builds the mod .jar into build\libs (for use in Prism Launcher or the vanilla launcher with Fabric).
cd /d "%~dp0"
echo ==== Cy Client build %date% %time% ==== > build-log.txt
call gradlew.bat build --console=plain >> build-log.txt 2>&1
echo --- exit code %ERRORLEVEL% --- >> build-log.txt
echo.
echo Done. The jar is in build\libs. Log saved to build-log.txt
pause

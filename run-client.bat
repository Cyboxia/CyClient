@echo off
REM Cy Client: builds the mod and launches a test copy of Minecraft 26.3.
REM Output shows in this window AND is saved to build-log.txt so Claude can read it.
cd /d "%~dp0"

REM Copy the Minecraft game jar to mc-ref\ so Claude can look up exact 26.3 class and method names.
if not exist mc-ref mkdir mc-ref
if not exist mc-ref\minecraft-26.3.jar (
  for /r ".gradle\loom-cache\minecraftMaven" %%f in (minecraft-merged-*-26.3.jar) do copy /y "%%f" mc-ref\minecraft-26.3.jar >nul
)

echo Building Cy Client and launching Minecraft... (first run can take a few minutes)
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "& .\gradlew.bat runClient --console=plain 2>&1 | ForEach-Object { \"$_\" } | Tee-Object -FilePath build-log.txt"
echo.
echo Done. Log saved to build-log.txt
pause

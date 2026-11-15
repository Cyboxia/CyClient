@echo off
REM Builds Cy Launcher (CyLauncher.exe) with the C# compiler that comes with Windows,
REM builds the Cy Client mod, and puts everything in launcher-dist\ ready to use or share.
cd /d "%~dp0"
set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
set LOG=launcher-build-log.txt
echo ==== Cy Launcher build %date% %time% ==== > %LOG%

if not exist "%CSC%" (
  echo Could not find the .NET Framework C# compiler at %CSC% >> %LOG%
  echo Could not find the .NET Framework C# compiler. See %LOG%
  pause
  exit /b 1
)
if not exist launcher-dist mkdir launcher-dist

REM Close any running copy so the compiler can overwrite CyLauncher.exe.
taskkill /f /im CyLauncher.exe >nul 2>&1

echo [1/3] Compiling CyLauncher.exe...
"%CSC%" /nologo /target:winexe /optimize+ /platform:anycpu ^
  /out:launcher-dist\CyLauncher.exe ^
  /win32icon:launcher\icon.ico /win32manifest:launcher\app.manifest ^
  /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll ^
  /r:System.Net.Http.dll /r:System.Web.Extensions.dll /r:System.Security.dll ^
  /resource:launcher\logo.png,CyLauncher.logo.png /resource:launcher\banner.png,CyLauncher.banner.png ^
  /resource:launcher\bg.png,CyLauncher.bg.png /resource:launcher\bg_blur.png,CyLauncher.bg_blur.png ^
  /resource:launcher\default-skin.png,CyLauncher.default-skin.png ^
  /resource:launcher\fonts\BricolageGrotesque-400.ttf,CyLauncher.BricolageGrotesque-400.ttf ^
  /resource:launcher\fonts\BricolageGrotesque-500.ttf,CyLauncher.BricolageGrotesque-500.ttf ^
  /resource:launcher\fonts\BricolageGrotesque-600.ttf,CyLauncher.BricolageGrotesque-600.ttf ^
  /resource:launcher\fonts\BricolageGrotesque-700.ttf,CyLauncher.BricolageGrotesque-700.ttf ^
  launcher\src\*.cs >> %LOG% 2>&1
set CSC_RESULT=%ERRORLEVEL%
echo --- csc exit code %CSC_RESULT% --- >> %LOG%
if not "%CSC_RESULT%"=="0" (
  echo Compile failed. Details are in %LOG%
  pause
  exit /b 1
)

echo [2/3] Building the Cy Client mod (this can take a minute)...
call gradlew.bat build --console=plain >> %LOG% 2>&1
echo --- gradle exit code %ERRORLEVEL% --- >> %LOG%
del /q launcher-dist\cyclient*.jar 2>nul
for %%f in (build\libs\cyclient-*.jar) do copy /y "%%f" launcher-dist\ >nul

echo [3/3] Copying the setup guide...
copy /y launcher\SETUP-LOGIN.txt launcher-dist\ >nul

echo.
echo Done! Your launcher is in the launcher-dist folder:
dir /b launcher-dist
echo.
pause

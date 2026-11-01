@echo off
REM Kopiert die gebaute Mod-Datei in den Mindustry-Mod-Ordner (%AppData%\Mindustry\mods).
setlocal
cd /d "%~dp0"
set SRC=build\libs\flux-protocolDesktop.jar
if not exist "%SRC%" set SRC=build\manual\flux-protocol.jar
if not exist "%SRC%" (
  echo Keine gebaute Jar gefunden. Zuerst build.bat ausfuehren.
  exit /b 1
)
set DEST=%AppData%\Mindustry\mods
if not exist "%DEST%" mkdir "%DEST%"
del /q "%DEST%\flux-protocol*.jar" 2>nul
copy /y "%SRC%" "%DEST%\flux-protocol.jar"
echo.
echo Installiert nach %DEST%\flux-protocol.jar - Mindustry neu starten.

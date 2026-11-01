@echo off
REM Baut den Mod mit Gradle (empfohlen). Ergebnis: build\libs\flux-protocolDesktop.jar
cd /d "%~dp0"
call gradlew.bat jar --console=plain
if errorlevel 1 (
  echo.
  echo BUILD FEHLGESCHLAGEN - bitte die komplette Ausgabe oben kopieren und senden.
  exit /b 1
)
echo.
echo FERTIG: %CD%\build\libs\flux-protocolDesktop.jar
echo Installieren mit: install.bat

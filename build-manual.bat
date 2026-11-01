@echo off
REM Plan B ohne Gradle: javac + jar. Benoetigt ein JDK 17+ im PATH (javac, jar) und curl (ab Windows 10 dabei).
REM Ergebnis: build\manual\flux-protocol.jar
setlocal
cd /d "%~dp0"
if not exist build\deps mkdir build\deps
if not exist build\deps\dependencies.jar (
  echo Lade Mindustry v160.7 dependencies.jar ...
  curl -L -o build\deps\dependencies.jar https://github.com/Anuken/Mindustry/releases/download/v160.7/dependencies.jar
  if errorlevel 1 exit /b 1
)
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
if not exist build\manual mkdir build\manual
powershell -NoProfile -Command "Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName.Replace('\','/') } | Set-Content -Encoding ascii build\sources.txt"
javac --release 17 -encoding UTF-8 -proc:none -sourcepath "" -d build\classes -cp build\deps\dependencies.jar @build\sources.txt
if errorlevel 1 (
  echo KOMPILIERFEHLER - Ausgabe oben kopieren und senden.
  exit /b 1
)
jar cf build\manual\flux-protocol.jar -C build\classes . -C assets . mod.hjson icon.png
if errorlevel 1 exit /b 1
echo.
echo FERTIG: %CD%\build\manual\flux-protocol.jar

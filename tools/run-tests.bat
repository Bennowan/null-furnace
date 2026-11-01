@echo off
REM Spielunabhaengige Tests (kein JUnit, kein Mindustry noetig): Flux-Logik und freie Materialsynthese. Benoetigt JDK 17+.
setlocal
cd /d "%~dp0\.."
if exist build\tests rmdir /s /q build\tests
mkdir build\tests\main
mkdir build\tests\test
mkdir build\tests\material
javac --release 17 -encoding UTF-8 -d build\tests\main src\skillable\flux\FluxMath.java src\skillable\flux\ConverterProfile.java src\skillable\flux\FluxSolver.java src\skillable\flux\InstabilityModel.java src\skillable\flux\FluxMember.java src\skillable\flux\FluxSource.java src\skillable\flux\FluxConsumer.java src\skillable\flux\FluxNetwork.java
if errorlevel 1 exit /b 1
javac --release 17 -encoding UTF-8 -d build\tests\test -cp build\tests\main test\skillable\FluxTests.java
if errorlevel 1 exit /b 1
java -cp build\tests\test;build\tests\main skillable.FluxTests
if errorlevel 1 exit /b 1
echo.
echo === Materialsynthese ===
javac --release 17 -encoding UTF-8 -d build\tests\material src\skillable\material\*.java test\skillable\MaterialFixtures.java test\skillable\MaterialTests.java test\skillable\RecordingCanvas.java test\skillable\ReactionTests.java
if errorlevel 1 exit /b 1
java -cp build\tests\material skillable.MaterialTests
if errorlevel 1 exit /b 1
echo.
echo === Thermik, Zustaende, Effektlogik ===
java -cp build\tests\material skillable.ReactionTests

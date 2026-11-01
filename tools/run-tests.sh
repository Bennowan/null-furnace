#!/bin/sh
# Spielunabhaengige Tests: Flux-Logik (FluxTests) und freie Materialsynthese (MaterialTests). Braucht nur ein JDK 17.
cd "$(dirname "$0")/.." && rm -rf build/tests && mkdir -p build/tests/main build/tests/test build/tests/material
F=src/skillable/flux
javac --release 17 -encoding UTF-8 -d build/tests/main $F/FluxMath.java $F/ConverterProfile.java $F/FluxSolver.java $F/InstabilityModel.java $F/FluxMember.java $F/FluxSource.java $F/FluxConsumer.java $F/FluxNetwork.java &&
javac --release 17 -encoding UTF-8 -d build/tests/test -cp build/tests/main test/skillable/FluxTests.java &&
java -cp build/tests/test:build/tests/main skillable.FluxTests || exit 1
echo
echo "=== Materialsynthese ==="
javac --release 17 -encoding UTF-8 -d build/tests/material src/skillable/material/*.java test/skillable/MaterialFixtures.java test/skillable/MaterialTests.java test/skillable/RecordingCanvas.java test/skillable/ReactionTests.java &&
java -cp build/tests/material skillable.MaterialTests &&
echo &&
echo "=== Thermik, Zustaende, Effektlogik ===" &&
java -cp build/tests/material skillable.ReactionTests

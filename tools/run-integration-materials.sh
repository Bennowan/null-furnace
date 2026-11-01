#!/bin/sh
# Entwickler-Werkzeug: Headless-Integrationstest der FREIEN MATERIALSYNTHESE im echten Mindustry-Server v160.7.
# Benoetigt: JDK 17+, dependencies.jar und server-release.jar von github.com/Anuken/Mindustry/releases/tag/v160.7
# Umgebungsvariablen: DEP (dependencies.jar), SRV (Ordner mit server.jar). Log: /tmp/matintegration.log
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEP="${DEP:-$ROOT/../deps/dep_v160.7.jar}"
SRV="${SRV:-$ROOT/../srv}"
rm -rf "$ROOT/out" /tmp/mst /tmp/modjar /tmp/mstjar && mkdir -p "$ROOT/out" /tmp/mst/out /tmp/modjar /tmp/mstjar
javac --release 17 -encoding UTF-8 -proc:none -sourcepath "" -d "$ROOT/out" -cp "$DEP" $(find "$ROOT/src" -name '*.java')
javac --release 17 -encoding UTF-8 -proc:none -sourcepath "" -d /tmp/mst/out -cp "$DEP:$ROOT/out" "$ROOT/tools/selftest-materials/src/fluxselftest/MaterialSelfTest.java"
cp -r "$ROOT/out/"* "$ROOT/assets/"* /tmp/modjar/ && cp "$ROOT/mod.hjson" "$ROOT/icon.png" /tmp/modjar/
(cd /tmp/modjar && jar cf /tmp/flux-protocol.jar .)
cp -r /tmp/mst/out/* /tmp/mstjar/ && cp "$ROOT/tools/selftest-materials/mod.hjson" /tmp/mstjar/
(cd /tmp/mstjar && jar cf /tmp/flux-matselftest.jar .)
mkdir -p "$SRV/config/mods" && rm -f "$SRV/config/mods/flux-selftest.jar" && cp /tmp/flux-protocol.jar /tmp/flux-matselftest.jar "$SRV/config/mods/"
cd "$SRV" && timeout 1500 java -Djava.awt.headless=true -jar server.jar < /dev/null > /tmp/matintegration.log 2>&1 || true
grep "MATTEST" /tmp/matintegration.log | sed 's/\x1b\[[0-9;]*m//g' | grep -E "FAIL|ERGEBNIS" || true

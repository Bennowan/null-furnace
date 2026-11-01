#!/bin/sh
# Entwickler-Werkzeug: baut Mod + Selftest-Mod und startet einen echten Mindustry-Headless-Server (v160.7).
# Benoetigt: JDK 17+, dependencies.jar und server-release.jar von github.com/Anuken/Mindustry/releases/tag/v160.7
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEP="${DEP:-$ROOT/../deps/dep_v160.7.jar}"
SRV="${SRV:-$ROOT/../srv}"
rm -rf "$ROOT/out" /tmp/st /tmp/modjar /tmp/stjar && mkdir -p "$ROOT/out" /tmp/st/out /tmp/modjar /tmp/stjar
javac --release 17 -encoding UTF-8 -proc:none -sourcepath "" -d "$ROOT/out" -cp "$DEP" $(find "$ROOT/src" -name '*.java')
javac --release 17 -encoding UTF-8 -proc:none -sourcepath "" -d /tmp/st/out -cp "$DEP:$ROOT/out" "$ROOT/tools/selftest/src/fluxselftest/SelfTestMod.java"
cp -r "$ROOT/out/"* "$ROOT/assets/"* /tmp/modjar/ && cp "$ROOT/mod.hjson" "$ROOT/icon.png" /tmp/modjar/
(cd /tmp/modjar && jar cf /tmp/flux-protocol.jar .)
cp -r /tmp/st/out/* /tmp/stjar/ && cp "$ROOT/tools/selftest/mod.hjson" /tmp/stjar/
(cd /tmp/stjar && jar cf /tmp/flux-selftest.jar .)
mkdir -p "$SRV/config/mods" && rm -f "$SRV/config/mods/flux-matselftest.jar" && cp /tmp/flux-protocol.jar /tmp/flux-selftest.jar "$SRV/config/mods/"
cd "$SRV" && timeout 1500 java -Djava.awt.headless=true -jar server.jar < /dev/null > /tmp/integ-full.log 2>&1

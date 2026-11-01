# PROJECT_RULES – Regeln für dieses Projekt (gelten dauerhaft)

**Dieses Projekt liegt öffentlich auf GitHub** (Autor: **Bennowan**). Alles, was committet wird, ist weltweit sichtbar – auch in der Git-Historie, auch nach einem späteren Löschen.
Jede zukünftige Änderung (durch Menschen **und** durch KI-Assistenten) muss die folgenden Regeln einhalten. Bei Zweifeln gilt: **nicht veröffentlichen und nachfragen.**

## 1. Veröffentlichungsregeln – was ins Repository darf

**Erlaubt:** Quellcode (`src/`, `test/`, `tools/`), Assets (`assets/`, `icon.png`), Dokumentation (`docs/`, `README.md`, `CHANGELOG.md`, diese Datei), Build-Skripte
(`build.gradle.kts`, `settings.gradle.kts`, `gradlew*`, `gradle/wrapper/*`, `build*.bat`, `install.bat`), `mod.hjson`, `.gitignore`, `.gitattributes`.

**Nicht erlaubt (muss durch `.gitignore` ausgeschlossen bleiben):**
* Build-Ergebnisse und Zwischenstände: `build/`, `out/`, `out-test/`, `.gradle/`, `*.class`, `*.jar` (**einzige Ausnahme:** `gradle/wrapper/gradle-wrapper.jar`), ZIP-Archive der Mod.
* Heruntergeladene Abhängigkeiten und Testumgebungen: `dependencies.jar`, `server-release.jar`, lokale Server-Ordner, `deps/`, `srv/`.
* Spielstände und Spieldaten (`*.msav`, `*.msch`), Logs (`*.log`), Absturzberichte (`hs_err_pid*`), temporäre Dateien.
* IDE- und Betriebssystemdateien (`.idea/`, `.vscode/`, `*.iml`, `.DS_Store`, `Thumbs.db` …).
* Lokale Konfiguration und Geheimnisse (siehe Abschnitt 2).

Neue Dateitypen, Werkzeuge oder Ausgabeordner dürfen erst eingeführt werden, **nachdem** ihre Artefakte in der `.gitignore` abgedeckt sind (Abschnitt 3).

## 2. Datenschutzregeln – was nie im Repository stehen darf

* **Keine lokalen Pfade** der Entwicklungsrechner: keine Laufwerks- oder Home-Pfade (`C:\Users\…`, `D:\…`, `/home/<name>/…`, `/Users/<name>/…`, `/mnt/…`), keine privaten Ordnernamen.
  In Anleitungen nur generische Platzhalter verwenden (`%APPDATA%`, `~`, relative Pfade, `<Spielordner>`).
* **Keine privaten Daten:** keine Klarnamen, E-Mail-Adressen, Telefonnummern, Adressen, IP-Adressen, Rechner-/Netzwerknamen. Der öffentliche Name ist ausschließlich **Bennowan**.
* **Keine Zugangsdaten:** keine Passwörter, Tokens (`ghp_…`, API-Schlüssel), privaten Schlüssel (`*.pem`, `*.key`, Keystores), `.env`-Dateien, Cookies, Sitzungsdaten.
* **Keine Fremdinhalte unklarer Herkunft:** kein kopierter Mindustry-Quellcode, keine fremden Grafiken/Töne ohne Lizenzklärung. Abhängigkeiten werden per Skript bezogen, nicht eingecheckt.
* Logs, Screenshots, Testausgaben und Stacktraces vor dem Veröffentlichen auf Pfade und Namen prüfen und bereinigen.
* Versehentlich committete private Daten sind **nicht** mit einem weiteren Commit erledigt: Historie bereinigen (z. B. `git filter-repo`), betroffene Zugangsdaten sofort widerrufen/ändern.

## 3. `.gitignore`-Regeln

* Die `.gitignore` ist Teil des Projekts und wird bei jeder Änderung an Build, Tools oder Dateistruktur **mitgeprüft und mitaktualisiert**.
* Neue Artefakte (z. B. neuer Ausgabeordner, neue Cache-Verzeichnisse, neue Testdaten) werden **im selben Commit** in die `.gitignore` aufgenommen.
* Ausnahmen (`!…`) nur für Dateien, die wirklich zum Quellstand gehören und keine privaten Daten enthalten (heute: `gradle/wrapper/gradle-wrapper.jar`).
* Einträge werden nicht gelöscht, nur weil sie „stören“; erst prüfen, ob die Datei veröffentlicht werden darf.
* `.gitattributes` hält Zeilenenden stabil (`*.bat` CRLF, `*.sh`/`gradlew` LF).

## 4. Pflichtprüfung vor jedem Commit/Release

1. `python tools/check_public.py` ausführen – muss **ohne Befunde** enden (prüft verbotene Dateien, lokale Pfade, E-Mail-Adressen, Zugangsdaten, Autorenangabe, Pflichteinträge der `.gitignore`).
2. `git status` ansehen: nur beabsichtigte Dateien, keine Build-Artefakte.
3. `git diff --staged` lesen: keine Pfade, Namen, Tokens, Logauszüge.
4. Tests und Build laufen lassen (Abschnitt 6) und die Ergebnisse in `docs/TESTING.md` **wahrheitsgemäß** festhalten.

## 5. Autorenangabe

* Der Mod-Autor ist überall **Bennowan**: `mod.hjson` (`author`), Test-Mods unter `tools/`, Dokumentation, Kommentare, Kopfzeilen.
* Keine anderen Namen oder Kürzel als Autor eintragen. Fremde Urheberhinweise in übernommenen Standarddateien (z. B. Gradle-Wrapper, Apache-2.0) bleiben unverändert.

## 6. Technische und inhaltliche Leitplanken

* Zielplattform: **Mindustry 160.7**, **JDK 17**. Bauen mit `build.bat` (Gradle) oder `build-manual.bat` (nur `javac`/`jar`); Tests mit `tools/run-tests.bat` (spielunabhängig) und – für Entwickler unter Linux – `tools/run-integration*.sh`.
* Bestehendes bewahren: funktionierende Systeme (freie Synthese, Materialidentität, Flux-Infrastruktur) werden **erweitert, nicht neu gebaut**. Spielstände älterer Versionen müssen lesbar bleiben.
* Materialidentität: keine Items zur Laufzeit registrieren; Träger-Items, Geschosstypen und Effekte werden beim Laden in fester Reihenfolge angelegt (deterministische IDs für Mehrspieler).
* Spielmechanik und Darstellung bleiben getrennt; sichtbare Effekte dürfen keine Wirkung vortäuschen, die es im Spiel nicht gibt.
* Tests erweitern statt nur neue zu schreiben; was nicht getestet wurde (Grafik, Mehrspieler, Windows-Skripte, Gradle), wird als **nicht getestet** gekennzeichnet – nie als erfolgreich behauptet.

## 7. Lizenz

Aktuell ist **keine Lizenzdatei** festgelegt. Ohne Lizenz gelten standardmäßig alle Rechte beim Autor. Die Wahl einer Lizenz trifft allein der Autor (Bennowan); bis dahin keine Fremdcode-Übernahmen mit Lizenzpflichten ohne Rücksprache.

#!/usr/bin/env python3
"""Pruefung vor der Veroeffentlichung auf GitHub (siehe PROJECT_RULES.md).

Findet verbotene Dateien, lokale Pfade, private Daten, Zugangsdaten, falsche Autorenangaben und fehlende .gitignore-Eintraege.
Aufruf (aus dem Projektordner):  python tools/check_public.py      Rueckgabewert 0 = keine Befunde.
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
AUTHOR = "Bennowan"

SKIP_DIRS = {".git"}
#Ordner, die gar nicht erst veroeffentlicht werden duerfen -> als Befund melden, wenn sie existieren
FORBIDDEN_DIRS = {"build", "out", "out-test", ".gradle", "__pycache__", ".idea", ".vscode", "deps", "srv"}
FORBIDDEN_FILE_PATTERNS = [r"\.class$", r"\.log$", r"\.msav$", r"\.msch$", r"\.zip$", r"hs_err_pid", r"\.DS_Store$", r"Thumbs\.db$",
                           r"\.env($|\.)", r"\.pem$", r"\.key$", r"\.keystore$", r"\.p12$", r"local\.properties$", r"\.iml$"]
ALLOWED_JARS = {"gradle/wrapper/gradle-wrapper.jar"}
SELF = {"PROJECT_RULES.md", "tools/check_public.py"}  # dokumentieren die Muster selbst

TEXT_EXT = {".java", ".md", ".txt", ".properties", ".hjson", ".kts", ".bat", ".sh", ".py", ".json", ".gradle", ".cmd", ".yml", ".yaml", ""}

CONTENT_PATTERNS = [
    ("lokaler Windows-Pfad", re.compile(r"(?<![A-Za-z0-9%])[A-Za-z]:\\\\?(?!\\)[A-Za-z0-9_ .-]+\\")),
    ("lokaler Linux/Mac-Home-Pfad", re.compile(r"(?<![A-Za-z0-9_.-])/(home|Users|mnt)/[A-Za-z0-9_.-]+")),
    ("AppData-Pfad mit festem Benutzer", re.compile(r"AppData\\Roaming\\(?!\.?mindustry|Mindustry)[A-Za-z]")),
    ("E-Mail-Adresse", re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z]{2,}")),
    ("GitHub-Token", re.compile(r"gh[pousr]_[A-Za-z0-9]{20,}")),
    ("AWS-Schluessel", re.compile(r"AKIA[0-9A-Z]{16}")),
    ("privater Schluessel", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("Passwort/Token-Zuweisung", re.compile(r"(?i)(password|passwd|secret|api[_-]?key|access[_-]?token)\s*[=:]\s*['\"]?[A-Za-z0-9_\-]{6,}")),
    ("IPv4-Adresse", re.compile(r"(?<![0-9.])(?!127\.0\.0\.1|0\.0\.0\.0)(\d{1,3}\.){3}\d{1,3}(?![0-9.])")),
]
#Zeilen, die Versionsnummern o. Ae. enthalten und keine IP sind, werden ueber diese Ausnahmen herausgefiltert
IP_FALSE_POSITIVE = re.compile(r"(?i)\b(version|ver)\b\s*[:=]?\s*\d")

REQUIRED_GITIGNORE = ["/build/", "/out/", ".gradle/", "*.class", "*.jar", "!gradle/wrapper/gradle-wrapper.jar", "*.log", "*.msav", ".idea/", ".vscode/",
                      ".env", "local.properties", "__pycache__/", ".DS_Store", "dependencies.jar", "server-release.jar"]


def rel(p):
    return os.path.relpath(p, ROOT).replace(os.sep, "/")


def main():
    problems = []
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for d in list(dirs):
            if d in FORBIDDEN_DIRS:
                problems.append(f"verbotener Ordner vorhanden (nicht veroeffentlichen): {rel(os.path.join(base, d))}/")
                dirs.remove(d)
        for f in files:
            p = os.path.join(base, f)
            r = rel(p)
            if f.endswith(".jar") and r not in ALLOWED_JARS:
                problems.append(f"verbotene Datei (nur der Gradle-Wrapper darf als .jar mit): {r}")
            for pat in FORBIDDEN_FILE_PATTERNS:
                if re.search(pat, f):
                    problems.append(f"verbotene Datei: {r}")
                    break
            ext = os.path.splitext(f)[1].lower()
            if r in SELF or ext not in TEXT_EXT or ext == ".png":
                continue
            if f == "gradlew" or f == "gradlew.bat" or r.startswith("gradle/"):
                continue  # unveraenderte Standarddateien des Gradle-Projekts (Apache 2.0)
            try:
                text = open(p, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            for n, line in enumerate(text.splitlines(), 1):
                for name, rx in CONTENT_PATTERNS:
                    m = rx.search(line)
                    if not m:
                        continue
                    if name == "IPv4-Adresse" and IP_FALSE_POSITIVE.search(line):
                        continue
                    if name == "E-Mail-Adresse" and "gradle" in line.lower():
                        continue
                    problems.append(f"{name}: {r}:{n}: {line.strip()[:110]}")
            if ext == ".java" and re.search(r"[^\x00-\x7F]", text):
                problems.append(f"Nicht-ASCII-Zeichen im Quellcode (Build ohne UTF-8-Einstellung wuerde scheitern): {r}")
            if f == "mod.hjson":
                m = re.search(r'^\s*author\s*:\s*"?([^"\n]+)"?', text, re.M)
                if not m or m.group(1).strip() != AUTHOR:
                    problems.append(f"Autorenangabe in {r} ist nicht '{AUTHOR}': {m.group(1).strip() if m else 'fehlt'}")
    gi = os.path.join(ROOT, ".gitignore")
    if not os.path.exists(gi):
        problems.append(".gitignore fehlt")
    else:
        lines = {l.strip() for l in open(gi, encoding="utf-8").read().splitlines()}
        for req in REQUIRED_GITIGNORE:
            if req not in lines:
                problems.append(f".gitignore enthaelt den Pflichteintrag nicht: {req}")
    for must in ("PROJECT_RULES.md", ".gitattributes", "README.md", "mod.hjson"):
        if not os.path.exists(os.path.join(ROOT, must)):
            problems.append(f"Pflichtdatei fehlt: {must}")
    if problems:
        print("BEFUNDE (" + str(len(problems)) + "):")
        for pr in problems:
            print("  - " + pr)
        return 1
    print("Keine Befunde: Das Projekt ist fuer die Veroeffentlichung vorbereitet (Regeln: PROJECT_RULES.md).")
    return 0


if __name__ == "__main__":
    sys.exit(main())

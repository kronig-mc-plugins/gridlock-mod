# Changelog

## [1.0.0] – 2026-09-20

Erste Version für Minecraft **26.3** (Fabric), passend zum GridLock-Plugin ab **1.7.0** (Protokoll 1).

### Hinzugefügt
- Harter Stopp an der Feld-Border: Der Client kollidiert selbst mit den Kanten, ohne Zurücksetzen durch den Server. Reine Kollisionsformen, nichts blockiert Klicks oder Platzieren.
- Border wird vom Client gezeichnet: dünne Leuchtlinien am Geländeprofil, weicher Schimmer, Wand bis zur Oberfläche, stufenloser Farbwechsel beim Erweitern und grüner Blitz beim neuen Block.
- Anmeldung beim Plugin mit Versionsprüfung. Bei einem Fehler meldet sich der Mod ab, und der Server zeigt wieder die normale Border.
- Tut nichts auf Servern ohne GridLock-Plugin.

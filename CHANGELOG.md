# Changelog

## [1.1.1] – 2026-09-22

### Hinzugefügt
- **GitHub Actions**: Jeder Push baut die Mod-Jar automatisch, jeder Tag `v*` erzeugt ein Release mit Download.

## [1.1.0] – 2026-09-22

### Geändert
- **Protokoll 2**, passend zu Plugin 1.8.0: Der Server schickt jetzt auch `border.curtain-share` (Stärke des durchgehenden Schimmers) und `border.climb-limit` (Hochziehen der Linie über Blöcke außerhalb). Die Werte gelten damit für alle Spieler gleich, ob mit oder ohne Mod.
- Passt der Mod nicht zum Server, zeigt der Server im Chat Versionen und einen klickbaren Download-Link.

## [1.0.3] – 2026-09-20

### Geändert
- **Der Rahmen läuft über die höhere Kante.** Steht direkt außerhalb der Grenze ein höherer Block (oder ein Stapel), läuft die Linie oben über dessen Kante statt unten an seinem Fuß entlang. Füllt die Wand außerhalb den ganzen Luftraum aus (Tunnel), bleibt die Linie am Boden. Die senkrechten Verbindungsstücke an den Ecken richten sich nach diesen Höhen, der Rahmen bleibt geschlossen.
- **Durchgehender Schimmer bis zur Oberfläche.** Zusätzlich zum Glow direkt über der Linie füllt ein deutlich schwächerer Schimmer den ganzen Luftraum vom Boden bis zur Decke, unter freiem Himmel bis zur Geländeoberfläche, und blendet darüber weich aus. So bleibt die Border auch an hohen Treppen und in Schächten durchgehend erkennbar.

## [1.0.2] – 2026-09-20

### Geändert
- **Echte Linien mit fester Pixelbreite** (wie die Umrandung beim Anvisieren eines Blocks) statt 3D-Stäben. Die Linien bleiben dünn und scharf, egal wie nah die Kamera ist. Vorher wurden sie direkt vor der Kamera zu fetten roten Balken. Die Server-Einstellung `border.line-width` bedeutet im Mod jetzt Pixel (3 = 3 px).
- **Ein einziger durchgehender Rahmen statt vieler Einzelrahmen.** Linie am Boden des Feldes entlang der Grenze, an Stufen geht sie senkrecht hoch oder runter und läuft weiter. Auf Blöcken außerhalb wird nichts mehr gezeichnet, Decken- und Absatzlinien entfallen.
- **Echte Blockhöhen:** Slabs, Ackerboden, Trampelpfade und ähnliche Blöcke werden dort umrandet, wo sie wirklich enden.

## [1.0.1] – 2026-09-20

### Behoben
- **Border-Linien verschwanden oder fehlten**, vor allem in Treppengängen, Schächten und unter Überhängen. Die Geometrie wurde nur relativ zur eigenen Höhe berechnet. Jetzt werden die tatsächlichen Lufträume jeder Kante über die ganze Höhe um den Spieler ausgewertet: jede Bodenlinie, Deckenlinie und jeder Geländeabsatz wird gezeichnet, egal ob vor, hinter, über oder unter dem Spieler.
- **Doppelte und gestrichelte senkrechte Linien** an Ecken: Pro Ecke gibt es nur noch einen Pfosten, den sich alle angrenzenden Kanten teilen, überlappende Stücke werden zusammengefasst.
- **Unsaubere Linien aus der Nähe**: Linien sind jetzt dünne Vierkant-Stäbe exakt mittig auf der Kante statt zwei versetzter Streifen.
- **Glow fehlte an manchen Kanten**: Jede Bodenlinie und jeder Absatz trägt jetzt seinen eigenen Glow.

### Geändert
- **Sofortige Aktualisierung**: Beim Abbauen oder Platzieren eines Blocks wird die Border im selben Frame neu berechnet statt mit bis zu einer Sekunde Verzögerung.
- Die flächige Rotfärbung ganzer Wände entfällt, es bleibt der weiche Glow über den Linien.
- Sichtweite der Border von 28 auf 40 Blöcke erhöht.
- Testschalter `-Dgridlock.disable=true` legt den Mod komplett still (zum Prüfen der Server-Darstellung).

## [1.0.0] – 2026-09-20

Erste Version für Minecraft **26.3** (Fabric), passend zum GridLock-Plugin ab **1.7.0** (Protokoll 1).

### Hinzugefügt
- Harter Stopp an der Feld-Border: Der Client kollidiert selbst mit den Kanten, ohne Zurücksetzen durch den Server. Reine Kollisionsformen, nichts blockiert Klicks oder Platzieren.
- Border wird vom Client gezeichnet: dünne Leuchtlinien am Geländeprofil, weicher Schimmer, Wand bis zur Oberfläche, stufenloser Farbwechsel beim Erweitern und grüner Blitz beim neuen Block.
- Anmeldung beim Plugin mit Versionsprüfung. Bei einem Fehler meldet sich der Mod ab, und der Server zeigt wieder die normale Border.
- Tut nichts auf Servern ohne GridLock-Plugin.
- Repository: [`kronig-mc-plugins/gridlock-mod`](https://github.com/kronig-mc-plugins/gridlock-mod), Plugin daneben in `gridlock-plugin`.

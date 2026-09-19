<p align="center"><img src="src/main/resources/assets/gridlock/icon.png" alt="GridLock Logo" width="160"></p>

<h1 align="center">GridLock Mod</h1>

Optionaler **Fabric-Client-Mod** für das [GridLock-Plugin](https://github.com/kronig-mc-plugins/gridlock-plugin) (Minecraft **26.3**).

## Was er macht

- **Harter Stopp an der Border.** Das Plugin schickt dem Mod das freigeschaltete Feld. Der Client kollidiert dann selbst mit den Feldkanten, wie mit einer Wand, **ohne dass der Server dich zurücksetzt**.
- **Nichts im Weg.** Die Wände sind reine Kollisionsformen für die eigene Bewegung, keine Blöcke und keine Entities. Klicken, Abbauen, Platzieren und Aufsammeln außerhalb des Feldes funktionieren ganz normal.
- **Border vom Client gezeichnet.** Dünne Leuchtlinien am Geländeprofil, weicher Schimmer, der nach oben ausblendet, Wand immer bis zur Oberfläche. Beim Erweitern wechselt die Farbe stufenlos von Rot über Orange und Gelb zu Grün und blitzt beim neuen Block kurz grün auf.
- Aussehen (Farbe, Schimmer-Höhe, Schimmer-Stärke, Linien-Dicke) kommt vom Server, also aus den Plugin-Einstellungen.

## Mit und ohne Mod

Der Mod ist freiwillig. Spieler ohne Mod spielen auf demselben Server ganz normal weiter (Server-Border, Stopp durch den Server). Auf Servern ohne das Plugin tut der Mod nichts.
Der Server prüft weiterhin selbst, ob ein Spieler im Feld ist. Der Mod ist also kein Vorteil und kein Cheat-Risiko.

## Installation

1. [Fabric Loader](https://fabricmc.net/use/installer/) für Minecraft 26.3 installieren
2. [Fabric API](https://modrinth.com/mod/fabric-api) in den `mods`-Ordner legen
3. `gridlock-mod-<version>.jar` in den `mods`-Ordner legen

## Bauen

```bash
./gradlew build
```

Braucht JDK 25 (Gradle lädt es bei Bedarf automatisch). Die Mod-Datei liegt danach in `build/libs/`.
Testen: `./gradlew runClient --args="--quickPlayMultiplayer localhost:25565"`

## Technik

- Kanal `gridlock:hello` (Client → Server): Protokollversion, oder `-1` zum Abmelden bei einem Fehler
- Kanal `gridlock:field` (Server → Client): Feld komplett, einzelne Blöcke dazu/weg, Kauf-Fortschritt, Blitz. Format siehe `ModLink` im Plugin.
- Kollision: Mixin in `Entity.collide`, fügt für den lokalen Spieler Wände für gesperrte Blocksäulen zu den Entity-Kollisionen hinzu.

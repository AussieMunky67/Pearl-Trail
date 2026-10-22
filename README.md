# Pearl Trail

Ender pearls leave a purple particle trail along their flight path that disappears after 5 seconds.
Client-side only: install it in your own mods folder and it works in singleplayer and on any server.
You see the trails of your own pearls and of every other player's pearls that are in render range.

Requires Minecraft 26.2, Fabric Loader and Fabric API. Java 25.

## Building
1. Generate a template at https://fabricmc.net/develop (Minecraft 26.2) and copy its `gradlew`, `gradlew.bat` and `gradle/` folder into this project.
2. If the build complains about versions, update `gradle.properties` with the values the generator gives you.
3. Run `./gradlew build`. The jar is in `build/libs/` (use the one without `-sources`).

## Publishing on Modrinth
Create a project, upload the jar, loader = Fabric, game version = 26.2, add Fabric API as a required dependency, and set the environment to "Client".

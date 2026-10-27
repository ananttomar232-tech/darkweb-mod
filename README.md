# Dark Web & iPhone Duo  (`darkweb_duo`)

Minecraft **1.20.1** · Forge **47.2.0+** · Java **17** · official Mojang mappings · no GeckoLib, no Mixins.

A giant PC corner, a keycard-locked vault, two cinematic bosses with their own music and arenas, and the iPhone Duo as the final reward.

## Build it (one command)

```bash
python build_mod.py            # generate missing assets -> verify -> gradle build -> dist/darkweb_duo-1.0.0.jar
python build_mod.py --check    # only generate + verify (no Gradle, needs no JDK)
python build_mod.py --regen    # regenerate ALL procedural assets (textures, models, 56 sounds, 2 boss themes)
python build_mod.py --client   # build, then start a dev client to try it
python build_mod.py --java /path/to/jdk17
```

Needs: Python 3.9+, **JDK 17** (e.g. Eclipse Temurin 17) and internet access for the first build (Gradle, Forge and Minecraft are downloaded).
`./gradlew build` works too (wrapper pinned to Gradle 8.1.1). Asset regeneration additionally needs `numpy pillow scipy` and `ffmpeg`;
the generated assets are already in `src/main/resources`, so a normal build does not need them.

Install: copy `dist/darkweb_duo-1.0.0.jar` into `.minecraft/mods` together with Forge 1.20.1-47.2.0 (or any newer 47.x).

## Playing it

| Step | What happens |
|---|---|
| **PC corner** | Craft the **Giant Monitor** (glass panes, redstone block, iron blocks; it places as a 5x3 screen), **Giant Keyboard** and **Giant Mouse** and put keyboard and mouse near it. Right-click the monitor to power it on (boot sequence); sneak + right-click powers it off. |
| **Order a box** | Once booted, right-click the monitor again: it charges emeralds (default 16, free in creative) and a **Delivery Box** drops from the sky. You can also craft one (8 paper around an ender pearl). |
| **Open the box** | Right-click it: lid animation, particles, and the **Dark Web Keycard** flies up and into your inventory. |
| **Vault door** | The **Keycard Door** opens only while you carry the Dark Web Keycard (redstone and bare hands do nothing). |
| **Boss 1** | `/summon boss fight [pos]` builds the Cipher arena and a sleeping **Cipher Warden**. Walk in front of it in survival mode and it wakes with a cutscene. 13 attacks, 3 phases, own theme music. Drops **700 emeralds** and an **Omega Delivery Box**. |
| **Boss 2** | Open the Omega box for the **Omega Keycard**, put it into the **Omega Terminal** (or build the sanctum with `/summon boss fight omega [pos]`). **Omega Root** rises: 18 attacks across 5 phases (Cipher Warden has 13 across 3), its own theme and cutscenes. |
| **Reward** | When Omega Root dies the **iPhone Duo** appears on top of the terminal: right-click to use (nine apps, camera, maps, music...), sneak + right-click to fold/unfold. |

Commands need permission level 2 (cheats on in singleplayer). Settings (boss health, damage multiplier, emerald drop, arena radius, wake rules) are in `config/darkweb_duo-common.toml`.

## Layout

```
build_mod.py                 one-command orchestrator
src/main/java/com/darkwebduo  blocks, entities, bosses, cutscenes, renderers, phone OS
src/main/resources            generated textures, models, blockstates, sounds, loot, recipes, lang
tools/                        deterministic procedural generators (Python): textures, 3D models, audio, boss themes
```

All textures, models and music are procedurally generated (`tools/gen_*.py`); nothing is copied from other mods or from Apple.
The iPhone Duo uses original art (no Apple logo).

## Verification status (honest)

The sandbox this was written in cannot reach Mojang/Forge servers, so **the mod has not been compiled or run in Minecraft yet**.
What *was* checked: Java syntax of all files, every `net.minecraftforge` import and event signature against the real Forge 1.20.1 source,
every `@Override` signature, constructor/method argument counts between our own classes, translation keys, texture/sound/model references,
and all JSON. That static review found and fixed three genuine compile errors (wrong `RenderLayer` package, `entitiesForRendering` on `Level`,
a missing `BlockEntity` import). If `build_mod.py` reports a compile error on your machine, it will name the file and line.

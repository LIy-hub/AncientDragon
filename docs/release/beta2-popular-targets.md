# Ancient Dragon Beta.2 — popular Fabric targets

This update ports the Sacred Mountain egg and companion hatchling from the Minecraft 26.3
release to 26.2, 1.21.11, 26.1.2 and 1.21.1. The four targets lead the existing Beta.1
file downloads outside 26.3 in the November 2026 CurseForge snapshot. [Ancient Dragon files](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/all)

## Installation

Install exactly one Ancient Dragon and one BlendLib JAR for your Minecraft version.
Install the matching Fabric API and loader on the client and server.

| Minecraft | Java | Fabric Loader | Fabric API | BlendLib |
| --- | --- | --- | --- | --- |
| 26.2 | 25+ | 0.19.5+ | 0.160.0+26.2 or later for 26.2 | [1.0.0-beta.3+26.2](https://www.curseforge.com/minecraft/mc-mods/blendlib/files/8857321) |
| 1.21.11 | 21+ | 0.19.5+ | 0.141.6+1.21.11 or later for 1.21.11 | [1.0.0-beta.3+1.21.11](https://www.curseforge.com/minecraft/mc-mods/blendlib/files/8857282) |
| 26.1.2 | 25+ | 0.19.5+ | 0.155.3+26.1.2 or later for 26.1.2 | [1.0.0-beta.3+26.1.2](https://www.curseforge.com/minecraft/mc-mods/blendlib/files/8857313) |
| 1.21.1 | 21 | 0.19.5+ | 0.116.17+1.21.1 | [1.0.0-beta.3+1.21.1](https://www.curseforge.com/minecraft/mc-mods/blendlib/files/8857223) |

The published BlendLib Beta.2 artifacts remain the checksum-pinned compile-time baseline.
Their metadata pins Fabric Loader 0.19.3 and an exact Fabric API per target. The production
verification below used the newer installable combinations listed above.

## Build and runtime verification

Run Gradle with JDK 25 and install JDK 21 for the Java 21 targets. The runtime JAR and source
archive are produced in `versions/<family>/build/<Minecraft>/libs/`.

```powershell
foreach ($minecraft in '26.2', '1.21.11', '26.1.2') {
    .\gradlew.bat -p versions/modern "-Pminecraft_version=$minecraft" --max-workers=1 build verifyRuntimeJar
}
.\gradlew.bat -p versions/legacy "-Pminecraft_version=1.21.1" --max-workers=1 build verifyRuntimeJar
```

| Minecraft | JUnit | Production server | Production Windows client |
| --- | ---: | --- | --- |
| 26.2 | 229 | Ready, saved, exit 0 | Nine models loaded, clean shutdown |
| 1.21.11 | 229 | Ready, saved, exit 0 | Nine models loaded, clean shutdown |
| 26.1.2 | 229 | Ready, saved, exit 0 | Nine models loaded, clean shutdown |
| 1.21.1 | 244 | Ready, saved, exit 0. Legacy relic contract passed | Nine models loaded, clean shutdown |

All 931 JUnit executions completed with zero failures, errors or skips. Every build passed
the runtime archive check. All production clients reported zero missing models and zero
BlendLib reload diagnostics. Production server and client checks used the exact release JARs
and the CurseForge BlendLib Beta.3 downloads listed above.

The packaged 26.2 Fabric GameTests used the BlendLib Beta.2 build baseline. They exercised
the nest's victory and inventory restrictions, the one-time egg claim, incubation and keeper
persistence, taming with a fish, the stay/follow toggle and autonomous takeoff, gliding and
landing. The client game test consumed food through right-click, rendered all six colors,
checked stats, reopened the world and confirmed color reversion when the enchanted golden
apple share fell back to one half.

The packaged 1.21.11, 26.1.2 and 1.21.1 servers also exercised the native incubation
ticker and saving with the BlendLib Beta.3 combinations. Console-driven scenarios
confirmed cracks, a dormant nest egg, a hatched entity retaining its keeper and the
removal of the shell. Reloading two diet counter states through native entity NBT produced
24 max health at one half and 32 max health above one half.

Diet persistence, old encounter records without the egg claim field and all eight hatchling
and egg model assets are covered by the shared JUnit suite. The runtime scenarios used
isolated test worlds. Public multiplayer play and upgrades of existing player-owned saves
remain outside the tested scenarios.

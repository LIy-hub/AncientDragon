# Ancient Dragon Beta.1 verification and publication

This record distinguishes build and tests, packaged startup, in-game acceptance, and platform moderation.
Release candidate: `0.1.0-beta.1+<Minecraft>`, Fabric Loader 0.19.3, public BlendLib Beta.2.

The version matrix is in progress. No candidate in this record is considered published before its
GitHub release and CurseForge file receipts are recorded here.

## Build interfaces

From the repository root, use Java 25 to run Gradle and install Java 21 for 1.21.x compilation:

```text
./gradlew -p versions/legacy -Pminecraft_version=1.21.1 build verifyRuntimeJar
./gradlew -p versions/modern -Pminecraft_version=1.21.11 build verifyRuntimeJar
```

Legacy targets: 1.21.1–1.21.8. Modern targets: 1.21.9–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2.
Outputs are isolated under `versions/<family>/build/<Minecraft>/libs/`.
The root build remains the standard 26.1.2 development entry point.

## Runtime boundaries

Verification uses fresh profiles, the actual runtime JAR, the exact public BlendLib JAR, and the
matching Fabric API. Server profiles bind loopback and use isolated generated worlds. Client checks
stop at the initial UI; no client enters an existing world. No in-game visual, multiplayer combat,
Iris/Sodium, GPU performance, or cross-version world downgrade acceptance is implied.

Existing worlds should be backed up before upgrading. The exact Minecraft version is part of every
artifact name and mod dependency declaration. Do not mix JARs built for different Minecraft versions.

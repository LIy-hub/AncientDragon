# Ancient Dragon for Minecraft 26.3

## Changes

- Add the 26.3 target to the existing modern shared-source build: Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Loom 1.17.21 and Gradle 9.6.0.
- Use native axe/hoe/shovel item components while preserving materials and attack attributes.
- Adapt server-only drop prediction, velocity synchronization and the new advancement loot-predicate codec.
- Port Sacred Mountain placement to the spreading-placement implementation, direct codec registry and the new biome resolver, retaining deterministic candidates and ocean-clearance rules.
- Remove obsolete block codecs and adapt the official city-template test to the 26.3 lower-case block-state palette IDs.
- Build against the complete public BlendLib 26.3 JAR from a pinned source revision, including its client APIs. Older versions continue using their checksum-pinned Beta.2 releases.

## Reproducible source build

From a checkout of this repository's `mc/26.3` branch with JDK 25:

```sh
git clone https://github.com/LIy-hub/BlendLib-Public.git ../BlendLib-Public
git -C ../BlendLib-Public checkout 5c5354c42876e769c5e4915005ec3341502d0ef0
bash gradlew -p versions/modern -Pminecraft_version=26.3 --no-daemon build verifyRuntimeJar
```

The library repository may be elsewhere by passing `-Pblendlib_source=/absolute/path/to/BlendLib-Public`.
The build checks its exact Git revision from `versions/modern/blendlib-source.properties`, builds it as a composite dependency, checks the resulting mod ID/version/Minecraft target, and prints its SHA-256.

The two runtime mods must be installed together, on both client and server:

- `ancient-dragon-0.1.0-beta.1+26.3.jar`
- `blendlib-fabric-1.0.0-beta.3+26.3.jar`

They also require Fabric API 0.161.0+26.3 and Loader 0.19.5 or newer. No private BlendLib source, local Maven publication or new GitHub Release is needed.

## Verified evidence

Code checkpoint: `7a2d43ad951773606cf3b883ff40e26edc50ce7c`.
[Successful 26.3 CI](https://github.com/LIy-hub/AncientDragon/actions/runs/37048006794).
[Runtime/source JARs and test reports](https://github.com/LIy-hub/AncientDragon/actions/runs/37048006794/artifacts/11244699259).

- Main, client and test source compilation passed.
- 222 tests in 48 suites passed, including the unchanged gameplay/asset contracts.
- The added Fabric Loader test verifies both mods are discovered, common mod initialization completes, the production camera Mixin is actually injected, and the Sacred Mountain placement JSON decodes with its registered codec.
- Packaged-JAR checks verify target metadata, every original gameplay class, required GLB/collision assets and license notices.
- The three official 26.3 city-centre templates still satisfy the exact 55-block gateway-frame contract; no assertions were weakened.

Runtime JAR SHA-256: `dbbafead779ed0b50dbc715af58abe8ad242f8fdd30c3cc2cd4a65cb2fd9411b`.

## Verification limits

No dedicated-server/world startup, visual boss fight, world-generation playtest, or multiplayer playtest is claimed. No EULA acceptance, release/tag, merge or CurseForge upload was performed.
The cloud environment prevents Loom's Unix-domain socket probe; the complete build ran on GitHub Actions using unmodified official tools.

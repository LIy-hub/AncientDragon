# Ancient Dragon Beta.1 — Fabric 1.21.1–1.21.8

This independent build adapts the complete common and client source trees from the repository root. It produces one exact-Minecraft runtime JAR and sources JAR per target, versioned `0.1.0-beta.1+<minecraft>`. Mod ID, encounter logic, finite corpse rewards, chronicle, gateway, Sacred Mountain generation/repair, thirteen altar rites, flight, tools, GLB rendering and the authored assets are retained.

## Build interface

Run Gradle with JDK 25 and install JDK 21 for compilation and game execution. A clean public checkout resolves Minecraft/Mojang mappings, Fabric and the matching published BlendLib directly from public repositories. No private repository, installed mod folder, nested facade extraction or machine-specific path is required.

```powershell
.\gradlew.bat -p versions/legacy '-Pminecraft_version=1.21.1' build check --max-workers=1 --no-daemon
python versions/legacy/freeze_release.py 1.21.1
```

```bash
./gradlew -p versions/legacy -Pminecraft_version=1.21.8 build check --max-workers=1 --no-daemon
```

Valid targets are `1.21.1` through `1.21.8`. Default target: `1.21.8`. Outputs and generated sources are isolated under `build/<minecraft>/`; the root `src` tree is never modified. CI can set `JAVA_HOME_21_X64` or `JAVA_HOME_21` to its installed JDK 21. Run only one target per Gradle invocation; this project uses one worker in the documented commands.

`freeze_release.py` verifies the runtime against `jar-verification.json`, then copies runtime and sources into `build/release-beta1/<minecraft>/<runtime-sha256>/`. It refuses to overwrite a different existing artifact. Client/release consumers should read this immutable directory rather than the live Gradle output directory.

## Public dependencies

All targets use Fabric Loader `0.19.3`, Loom remap `1.16.2`, Java 21 bytecode and JUnit `5.12.2`. BlendLib comes from [the public Beta.2 release](https://github.com/LIy-hub/BlendLib-Public/releases/tag/v1.0.0-beta.2), version `1.0.0-beta.2+<minecraft>`, with its SHA-256 pinned and checked before compilation. The runtime metadata requires BlendLib Beta.2 or newer and the exact Minecraft target.

| Minecraft | Fabric API |
| --- | --- |
| 1.21.1 | 0.116.17+1.21.1 |
| 1.21.2 | 0.106.1+1.21.2 |
| 1.21.3 | 0.114.1+1.21.3 |
| 1.21.4 | 0.119.4+1.21.4 |
| 1.21.5 | 0.128.2+1.21.5 |
| 1.21.6 | 0.128.2+1.21.6 |
| 1.21.7 | 0.129.0+1.21.7 |
| 1.21.8 | 0.136.1+1.21.8 |

Optional `build/public-releases` and `build/fabric-maven` directories are transport caches of public artifacts. Empty checkouts use the public GitHub/Fabric repositories instead. BlendLib SHA validation remains mandatory when the cache is present. Build configuration does not read the prior BlendLib worktree.

## Version adaptations and their limits

`transforms.gradle`, `postprocess.gradle` and `1.21.1.gradle` generate the full source tree, with narrow compatibility implementations in `overrides`. Original tests are transformed to the actual target APIs and all 221 continue to execute. Compatibility tests are added to that same generated test tree.

- Legacy names and lifecycle hooks use the actual mapped target APIs: `ResourceLocation`, `ServerWorldEvents`, play payload registries, `ClickType`, server weather data, `interactAt`, chunk coordinate fields and `Camera.setup`. Corpse harvesting remains a server-authoritative interaction. Unsupported `ALLOW_LOAD` is implemented through immediate `ENTITY_LOAD` rejection, retaining the existing environment cleanup passes.
- Minecraft 1.21.1–1.21.5 read/write the same entity NBT keys through `LegacyValueInput`/`LegacyValueOutput`; participant lists and corpse harvest state remain intact. Minecraft 1.21.1–1.21.4 bridge the six original state codecs to native `SavedData.Factory` and `save(CompoundTag, HolderLookup.Provider)`. File IDs remain path-safe and stable. Tests cover six factories, exact-once pending rewards/books and partial repair cursors.
- The pre-1.21.6 collision API cannot receive the colliding entity in `canBeCollidedWith()`. A required `Entity.canCollideWith` Mixin preserves the original player-only dragon/part collision restriction. Minecraft 1.21.1–1.21.3 retain the part's dynamic bounding box through the native no-argument `makeBoundingBox()` override.
- Altar screens use each target's real `GuiGraphics` API. Through 1.21.5 the preview is a detached ArmorStand created in `subInit`, equipped in all relevant slots, and drawn by `InventoryScreen.renderEntityInInventory`; it is never added to a world. Minecraft 1.21.1 uses native `ItemCombinerMenu.createInputSlotDefinitions` and item/block interaction result types, retaining quick-move and exact material costs.
- All eight targets predate vanilla spears. Obtainable `ancient_dragon:diamond_spear` and `ancient_dragon:netherite_spear` supply the otherwise unavailable altar input. Crafting costs one diamond and two sticks; upgrading costs one netherite ingot and one netherite upgrade template. The dragonbone spear rite still costs ten Ancient Bones. Other altar rites are unchanged.
- `LegacySpearItem` preserves held kinetic contact, minimum/maximum ray distances, delay and effect windows, cooldown, dismount, knockback and durability. Native 26.x bytecode was checked: kinetic damage uses the attack attribute **base** plus `floor(relative forward speed * 1.2)`, then enchantment modifiers. Fully charged jabs call the native player attack for every permitted ray contact, preserving its enchantments and damage lifecycle; block occlusion, allies, PvP restrictions and the two-block dead zone remain enforced. The legacy animation uses the native spear/trident use pose and existing item model; this is not a claim of identical 26.x spear animation.
- Minecraft 1.21.1 uses native tool `Tier`/SwordItem/PickaxeItem/etc., native ArmorItem and the official `FabricElytraItem` hook. Both wings keep gliding and existing player flight grants; the reinforced wing also remains native armor. Required client Mixins render the original wing PNG through the native ElytraModel for every renderer with an ElytraLayer, and suppress a player's cape while a wing is equipped. The original humanoid/leggings PNGs are copied to the old armor layer paths. Runtime verification optionally enabled with `-Dancientdragon.verifyLegacyRelics=true` checks actual registered armor defense, relic bonuses, durability, equipment slots, glider hooks and spear attributes. Normal gameplay does not enable this verification flag.
- Minecraft 1.21.2–1.21.3 equipment definitions use `Equippable.setModel(ResourceLocation)` and are additionally placed in `models/equipment/`. Later targets use equipment assets. The 26-only `humanoid_baby` layer is removed from all legacy equipment JSON; humanoid, leggings and wings remain.
- Copper flames are registered custom particles using the native flame provider with a copper tint. Before 1.21.5, firefly ambience uses a registered luminous particle with native glow sprites. Before 1.21.4, pale leaves use native falling-leaf motion and an original pale leaf mask. These are intentional visual approximations for missing vanilla particles; emission zones/counts and server/client delivery remain.

The frozen Sacred Mountain tile bytes, manifest, 111 authored states and 1,372 tile hashes remain unchanged. Only the actual placement/repair parser translates unavailable decorative flora; original asset tests use that same resolver and also check every target exists, remains non-air and supports all four rotations:

| Target range | Authored state | Native placement state |
| --- | --- | --- |
| 1.21.1–1.21.4 | `minecraft:firefly_bush` | `minecraft:fern` |
| 1.21.1–1.21.3 | `minecraft:closed_eyeblossom` | `minecraft:azure_bluet` |
| 1.21.1–1.21.3 | `minecraft:open_eyeblossom` | `minecraft:poppy` |

These non-colliding plants retain vegetation positions and mountain topology. Their appearance and native flower behavior approximate the newer decorative plants; an eyeblossom day/night lifecycle and firefly-bush-specific interactions are not backported. The mod's independent atmospheric particle systems remain available.

## Validation and game safety

`check` runs the native JUnit suite, GLB/collision/animation invariants, full tile checks and runtime metadata/asset checks. The runtime JAR must have exact target metadata, Java 21 classes, all original resources, and no duplicate BlendLib classes or Java sources. Both sources and runtime are separately hashed. See [VERIFICATION.md](VERIFICATION.md) for exact hashes and evidence.

`smoke_server.py` accepts explicit runtime, matching BlendLib, runtime Fabric API, Java executable and loader launcher paths. It creates a fresh directory under this project's `build`, binds an unused loopback port, launches an isolated server, waits for readiness, then sends `stop`. Its optional caches copy only launcher libraries/version binaries, never another world's files. Tests used ports 25731–25738; no existing `run` save was opened or modified.

Packaged client startup is coordinated by the main task. Startup/resource reload and clean window closure are recorded independently from JUnit and dedicated-server results. No world entry or gameplay input was automated. Boss combat, movement, armor/wing appearance and altar visuals still require user visual/gameplay acceptance.

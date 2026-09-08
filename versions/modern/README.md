# Ancient Dragon modern Fabric targets

This independent Gradle build covers Minecraft 1.21.9, 1.21.10, 1.21.11, 26.1,
26.1.1, 26.1.2, and 26.2. Run from the repository root:

```text
./gradlew -p versions/modern -Pminecraft_version=1.21.11 build verifyRuntimeJar
```

Gradle runs with Java 25; 1.21.x compilation and runtime use Java 21. Public BlendLib
Beta.2 artifacts are pinned by Minecraft target and SHA-256 in `build.gradle.kts`.
No author checkout or local Maven publication is required.

The build generates all shared gameplay, client, and test sources from the root `src/`
tree. Adapters preserve the encounter, corpse harvesting, gateway, persistence, menu,
camera effects, and model assets while translating native API names and signatures.
Overrides add code only where a target lacks the corresponding native feature.

## Version differences

- 1.21.x uses remapped Mojang names and Java 21 mixins. Equipment definitions omit
  the unsupported `humanoid_baby` layer while retaining adult armor layers.
- 1.21.9 and 1.21.10 use older identifiers, permission checks, and altar preview state.
  Only 1.21.9 lacks the final intersection flag on the gateway block callback.
- Versions lacking Fabric's entity-load veto discard prohibited mountain hostiles
  in the entity-load callback. The shared periodic cleanup and weather restoration remain.
- 26.2 uses its native advancement predicate packages, entity type registry, concrete
  color collection, and block-position vector conversion.

## Spears before Minecraft 1.21.11

Minecraft 1.21.9 and 1.21.10 have no vanilla spears. These targets register obtainable
`ancient_dragon:diamond_spear` and `ancient_dragon:netherite_spear`, with crafting and
smithing recipes. The altar accepts the latter as its Emberbone spear input.

The compatibility spear retains server-authoritative kinetic contact and a charged
piercing jab, including range/dead-zone limits, contact cooldown, durability, vanilla
attack handling, enchantment hooks, knockback, and dismount behavior. Six boundary tests
cover its timing and velocity rules. These older games do not provide the later native
spear components, dedicated damage types, or Lunge enchantment; their API representation
and held-use animation differ. Minecraft 1.21.11 and 26.x retain native spears.

`build verifyRuntimeJar` runs the shared tests and checks metadata, all original runtime
classes, required model/collision/license assets, and absence of development files.
The [release record](../../docs/release/beta1-verification.md) separately records the
packaged client/server checks and the limits of in-game acceptance.

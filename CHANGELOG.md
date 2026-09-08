# Changelog

All notable changes to Ancient Dragon are documented here.

## 0.1.0-beta.1 - 2026-09-08

First public Beta, updated to the released BlendLib Beta.2 version family.

- Exact-version Fabric builds for Minecraft 1.21.1–1.21.11, 26.1, 26.1.1, 26.1.2, and 26.2.
- Public, SHA-256-pinned BlendLib dependencies for every supported Minecraft target.
- Version-specific adapters for entity interactions, persistent world data, lifecycle events,
  native menus, camera effects, and equipment resources; shared encounter logic and assets retained.
- Java 21 for Minecraft 1.21.x and Java 25 for Minecraft 26.x; install the matching Fabric API,
  BlendLib, and Ancient Dragon files on the server and every client.
- Target builds, packaged runtime checks, and platform publication are recorded in the
  [Beta.1 verification record](docs/release/beta1-verification.md).

This remains a Beta. Startup and automated tests do not replace in-game visual or multiplayer
acceptance. Back up worlds before upgrading; downgrading a world to an older Minecraft release
is not supported.

## 0.1.0-alpha.2 - 2026-08-07

Sunheart Altar interface and forging update.

### Changed

- Rebuilt the Sunheart Altar around a server-authoritative vanilla container menu with dedicated
  target, material, and result slots.
- Added live recipe validation, exact material-count feedback, bilingual error messages, and an
  equipment or held-item result preview.
- Added normal-click and shift-click result handling while preserving the forged target's custom
  name, enchantments, durability, repair cost, and other component data.
- Replaced the altar's custom open/forge networking payloads with the registered menu flow.
- Updated the altar item description and bilingual interface copy for the slot-based workflow.

### Compatibility

- Minecraft: `26.1.2`
- Fabric Loader: `0.19.3` or newer
- Java: `25` or newer
- Required: Fabric API and BlendLib `1.0.0-alpha.1+26.1.2` or newer compatible release
- Installation side: client and server

### Notice

- This remains an alpha release. Back up important worlds before installation or upgrade.

## 0.1.0-alpha.1 - 2026-08-04

Initial public alpha.

### Added

- A world-unique Ancient Dragon encounter for Minecraft 26.1.2 on Fabric.
- Deterministic sacred-mountain generation in a distant deep-ocean candidate region.
- Ancient City gateway activation using an Echo Shard.
- Server-authoritative aerial and ground combat with mountain, storm, and solar phases.
- Twenty-four animated multipart hitboxes, targeted damage, wing stability, threat selection, and
  multiplayer health scaling.
- Persistent encounter state from dormancy through combat, death, corpse harvesting, and rewards.
- Collectible chronicles, Ancient Dragon materials, Sunheart Altar upgrades, equipment, tools, and
  advancements.
- Operator commands for encounter, route, phase, hitbox, attack, and structure diagnostics.

### Compatibility

- Minecraft: `26.1.2`
- Fabric Loader: `0.19.3` or newer
- Java: `25` or newer
- Required: Fabric API and BlendLib `1.0.0-alpha.1+26.1.2` or newer compatible release
- Installation side: client and server

### Known limitations

- This is an alpha release. Back up important worlds before installation or upgrade.
- Natural sacred-mountain generation only affects previously ungenerated chunks.
- BlendLib must be installed separately from its public GitHub Release or CurseForge project.
- Visual gameplay verification and broad mod-compatibility testing are ongoing.

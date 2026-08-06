# Changelog

All notable changes to Ancient Dragon are documented here.

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

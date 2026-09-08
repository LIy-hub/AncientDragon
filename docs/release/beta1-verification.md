# Ancient Dragon Beta.1 verification and publication

Verified on 2026-09-08. Release: `0.1.0-beta.1+<Minecraft>`, Fabric Loader 0.19.3+, matching Fabric API, and the exact public BlendLib `1.0.0-beta.2+<Minecraft>` artifact. Install the matching dependencies on both client and server.

All 15 targets passed builds, JUnit tests, runtime-JAR inspection, packaged-client model reload and isolated dedicated-server startup. **3,404 automated test executions** completed with zero failures, errors or skipped tests. The original 221 tests are retained on every target, with additional compatibility tests where required.

The clean GitHub [15-target matrix](https://github.com/LIy-hub/AncientDragon/actions/runs/34198186718) passed at source commit [`080cfc2`](https://github.com/LIy-hub/AncientDragon/commit/080cfc2656bc26f8ca6b7caa317ca321479badfc). The [root 26.1.2 CI build](https://github.com/LIy-hub/AncientDragon/actions/runs/34198186678) also passed. The first matrix run failed before compilation because PowerShell split an unquoted version property; quoting the argument fixed the shared workflow issue.

## Per-version results

| Minecraft | Java | Tests | Build / JAR | Client reload | Server / exit | CurseForge Beta |
| --- | ---: | ---: | --- | --- | --- | --- |
| 1.21.1 | 21 | 236 | PASS | PASS | PASS / 0 | [8835796](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835796) — Under Review |
| 1.21.2 | 21 | 233 | PASS | PASS | PASS / 0 | [8835768](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835768) — Under Review |
| 1.21.3 | 21 | 233 | PASS | PASS | PASS / 0 | [8835733](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835733) — Approved |
| 1.21.4 | 21 | 233 | PASS | PASS | PASS / 0 | [8835692](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835692) — Approved |
| 1.21.5 | 21 | 229 | PASS | PASS | PASS / 0 | [8835673](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835673) — Approved |
| 1.21.6 | 21 | 227 | PASS | PASS | PASS / 0 | [8835667](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835667) — Approved |
| 1.21.7 | 21 | 227 | PASS | PASS | PASS / 0 | [8835653](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835653) — Approved |
| 1.21.8 | 21 | 227 | PASS | PASS | PASS / 0 | [8835647](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835647) — Approved |
| 1.21.9 | 21 | 227 | PASS | PASS | PASS / 0 | [8835637](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835637) — Approved |
| 1.21.10 | 21 | 227 | PASS | PASS | PASS / 0 | [8835628](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835628) — Approved |
| 1.21.11 | 21 | 221 | PASS | PASS | PASS / 0 | [8835615](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835615) — Approved |
| 26.1 | 25 | 221 | PASS | PASS | PASS / 0 | [8835605](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835605) — Approved |
| 26.1.1 | 25 | 221 | PASS | PASS | PASS / 0 | [8835590](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835590) — Approved |
| 26.1.2 | 25 | 221 | PASS | PASS | PASS / 0 | [8835570](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835570) — Approved |
| 26.2 | 25 | 221 | PASS | PASS | PASS / 0 | [8835559](https://www.curseforge.com/minecraft/mc-mods/ancient-dragon/files/8835559) — Approved |

Every CurseForge file is marked Beta, Fabric, Client and Server, with one exact Minecraft version and the matching Java version. Fabric API and BlendLib are required dependencies. All 15 public CDN downloads were hashed and match the frozen runtime JARs. The bilingual project description and default dependency relations were updated. Files marked Under Review await platform moderation and are configured to publish automatically when approved; CDN availability alone is not approval.

## Packaged runtime evidence

Each check used a fresh profile with the production runtime JAR, the exact public BlendLib JAR, and matching Fabric API. All 15 clients reached initial UI and published the dragon model: `models=1`, `missing=0`, `diagnostics=0`, `published=true`. Their windows closed normally and their processes exited; client exit codes were not captured and are not asserted.

All 15 isolated servers bound loopback, initialized Ancient Dragon, reached readiness, received `stop`, saved and exited with code 0. Unexpected mod/Mixin/resource errors were zero. Offline authentication/Realms and the 26.2 host OSHI/Perflib diagnostic were classified separately; they are not converted into mod failures or silently discarded. Minecraft 1.21.1 also passed the registered armor, glider and spear contract in the actual packaged server.

These checks do **not** establish in-game visual acceptance, multiplayer combat, Iris/Sodium compatibility, GPU performance or world-downgrade safety. No client entered an existing world. Back up worlds before upgrading; do not mix Minecraft-targeted artifacts.

## Artifacts and source correspondence

[GitHub Beta prerelease](https://github.com/LIy-hub/AncientDragon/releases/tag/v0.1.0-beta.1) contains 15 runtime JARs, 15 source JARs, `SHA256SUMS` and `RELEASE_MANIFEST.json`. The [machine-readable record](beta1-verification.json) includes all artifact sizes and SHA-256 values, dependency hashes, individual test counts and startup outcomes.

Runtime hashes identify the exact frozen JARs tested and uploaded. The eight legacy source archives were regenerated from the integrated source tree to correct the raw BlendLib dependency template from Alpha to Beta.2. Their Java contents are unchanged after line-ending normalization; no runtime JAR was replaced. See the [legacy verification record](../../versions/legacy/VERIFICATION.md).

A fresh integrated 1.21.1 build also passed all 236 tests. Its class files and other resources match the frozen runtime; line endings in the bundled license and collision JSON changed its ZIP hash. Byte-for-byte rebuild reproducibility across differently normalized checkouts is not claimed.

## Compatibility and build interfaces

All 96 original production source classes and authored model/texture payloads are retained. Version-specific persistence, entity, UI, equipment and rendering APIs are adapted. Pre-native-spear targets include obtainable diamond/netherite spears and compatible kinetic contact/piercing behavior. Native spear components absent in older Minecraft are represented through that version's available APIs. Missing decorative flora/particles use documented native approximations; sacred-mountain tile data and vegetation positions remain present.

Detailed boundaries: [legacy targets](../../versions/legacy/README.md), [modern targets](../../versions/modern/README.md).

Run Gradle with Java 25 and install Java 21 for 1.21.x compilation:

```powershell
.\gradlew.bat -p versions/legacy "-Pminecraft_version=1.21.1" build verifyRuntimeJar
.\gradlew.bat -p versions/modern "-Pminecraft_version=1.21.11" build verifyRuntimeJar
```

Legacy targets are 1.21.1–1.21.8. Modern targets are 1.21.9–1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2. Outputs are isolated under `versions/<family>/build/<Minecraft>/libs/`. The root remains the standard 26.1.2 development entry point. Builds pin public BlendLib artifacts and verify their SHA-256; no author-local library path is required.

Local raw build/client/server receipts remain under ignored build directories. Public summaries distinguish automated evidence from user-operated in-game acceptance; local evidence paths in the older detailed record are not build inputs.

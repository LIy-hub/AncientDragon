# Legacy Beta.1 verification

Verified on 2026-09-08 against the public BlendLib Beta.2 runtime artifacts. All final runtime hashes below match the exact JAR copied into their fresh dedicated-server verification directories. Full original production source coverage and both runtime/sources hashes were checked.

| Minecraft | JUnit tests | Build / artifact | Dedicated server | Packaged client |
| --- | ---: | --- | --- | --- |
| 1.21.1 | 236 | PASS | PASS, ready → stop → exit 0 | PASS, resource reload / close |
| 1.21.2 | 233 | PASS | PASS, ready → stop → exit 0 | PASS, resource reload / close |
| 1.21.3 | 233 | PASS | PASS, ready → stop → exit 0 | PASS, resource reload / close |
| 1.21.4 | 233 | PASS | PASS, ready → stop → exit 0 | PASS, resource reload / close |
| 1.21.5 | 229 | PASS | PASS, ready → stop → exit 0 | PASS, resource reload / close |
| 1.21.6 | 227 | PASS | PASS, ready → stop → exit 0 | PASS, resource reload / close |
| 1.21.7 | 227 | PASS | PASS, ready → stop → exit 0 | PASS, resource reload / close |
| 1.21.8 | 227 | PASS | PASS, ready → stop → exit 0 | PASS, resource reload / close |

All JUnit suites have zero failures/errors. Counts include the original 221 tests plus six kinetic tests; pre-1.21.6 adds two NBT tests, pre-1.21.5 adds three SavedData and one flora test, and 1.21.1 adds three native equipment API tests. Minecraft 1.21.1 also passed the actual registered-item contract in the packaged server (`Legacy relic contract verified: armor, gliders, spear`).

## Exact artifact SHA-256

### 1.21.1

- Runtime: `a49281922592ed044613f54e1a93ce28a6c661de6f1d9a6e1829870b4cb65936`
- Sources: `de024b8b34d9e5c3f16226b50199b8780bcf2e591bd8324e637fca9f52bb8615`
- Public BlendLib: `8c45f73ece19a33ee5d7d322544d2701c51e44a689a448acd9d294a8b3d867c6`
- Classes: 237; original production sources preserved: 96; source-JAR Java files: 119; authored resources verified: 1468.
- Build log: `build/build-1.21.1-r12.log`.
- Server receipt: `build/smoke/1.21.1-server-r2/result.json`; 19.03 seconds; no startup errors.
- Client receipt: `D:\AncientDragon\build\smoke\1.21.1-client-1\result.json`.

### 1.21.2

- Runtime: `f005b547de14a615affebf7a368f6c9484dbd30baa94c4aff826d77af716d4f9`
- Sources: `df6a26a920a2f1626353fe1846d86cc383790b130ceedc71f5789bcff505d80e`
- Public BlendLib: `b4f759b2f6690461c697c53e6356e458fb8add823fd843d432ee4b7e55b8057c`
- Classes: 230; original production sources preserved: 96; source-JAR Java files: 112; authored resources verified: 1468.
- Build log: `build/build-1.21.2-r6.log`.
- Server receipt: `build/smoke/1.21.2-server-r1/result.json`; 19.05 seconds; no startup errors.
- Client receipt: `D:\AncientDragon\build\smoke\1.21.2-client-1\result.json`.

### 1.21.3

- Runtime: `1094695eeac4a19254d0a486226c75706c71af53068f0eee4d3a6a32a5fa7902`
- Sources: `24f3b916695ae9366dcc69141d4a5f878e598d37086d3f0c5e50332b8da8c5a7`
- Public BlendLib: `9cc5611b8526974f3cf3c67d61ef753d9c86102a6c22a54cdd017049b645c6be`
- Classes: 230; original production sources preserved: 96; source-JAR Java files: 112; authored resources verified: 1468.
- Build log: `build/build-1.21.3-r6.log`.
- Server receipt: `build/smoke/1.21.3-server-r1/result.json`; 23.12 seconds; no startup errors.
- Client receipt: `D:\AncientDragon\build\smoke\1.21.3-client-1\result.json`.

### 1.21.4

- Runtime: `deec84aa014168bf1fdc5ef57a057d58647ea05a20db33a80ebc5a086745c84e`
- Sources: `6defe96b28895d1e8d6129056e7dd39efcb59c131f35c63af7a1a696810ff725`
- Public BlendLib: `8dd6f4d6113e0fb8425c0f63f1a201adf87562e73592314ec0fbd33399cae6bb`
- Classes: 227; original production sources preserved: 96; source-JAR Java files: 110; authored resources verified: 1468.
- Build log: `build/build-1.21.4-r5.log`.
- Server receipt: `build/smoke/1.21.4-server-r1/result.json`; 27.79 seconds; no startup errors.
- Client receipt: `D:\AncientDragon\build\smoke\1.21.4-client-1\result.json`.

### 1.21.5

- Runtime: `bae88d9bd0c75f6699709a15b0c056bb69552ea19abba6fb29d058b91f6a25df`
- Sources: `d0adbd6cc9aa652d8b889e23b5ed90875e1f89cd626dfdba7a96e426950d3f4a`
- Public BlendLib: `92cd6c64a6e90b8c44aef655ece58b05afc4172ff53715a760d74f3c81d857f9`
- Classes: 221; original production sources preserved: 96; source-JAR Java files: 105; authored resources verified: 1468.
- Build log: `build/build-1.21.5-final.log`.
- Server receipt: `build/smoke/1.21.5-server-r1/result.json`; 24.76 seconds; no startup errors.
- Client receipt: `D:\AncientDragon\build\smoke\1.21.5-client-1\result.json`.

### 1.21.6

- Runtime: `4760289b498cd3d42a1ba9cc7e4f32c14ac16689a6a2ee2129cd3eb2959d9e55`
- Sources: `3b689a1db8d0967af12d58e36f264970c470d550dac9690be67ef19c87cb749d`
- Public BlendLib: `99e7cdb12f057b844de4252a313398201076db1ea583da8214285be72fa32c8c`
- Classes: 217; original production sources preserved: 96; source-JAR Java files: 102; authored resources verified: 1468.
- Build log: `build/build-1.21.6-final.log`.
- Server receipt: `build/smoke/1.21.6-server-r1/result.json`; 25.21 seconds; no startup errors.
- Client receipt: `D:\AncientDragon\build\smoke\1.21.6-client-1\result.json`.

### 1.21.7

- Runtime: `324f650ded7d3883c74dbf9034a1be780ce2950d86769f791ad2ebf39395c7af`
- Sources: `28d415c15172cb1b6966e9170175e07693c9bb70fc14a29efd1eb6c727589721`
- Public BlendLib: `7816988cbcc66a64774f8c0c509c58f2724f386e9cfbb27dd532eaa078635f34`
- Classes: 217; original production sources preserved: 96; source-JAR Java files: 102; authored resources verified: 1468.
- Build log: `build/build-1.21.7-final.log`.
- Server receipt: `build/smoke/1.21.7-server-r2/result.json`; 25.78 seconds; no startup errors.
- Client receipt: `D:\AncientDragon\build\smoke\1.21.7-client-1\result.json`.

### 1.21.8

- Runtime: `4099a39aeab2f70e27186aabb11c548acd8558f1d07b72dc421c9726c690083d`
- Sources: `74234627478172c005ae2b336d5a00f5b21fb7d70086ca18fdc23701888c23b6`
- Public BlendLib: `ccb4c5bf4a132c5a33f6d5a5ee39586cf4ad7d4f5f2b69c8a6b5c3233575319b`
- Classes: 217; original production sources preserved: 96; source-JAR Java files: 102; authored resources verified: 1468.
- Build log: `build/build-1.21.8-final.log`.
- Server receipt: `build/smoke/1.21.8-server-r3/result.json`; 28.08 seconds; no startup errors.
- Client receipt: `D:\AncientDragon\build\smoke\1.21.8-client-1\result.json`.

## Evidence boundaries and retained failures

- Raw local build/runtime logs are retained under ignored `build/`. [verification-results.json](verification-results.json) records their final hashes, native test counts and compact completed runtime receipts for review; machine paths in receipts are evidence locations, not build inputs.
- All packaged clients are launched and closed by the main task. Passing receipts report GLB resource publication with zero diagnostics, no unexpected errors and clean window closure. No gameplay world was entered and visual/gameplay acceptance is not claimed.
- Earlier 1.21.8 server attempt `server-r1` exposed an inherited-field Mixin shadow; the current spear Mixin extends LivingEntity and uses its protected field. Final 1.21.8 evidence is `server-r3` with the exact final hash, not the earlier runtime.
- Minecraft 1.21.4 original tile regression exposed unsupported `firefly_bush`. The fixed shared placement/repair resolver and explicit non-air/rotation test passed; flower/particle visual approximations are documented in README.
- The first 1.21.1 runtime (hash `839c21e5df74fc0bbcfb44e16dc1533068eb7bee6b916da18223c44878f7a5a0`) reached readiness and stopped normally, but logged the vanilla Builder missing-data-fixer diagnostic and a remote Yggdrasil TLS error. It was retained as failed evidence. The final Fabric builder preserves saved entity state and avoids that diagnostic; `1.21.1-server-r2` passed with no startup errors.
- An initial 1.21.1 JUnit fixture tried registering production items after vanilla Bootstrap had frozen its intrusive-holder registry. Those runtime checks were moved to the guarded packaged-server contract; JUnit independently checks the native baseline getter, supported class interfaces and tier values.

## Reproduce

Use the commands in [README.md](README.md). Each final build executed `build check --max-workers=1 --no-daemon` against its exact target. The current generated-source pipeline includes all original and additional tests. Source generation preserves the original root tree. The final packaged server command uses JDK 21, `-Xms128m -Xmx1G -Dancientdragon.verifyLegacyRelics=true -jar fabric-server.jar nogui` in a fresh loopback-only directory; see `smoke_server.py` for its required explicit inputs. Earlier successful targets ran without the 1.21.1-specific verification flag, which has no handler on those targets.

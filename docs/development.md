# Development and debugging / 开发与调试

[Project overview / 返回项目介绍](../README.md)

The root project targets Minecraft 26.1.2. Run Gradle with JDK 25; builds for Minecraft 1.21.x also need the Java 21 toolchain. Gradle downloads the pinned BlendLib release and checks its SHA-256.

根项目面向 Minecraft 26.1.2。使用 JDK 25 启动 Gradle；编译 1.21.x 时还需 Java 21 工具链。BlendLib 由构建脚本下载并校验，无需配置作者电脑上的本地库路径。

## Build

Run from the repository root / 在仓库根目录执行：

```powershell
.\gradlew.bat --no-daemon --max-workers=1 check build
```

The root JAR is written to `build/libs/`. Other Minecraft targets use the version projects:

```powershell
.\gradlew.bat -p versions/legacy "-Pminecraft_version=1.21.1" build verifyRuntimeJar
.\gradlew.bat -p versions/modern "-Pminecraft_version=1.21.11" build verifyRuntimeJar
```

| Project | Targets | Output |
| --- | --- | --- |
| Root | 26.1.2 | `build/libs/` |
| `versions/legacy` | 1.21.1–1.21.8 | `versions/legacy/build/<Minecraft>/libs/` |
| `versions/modern` | 1.21.9–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | `versions/modern/build/<Minecraft>/libs/` |

See the [legacy build notes](../versions/legacy/README.md), [modern build notes](../versions/modern/README.md), and [Beta.1 verification record](release/beta1-verification.md) for target-specific details.

## Test world

```powershell
.\gradlew.bat runClient
```

Use a separate world with commands enabled. The commands below change the test encounter; they are not needed for normal survival play.

请使用单独的测试世界并开启命令。以下命令用于检查遭遇与动画，正常生存游玩不需要执行。

```text
/ancientdragon spawn
/ancientdragon awaken
/ancientdragon status
/ancientdragon combat status
/ancientdragon combat participants
/ancientdragon combat hitboxes on
/ancientdragon combat route on
/ancientdragon combat director on
```

Animation probes include `animation idle`, `animation cruise`, `animation bite`, and `animation death`. Combat probes include `combat phase <mountain|storm|solar>`, `combat attack <dive|tail|storm|solar|bite|wing>`, and `combat stability break`, all below `/ancientdragon`. Animation overrides can temporarily replace the state machine's current animation.

For visual checks, compare `F3+B` hitboxes with the model during flight, turns, attacks, and death. Test head and wing hits separately, then check the recorded part and stability. Startup and model loading alone do not verify a full encounter.

画面检查应覆盖飞行、转弯、攻击与死亡过程中的模型和 `F3+B` 碰撞箱对应关系，并分别检查头部伤害与翼部稳定性。成功启动和加载模型不能替代完整战斗验证。

## Mountain authoring

Natural generation and manual authoring are separate workflows. For an existing world's selected mountain, start with the read-only `structure locate` and `structure status` commands.

Manual terrain work belongs in a dedicated flat authoring world. Preview a terrain seed before building it. Once a base is accepted, finalize it before hand editing; finalization disables automatic reset.

```text
/ancientdragon mountain preview 984221
/ancientdragon mountain status
/ancientdragon mountain preview clear
/ancientdragon mountain build 984221
/ancientdragon mountain finalize
```

To place the frozen structure manually, run `structure preflight [quarter_turns]`, inspect `structure status`, then use `structure place <placementId> confirm` only after the preflight is ready. Started placement jobs support pause and resume; an unstarted preflight can be discarded with its placement ID.

自然生成与手动美术建造使用不同流程。查询当前世界的神山先用只读命令；制作底模或放置冻结结构请在专用美术世界中完成，并检查预览、预检和返回的任务 ID。

## Assets

The authoring source is `art/blender/ancient_dragon_authoring_v011.blend`. Export instructions are in [EXPORT_NOTES.md](../art/blender/EXPORT_NOTES.md). See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) before redistributing adapted assets.

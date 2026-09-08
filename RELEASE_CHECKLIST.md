# Ancient Dragon 发布就绪清单

## Beta.1 多版本发布

当前发布任务为 `0.1.0-beta.1`，目标覆盖 `1.21.1–1.21.11` 与 `26.1–26.2` 的上述 15 个正式游戏版本，
依赖已公开的 BlendLib `1.0.0-beta.2+<Minecraft>`。构建、运行、制品校验与两平台发布状态统一记录在
[Beta.1 多版本记录](docs/release/beta1-verification.md)。下方保留 Alpha.2 的历史证据，不表示本轮重新验收。

## Alpha.2 历史记录

状态：**`0.1.0-alpha.2` 已发布到 GitHub，并提交到 CurseForge 文件处理队列；保留实机回归项**（2026-08-07 审计）。

本文只记录发布门槛，不授予任何代码或素材许可。所有勾选项都应有可复核证据。

## 本次审计结果

- [x] 已创建 `LIy-hub/AncientDragon` 并绑定本地 `origin`。
- [x] 本机执行 `gradlew clean check build` 成功；47 个测试套件、221 个测试全部通过，古龙资源校验通过。
- [x] 当前运行 JAR 为 `ancient-dragon-0.1.0-alpha.2.jar`，大小 8,216,635 字节，SHA-256 为
  `8E0609B69009DE7DE0AA18DAE9FC7D47C953807E946D76BE688FC0C96BBFFCD6`。
- [x] 已筛选并压缩 3 张正式截图；仓库内最大单文件约 2.18 MiB，未触及 GitHub 的 100 MiB 单文件上限。
- [x] 发布 JAR 未发现 Blender 源文件、日志、测试存档、`tmp`、`art` 或 Snowbrush 数据包条目。

## P0：公开仓库或上传模组前必须解决

- [x] 已核实古龙 v011 模型、骨骼、动画和纹理的来源与权利。
  - 基础模型：Matt Alexander，`Realistic Minecraft Ender Dragon`，CC BY 4.0。
  - 纹理版本：Jazz Vincent / Stomy Circles，`Realistic Dragon Textures`，CC BY 4.0。
  - 两个上游条目均声明必须署名且允许商业使用；来源、许可证和修改内容已写入 `THIRD_PARTY_NOTICES.md`。
- [x] BlendLib 已采用 Apache-2.0（Blender Add-on 独立 GPL-3.0-or-later）并发布公开依赖。
  - 公开源码：`https://github.com/LIy-hub/BlendLib-Public`。
  - GitHub Release：`v1.0.0-alpha.1+26.1.2`，包含玩家安装用 Fabric JAR。
  - CurseForge：`https://www.curseforge.com/minecraft/mc-mods/blendlib`。
  - Ancient Dragon 依赖已改为 `blendlib >=1.0.0-alpha.1+26.1.2`。
- [x] Ancient Dragon 代码采用 MIT，媒体与派生模型资产采用 CC BY 4.0。
- [x] 已明确允许整合包、服务器客户端包、视频直播和平台收益，并要求保留许可证与署名。
- [x] 已移除 `fabric.mod.json` 的内部原型描述。
- [x] 版本已递增为 `0.1.0-alpha.2`，并更新 `CHANGELOG.md`。

## P1：远端仓库资料

- [x] GitHub 仓库确定为 `LIy-hub/AncientDragon`；源码采用公开策略。
- [x] `fabric.mod.json` 已补充 `contact.homepage`、`contact.sources`、`contact.issues`。
- [x] 已生成 512×512 模组图标与 1024×1024 平台 Logo，均为透明 PNG。
- [x] 已增加中英双语项目简介与 `CURSEFORGE_DESCRIPTION.md`。
- [x] README 已增加安装、依赖、客户端/服务端要求、兼容版本、世界备份和反馈入口。
- [x] 已增加 `CHANGELOG.md`。
- [x] 已增加 `THIRD_PARTY_NOTICES.md`，并打包进发布 JAR。
- [x] 已准备 1 张透明 Logo 和 3 张游戏内截图；未加入第三方音乐或视频素材。
- [x] 发布渠道确定为 GitHub Releases 与 CurseForge。

## P1：可复现构建与依赖

- [x] 已在独立 Gradle 用户目录中仅凭公开依赖完成 Java 编译，并在正常环境完成完整 `clean check build`。
- [x] Gradle 从固定 BlendLib GitHub Release 获取运行 JAR，校验 SHA-256 后提取嵌套 API/Common 编译 facade，不再依赖作者机器的 `D:` 盘路径。
- [x] 已增加 GitHub Actions 构建验证，固定 Java 25、Minecraft 26.1.2、Fabric Loader 0.19.3 与 Fabric API 版本。
- [x] 已验证发布 JAR 不含源模型备份、本地路径、测试存档、日志或账号信息。
- [x] 已验证 JAR 内包含正式 `fabric.mod.json`、Logo、MIT License、资产许可证和第三方署名。
- [x] 已记录发布 JAR 的文件名、大小和 SHA-256。

## 平台发布资料（已落实）

- 项目名：`Ancient Dragon`；中文名：`远古巨龙`。
- 一句话英文 Summary：已写入 `CURSEFORGE_DESCRIPTION.md`。
- 一句话中文简介：已写入 `CURSEFORGE_DESCRIPTION.md`。
- 详细中英双语 Description：已写入 `CURSEFORGE_DESCRIPTION.md`。
- 作者显示名：`Liy`；GitHub：`LIy-hub`。
- 支持平台：Fabric。
- 支持 Minecraft：当前精确声明 `26.1.2`。
- Java：25。
- 运行侧：当前为客户端与服务端都需要（`environment: "*"`）。
- 必需依赖：Fabric API、BlendLib。Fabric API 已设为 CurseForge 默认必需依赖；`0.1.0-alpha.2` 文件已将 BlendLib 项目 `1638315` 关联为必需依赖。
- 可选依赖与已知冲突：待补充。
- 许可证：代码 MIT；媒体与派生资产 CC BY 4.0。
- Issue/支持渠道：GitHub Issues。
- 允许整合包收录与服务器客户端包分发；分发时须保留许可证与署名。
- 允许 GitHub/CurseForge 发布及平台收益。
- GitHub Release：`https://github.com/LIy-hub/AncientDragon/releases/tag/v0.1.0-alpha.2`。
- CurseForge 项目：ID `1638465`，`https://www.curseforge.com/minecraft/mc-mods/ancient-dragon`。
- CurseForge `0.1.0-alpha.1` 文件：ID `8573068`，Alpha，Approved。
- CurseForge `0.1.0-alpha.2` 文件：ID `8590057`，Alpha，已上传并进入 Baking 文件处理队列。

## 首发验收

- [x] `gradlew check build` 全部通过。
- [ ] 干净客户端仅安装 Fabric API、BlendLib 与 Ancient Dragon 后可以进入世界。
- [ ] 独立服务端可以启动，客户端可以连接。
- [ ] 新世界与已有世界各完成一次神山定位、生成、古龙苏醒、战斗、死亡、尸骸采集验证。
- [ ] 发布前备份测试世界，并验证升级与移除模组后的行为已在说明中写清。
- [x] 发布 JAR 的 `fabric.mod.json` 已包含正式版本、依赖、许可证、图标和 GitHub 链接。
- [x] 已创建 Git tag `v0.1.0-alpha.1` 与同名 GitHub Prerelease，并上传同一 SHA-256 的 JAR 和 `SHA256SUMS`。
- [x] 已创建 CurseForge 项目并提交同一首发 JAR；平台当前正在处理文件并审核新项目。
- [x] `0.1.0-alpha.2` 发布候选已通过完整构建与 221 项测试，JAR 元数据和 SHA-256 已复核。
- [x] 已创建 `v0.1.0-alpha.2` GitHub Prerelease，并向 CurseForge 提交对应 Alpha 文件 `8590057`。

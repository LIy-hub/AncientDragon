# Ancient Dragon 发布就绪清单

状态：**源码仓库可公开；二进制首发仍等待 BlendLib 公开发布与实机验收**（2026-08-04 审计）。

本文只记录发布门槛，不授予任何代码或素材许可。所有勾选项都应有可复核证据。

## 本次审计结果

- [x] 已创建 `LIy-hub/AncientDragon` 并绑定本地 `origin`。
- [x] 本机离线执行 `gradlew check build` 成功；47 个测试套件、190 个测试全部通过，古龙资源校验通过。
- [x] 当前运行 JAR 为 `ancient-dragon-0.1.0-alpha.1.jar`，大小 8,219,170 字节，SHA-256 为
  `C2F6B0DA5CBC26C0B57B4BC6AA5618CE4A2E3B25BF3DC28F0F17295051FB4727`。
- [x] 忽略缓存、临时输出、Blender 备份和可再生成的 Snowbrush 笔刷函数后，首提候选约 17 MiB；最大单文件约 4.75 MiB，未触及 GitHub 的 100 MiB 单文件上限。
- [x] 发布 JAR 未发现 Blender 源文件、日志、测试存档、`tmp`、`art` 或 Snowbrush 数据包条目。

## P0：公开仓库或上传模组前必须解决

- [x] 已核实古龙 v011 模型、骨骼、动画和纹理的来源与权利。
  - 基础模型：Matt Alexander，`Realistic Minecraft Ender Dragon`，CC BY 4.0。
  - 纹理版本：Jazz Vincent / Stomy Circles，`Realistic Dragon Textures`，CC BY 4.0。
  - 两个上游条目均声明必须署名且允许商业使用；来源、许可证和修改内容已写入 `THIRD_PARTY_NOTICES.md`。
- [ ] 为 BlendLib 选择并落地正式许可证，发布可供玩家安装的同版本依赖。
  - 当前 Ancient Dragon 依赖 `blendlib >=1.0.0-rc.1+26.1.2`。
  - 当前构建只从 `D:/BlendLib/build/local-maven` 和本地 Common JAR 解析，外部克隆无法复现。
  - BlendLib 当前 GitHub 仓库是私有仓库，根目录只有 `LICENSE-PENDING`。
- [x] Ancient Dragon 代码采用 MIT，媒体与派生模型资产采用 CC BY 4.0。
- [x] 已明确允许整合包、服务器客户端包、视频直播和平台收益，并要求保留许可证与署名。
- [x] 已移除 `fabric.mod.json` 的内部原型描述。
- [x] 版本已改为 `0.1.0-alpha.1`，并新增 `CHANGELOG.md`。

## P1：远端仓库资料

- [x] GitHub 仓库确定为 `LIy-hub/AncientDragon`；源码采用公开策略。
- [x] `fabric.mod.json` 已补充 `contact.homepage`、`contact.sources`、`contact.issues`。
- [x] 已生成 512×512 模组图标与 1024×1024 平台 Logo，均为透明 PNG。
- [x] 已增加中英双语项目简介与 `CURSEFORGE_DESCRIPTION.md`。
- [x] README 已增加安装、依赖、客户端/服务端要求、兼容版本、世界备份和反馈入口。
- [x] 已增加 `CHANGELOG.md`。
- [x] 已增加 `THIRD_PARTY_NOTICES.md`，并打包进发布 JAR。
- [ ] 准备至少 1 张 Logo、3～6 张游戏内截图，以及可选的 Boss 战演示视频；不得使用无授权音乐或素材。
- [x] 发布渠道确定为 GitHub Releases 与 CurseForge。

## P1：可复现构建与依赖

- [ ] 让全新目录只凭公开依赖即可执行 `gradlew check build`。
- [ ] 为 BlendLib 提供公开 Maven 坐标，或在 CI 中从固定公开 tag 构建并发布到临时本地仓库；不能依赖作者机器的 `D:` 盘路径。
- [ ] 增加 GitHub Actions 构建验证，固定 Java 25、Minecraft 26.1.2、Fabric Loader 0.19.3 与 Fabric API 版本。
- [x] 已验证发布 JAR 不含源模型备份、本地路径、测试存档、日志或账号信息。
- [x] 已验证 JAR 内包含正式 `fabric.mod.json`、Logo、MIT License、资产许可证和第三方署名。
- [x] 已记录发布 JAR 的文件名、大小和 SHA-256。

## 平台发布资料（需要作者补充）

- 项目名：`Ancient Dragon`；中文名：`远古巨龙`。
- 一句话英文 Summary：已写入 `CURSEFORGE_DESCRIPTION.md`。
- 一句话中文简介：已写入 `CURSEFORGE_DESCRIPTION.md`。
- 详细中英双语 Description：已写入 `CURSEFORGE_DESCRIPTION.md`。
- 作者显示名：`Liy`；GitHub：`LIy-hub`。
- 支持平台：Fabric。
- 支持 Minecraft：当前精确声明 `26.1.2`。
- Java：25。
- 运行侧：当前为客户端与服务端都需要（`environment: "*"`）。
- 必需依赖：Fabric API、BlendLib；需要各平台的项目链接/项目 ID。
- 可选依赖与已知冲突：待补充。
- 许可证：代码 MIT；媒体与派生资产 CC BY 4.0。
- Issue/支持渠道：GitHub Issues。
- 允许整合包收录与服务器客户端包分发；分发时须保留许可证与署名。
- 允许 GitHub/CurseForge 发布及平台收益。

## 首发验收

- [x] `gradlew check build` 全部通过。
- [ ] 干净客户端仅安装 Fabric API、BlendLib 与 Ancient Dragon 后可以进入世界。
- [ ] 独立服务端可以启动，客户端可以连接。
- [ ] 新世界与已有世界各完成一次神山定位、生成、古龙苏醒、战斗、死亡、尸骸采集验证。
- [ ] 发布前备份测试世界，并验证升级与移除模组后的行为已在说明中写清。
- [x] 发布 JAR 的 `fabric.mod.json` 已包含正式版本、依赖、许可证、图标和 GitHub 链接。
- [ ] 创建 Git tag 与同名 Release，上传同一 SHA-256 的 JAR。

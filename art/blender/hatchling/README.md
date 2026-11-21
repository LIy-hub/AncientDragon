# Ancient Dragon 幼龙与龙蛋 · 美术原型

[预览图](preview.png)

| 资产 | Blender 源文件 | 导出 |
| --- | --- | --- |
| 幼龙 | [ancient_dragon_hatchling.blend](ancient_dragon_hatchling.blend) | [蒙皮 GLB](ancient_dragon_hatchling.glb) |
| 龙蛋 | [ancient_dragon_egg.blend](ancient_dragon_egg.blend) | [完整 GLB](ancient_dragon_egg.glb) · [临孵 GLB](ancient_dragon_egg_cracked.glb) · [32×32 背包图标](ancient_dragon_egg_item.png) |

两份 Blender 源文件均已打包纹理。龙蛋的两个状态可以从顶部场景列表切换。幼龙选中骨架后，可以在动作编辑器切换四个动作。

| 幼龙动作 | 时长 | 内容 |
| --- | --- | --- |
| `Hatchling_Perch_Idle` | 3.2 秒 | 收翼、呼吸、轻晃尾巴，可循环 |
| `Hatchling_Greet` | 2.8 秒 | 抬头转向、短暂注视，再回到起始姿态 |
| `Hatchling_Fly_Loop` | 0.8 秒 | 双翼拍动、轻微起伏，可循环，水平位移由实体控制 |
| `Hatchling_Glide` | 2 秒 | 展翼滑翔，翼尖和尾巴随气流轻摆，可循环 |

按 1 模型单位约等于 1 个方块制作。龙蛋高约 0.70，底部落在原点平面。幼龙站姿高约 0.86，这个飞行预览姿态的翼展约 3.0。Blender 中 +Z 向上，幼龙朝 −Y。导出的 GLB 使用 +Y 向上，幼龙朝 +Z。接入时需要按幼龙资产的尺度校准渲染和碰撞。幼龙 GLB 使用单个骨架、每顶点最多四个骨骼权重和内嵌贴图。运行时使用 `python tools/prepare_hatchling_models.py` 生成的外置贴图和严格 BlendLib 描述。重新导出美术 GLB 后运行此命令同步游戏资源。

幼龙改自 Ancient Dragon 的成年龙资产。保留骨架、双对翼和身体的 UV 布局。幼龙采用更短的吻部、嵌入眼眶的杏形眼和乳色短角，身体以青蓝和浅腹鳞为主。眼睛和眼睑共用一张贴图，保留青绿虹膜和竖瞳。短角有独立贴图。原始模型为 Matt Alexander 的 *Realistic Minecraft Ender Dragon*，原始贴图为 Jazz Vincent 的 *Realistic Dragon Textures*。作者、原始作品链接及授权见[第三方声明](../../../THIRD_PARTY_NOTICES.md)。龙蛋模型和图标为本项目原创。上述美术资产适用 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。

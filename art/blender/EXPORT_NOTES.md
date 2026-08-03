# v011 BlendLib export notes

- Authoring file: `ancient_dragon_authoring_v011.blend`
- Export collection: `ANCIENT_DRAGON_BLENDLIB_EXPORT`
- Runtime armature: `AncientDragon_GameRig`
- Runtime meshes: the ten objects prefixed with `GAME_`
- Profile: `blendlib:skinned_v1`
- Model key: `ancient_dragon:entity/ancient_dragon`
- Descriptor: `src/main/resources/assets/ancient_dragon/blend_models/entity/ancient_dragon.json`
- GLB: `src/main/resources/assets/ancient_dragon/models3d/entity/ancient_dragon.glb`

The stock strict exporter regenerates an automatic descriptor with `drg_*` semantic keys and
`units_per_block = 1.0`. Do not overwrite the curated project descriptor without restoring its
semantic state names, transitions, material flags, and `units_per_block = 0.1`.

The original v011 model and the pre-normalization authoring copy remain outside this project so a
future deformation review can compare before and after without destructive edits.

from pathlib import Path
import hashlib, json, shutil, struct, sys, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).parent
CONFIG = {
    'BlendLib-Public': dict(name='BlendLib', mod_id='blendlib', version='1.0.0-beta.3+26.3', tag='v1.0.0-beta.3+26.3', sha='61620626a0c33cd7142bcb7a8e356854290a4eb5', run=37047747831, cf=1638315, runtime_sha='8cf9adc9e415e83d4b987852867d150ec925ee4b9ba2898da22a1895920c30b4', changes_en='Ports GPU namespaces, pipeline metadata, pose/frustum APIs and the final-present hooks to Minecraft 26.3.', changes_zh='适配 26.3 的 GPU 命名空间、管线元数据、姿态/视锥接口与最终呈现钩子。', checks_en='Four focused pipeline/Mixin tests, main/client compilation and complete runtime-JAR validation passed.', checks_zh='4 项管线/Mixin 测试、公共端与客户端编译及完整运行 JAR 检查通过。'),
    'AncientDragon': dict(name='Ancient Dragon', mod_id='ancient_dragon', version='0.1.0-beta.1+26.3', tag='v0.1.0-beta.1+26.3', sha='bc6c5a5b77958c9bd979fbdcc7efbf9b2e47da1c', run=37048476420, cf=1638465, runtime_sha='dbbafead779ed0b50dbc715af58abe8ad242f8fdd30c3cc2cd4a65cb2fd9411b', changes_en='Ports item components, loot/velocity APIs, Sacred Mountain placement and registry codecs to 26.3. Uses the complete public BlendLib 26.3 library.', changes_zh='适配 26.3 的物品组件、战利品/速度接口、圣山放置与注册编解码器；使用完整的公开 BlendLib 26.3 库。', checks_en='222 tests in 48 suites, main/client compilation, Loader/Mixin/registry bootstrap and packaged gameplay/asset contracts passed.', checks_zh='48 个测试套件的 222 项测试、公共端与客户端编译、Loader/Mixin/注册初始化及打包玩法与资源契约检查通过。'),
    'level-10-enchantments': dict(name='Level 10 Enchantments', mod_id='level10_enchantments', version='1.5.1', tag='v1.5.1+26.3', sha='bfab35ea207d2e2e3798a122455c3c68a7c070b6', run=37046983086, cf=1615509, runtime_sha='2d5e106602858e50c728322ffb279fa9a1ed20c12b7870daee4be2d54e20d287', changes_en='Rebases all 29 enchantment definitions on official 26.3 resources and adapts registry/tag predicates and the Frost Walker block-state schema. Keeps the 1.5.1 gameplay policy.', changes_zh='按官方 26.3 资源适配全部 29 种附魔定义、注册/标签条件及冰霜行者方块状态格式，保持 1.5.1 的玩法策略。', checks_en='Eight policy executables, resource parity, main/client compilation, packaged-JAR validation and Loader/common/client Mixin bootstrap passed.', checks_zh='8 个策略检查程序、资源一致性、公共端与客户端编译、打包 JAR 检查及 Loader/公共端/客户端 Mixin 初始化检查通过。'),
    'LoliPickaxe-Fabric': dict(name='LoliPickaxe Fabric', mod_id='liymod', version='1.0.0', tag='v1.0.0+26.3', sha='63fa0103440a30fd13a302e9a9fe2dfc59336680', run=37045152836, cf=1620886, runtime_sha=None, changes_en='Adapts main-hand punch handling, invulnerability, item-drop prediction/storage protection, keybinding categories and artwork links to 26.3.', changes_zh='适配 26.3 的主手攻击、无敌状态、物品掉落预测/存储保护、按键分类及图片链接接口。', checks_en='78 configuration/safe-effect assertions, main/client compilation, release/content contracts (28 items, 5 blocks, 23 recipes), and Loader/Mixin bootstrap passed.', checks_zh='78 项配置/安全效果断言、公共端与客户端编译、发布/内容契约（28 件物品、5 个方块、23 个配方）及 Loader/Mixin 初始化检查通过。'),
}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

for repo in sys.argv[1:] or CONFIG:
    cfg = CONFIG[repo]
    folder = ROOT / repo
    ci = json.loads((folder / 'ci-run.json').read_text(encoding='utf-8-sig'))
    assert ci['head_sha'] == cfg['sha'] and ci['conclusion'] == 'success' and ci['status'] == 'completed'
    assert ci['head_branch'] == 'mc/26.3'
    jars = list((folder / 'ci-artifacts').rglob('*.jar'))
    runtime = [p for p in jars if not p.name.endswith('-sources.jar')]
    sources = [p for p in jars if p.name.endswith('-sources.jar')]
    assert len(runtime) == len(sources) == 1, [str(p) for p in jars]
    runtime, sources = runtime[0], sources[0]
    with zipfile.ZipFile(runtime) as jar:
        assert jar.testzip() is None
        metadata = json.loads(jar.read('fabric.mod.json'))
        expected_mod_id = 'level10enchantments' if repo == 'level-10-enchantments' else cfg['mod_id']
        assert metadata['id'] == expected_mod_id, metadata['id']
        assert metadata['version'] == cfg['version']
        depends = metadata['depends']
        expected_minecraft = '~26.3' if repo == 'LoliPickaxe-Fabric' else '26.3'
        assert depends['minecraft'] == expected_minecraft
        assert depends['java'] == '>=25'
        assert depends['fabricloader'] == '>=0.19.5'
        expected_api = '*' if repo == 'LoliPickaxe-Fabric' else ('>=0.161.0' if repo == 'level-10-enchantments' else '>=0.161.0+26.3')
        assert depends['fabric-api'] == expected_api, depends
        assert metadata['icon'] in jar.namelist()
        assert any('license' in n.lower() for n in jar.namelist())
        classes = [n for n in jar.namelist() if n.endswith('.class')]
        assert len(classes) > 0
        versions = sorted(set(struct.unpack('>H', jar.read(n)[6:8])[0] for n in classes))
        assert max(versions) <= 69
        for name in metadata.get('entrypoints', {}).values():
            for cls in name:
                if isinstance(cls, str):
                    assert cls.replace('.', '/') + '.class' in jar.namelist()
        if repo == 'AncientDragon':
            assert depends['blendlib'] == '>=1.0.0-beta.3 <1.1.0', depends
    if cfg['runtime_sha']:
        assert digest(runtime) == cfg['runtime_sha'], (repo, digest(runtime), cfg['runtime_sha'])
    totals = dict(tests=0, failures=0, errors=0, skipped=0, suites=0)
    for report in (folder / 'ci-artifacts').rglob('TEST-*.xml'):
        suite = ET.parse(report).getroot()
        totals['suites'] += 1
        for k in ('tests', 'failures', 'errors', 'skipped'):
            totals[k] += int(suite.get(k, '0'))
    assert totals['failures'] == totals['errors'] == 0
    if repo == 'BlendLib-Public':
        assert totals['tests'] == 4
    if repo == 'AncientDragon':
        assert totals['tests'] == 222 and totals['suites'] == 48
    out = folder / 'release'
    out.mkdir(exist_ok=True)
    runtime_name = runtime.name if '26.3' in runtime.name else runtime.stem + '+26.3.jar'
    source_name = sources.name if '26.3' in sources.name else sources.name.removesuffix('-sources.jar') + '+26.3-sources.jar'
    assets = []
    for original, name, role in [(runtime, runtime_name, 'runtime'), (sources, source_name, 'sources')]:
        dest = out / name
        shutil.copyfile(original, dest)
        assert digest(dest) == digest(original)
        assets.append(dict(filename=name, original_ci_filename=original.name, role=role, size=dest.stat().st_size, sha256=digest(dest)))
    manifest = dict(project=cfg['name'], repository=f'LIy-hub/{repo}', minecraft='26.3', release_tag=cfg['tag'], source_commit=cfg['sha'], source_branch='mc/26.3', ci_url=ci['html_url'], ci_run_id=cfg['run'], ci_conclusion=ci['conclusion'], java='25+', fabric_loader='0.19.5+', fabric_api='0.161.0+26.3', mod_metadata={k:metadata[k] for k in ['id','version','environment','depends']}, class_count=len(classes), class_file_versions=versions, junit_reports=totals, automated_checks=cfg['checks_en'], client_world_playtest=False, dedicated_server_world_startup=False, multiplayer_playtest=False, assets=assets, packaging='Unmodified CI JAR bytes; filenames include the exact Minecraft target.', curseforge_project_id=cfg['cf'])
    (out/'verification-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    checksum_files = [out/a['filename'] for a in assets] + [out/'verification-manifest.json']
    (out/'SHA256SUMS.txt').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in checksum_files),encoding='utf-8')
    extra_en = '\nAncient Dragon also requires [BlendLib 1.0.0-beta.3+26.3](https://github.com/LIy-hub/BlendLib-Public/releases/tag/v1.0.0-beta.3%2B26.3), installed alongside it on both client and server.\n' if repo == 'AncientDragon' else ''
    extra_zh = '\nAncient Dragon 还需要同时安装 [BlendLib 1.0.0-beta.3+26.3](https://www.curseforge.com/minecraft/mc-mods/blendlib)，客户端与服务端都需安装。\n' if repo == 'AncientDragon' else ''
    notes = f'''## English

{cfg['name']} {cfg['version']} for **Minecraft 26.3 / Fabric**. This is a Beta compatibility release from the verified `mc/26.3` branch.

{cfg['changes_en']}

Requires **Java 25+, Fabric Loader 0.19.5+ and Fabric API 0.161.0+26.3 or a newer compatible 26.3 build**. Use the runtime JAR for Minecraft 26.3; the sources JAR is for development.
{extra_en}
{cfg['checks_en']} [Successful CI]({ci['html_url']}) at `{cfg['sha']}`. The attached JARs retain their original CI bytes; SHA-256 values and metadata are included in the verification manifest.

Dedicated-server/world startup, interactive client/world testing and multiplayer playtests have not yet been verified for 26.3. Automated bootstrap tests do not establish those results. Existing releases for older Minecraft versions remain available separately.

## 中文

{cfg['name']} {cfg['version']}，适用于 **Minecraft 26.3 / Fabric**。本次按 Beta 适配版发布，源码来自已验证的 `mc/26.3` 分支。

{cfg['changes_zh']}

需要 **Java 25+、Fabric Loader 0.19.5+，以及 Fabric API 0.161.0+26.3 或更新的兼容 26.3 构建**。安装对应 Minecraft 26.3 的运行 JAR；源码 JAR 仅供开发使用。
{extra_zh}
{cfg['checks_zh']} [CI 构建记录]({ci['html_url']})对应提交 `{cfg['sha']}`。附件保留 CI 原始 JAR 字节，校验和及元数据见验证清单。

26.3 的专用服务器/世界启动、客户端游戏内测试及多人测试尚未验证；自动初始化测试不代表这些测试已完成。旧 Minecraft 版本的发布文件仍单独保留。
'''
    (folder/'release-notes.md').write_text(notes,encoding='utf-8')
    (folder/'curseforge-notes.md').write_text(notes,encoding='utf-8')
    print(json.dumps(dict(repo=repo,tag=cfg['tag'],runtime=runtime_name,size=runtime.stat().st_size,sha256=digest(runtime),depends=depends,junit=totals),ensure_ascii=False),flush=True)

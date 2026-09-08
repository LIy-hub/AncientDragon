"""Copy verified version artifacts to a content-addressed, immutable client/release staging directory."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("minecraft")
args = parser.parse_args()
base = Path(__file__).resolve().parent / "build"
build = base / args.minecraft
report = json.loads((build / "jar-verification.json").read_text())
runtime = build / "libs" / f"ancient-dragon-0.1.0-beta.1+{args.minecraft}.jar"
sources = runtime.with_name(runtime.stem + "-sources.jar")
digest = hashlib.sha256(runtime.read_bytes()).hexdigest()
assert digest == report["sha256"]
with zipfile.ZipFile(runtime) as archive:
    metadata = json.loads(archive.read("fabric.mod.json"))
assert metadata["depends"]["minecraft"] == args.minecraft
directory = base / "release-beta1" / args.minecraft / digest
directory.mkdir(parents=True, exist_ok=True)
hashes = {}
for source in [runtime, sources]:
    target = directory / source.name
    source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    if target.exists():
        assert hashlib.sha256(target.read_bytes()).hexdigest() == source_hash, "Refusing to overwrite frozen artifact"
    else:
        shutil.copy2(source, target)
    hashes[target.name] = source_hash
(directory / "receipt.json").write_text(json.dumps({"minecraft": args.minecraft, "hashes": hashes}, indent=2))
print(directory)

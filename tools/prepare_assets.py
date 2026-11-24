"""Convert supplied assets with the cached Android Gradle SVG converter (no app dependency)."""
from pathlib import Path
from PIL import Image
import hashlib
import json
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
coordinates = ["com.android.tools/sdk-common", "com.android.tools/common", "com.google.guava/guava",
               "com.android.tools/annotations", "org.jetbrains.kotlin/kotlin-stdlib", "com.google.code.gson/gson"]
jars = []
for coordinate in coordinates:
    candidates = sorted(p for p in (cache / coordinate).rglob("*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
    if not candidates:
        raise RuntimeError(f"Missing cached dependency: {coordinate}; initialize this project's Gradle build first")
    jars.append(str(candidates[-1]))
out = ROOT / "build/asset-conversion"
out.mkdir(parents=True, exist_ok=True)
result = subprocess.run(["java", "-cp", os.pathsep.join(jars), str(ROOT / "tools/ConvertSvg.java"),
    str(ROOT / "design-assets/teams"), str(out / "vectors"), str(out / "previews")],
    check=True, capture_output=True, text=True, encoding="utf-8")
(out / "conversion.log").write_text(result.stdout + result.stderr, encoding="utf-8")
print(result.stdout)
resources = ROOT / "app/src/main/res"
for vector in (out / "vectors").glob("team_logo_*.xml"):
    shutil.copy2(vector, resources / "drawable" / vector.name)
for asset in (ROOT / "design-assets/branding").glob("*.png"):
    shutil.copy2(asset, resources / "drawable-nodpi" / asset.name)
icon = Image.open(ROOT / "design-assets/branding/app_icon.png")
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    target = resources / ("mipmap-" + density)
    target.mkdir(exist_ok=True)
    icon.resize((size, size), Image.Resampling.LANCZOS).save(target / "ic_launcher.png")
manifest = [{"path": str(p.relative_to(ROOT)).replace("\\", "/"),
             "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted((ROOT / "design-assets").rglob("*")) if p.is_file()]
(ROOT / "docs/asset-source-hashes.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")

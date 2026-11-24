"""List handoff source files without staging, deleting, or exporting local build/data files."""
import subprocess
from pathlib import Path, PurePosixPath

root = Path(__file__).resolve().parents[1]
target = root / "docs/handoff-files.txt"
ignored_parts = {".git", ".gradle", ".kotlin", ".idea", "build", ".cxx", ".externalNativeBuild", "captures", "__pycache__"}
ignored_suffixes = {".hprof", ".apk", ".aab", ".jks", ".keystore", ".log", ".pyc", ".class"}
ignored_names = {"local.properties", "keystore.properties", "google-services.json"}
raw = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root)
candidates = {p.decode("utf-8") for p in raw.split(b"\0") if p}
candidates.add(target.relative_to(root).as_posix())
files = []
for name in sorted(candidates):
    path = PurePosixPath(name)
    if ignored_parts.intersection(path.parts) or path.suffix.lower() in ignored_suffixes or path.name in ignored_names:
        continue
    if (root / name).is_file() or root / name == target:
        files.append(name)
target.write_text("\n".join(files) + "\n", encoding="utf-8")
print(f"Listed {len(files)} source/document files in {target.relative_to(root)}; no stage/export/delete performed")

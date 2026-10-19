"""比较解析到的依赖与已提交的清单；不提交包含本机路径的 TSV。"""
from pathlib import Path
import csv, json, sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

root = Path(__file__).resolve().parents[2]
path = root / "build/audit/dependencies.tsv"
if not path.is_file():
    sys.exit("请先按 docs/BUILDING.md 导出依赖")
with path.open(encoding="utf-8", newline="") as stream:
    rows = list(csv.DictReader(stream, delimiter="\t"))
actual = {":".join(row[key] for key in ("group", "artifact", "version")) for row in rows}
records = json.loads((root / "docs/dependencies.json").read_text(encoding="utf-8"))
known = {item["coordinate"] for item in records["dependencies"]}
actual_scopes = {}
for row in rows:
    coordinate = ":".join(row[key] for key in ("group", "artifact", "version"))
    actual_scopes.setdefault(coordinate, set()).add(row["scope"])
known_scopes = {item["coordinate"]: set(item["scopes"]) for item in records["dependencies"]}
added, removed = actual - known, known - actual
changed = {name for name in actual & known if actual_scopes[name] != known_scopes[name]}
for name in sorted(added): print("新增: " + name)
for name in sorted(removed): print("不再解析: " + name)
for name in sorted(changed): print("配置范围变化: " + name)
if added or removed or changed:
    sys.exit("请更新清单、许可全文与 APK 中的运行时许可副本")
print("依赖坐标与已提交清单一致")

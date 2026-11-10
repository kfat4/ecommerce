# usage: python3 gen-pom.py <name> <description> <dep>...   dep = groupId:artifactId[:scope]
import sys, pathlib
name, desc, deps = sys.argv[1], sys.argv[2], sys.argv[3:]
tpl = pathlib.Path(__file__).with_name("service-pom.xml").read_text()
xml = []
for d in deps:
    parts = d.split(":")
    scope = f"\n            <scope>{parts[2]}</scope>" if len(parts) > 2 else ""
    xml.append(f"        <dependency>\n            <groupId>{parts[0]}</groupId>\n            <artifactId>{parts[1]}</artifactId>{scope}\n        </dependency>")
out = tpl.replace("__NAME__", name).replace("__DESC__", desc).replace("__EXTRA__", "\n".join(xml))
pathlib.Path(f"services/{name}/pom.xml").write_text(out)

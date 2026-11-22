#!/usr/bin/env python3
"""CI adım 1-2: gerekli dosyalar var mı, sürüm her yerde aynı mı, belgelerdeki birim test sayısı gerçek @Test sayısıyla eşleşiyor mu.
Derleme gerektirmez. Eşleşmeyen belge CI'ı kırar (kanıtsız/eski bilgi kalmasın)."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
rd = lambda p: (root / p).read_text(encoding="utf-8")
err = []

for f in ["build.gradle.kts", "settings.gradle.kts", "gradle.properties", "app/build.gradle.kts", "app/src/main/AndroidManifest.xml",
          ".github/workflows/build.yml", ".env.example", ".gitignore", "README.md", "PROJECT_STATUS.md", "TEST_RESULTS.md", "CHANGELOG.md",
          "docs/CI_CALISTIRMA.md", "docs/ANDROID16.md", "docs/SECURITY.md", "docs/FEATURE_SPECS.md"]:
    if not (root / f).is_file(): err.append(f"eksik dosya: {f}")
if err: print("\n".join(err)); sys.exit(1)

g = rd("app/build.gradle.kts")
ver = re.search(r'versionName\s*=\s*"([^"]+)"', g).group(1)
code = int(re.search(r'versionCode\s*=\s*(\d+)', g).group(1))
checks = {
    "README.md": r"^# Zyren (\S+)",
    "PROJECT_STATUS.md": r"^# PROJECT_STATUS — Zyren (\S+)",
    "TEST_RESULTS.md": r"Sürüm (\S+)",
    "CHANGELOG.md": r"^## (\d+\.\d+\.\d+)",
    "app/src/main/java/com/zyren/app/ui/SettingsScreen.kt": r"Zyren (\d+\.\d+\.\d+) ·",
}
for f, pat in checks.items():
    m = re.search(pat, rd(f), re.M)
    if not m: err.append(f"{f}: sürüm bulunamadı")
    elif m.group(1) != ver: err.append(f"{f}: sürüm {m.group(1)} ≠ build.gradle {ver}")

# gizli bilgi dosyaları depoda olmamalı
gi = rd(".gitignore")
for t in [".env", "*.jks", "*.keystore", "local.properties"]:
    if t not in gi: err.append(f".gitignore'da {t} yok")
if any(p.name == ".env" for p in root.rglob(".env")): err.append(".env dosyası depoda")

# gerçek test sayıları ↔ belgeler
def count(d): return sum(len(re.findall(r"@Test\b", p.read_text(encoding="utf-8"))) for p in (root / d).rglob("*.kt"))
unit, inst = count("app/src/test"), count("app/src/androidTest")
for f in ["README.md", "PROJECT_STATUS.md", "TEST_RESULTS.md", "docs/CI_CALISTIRMA.md"]:
    for n in re.findall(r"(\d+)\s*birim", rd(f)) + re.findall(r"[Bb]irim testleri \((\d+)\)", rd(f)):
        if int(n) != unit: err.append(f"{f}: {n} birim testi yazıyor, gerçek sayı {unit}")

if err: print("\n".join(err)); sys.exit(1)
print(f"proje kontrolü tamam: sürüm {ver} (versionCode {code}) her yerde aynı; {unit} birim + {inst} cihaz testi, belgelerle eşleşiyor")

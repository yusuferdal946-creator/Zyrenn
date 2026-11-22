#!/usr/bin/env python3
"""CI: 12 özellik FEATURE_SPECS.md ve PROJECT_STATUS.md'de korunuyor mu? Testsiz 'TAMAM/CİHAZDA TEST EDİLDİ' iddiası var mı?"""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
FEATURES = ["Akıllı Planlama", "Otomatik Test ve Kalite Kontrol", "Bilgi Merkezi", "Hata ve Performans Merkezi", "Güvenli Otomasyon",
            "Yaşayan Hafıza", "Simülasyon Laboratuvarı", "Kendi Araçlarını Geliştirme", "Otonom Görev Merkezi",
            "Telefonun Dijital İkizi", "Holografik 3D Kontrol Merkezi", "Çoklu Uzman Ajanlar"]
errors = []
for f in ("docs/FEATURE_SPECS.md", "PROJECT_STATUS.md"):
    text = (root / f).read_text(encoding="utf-8")
    for name in FEATURES:
        if name not in text:
            errors.append(f"{f}: '{name}' yok")
status = (root / "PROJECT_STATUS.md").read_text(encoding="utf-8")
results = (root / "TEST_RESULTS.md").read_text(encoding="utf-8")
has_device_evidence = re.search(r"^- \[x\]", results, re.M) is not None
for line in status.splitlines():
    if line.startswith("|") and re.search(r"\|\s*(TAMAM|CİHAZDA TEST EDİLDİ)\s*\|", line) and not has_device_evidence:
        errors.append("PROJECT_STATUS.md: kanıt (TEST_RESULTS.md'de işaretli test) olmadan 'TAMAM/CİHAZDA TEST EDİLDİ': " + line[:80])
if errors:
    print("\n".join(errors)); sys.exit(1)
print(f"belge kontrolü tamam: {len(FEATURES)} özellik iki belgede de var; kanıtsız TAMAM iddiası yok")

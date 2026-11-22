#!/usr/bin/env python3
"""CI politika denetimi (statik): her aracın yetkiye bağlı olduğunu, kritik işlemlerin onay gerektirdiğini,
araçların izinleri değiştiremediğini ve ağ/yedek güvenlik ayarlarını kaynak koddan doğrular. Derleme gerektirmez."""
import re, sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent / "app/src/main"
J = R / "java/com/zyren/app"
rd = lambda p: (J / p).read_text(encoding="utf-8")
err = []

# --- araçlar ---
def strip_comments(t):
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    return re.sub(r"(?m)^\s*//.*$", "", t)

tools = {}
simple = 0
for f in ["core/tools/BuiltinTools.kt", "core/tools/FeatureTools.kt", "core/tools/AtelierTools.kt", "core/tools/OpenSourceTools.kt"]:
    src = strip_comments(rd(f))
    parts = src.split("reg.add(Tool(")[1:]
    simple += len(parts)
    for seg in parts:
        m = re.match(r'"(\w+)",\s*"(\w+)"', seg)
        if not m: err.append("ayrıştırılamayan araç: " + seg[:40]); continue
        n = m.group(1)
        # yetki: ad+eklenti+açıklama'dan sonra gelen ilk 'null|Capability.X' ve ardından 'none|none2|{' (iç içe tırnaklı açıklamalara dayanıklı)
        cm = re.search(r',\s*(null|Capability\.[A-Z_]+)\s*,\s*(?:none2?|\{)', seg)
        if not cm: err.append(f"{n}: yetki ayrıştırılamadı"); continue
        if n in tools: err.append(f"yinelenen araç: {n}")
        tools[n] = cm.group(1)
if simple != len(tools): err.append(f"araç ayrıştırma uyuşmazlığı: {simple} araç var, {len(tools)} ayrıştırıldı")
src = rd("core/tools/CustomTools.kt")
if "Capability.CODE_RUN" not in src: err.append("özel araçlar CODE_RUN'a bağlı değil")

SELF_GATED = {"dosya_listele", "dosya_oku", "dosya_yaz", "dosya_sil", "dosya_ara", "komut_calistir", "ham_kabuk", "bilgi_dosyaya_aktar", "simule_et"}
nul = {n for n, c in tools.items() if c == "null"}
if nul != SELF_GATED: err.append(f"yetkisiz araç kümesi beklenenden farklı: fazla={nul - SELF_GATED}, eksik={SELF_GATED - nul}")

REQUIRED = {"sms_gonder": "SMS_SEND", "ara": "CALL_PHONE", "ekran_oku": "ACCESSIBILITY", "ekrana_dokun": "ACCESSIBILITY", "ekrana_yaz": "ACCESSIBILITY",
            "sistem_tusu": "ACCESSIBILITY", "otomasyon_ekle": "AUTOMATION_MANAGE", "otomasyon_sil": "AUTOMATION_MANAGE", "ozel_arac_olustur": "TOOL_CREATE",
            "ozel_arac_sil": "TOOL_CREATE", "atolye_uygulama_sil": "APP_DELETE", "atolye_uygulama_yap": "APP_BUILD", "bildirimleri_oku": "NOTIF_READ", "kisi_bul": "CONTACTS_READ", "arama_gecmisi": "CALLLOG_READ",
            "whatsapp_mesaj": "MESSAGE_APP", "web_ara": "WEB", "acik_kaynak_ara": "WEB", "acik_kaynak_incele": "WEB", "sayfa_oku": "WEB", "sayfa_oku ": "WEB"}
for n, cap in REQUIRED.items():
    n = n.strip()
    if tools.get(n) != f"Capability.{cap}": err.append(f"{n}: beklenen Capability.{cap}, bulunan {tools.get(n)}")

# --- yetki sınıfları ve riskleri ---
pm = rd("core/security/PermissionManager.kt")
risk = dict(re.findall(r'^\s+([A-Z_]+)\("(?:[^"\\]|\\.)*",\s*Risk\.(\w+)', pm, re.M))
for c in ["SMS_SEND", "CALL_PHONE", "FILE_OVERWRITE", "FILE_DELETE", "BACKUP_RESTORE", "SHELL_ANY", "ACCESSIBILITY", "AUTOMATION_MANAGE", "TOOL_CREATE", "APP_DELETE"]:
    if risk.get(c) != "CRITICAL": err.append(f"{c} KRİTİK olmalı (bulunan {risk.get(c)})")
for c in tools.values():
    if c != "null" and c.split(".")[1] not in risk: err.append(f"bilinmeyen yetki {c}")
off = re.search(r"fun defaultEnabled\(c: Capability\): Boolean = when \(c\) \{(.*?)-> false", pm, re.S)
for c in ["SHELL_ANY", "SMS_SEND", "CALL_PHONE", "ACCESSIBILITY", "NOTIF_READ", "CALLLOG_READ", "CONTACTS_READ", "AUTOMATION_RUN", "TOOL_CREATE", "AUTOMATION_MANAGE"]:
    if not off or c not in off.group(1): err.append(f"{c} varsayılan KAPALI olmalı")
chk = pm[pm.index("suspend fun check("):pm.index("fun riskOf")]
for token in ["Risk.CRITICAL", "approvalHandler?.invoke", "?: false", "_lockdown.value"]:
    if token not in chk: err.append(f"PermissionManager.check içinde '{token}' yok")

# --- dosya/sandbox işlemleri ---
ws = rd("core/workspace/Workspace.kt")
def body(name):
    m = re.search(r"(?:suspend )?fun " + name + r"\(.*?(?=\n    (?:suspend |private |fun |var |val |/\*\*)|\Z)", ws, re.S)
    return m.group(0) if m else ""
need = {"list": ["guarded("], "read": ["guarded(", "notReserved"], "write": ["FILE_OVERWRITE", "FILE_WRITE", "isReserved"], "writeNew": ["guarded(", "notReserved"],
        "importStream": ["guarded(", "notReserved"], "mkdir": ["guarded(", "notReserved"], "delete": ["FILE_DELETE", "notReserved"], "search": ["guarded("],
        "restoreVersion": ["BACKUP_RESTORE", "notReserved"], "restoreBackup": ["BACKUP_RESTORE"], "backup": ["guarded("]}
for fn, toks in need.items():
    b = body(fn)
    for t in toks:
        if t not in b: err.append(f"Workspace.{fn}: '{t}' yok")
sh = rd("core/tools/SafeShell.kt")
for tok in ['"rm" -> Capability.FILE_DELETE', '"mkdir", "touch", "cp", "mv" -> Capability.FILE_WRITE', '"ls", "cat", "head", "tail", "wc", "grep", "find" -> Capability.FILE_READ', "!check(cap"]:
    if tok not in sh: err.append(f"SafeShell: '{tok}' yok")
if "ProcessBuilder" in sh or "Runtime.getRuntime().exec" in sh: err.append("SafeShell süreç başlatmamalı")
if "Capability.SHELL_ANY" not in rd("core/tools/Runners.kt"): err.append("ShellRunner SHELL_ANY denetlemiyor")

# --- araçlar izinleri değiştiremez / onay işleyicisi tek yerde ---
for f in ["core/tools/BuiltinTools.kt", "core/tools/FeatureTools.kt", "core/tools/AtelierTools.kt", "core/tools/OpenSourceTools.kt", "knowledge/OpenSourceCheck.kt", "knowledge/KnowledgeGraph.kt", "atelier/Atelier.kt", "atelier/MiniAppGuard.kt", "core/tools/CustomTools.kt", "agents/AgentRoles.kt", "automation/AutomationEngine.kt", "plan/PlanEngine.kt", "knowledge/KnowledgeHub.kt"]:
    s = strip_comments(rd(f))
    if re.search(r"permissions\.setEnabled|setLockdown\(|approvalHandler|PermissionManager|c\.permissions|PermissionBridge", s): err.append(f"{f}: izin sistemine erişiyor")
for p in J.rglob("*.kt"):
    s = p.read_text(encoding="utf-8")
    rel = p.relative_to(J).as_posix()
    if "approvalHandler =" in s and rel != "ui/ZyrenViewModel.kt": err.append(f"{rel}: onay işleyicisi yalnızca arayüzde atanabilir")
    if re.search(r"permissions\.setEnabled\(|permissions\.setLockdown\(", s) and not rel.startswith("ui/"): err.append(f"{rel}: izinleri yalnızca arayüz değiştirebilir")

# --- arka plan güvenliği ---
ae = rd("automation/AutomationEngine.kt")
bg = set(re.findall(r'"(\w+)"', re.search(r"BACKGROUND_ALLOWED = setOf\((.*?)\)", ae, re.S).group(1)))
for n in bg:
    if n not in tools: err.append(f"arka plan izinli araç yok: {n}")
    elif tools[n] != "null" and risk.get(tools[n].split(".")[1]) == "CRITICAL": err.append(f"arka planda kritik araç: {n}")
if "risk == Risk.CRITICAL" not in ae: err.append("AutomationEngine kritik araçları atlamıyor")

# --- ağ ve yedek ---
mf = (R / "AndroidManifest.xml").read_text(encoding="utf-8")
if "usesCleartextTraffic=\"true\"" in mf: err.append("usesCleartextTraffic=true yasak")
if "networkSecurityConfig" not in mf: err.append("networkSecurityConfig yok")
if 'allowBackup="false"' not in mf or "dataExtractionRules" not in mf: err.append("yedekleme kapalı olmalı")
ns = (R / "res/xml/network_security_config.xml").read_text(encoding="utf-8")
if 'base-config cleartextTrafficPermitted="false"' not in ns: err.append("taban yapılandırma cleartext'i yasaklamalı")
doms = re.findall(r"<domain[^>]*>([^<]+)</domain>", ns)
if sorted(doms) != ["127.0.0.1", "localhost"]: err.append(f"cleartext istisnaları yalnızca yerel olmalı: {doms}")
if "isCleartextAllowed" not in rd("core/model/Models.kt"): err.append("istemci tarafı cleartext koruması yok")
if 'u.scheme == "https"' not in rd("core/tools/WebTools.kt"): err.append("web okuma yalnızca https olmalı")
bc = rd("core/workspace/BackupCrypto.kt")
for t in ["AES/GCM/NoPadding", "PBKDF2WithHmacSHA256", "updateAAD", "clearPassword", "SecureRandom"]:
    if t not in bc: err.append(f"BackupCrypto: {t} yok")
if "backup()" not in ws[ws.index("suspend fun exportEncrypted"):]: err.append("şifreli dışa aktarma yedek akışını kullanmıyor")

if err:
    print("POLİTİKA İHLALİ:\n- " + "\n- ".join(err)); sys.exit(1)
print(f"politika denetimi tamam: {len(tools)} araç; yetkisiz {len(nul)} (hepsi kendi içinde denetliyor); kritik yetkiler, varsayılan-kapalı kümesi, onay hattı, ağ ve yedek ayarları doğrulandı")

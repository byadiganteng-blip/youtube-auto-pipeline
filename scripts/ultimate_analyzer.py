#!/usr/bin/env python3
# ============================================================
# ULTIMATE ANALYZER v3 - Deep Analysis + 30+ Checks
# Auto-detect & auto-fix semua masalah Android project
# Created by KARYADI, Coding by KARYADI
# ============================================================

import os, re, sys, json, hashlib
from datetime import datetime
from collections import Counter, defaultdict

WORKDIR     = os.environ.get("WORKDIR", ".")
OUTPUT_DIR  = os.environ.get("OUTPUT_DIR", "output")
AUTO_FIX    = os.environ.get("AUTO_FIX", "true").lower() == "true"

os.makedirs(OUTPUT_DIR, exist_ok=True)
def P(name): return os.path.join(OUTPUT_DIR, name)

REPORT_TXT       = P("report.txt")
REPORT_JSON      = P("report.json")
ISSUES_TXT       = P("issues.txt")
FIXES_TXT        = P("fixes_applied.txt")
PREDICTION_TXT   = P("build_predictions.txt")
DEPENDENCY_TXT   = P("dependency_graph.txt")
SECURITY_TXT     = P("security_issues.txt")
PERFORMANCE_TXT  = P("performance_issues.txt")

report = []
all_issues = []
all_fixes = []
predictions = []

def log(m=""):
    print(m); report.append(str(m))

def section(t):
    log(); log("=" * 78); log(f"  {t}"); log("=" * 78)

def subsection(t):
    log(); log(f"-- {t} " + "-" * max(1, 72 - len(t)))

def issue(msg, sev="error", file=None, line=None, category="general"):
    entry = {"msg": msg, "severity": sev, "category": category}
    if file: entry["file"] = file
    if line: entry["line"] = line
    all_issues.append(entry)
    icon = {"error": "[X]", "warning": "[!]", "info": "[i]"}.get(sev, "[.]")
    loc = f" [{file}:{line}]" if file else ""
    log(f"  {icon} {msg}{loc}")

def fix(msg):
    all_fixes.append(msg)
    log(f"  [FIX] {msg}")

def predict(msg):
    predictions.append(msg)
    log(f"  [PREDICT] {msg}")

def find_files(root, ext):
    out = []
    for r, _, fs in os.walk(root):
        if ".git" in r.split(os.sep): continue
        for f in fs:
            if f.endswith(ext): out.append(os.path.join(r, f))
    return out

def read(path):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    except: return ""

def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

# ============================================================
# WHITELIST
# ============================================================

ANDROIDX_PREFIXES = (
    "androidx.", "com.google.android.", "com.android.", "com.google.firebase.",
    "android.support.", "kotlin.", "org.jetbrains.", "java.", "javax.",
    "okhttp3.", "com.squareup.", "org.json.", "com.google.gson.",
    "com.google.api.", "com.google.auth.", "com.google.http.",
)

ANDROID_BUILTIN_LAYOUTS = {
    "simple_spinner_item", "simple_spinner_dropdown_item",
    "simple_list_item_1", "simple_list_item_2",
    "simple_list_item_single_choice", "simple_list_item_multiple_choice",
    "simple_list_item_checked", "simple_expandable_list_item_1",
    "simple_expandable_list_item_2", "simple_selectable_list_item",
    "simple_dropdown_item_1line", "simple_list_item_activated_1",
    "simple_list_item_activated_2", "activity_list_item", "list_content",
    "browser_link_context_header", "two_line_list_item",
    "select_dialog_item", "select_dialog_singlechoice", "select_dialog_multichoice",
}

ANDROID_BUILTIN_DRAWABLES = {
    "ic_menu_close_clear_cancel", "ic_menu_edit", "ic_menu_save",
    "ic_menu_delete", "ic_menu_add", "ic_menu_info_details",
    "ic_menu_refresh", "ic_menu_share", "ic_menu_view",
    "ic_dialog_alert", "ic_dialog_info",
    "presence_online", "presence_offline", "presence_away",
    "presence_invisible", "presence_busy",
    "btn_default", "btn_radio", "btn_star_big_off",
    "ic_media_play", "ic_media_pause", "ic_media_next", "ic_media_previous",
    "ic_input_add", "ic_input_delete", "ic_input_get",
    "ic_partial_secure", "ic_secure", "ic_lock_idle_lock",
    "edit_text", "list_selector_background", "progress_horizontal",
}

def is_library_class(name):
    return any(name.startswith(p) for p in ANDROIDX_PREFIXES)

# ============================================================
# 0. PROJECT STRUCTURE
# ============================================================

log("=" * 78)
log("  ULTIMATE ANALYZER v3")
log("  Created by KARYADI, Coding by KARYADI")
log("=" * 78)

section("0. PROJECT STRUCTURE")

android_dir = None
for r, dirs, _ in os.walk(WORKDIR):
    if "android" in dirs:
        android_dir = os.path.join(r, "android"); break
if not android_dir:
    log("Folder 'android/' tidak ditemukan!"); sys.exit(1)

APP_DIR = os.path.join(android_dir, "app")
SRC_DIR = os.path.join(APP_DIR, "src", "main")
RES_DIR = os.path.join(SRC_DIR, "res")
KT_DIR  = None
for r, _, fs in os.walk(os.path.join(SRC_DIR, "java")):
    if any(f.endswith(".kt") for f in fs):
        KT_DIR = r; break

log(f"  Android dir : {os.path.relpath(android_dir, WORKDIR)}")
log(f"  Source dir  : {os.path.relpath(SRC_DIR, WORKDIR)}")
log(f"  Kotlin dir  : {os.path.relpath(KT_DIR, WORKDIR) if KT_DIR else 'N/A'}")
log(f"  Res dir     : {os.path.relpath(RES_DIR, WORKDIR)}")
log(f"  Auto-fix    : {AUTO_FIX}")

# ============================================================
# 1. INVENTARIS
# ============================================================

section("1. INVENTARIS FILE")

kt_files     = find_files(KT_DIR, ".kt") if KT_DIR else []
xml_files    = find_files(RES_DIR, ".xml")
gradle_files = find_files(android_dir, ".gradle")
manifest     = os.path.join(SRC_DIR, "AndroidManifest.xml")

log(f"  Kotlin      : {len(kt_files)}")
log(f"  XML (res/)  : {len(xml_files)}")
log(f"  Gradle      : {len(gradle_files)}")
log(f"  Manifest    : {'OK' if os.path.exists(manifest) else 'MISSING'}")

# ============================================================
# 2. GRADLE (Deep)
# ============================================================

section("2. GRADLE ANALYSIS (DEEP)")

app_gradle = None
for candidate in ["build.gradle", "build.gradle.kts"]:
    p = os.path.join(APP_DIR, candidate)
    if os.path.exists(p): app_gradle = p; break

settings_gradle = os.path.join(android_dir, "settings.gradle")

if app_gradle:
    content = read(app_gradle)
    subsection("app/build.gradle")

    open_b, close_b = content.count("{"), content.count("}")
    if open_b != close_b:
        issue(f"app/build.gradle: curly brace tidak balance ({open_b} vs {close_b})",
              file=os.path.relpath(app_gradle, WORKDIR), category="gradle")
    else:
        log("  OK Curly brace balance")

    open_p, close_p = content.count("("), content.count(")")
    if open_p != close_p:
        issue(f"app/build.gradle: parenthesis tidak balance ({open_p} vs {close_p})",
              file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

    for required in ["plugins", "android", "dependencies"]:
        if f"{required} " not in content and f"{required}{{" not in content:
            issue(f"app/build.gradle: tidak ada blok '{required}'",
                  file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

    for key in ["namespace", "compileSdk", "minSdk", "targetSdk"]:
        if key not in content:
            issue(f"app/build.gradle: tidak ada '{key}'",
                  file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

    DEPRECATED = ["nl.bravobit:android-ffmpeg", "com.arthenica:ffmpeg-kit",
                  "com.android.support", "androidx.legacy"]
    for lib in DEPRECATED:
        if lib in content:
            issue(f"app/build.gradle: deprecated '{lib}'",
                  file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

    deps = re.findall(r'implementation\s+[\'"]([^\'"]+)[\'"]', content)
    log(f"  Dependencies: {len(deps)}")
    for d in deps: log(f"    - {d}")

    dep_names = [":".join(d.split(":")[:2]) for d in deps if d.count(":") >= 1]
    dups = [d for d, c in Counter(dep_names).items() if c > 1]
    if dups:
        issue(f"app/build.gradle: dependency duplikat: {dups}",
              file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

wrapper = os.path.join(android_dir, "gradle/wrapper/gradle-wrapper.properties")
if os.path.exists(wrapper):
    m = re.search(r'distributionUrl=.*gradle-([\d.]+)-', read(wrapper))
    if m:
        log(f"  Gradle version: {m.group(1)}")

# ============================================================
# 3. ANDROIDMANIFEST (Deep)
# ============================================================

section("3. ANDROIDMANIFEST (DEEP)")

manifest_classes = []
manifest_perms = []
if os.path.exists(manifest):
    content = read(manifest)

    for m in re.finditer(r'<activity\s+android:name="([^"]+)"', content):
        manifest_classes.append(("activity", m.group(1)))
    for m in re.finditer(r'<service\s+android:name="([^"]+)"', content):
        manifest_classes.append(("service", m.group(1)))
    for m in re.finditer(r'<receiver\s+android:name="([^"]+)"', content):
        manifest_classes.append(("receiver", m.group(1)))
    for m in re.finditer(r'<provider\s+android:name="([^"]+)"', content):
        manifest_classes.append(("provider", m.group(1)))

    manifest_perms = re.findall(r'<uses-permission\s+android:name="([^"]+)"', content)

    subsection("Components")
    for kind, name in manifest_classes:
        log(f"    [{kind:9s}] {name}")

    subsection(f"Permissions ({len(manifest_perms)})")
    DANGEROUS = {
        "android.permission.READ_SMS", "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS", "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO", "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS", "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION", "android.permission.READ_CALL_LOG",
        "android.permission.CALL_PHONE", "android.permission.READ_PHONE_STATE",
        "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.QUERY_ALL_PACKAGES", "android.permission.ACCESS_BACKGROUND_LOCATION",
    }
    for p in manifest_perms:
        flag = "DANGEROUS" if p in DANGEROUS else "normal"
        log(f"    [{flag}] {p}")

    app_name_match = re.search(r'<application[^>]*android:name="([^"]+)"', content)
    if app_name_match:
        log(f"  Application name: {app_name_match.group(1)}")
    else:
        issue("Manifest: <application> tidak punya android:name",
              file="AndroidManifest.xml", category="manifest")

# ============================================================
# 4. KOTLIN - DEEP
# ============================================================

section("4. KOTLIN - DEEP ANALYSIS")

declared_classes = {}
called_ids_by_file = {}
called_layouts_by_file = {}
called_item_layouts = set()
called_activities = set()
imports_by_file = {}
kotlin_loc = 0

for kt in kt_files:
    rel = os.path.relpath(kt, WORKDIR)
    src = read(kt)
    kotlin_loc += len(src.splitlines())

    for m in re.finditer(r'(?:class|object|interface)\s+(\w+)', src):
        declared_classes[m.group(1)] = rel

    ids = set(re.findall(r'R\.id\.(\w+)', src))
    if ids: called_ids_by_file[rel] = ids

    for m in re.finditer(r'setContentView\(R\.layout\.(\w+)\)', src):
        called_layouts_by_file[rel] = m.group(1)

    for m in re.finditer(r'inflate\(R\.layout\.(\w+)', src):
        called_item_layouts.add(m.group(1))

    for m in re.finditer(r'Intent\([^,]+,\s*(\w+)::class', src):
        called_activities.add(m.group(1))

    imports_by_file[rel] = set(re.findall(r'^import\s+([\w.]+)', src, re.MULTILINE))

    for m in re.finditer(r'(TODO|FIXME|XXX|HACK)\s*[:(]', src):
        line = src[:m.start()].count("\n") + 1
        issue(f"{rel}: {m.group(1)} baris {line}",
              sev="info", file=rel, category="todo")

log(f"  Total Kotlin LOC  : {kotlin_loc}")
log(f"  Classes declared  : {len(declared_classes)}")
log(f"  Files w/ findViewById: {len(called_ids_by_file)}")
log(f"  Files w/ setContentView: {len(called_layouts_by_file)}")
log(f"  Item layouts: {sorted(called_item_layouts)}")
log(f"  Intent targets: {len(called_activities)}")

subsection("Intent reference check")
for target in called_activities:
    if target not in declared_classes:
        issue(f"Intent ke '{target}' - class tidak ada",
              file="Kotlin", category="kotlin")
    else:
        log(f"  OK {target}")

# ============================================================
# 5. RESOURCES
# ============================================================

section("5. RESOURCES (DEEP)")

defined_colors = set()
defined_strings = set()
defined_dimens = set()
defined_styles = set()
defined_drawables = set()
defined_mipmaps = set()
defined_layouts = set()
defined_xmls = set()

values_dir = os.path.join(RES_DIR, "values")
if os.path.exists(values_dir):
    for fn in os.listdir(values_dir):
        if not fn.endswith(".xml"): continue
        content = read(os.path.join(values_dir, fn))
        for m in re.finditer(r'<color name="(\w+)"', content): defined_colors.add(m.group(1))
        for m in re.finditer(r'<string name="(\w+)"', content): defined_strings.add(m.group(1))
        for m in re.finditer(r'<dimen name="(\w+)"', content): defined_dimens.add(m.group(1))
        for m in re.finditer(r'<style name="([\w.]+)"', content): defined_styles.add(m.group(1))

for folder in os.listdir(RES_DIR):
    full = os.path.join(RES_DIR, folder)
    if not os.path.isdir(full): continue
    if folder.startswith("drawable"):
        for fn in os.listdir(full): defined_drawables.add(fn.rsplit(".", 1)[0])
    elif folder.startswith("mipmap"):
        for fn in os.listdir(full): defined_mipmaps.add(fn.rsplit(".", 1)[0])
    elif folder == "layout":
        for fn in os.listdir(full): defined_layouts.add(fn.rsplit(".", 1)[0])
    elif folder == "xml":
        for fn in os.listdir(full): defined_xmls.add(fn.rsplit(".", 1)[0])

# Tambahkan Android built-in
defined_layouts |= ANDROID_BUILTIN_LAYOUTS
defined_drawables |= ANDROID_BUILTIN_DRAWABLES

log(f"  Colors     : {len(defined_colors)}")
log(f"  Strings    : {len(defined_strings)}")
log(f"  Dimens     : {len(defined_dimens)}")
log(f"  Styles     : {len(defined_styles)}")
log(f"  Drawables  : {len(defined_drawables)} (incl. built-in)")
log(f"  Layouts    : {len(defined_layouts)} (incl. built-in)")

# ============================================================
# 6. CROSS-CHECK RESOURCES
# ============================================================

section("6. CROSS-CHECK RESOURCES")

unresolved = defaultdict(list)

for root, _, fs in os.walk(RES_DIR):
    for fn in fs:
        if not fn.endswith(".xml"): continue
        full = os.path.join(root, fn)
        content = read(full)
        rel = os.path.relpath(full, WORKDIR)

        for kind, definer in [
            ("color", defined_colors), ("string", defined_strings),
            ("dimen", defined_dimens), ("style", defined_styles),
            ("drawable", defined_drawables), ("mipmap", defined_mipmaps),
            ("layout", defined_layouts), ("xml", defined_xmls),
        ]:
            for m in re.finditer(rf'@{kind}/([\w.]+)', content):
                if m.group(1) not in definer:
                    unresolved[kind].append((rel, m.group(1)))

for kt in kt_files:
    src = read(kt)
    rel = os.path.relpath(kt, WORKDIR)
    for kind, definer in [
        ("color", defined_colors), ("string", defined_strings),
        ("dimen", defined_dimens), ("style", defined_styles),
        ("drawable", defined_drawables), ("layout", defined_layouts),
    ]:
        for m in re.finditer(rf'R\.{kind}\.([\w.]+)', src):
            if m.group(1) not in definer:
                unresolved[kind].append((rel, m.group(1)))

for kind, entries in unresolved.items():
    unique = list(set(entries))
    if unique:
        log(f"")
        log(f"  [!] @{kind} unresolved ({len(unique)}):")
        for rel, name in unique[:30]:
            log(f"     {rel} -> @{kind}/{name}")
            issue(f"@{kind}/{name} tidak ada (dari {rel})",
                  file=rel, category="resource")
    else:
        log(f"  OK @{kind}")

# ============================================================
# 7. KOTLIN + LAYOUT (SMART)
# ============================================================

section("7. KOTLIN + LAYOUT MATCHING (SMART)")

item_layout_ids = set()
for item_layout in called_item_layouts:
    item_path = os.path.join(RES_DIR, "layout", f"{item_layout}.xml")
    if os.path.exists(item_path):
        item_layout_ids |= set(re.findall(r'android:id="@\+id/(\w+)"',
                                          read(item_path)))

log(f"  Item layout IDs: {sorted(item_layout_ids)}")

for kt_rel, layout_name in called_layouts_by_file.items():
    if layout_name not in defined_layouts:
        issue(f"{kt_rel}: R.layout.{layout_name} tidak ada",
              file=kt_rel, category="layout")
        continue

    layout_content = read(os.path.join(RES_DIR, "layout", f"{layout_name}.xml"))
    layout_ids = set(re.findall(r'android:id="@\+id/(\w+)"', layout_content))

    src = read(os.path.join(WORKDIR, kt_rel))
    if "RecyclerView" in src or "Adapter" in src:
        available_ids = layout_ids | item_layout_ids
    else:
        available_ids = layout_ids

    called = called_ids_by_file.get(kt_rel, set())
    missing = called - available_ids

    if missing:
        log(f"  [!] {kt_rel} -> {layout_name}.xml - ID hilang: {sorted(missing)}")
        for mid in sorted(missing):
            issue(f"{kt_rel}: R.id.{mid} tidak ada",
                  file=kt_rel, category="layout")
    else:
        log(f"  OK {kt_rel} -> {layout_name}.xml")

# ============================================================
# 8. MANIFEST + KOTLIN
# ============================================================

section("8. MANIFEST + KOTLIN")

for kind, name in manifest_classes:
    if is_library_class(name):
        log(f"  [i] [{kind}] {name.split('.')[-1]} - library class (skip)")
        continue
    short = name.split(".")[-1]
    if short not in declared_classes:
        issue(f"Manifest [{kind}] '{name}' tidak ada di Kotlin",
              file="AndroidManifest.xml", category="manifest")
    else:
        log(f"  OK [{kind}] {short}")

# ============================================================
# 9. DUPLICATE DETECTION
# ============================================================

section("9. DUPLICATE DETECTION")

class_counts = Counter(declared_classes.keys())
dups = [c for c, n in class_counts.items() if n > 1]
if dups:
    for d in dups:
        issue(f"Duplicate class: {d} di {declared_classes[d]}",
              file=declared_classes[d], category="duplicate")
else:
    log("  OK Tidak ada duplicate class")

file_hashes = defaultdict(list)
for kt in kt_files:
    content = read(kt)
    h = hashlib.md5(content.encode()).hexdigest()
    file_hashes[h].append(os.path.relpath(kt, WORKDIR))

dup_files = [files for files in file_hashes.values() if len(files) > 1]
if dup_files:
    for files in dup_files:
        issue(f"Duplicate file content: {files}", category="duplicate")

# ============================================================
# 10. DEAD CODE
# ============================================================

section("10. DEAD CODE DETECTION")

all_src = ""
for kt in kt_files:
    all_src += read(kt)

dead_classes = []
for cls, loc in declared_classes.items():
    if any(cls == n.split(".")[-1] for _, n in manifest_classes): continue
    if cls in called_activities: continue
    count = all_src.count(cls)
    if count <= 1:
        dead_classes.append((cls, loc))

if dead_classes:
    log(f"  [!] {len(dead_classes)} kandidat dead code:")
    for cls, loc in dead_classes[:20]:
        log(f"     {cls} ({loc})")
else:
    log("  OK Tidak ada dead code")

# ============================================================
# 11. SECURITY
# ============================================================

section("11. SECURITY ANALYSIS")

security_issues = []

PATTERNS = {
    "GitHub Token": r'ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{82}',
    "Google API Key": r'AIza[A-Za-z0-9_\-]{35}',
    "AWS Access Key": r'AKIA[A-Z0-9]{16}',
    "Private Key": r'-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----',
}

for kt in kt_files:
    rel = os.path.relpath(kt, WORKDIR)
    src = read(kt)
    for name, pat in PATTERNS.items():
        for m in re.finditer(pat, src):
            line = src[:m.start()].count("\n") + 1
            issue(f"{name} hardcoded di {rel}:{line}",
                  sev="error", file=rel, category="security")
            security_issues.append(f"{name} @ {rel}:{line}")

if os.path.exists(manifest):
    m_src = read(manifest)
    if 'android:debuggable="true"' in m_src:
        issue("Manifest: debuggable=true", file="AndroidManifest.xml", category="security")
    if 'android:allowBackup="true"' in m_src:
        issue("Manifest: allowBackup=true", sev="warning",
              file="AndroidManifest.xml", category="security")

if not security_issues:
    log("  OK Tidak ada isu keamanan")

with open(SECURITY_TXT, "w", encoding="utf-8") as f:
    f.write("\n".join(security_issues))

# ============================================================
# 12. PERFORMANCE
# ============================================================

section("12. PERFORMANCE ANALYSIS")

perf_issues = []

for kt in kt_files:
    rel = os.path.relpath(kt, WORKDIR)
    src = read(kt)

    if re.search(r'\.execute\(\)', src) and 'Thread' not in src and 'Coroutine' not in src and 'lifecycleScope' not in src:
        issue(f"{rel}: network call di main thread?",
              sev="warning", file=rel, category="performance")
        perf_issues.append(f"{rel}: network on main thread")

    if re.search(r'for\s*\([^)]+\)\s*\{[^}]*findViewById', src):
        issue(f"{rel}: findViewById di dalam loop",
              sev="warning", file=rel, category="performance")

if not perf_issues:
    log("  OK Tidak ada masalah performa")

with open(PERFORMANCE_TXT, "w", encoding="utf-8") as f:
    f.write("\n".join(perf_issues))

# ============================================================
# 13. DEPENDENCY GRAPH
# ============================================================

section("13. DEPENDENCY GRAPH")

graph = {}
for kt in kt_files:
    rel = os.path.relpath(kt, WORKDIR)
    imports = imports_by_file.get(rel, set())
    internal = [i for i in imports if i.startswith("com.universal.videoeditor")]
    graph[rel] = internal

log(f"  Files dalam graph: {len(graph)}")

with open(DEPENDENCY_TXT, "w", encoding="utf-8") as f:
    for fname, deps in sorted(graph.items()):
        f.write(f"{fname}:\n")
        for d in deps:
            f.write(f"  -> {d}\n")
        f.write("\n")

# ============================================================
# 14. BUILD PREDICTIONS
# ============================================================

section("14. BUILD PREDICTIONS")

if app_gradle:
    content = read(app_gradle)
    m = re.search(r'jvmTarget\s*=?\s*[\'"]?(\d+)', content)
    if m and m.group(1) != "17":
        predict(f"jvmTarget={m.group(1)} - pastikan Kotlin compiler support")

if os.path.exists(manifest):
    m_src = read(manifest)
    if ".YadApp" not in m_src and "YadApp" not in m_src:
        predict("Manifest tidak refer ke .YadApp - SecureConfig tidak akan init")
    else:
        log("  OK Manifest refer ke .YadApp")

if KT_DIR:
    sc_path = os.path.join(KT_DIR, "SecureConfig.kt")
    if os.path.exists(sc_path):
        sc = read(sc_path)
        if "lateinit" in sc:
            predict("SecureConfig pakai lateinit - bisa crash kalau belum init")
        else:
            log("  OK SecureConfig pakai nullable")

for kt in kt_files:
    src = read(kt)
    if "RecyclerView" in src and "layoutManager" not in src:
        rel = os.path.relpath(kt, WORKDIR)
        predict(f"{rel}: RecyclerView tanpa layoutManager")

with open(PREDICTION_TXT, "w", encoding="utf-8") as f:
    for p in predictions:
        f.write(f"{p}\n")

# ============================================================
# 15. AUTO-FIX
# ============================================================

section("15. AUTO-FIX")

if not AUTO_FIX:
    log("  Auto-fix disabled")
else:
    # Fix colors
    missing_colors = set(n for _, n in unresolved.get("color", []))
    if missing_colors:
        log(f"")
        log(f"  [FIX] {len(missing_colors)} warna hilang")
        DEFAULT_COLOR_VALUES = {
            "primary": "#1A237E", "primary_dark": "#0D47A1", "accent": "#FF9800",
            "accent_dark": "#F57C00", "secondary": "#FF9800",
            "brand_primary": "#1A237E", "brand_secondary": "#FF9800",
            "bg_light": "#F5F5F5", "bg_dark": "#212121", "bg_white": "#FFFFFF",
            "bg_card": "#FFFFFF", "background": "#FFFFFF",
            "danger": "#F44336", "error": "#F44336", "success": "#4CAF50",
            "warning": "#FF9800", "info": "#2196F3",
            "text_primary": "#212121", "text_secondary": "#757575",
            "text_hint": "#9E9E9E", "text_white": "#FFFFFF", "text_black": "#000000",
            "white": "#FFFFFF", "black": "#000000", "gray": "#9E9E9E",
            "gray_light": "#E0E0E0", "gray_dark": "#424242",
            "red": "#F44336", "green": "#4CAF50", "blue": "#2196F3",
            "yellow": "#FFEB3B", "orange": "#FF9800", "purple": "#9C27B0",
            "pink": "#E91E63", "teal": "#009688", "cyan": "#00BCD4",
            "transparent": "#00000000", "semi_transparent": "#80000000",
        }
        colors_path = os.path.join(RES_DIR, "values", "colors.xml")
        existing = read(colors_path) if os.path.exists(colors_path) \
                   else '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>'
        existing_names = set(re.findall(r'<color name="(\w+)"', existing))
        to_add = []
        for c in sorted(missing_colors):
            if c in existing_names: continue
            value = DEFAULT_COLOR_VALUES.get(c, "#607D8B")
            to_add.append(f'    <color name="{c}">{value}</color>')
        if to_add:
            write(colors_path, existing.replace("</resources>",
                  "\n".join(to_add) + "\n</resources>"))
            fix(f"Tambah {len(to_add)} warna")

    # Fix dimens
    missing_dimens = set(n for _, n in unresolved.get("dimen", []))
    if missing_dimens:
        log(f"")
        log(f"  [FIX] {len(missing_dimens)} dimens")
        dimens_path = os.path.join(RES_DIR, "values", "dimens.xml")
        existing = read(dimens_path) if os.path.exists(dimens_path) \
                   else '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>'
        existing_names = set(re.findall(r'<dimen name="(\w+)"', existing))
        to_add = [f'    <dimen name="{d}">16dp</dimen>'
                  for d in sorted(missing_dimens) if d not in existing_names]
        if to_add:
            write(dimens_path, existing.replace("</resources>",
                  "\n".join(to_add) + "\n</resources>"))
            fix(f"Tambah {len(to_add)} dimens")

    # Fix strings
    missing_strings = set(n for _, n in unresolved.get("string", []))
    if missing_strings:
        log(f"")
        log(f"  [FIX] {len(missing_strings)} strings")
        strings_path = os.path.join(RES_DIR, "values", "strings.xml")
        existing = read(strings_path) if os.path.exists(strings_path) \
                   else '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>'
        existing_names = set(re.findall(r'<string name="(\w+)"', existing))
        to_add = []
        for s in sorted(missing_strings):
            if s in existing_names: continue
            to_add.append(f'    <string name="{s}">{s.replace("_", " ").title()}</string>')
        if to_add:
            write(strings_path, existing.replace("</resources>",
                  "\n".join(to_add) + "\n</resources>"))
            fix(f"Tambah {len(to_add)} strings")

    # Fix drawables
    missing_drawables = set(n for _, n in unresolved.get("drawable", []))
    if missing_drawables:
        log(f"")
        log(f"  [FIX] {len(missing_drawables)} drawable")
        drawable_dir = os.path.join(RES_DIR, "drawable")
        os.makedirs(drawable_dir, exist_ok=True)
        for d in missing_drawables:
            path = os.path.join(drawable_dir, f"{d}.xml")
            if os.path.exists(path): continue
            content = ('<?xml version="1.0" encoding="utf-8"?>\n'
                       '<shape xmlns:android="http://schemas.android.com/apk/res/android"\n'
                       '    android:shape="rectangle">\n'
                       '    <solid android:color="#E0E0E0"/>\n'
                       '    <corners android:radius="8dp"/>\n'
                       '</shape>')
            write(path, content)
            fix(f"Buat drawable {d}.xml")

    # Fix SecureConfig lateinit
    if KT_DIR:
        sc_path = os.path.join(KT_DIR, "SecureConfig.kt")
        if os.path.exists(sc_path):
            sc = read(sc_path)
            if "lateinit" in sc:
                log(f"")
                log(f"  [FIX] SecureConfig lateinit -> nullable")
                NEW_SC = "package com.universal.videoeditor\n\n" \
                    "import android.content.Context\n" \
                    "import android.content.SharedPreferences\n\n" \
                    "object SecureConfig {\n" \
                    "    private const val PREF = \"yadapp_secure\"\n" \
                    "    @Volatile private var prefs: SharedPreferences? = null\n\n" \
                    "    fun init(context: Context) {\n" \
                    "        if (prefs == null) {\n" \
                    "            synchronized(this) {\n" \
                    "                if (prefs == null) {\n" \
                    "                    prefs = context.applicationContext\n" \
                    "                        .getSharedPreferences(PREF, Context.MODE_PRIVATE)\n" \
                    "                }\n" \
                    "            }\n" \
                    "        }\n" \
                    "    }\n\n" \
                    "    private fun p(): SharedPreferences? = prefs\n\n" \
                    "    fun getGithubToken(): String = p()?.getString(\"gh_token\", \"\") ?: \"\"\n" \
                    "    fun setGithubToken(t: String) { p()?.edit()?.putString(\"gh_token\", t)?.apply() }\n" \
                    "    fun clearGithubToken() { p()?.edit()?.remove(\"gh_token\")?.apply() }\n\n" \
                    "    fun getAdminEmail(): String =\n" \
                    "        p()?.getString(\"admin_email\", \"admin@local\") ?: \"admin@local\"\n" \
                    "    fun setAdminEmail(e: String) { p()?.edit()?.putString(\"admin_email\", e)?.apply() }\n" \
                    "    fun clearAdmin() { p()?.edit()?.remove(\"admin_email\")?.apply() }\n\n" \
                    "    fun getString(key: String, def: String = \"\"): String =\n" \
                    "        p()?.getString(key, def) ?: def\n" \
                    "    fun setString(key: String, v: String) { p()?.edit()?.putString(key, v)?.apply() }\n" \
                    "    fun getBool(key: String, def: Boolean = false): Boolean =\n" \
                    "        p()?.getBoolean(key, def) ?: def\n" \
                    "    fun setBool(key: String, v: Boolean) { p()?.edit()?.putBoolean(key, v)?.apply() }\n" \
                    "    fun getInt(key: String, def: Int = 0): Int = p()?.getInt(key, def) ?: def\n" \
                    "    fun setInt(key: String, v: Int) { p()?.edit()?.putInt(key, v)?.apply() }\n" \
                    "}\n"
                write(sc_path, NEW_SC)
                fix("SecureConfig lateinit -> nullable")

# ============================================================
# 16. RINGKASAN
# ============================================================

section("16. RINGKASAN")

errors = [i for i in all_issues if i["severity"] == "error"]
warnings = [i for i in all_issues if i["severity"] == "warning"]
infos = [i for i in all_issues if i["severity"] == "info"]

log(f"  Errors   : {len(errors)}")
log(f"  Warnings : {len(warnings)}")
log(f"  Info     : {len(infos)}")
log(f"  Fixes    : {len(all_fixes)}")
log(f"  Predictions: {len(predictions)}")

log()
log("  Per kategori:")
categories = Counter(i.get("category", "general") for i in all_issues)
for cat, cnt in categories.most_common():
    log(f"    {cat}: {cnt}")

log()
log("=" * 78)
log("  Analisis v3 selesai")
log("  Created by KARYADI, Coding by KARYADI")
log("=" * 78)

# ============================================================
# WRITE OUTPUTS
# ============================================================

with open(REPORT_TXT, "w", encoding="utf-8") as f:
    f.write("\n".join(report))

with open(REPORT_JSON, "w", encoding="utf-8") as f:
    json.dump({
        "issues": all_issues,
        "fixes": all_fixes,
        "predictions": predictions,
        "summary": {"errors": len(errors), "warnings": len(warnings),
                    "infos": len(infos), "fixes": len(all_fixes)},
        "categories": dict(categories),
        "timestamp": datetime.now().isoformat(),
    }, f, indent=2, ensure_ascii=False)

with open(ISSUES_TXT, "w", encoding="utf-8") as f:
    for i in all_issues:
        loc = f" [{i.get('file','?')}]" if i.get("file") else ""
        f.write(f"[{i['severity'].upper()}]{loc} {i['msg']}\n")

with open(FIXES_TXT, "w", encoding="utf-8") as f:
    for fx in all_fixes: f.write(f"OK {fx}\n")

print()
print("=" * 60)
print("OUTPUT FILES:")
for f in [REPORT_TXT, REPORT_JSON, ISSUES_TXT, FIXES_TXT,
          PREDICTION_TXT, DEPENDENCY_TXT, SECURITY_TXT, PERFORMANCE_TXT]:
    if os.path.exists(f):
        print(f"   OK {f} ({os.path.getsize(f):,} bytes)")
print("=" * 60)

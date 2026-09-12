#!/usr/bin/env python3
# ============================================================
# ULTIMATE ANALYZER v2 — Deep analysis + smart auto-fix
# Fix false positives, pahami RecyclerView adapter, dll.
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

REPORT_TXT      = P("report.txt")
REPORT_JSON     = P("report.json")
ISSUES_TXT      = P("issues.txt")
FIXES_TXT       = P("fixes_applied.txt")
STRUCTURE_TXT   = P("project_structure.txt")
KOTLIN_TXT      = P("kotlin_analysis.txt")
LAYOUT_TXT      = P("layout_analysis.txt")
RESOURCES_TXT   = P("resources_analysis.txt")
MANIFEST_TXT    = P("manifest_analysis.txt")
GRADLE_TXT      = P("gradle_analysis.txt")
ENTRYPOINTS_TXT = P("entrypoints.txt")

report = []
all_issues = []
all_fixes = []

def log(m=""):
    print(m); report.append(str(m))

def section(t):
    log(); log("=" * 78); log(f"  {t}"); log("=" * 78)

def subsection(t):
    log(); log(f"── {t} " + "─" * max(1, 72 - len(t)))

def issue(msg, sev="error", file=None, line=None, category="general"):
    entry = {"msg": msg, "severity": sev, "category": category}
    if file: entry["file"] = file
    if line: entry["line"] = line
    all_issues.append(entry)
    icon = {"error": "🔴", "warning": "🟡", "info": "🔵"}.get(sev, "⚪")
    loc = f" [{file}:{line}]" if file else ""
    log(f"  {icon} {msg}{loc}")

def fix(msg):
    all_fixes.append(msg)
    log(f"  🔧 {msg}")

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
# ANDROIDX/GOOGLE LIBRARY WHITELIST (untuk hindari false positive)
# ============================================================

ANDROIDX_PREFIXES = (
    "androidx.", "com.google.android.", "com.android.", "com.google.firebase.",
    "android.support.", "kotlin.", "org.jetbrains.", "java.", "javax.",
    "okhttp3.", "com.squareup.", "org.json.", "com.google.gson.",
)

def is_library_class(name):
    return any(name.startswith(p) for p in ANDROIDX_PREFIXES)

# ============================================================
# 0. PROJECT STRUCTURE
# ============================================================

log("=" * 78)
log("  ULTIMATE ANALYZER v2 — youtube-auto-pipeline")
log("  Created by KARYADI, Coding by KARYADI")
log("=" * 78)

section("0. PROJECT STRUCTURE")

android_dir = None
for r, dirs, _ in os.walk(WORKDIR):
    if "android" in dirs:
        android_dir = os.path.join(r, "android"); break

if not android_dir:
    log("❌ Folder 'android/' tidak ditemukan!"); sys.exit(1)

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
log(f"  Manifest    : {'✅' if os.path.exists(manifest) else '❌'}")

# ============================================================
# 2. GRADLE
# ============================================================

section("2. GRADLE ANALYSIS")

app_gradle = None
for candidate in ["build.gradle", "build.gradle.kts"]:
    p = os.path.join(APP_DIR, candidate)
    if os.path.exists(p): app_gradle = p; break

root_gradle = os.path.join(android_dir, "build.gradle")
settings_gradle = os.path.join(android_dir, "settings.gradle")

if app_gradle:
    content = read(app_gradle)
    subsection("app/build.gradle")

    open_b, close_b = content.count("{"), content.count("}")
    if open_b != close_b:
        issue(f"app/build.gradle: curly brace tidak balance ({open_b} vs {close_b})",
              file=os.path.relpath(app_gradle, WORKDIR), category="gradle")
    else:
        log("  ✅ Curly brace balance")

    if "plugins {" not in content and "plugins{" not in content:
        issue("app/build.gradle: tidak ada 'plugins { }'",
              file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

    if "namespace" not in content:
        issue("app/build.gradle: tidak ada 'namespace'",
              file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

    if "compileSdk" not in content:
        issue("app/build.gradle: tidak ada 'compileSdk'",
              file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

    if "minSdk" not in content:
        issue("app/build.gradle: tidak ada 'minSdk'",
              file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

    # Deprecated library check
    DEPRECATED_LIBS = [
        "nl.bravobit:android-ffmpeg",
        "com.arthenica:ffmpeg-kit",
        "com.android.support",
        "androidx.legacy",
    ]
    for lib in DEPRECATED_LIBS:
        if lib in content:
            issue(f"app/build.gradle: pakai library deprecated '{lib}'",
                  file=os.path.relpath(app_gradle, WORKDIR), category="gradle")

    deps = re.findall(r'implementation\s+[\'"]([^\'"]+)[\'"]', content)
    log(f"  Dependencies: {len(deps)}")
    for d in deps: log(f"    - {d}")

if os.path.exists(settings_gradle):
    content = read(settings_gradle)
    subsection("settings.gradle")
    if "FAIL_ON_PROJECT_REPOS" in content or "PREFER_SETTINGS" in content:
        log("  ✅ Repositories mode set")
    if "jitpack.io" not in content:
        issue("settings.gradle: tidak ada JitPack",
              sev="info", file=os.path.relpath(settings_gradle, WORKDIR),
              category="gradle")

# ============================================================
# 3. ANDROIDMANIFEST
# ============================================================

section("3. ANDROIDMANIFEST")

manifest_classes = []
manifest_refs = []  # reference ke class Kotlin
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

    subsection("Components")
    for kind, name in manifest_classes:
        log(f"    [{kind:9s}] {name}")

    perms = re.findall(r'<uses-permission\s+android:name="([^"]+)"', content)
    subsection(f"Permissions ({len(perms)})")
    for p in perms: log(f"    - {p}")

    subsection("Resource references")
    for m in re.finditer(r'@(\w+)/(\w+)', content):
        log(f"    @{m.group(1)}/{m.group(2)}")

# ============================================================
# 4. KOTLIN — DEEP
# ============================================================

section("4. KOTLIN — DEEP ANALYSIS")

declared_classes = {}
called_ids_by_file = {}
called_layouts_by_file = {}
called_item_layouts = set()   # ← untuk RecyclerView adapter
called_activities = set()
imports_by_file = {}

for kt in kt_files:
    rel = os.path.relpath(kt, WORKDIR)
    src = read(kt)

    # Classes
    for m in re.finditer(r'(?:class|object|interface)\s+(\w+)', src):
        declared_classes[m.group(1)] = rel

    # findViewById → semua R.id.*
    ids = set(re.findall(r'R\.id\.(\w+)', src))
    if ids: called_ids_by_file[rel] = ids

    # setContentView
    for m in re.finditer(r'setContentView\(R\.layout\.(\w+)\)', src):
        called_layouts_by_file[rel] = m.group(1)

    # Inflate (untuk RecyclerView adapter)
    for m in re.finditer(r'inflate\(R\.layout\.(\w+)', src):
        called_item_layouts.add(m.group(1))

    # Intent
    for m in re.finditer(r'Intent\([^,]+,\s*(\w+)::class', src):
        called_activities.add(m.group(1))

    # Imports (untuk cek missing dependency)
    imports = set(re.findall(r'^import\s+([\w.]+)', src, re.MULTILINE))
    imports_by_file[rel] = imports

log(f"  Classes declared   : {len(declared_classes)}")
log(f"  Files with findViewById: {len(called_ids_by_file)}")
log(f"  Files with setContentView: {len(called_layouts_by_file)}")
log(f"  Item layouts (adapter): {sorted(called_item_layouts)}")
log(f"  Intent targets     : {len(called_activities)}")

subsection("Intent reference check")
for target in called_activities:
    if target not in declared_classes:
        issue(f"Intent ke '{target}' — class tidak ada",
              file="Kotlin", category="kotlin")
    else:
        log(f"  ✅ {target}")

# ============================================================
# 5. RESOURCES
# ============================================================

section("5. RESOURCES")

defined_colors = set()
defined_strings = set()
defined_dimens = set()
defined_styles = set()
defined_drawables = set()
defined_mipmaps = set()
defined_layouts = set()
defined_xmls = set()
defined_arrays = set()
defined_bools = set()
defined_integers = set()

values_dir = os.path.join(RES_DIR, "values")
if os.path.exists(values_dir):
    for fn in os.listdir(values_dir):
        if not fn.endswith(".xml"): continue
        content = read(os.path.join(values_dir, fn))
        for m in re.finditer(r'<color name="(\w+)"', content): defined_colors.add(m.group(1))
        for m in re.finditer(r'<string name="(\w+)"', content): defined_strings.add(m.group(1))
        for m in re.finditer(r'<dimen name="(\w+)"', content): defined_dimens.add(m.group(1))
        for m in re.finditer(r'<style name="([\w.]+)"', content): defined_styles.add(m.group(1))
        for m in re.finditer(r'<array name="(\w+)"', content): defined_arrays.add(m.group(1))
        for m in re.finditer(r'<bool name="(\w+)"', content): defined_bools.add(m.group(1))
        for m in re.finditer(r'<integer name="(\w+)"', content): defined_integers.add(m.group(1))

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

log(f"  Colors     : {len(defined_colors)}")
log(f"  Strings    : {len(defined_strings)}")
log(f"  Dimens     : {len(defined_dimens)}")
log(f"  Styles     : {len(defined_styles)}")
log(f"  Drawables  : {len(defined_drawables)}")
log(f"  Mipmaps    : {len(defined_mipmaps)}")
log(f"  Layouts    : {len(defined_layouts)}")
log(f"  XMLs       : {len(defined_xmls)}")

# ============================================================
# 6. CROSS-CHECK RESOURCE REFERENCES
# ============================================================

section("6. CROSS-CHECK RESOURCES")

unresolved = defaultdict(list)

for root, _, fs in os.walk(RES_DIR):
    for fn in fs:
        if not fn.endswith(".xml"): continue
        full = os.path.join(root, fn)
        content = read(full)
        rel = os.path.relpath(full, WORKDIR)

        for m in re.finditer(r'@color/(\w+)', content):
            if m.group(1) not in defined_colors:
                unresolved["color"].append((rel, m.group(1)))
        for m in re.finditer(r'@string/(\w+)', content):
            if m.group(1) not in defined_strings:
                unresolved["string"].append((rel, m.group(1)))
        for m in re.finditer(r'@dimen/(\w+)', content):
            if m.group(1) not in defined_dimens:
                unresolved["dimen"].append((rel, m.group(1)))
        for m in re.finditer(r'@style/([\w.]+)', content):
            if m.group(1) not in defined_styles:
                unresolved["style"].append((rel, m.group(1)))
        for m in re.finditer(r'@drawable/(\w+)', content):
            if m.group(1) not in defined_drawables:
                unresolved["drawable"].append((rel, m.group(1)))
        for m in re.finditer(r'@mipmap/(\w+)', content):
            if m.group(1) not in defined_mipmaps:
                unresolved["mipmap"].append((rel, m.group(1)))
        for m in re.finditer(r'@layout/(\w+)', content):
            if m.group(1) not in defined_layouts:
                unresolved["layout"].append((rel, m.group(1)))
        for m in re.finditer(r'@xml/(\w+)', content):
            if m.group(1) not in defined_xmls:
                unresolved["xml"].append((rel, m.group(1)))

# Kotlin
for kt in kt_files:
    src = read(kt)
    rel = os.path.relpath(kt, WORKDIR)
    for m in re.finditer(r'R\.color\.(\w+)', src):
        if m.group(1) not in defined_colors:
            unresolved["color"].append((rel, m.group(1)))
    for m in re.finditer(r'R\.string\.(\w+)', src):
        if m.group(1) not in defined_strings:
            unresolved["string"].append((rel, m.group(1)))
    for m in re.finditer(r'R\.drawable\.(\w+)', src):
        if m.group(1) not in defined_drawables:
            unresolved["drawable"].append((rel, m.group(1)))
    for m in re.finditer(r'R\.layout\.(\w+)', src):
        if m.group(1) not in defined_layouts:
            unresolved["layout"].append((rel, m.group(1)))
    for m in re.finditer(r'R\.style\.([\w.]+)', src):
        if m.group(1) not in defined_styles:
            unresolved["style"].append((rel, m.group(1)))
    for m in re.finditer(r'R\.array\.(\w+)', src):
        if m.group(1) not in defined_arrays:
            unresolved["array"].append((rel, m.group(1)))

for kind, entries in unresolved.items():
    if entries:
        # Deduplicate
        unique = list(set(entries))
        log(f"\n  ❌ @{kind} unresolved ({len(unique)}):")
        for rel, name in unique[:30]:
            log(f"     {rel} → @{kind}/{name}")
            issue(f"@{kind}/{name} tidak ada (dari {rel})", file=rel, category="resource")
    else:
        log(f"  ✅ @{kind} — semua OK")

# ============================================================
# 7. KOTLIN ↔ LAYOUT MATCHING (SMART — pahami adapter)
# ============================================================

section("7. KOTLIN ↔ LAYOUT MATCHING (SMART)")

for kt_rel, layout_name in called_layouts_by_file.items():
    if layout_name not in defined_layouts:
        issue(f"{kt_rel}: R.layout.{layout_name} tidak ada",
              file=kt_rel, category="layout")
        continue

    layout_content = read(os.path.join(RES_DIR, "layout", f"{layout_name}.xml"))
    layout_ids = set(re.findall(r'android:id="@\+id/(\w+)"', layout_content))

    # SMART: Item layout IDs juga tersedia untuk file ini (via adapter)
    item_ids = set()
    for item_layout in called_item_layouts:
        item_path = os.path.join(RES_DIR, "layout", f"{item_layout}.xml")
        if os.path.exists(item_path):
            item_content = read(item_path)
            item_ids |= set(re.findall(r'android:id="@\+id/(\w+)"', item_content))

    available_ids = layout_ids | item_ids

    called = called_ids_by_file.get(kt_rel, set())
    missing = called - available_ids

    if missing:
        log(f"  ❌ {kt_rel} → {layout_name}.xml + item layouts")
        log(f"     ID hilang: {sorted(missing)}")
        for mid in sorted(missing):
            issue(f"{kt_rel}: R.id.{mid} tidak ada di {layout_name}.xml atau item layouts",
                  file=kt_rel, category="layout")
    else:
        log(f"  ✅ {kt_rel} → {layout_name}.xml (+{len(item_ids)} item IDs) — semua OK")

# ============================================================
# 8. MANIFEST ↔ KOTLIN
# ============================================================

section("8. MANIFEST ↔ KOTLIN")

for kind, name in manifest_classes:
    short = name.split(".")[-1]
    # Skip library classes (AndroidX, Google, dll)
    if is_library_class(name):
        log(f"  ℹ️  [{kind}] {short} — library class (skip)")
        continue

    if short not in declared_classes:
        issue(f"Manifest [{kind}] '{name}' tidak ada di Kotlin",
              file="AndroidManifest.xml", category="manifest")
    else:
        log(f"  ✅ [{kind}] {short}")

# ============================================================
# 9. AUTO-FIX (SMART)
# ============================================================

section("9. AUTO-FIX")

if not AUTO_FIX:
    log("  ⚠️  Auto-fix disabled")
else:
    # Fix 1: colors.xml
    colors_path = os.path.join(RES_DIR, "values", "colors.xml")
    missing_colors = set(n for _, n in unresolved.get("color", []))
    if missing_colors:
        log(f"\n  🔧 Fix 1: Tambah {len(missing_colors)} warna")
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
        existing = read(colors_path) if os.path.exists(colors_path) \
                   else '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>'
        existing_names = set(re.findall(r'<color name="(\w+)"', existing))
        to_add = []
        for c in sorted(missing_colors):
            if c in existing_names: continue
            value = DEFAULT_COLOR_VALUES.get(c)
            if not value:
                if "dark" in c: value = "#212121"
                elif "light" in c: value = "#F5F5F5"
                elif "bg" in c or "background" in c: value = "#FFFFFF"
                elif "text" in c: value = "#212121"
                else: value = "#607D8B"
            to_add.append(f'    <color name="{c}">{value}</color>')
        if to_add:
            write(colors_path, existing.replace("</resources>",
                  "\n".join(to_add) + "\n</resources>"))
            fix(f"Tambah {len(to_add)} warna")

    # Fix 2: dimens.xml
    dimens_path = os.path.join(RES_DIR, "values", "dimens.xml")
    missing_dimens = set(n for _, n in unresolved.get("dimen", []))
    if missing_dimens:
        log(f"\n  🔧 Fix 2: Tambah {len(missing_dimens)} dimens")
        existing = read(dimens_path) if os.path.exists(dimens_path) \
                   else '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>'
        existing_names = set(re.findall(r'<dimen name="(\w+)"', existing))
        to_add = [f'    <dimen name="{d}">16dp</dimen>'
                  for d in sorted(missing_dimens) if d not in existing_names]
        if to_add:
            write(dimens_path, existing.replace("</resources>",
                  "\n".join(to_add) + "\n</resources>"))
            fix(f"Tambah {len(to_add)} dimens")

    # Fix 3: strings.xml
    strings_path = os.path.join(RES_DIR, "values", "strings.xml")
    missing_strings = set(n for _, n in unresolved.get("string", []))
    if missing_strings:
        log(f"\n  🔧 Fix 3: Tambah {len(missing_strings)} strings")
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

    # Fix 4: styles
    missing_styles = set(n for _, n in unresolved.get("style", []))
    if missing_styles:
        log(f"\n  🔧 Fix 4: Tambah {len(missing_styles)} styles")
        themes_path = os.path.join(RES_DIR, "values", "themes.xml")
        existing = read(themes_path) if os.path.exists(themes_path) else \
                   ('<?xml version="1.0" encoding="utf-8"?>\n'
                    '<resources xmlns:tools="http://schemas.android.com/tools">\n</resources>')
        for s in missing_styles:
            if f'name="{s}"' in existing: continue
            parent = "Theme.MaterialComponents.DayNight.NoActionBar" if s.startswith("Theme.") \
                     else "android:Widget.Material.Button"
            style_xml = (f'\n    <style name="{s}" parent="{parent}"/>\n')
            existing = existing.replace("</resources>", style_xml + "</resources>")
            fix(f"Tambah style {s}")
        write(themes_path, existing)

    # Fix 5: drawables
    missing_drawables = set(n for _, n in unresolved.get("drawable", []))
    if missing_drawables:
        log(f"\n  🔧 Fix 5: Buat {len(missing_drawables)} drawable placeholder")
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

# ============================================================
# 10. ADDITIONAL CHECKS (yang kita alami)
# ============================================================

section("10. ADDITIONAL CHECKS")

# Cek gradle wrapper
wrapper_path = os.path.join(android_dir, "gradle", "wrapper", "gradle-wrapper.properties")
if os.path.exists(wrapper_path):
    content = read(wrapper_path)
    m = re.search(r'distributionUrl=.*gradle-([\d.]+)-', content)
    if m:
        log(f"  📦 Gradle version : {m.group(1)}")
else:
    issue("gradle-wrapper.properties tidak ada", category="gradle")

# Cek file kunci
CRITICAL_FILES = {
    "Manifest": manifest,
    "App Gradle": app_gradle,
    "Root Gradle": root_gradle,
    "Settings Gradle": settings_gradle,
    "Colors": os.path.join(RES_DIR, "values", "colors.xml"),
    "Strings": os.path.join(RES_DIR, "values", "strings.xml"),
    "Themes": os.path.join(RES_DIR, "values", "themes.xml"),
}

subsection("Critical files")
for name, path in CRITICAL_FILES.items():
    if path and os.path.exists(path):
        log(f"  ✅ {name}")
    else:
        issue(f"{name} tidak ada", category="resource")

# Cek YadApp.kt (untuk SecureConfig.init)
yadapp = None
for kt in kt_files:
    if "YadApp" in kt: yadapp = kt; break

if yadapp:
    src = read(yadapp)
    if "SecureConfig.init" not in src:
        issue("YadApp.kt: tidak panggil SecureConfig.init(this)",
              file=os.path.relpath(yadapp, WORKDIR), category="kotlin")
    else:
        log("  ✅ YadApp.kt: SecureConfig.init OK")

# Cek SecureConfig lateinit
secure = None
for kt in kt_files:
    if "SecureConfig" in kt: secure = kt; break

if secure:
    src = read(secure)
    if "lateinit" in src:
        issue("SecureConfig.kt: pakai 'lateinit' — bisa crash kalau belum init",
              sev="warning", file=os.path.relpath(secure, WORKDIR), category="kotlin")
    else:
        log("  ✅ SecureConfig.kt: pakai nullable (aman)")

# ============================================================
# 11. RINGKASAN
# ============================================================

section("11. RINGKASAN")

errors = [i for i in all_issues if i["severity"] == "error"]
warnings = [i for i in all_issues if i["severity"] == "warning"]
infos = [i for i in all_issues if i["severity"] == "info"]

log(f"  🔴 Errors   : {len(errors)}")
log(f"  🟡 Warnings : {len(warnings)}")
log(f"  🔵 Info     : {len(infos)}")
log(f"  🔧 Fixes    : {len(all_fixes)}")

# By category
log()
log("  Per kategori:")
categories = Counter(i.get("category", "general") for i in all_issues)
for cat, cnt in categories.most_common():
    log(f"    {cat}: {cnt}")

log()
log("=" * 78)
log("  ✅ Analisis selesai")
log("  Created by KARYADI, Coding by KARYADI")
log("=" * 78)

# ============================================================
# WRITE OUTPUTS
# ============================================================

with open(REPORT_TXT, "w", encoding="utf-8") as f:
    f.write("\n".join(report))

with open(REPORT_JSON, "w", encoding="utf-8") as f:
    json.dump({
        "issues": all_issues, "fixes": all_fixes,
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
    for fx in all_fixes: f.write(f"✅ {fx}\n")

print()
print("=" * 60)
print("📄 OUTPUT FILES:")
for f in [REPORT_TXT, REPORT_JSON, ISSUES_TXT, FIXES_TXT]:
    if os.path.exists(f):
        print(f"   ✅ {f} ({os.path.getsize(f):,} bytes)")
print("=" * 60)

#!/usr/bin/env python3
# ============================================================
# ULTIMATE ANALYZER — Deep analysis + auto-fix
# 20+ lapisan analisis untuk Android project
# Created by KARYADI, Coding by KARYADI
# ============================================================

import os, re, sys, json, subprocess, shutil
from datetime import datetime
from collections import Counter, defaultdict

WORKDIR     = os.environ.get("WORKDIR", ".")
OUTPUT_DIR  = os.environ.get("OUTPUT_DIR", "output")
AUTO_FIX    = os.environ.get("AUTO_FIX", "true").lower() == "true"

os.makedirs(OUTPUT_DIR, exist_ok=True)

def P(name): return os.path.join(OUTPUT_DIR, name)

REPORT_TXT = P("report.txt")
REPORT_JSON= P("report.json")
ISSUES_TXT = P("issues.txt")
FIXES_TXT  = P("fixes_applied.txt")

report = []
all_issues = []
all_fixes = []

def log(m=""):
    print(m); report.append(str(m))

def section(t):
    log(); log("=" * 78); log(f"  {t}"); log("=" * 78)

def subsection(t):
    log(); log(f"── {t} " + "─" * max(1, 72 - len(t)))

def issue(msg, sev="error", file=None, line=None):
    entry = {"msg": msg, "severity": sev}
    if file: entry["file"] = file
    if line: entry["line"] = line
    all_issues.append(entry)
    icon = {"error": "🔴", "warning": "🟡", "info": "🔵"}.get(sev, "⚪")
    loc = f" [{file}:{line}]" if file else ""
    log(f"  {icon} {msg}{loc}")

def fix(msg):
    all_fixes.append(msg)
    log(f"  🔧 {msg}")

# ============================================================
# UTILITY
# ============================================================

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
    except Exception as e:
        log(f"  ⚠️  Cannot read {path}: {e}")
        return ""

def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

# ============================================================
# FIND PROJECT STRUCTURE
# ============================================================

log("=" * 78)
log("  ULTIMATE ANALYZER — youtube-auto-pipeline")
log("  Created by KARYADI, Coding by KARYADI")
log("=" * 78)

section("0. PROJECT STRUCTURE")

# Cari folder android/
android_dir = None
for r, dirs, _ in os.walk(WORKDIR):
    if "android" in dirs:
        android_dir = os.path.join(r, "android")
        break

if not android_dir:
    log("❌ Folder 'android/' tidak ditemukan!")
    sys.exit(1)

APP_DIR = os.path.join(android_dir, "app")
SRC_DIR = os.path.join(APP_DIR, "src", "main")
RES_DIR = os.path.join(SRC_DIR, "res")
KT_DIR  = None

# Cari folder package Kotlin
for r, _, fs in os.walk(os.path.join(SRC_DIR, "java")):
    if any(f.endswith(".kt") for f in fs):
        KT_DIR = r; break

log(f"  Android dir : {os.path.relpath(android_dir, WORKDIR)}")
log(f"  Source dir  : {os.path.relpath(SRC_DIR, WORKDIR)}")
log(f"  Kotlin dir  : {os.path.relpath(KT_DIR, WORKDIR) if KT_DIR else 'N/A'}")
log(f"  Res dir     : {os.path.relpath(RES_DIR, WORKDIR)}")
log(f"  Auto-fix    : {AUTO_FIX}")

# ============================================================
# 1. INVENTARIS FILE
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
# 2. GRADLE ANALYSIS
# ============================================================

section("2. GRADLE ANALYSIS")

app_gradle = os.path.join(APP_DIR, "build.gradle")
if not os.path.exists(app_gradle):
    app_gradle = os.path.join(APP_DIR, "build.gradle.kts")

root_gradle = os.path.join(android_dir, "build.gradle")
settings_gradle = os.path.join(android_dir, "settings.gradle")

# --- 2a. app/build.gradle ---
if os.path.exists(app_gradle):
    content = read(app_gradle)
    subsection(f"app/build.gradle ({len(content)} bytes)")

    # Cek balance curly brace
    open_b  = content.count("{")
    close_b = content.count("}")
    if open_b != close_b:
        issue(f"app/build.gradle: curly brace tidak balance ({open_b} buka vs {close_b} tutup)",
              file=os.path.relpath(app_gradle, WORKDIR))
    else:
        log("  ✅ Curly brace balance")

    # Cek plugins block
    if "plugins {" not in content and "plugins{" not in content:
        issue("app/build.gradle: tidak ada blok 'plugins { }'",
              file=os.path.relpath(app_gradle, WORKDIR))

    # Cek namespace
    if "namespace" not in content:
        issue("app/build.gradle: tidak ada 'namespace'",
              file=os.path.relpath(app_gradle, WORKDIR))

    # Cek deprecated libs
    for lib in ["nl.bravobit:android-ffmpeg", "com.arthenica:ffmpeg-kit"]:
        if lib in content:
            issue(f"app/build.gradle: pakai library deprecated '{lib}'",
                  file=os.path.relpath(app_gradle, WORKDIR))

    # Daftar dependency
    deps = re.findall(r'implementation\s+[\'"]([^\'"]+)[\'"]', content)
    log(f"  Dependencies: {len(deps)}")
    for d in deps: log(f"    - {d}")

# --- 2b. settings.gradle ---
if os.path.exists(settings_gradle):
    content = read(settings_gradle)
    subsection("settings.gradle")

    if "FAIL_ON_PROJECT_REPOS" in content or "PREFER_SETTINGS" in content:
        log("  ✅ Repositories mode: FAIL_ON_PROJECT_REPOS atau PREFER_SETTINGS")

    if "jitpack.io" not in content:
        issue("settings.gradle: tidak ada JitPack repository (kalau butuh)",
              sev="info", file=os.path.relpath(settings_gradle, WORKDIR))

# ============================================================
# 3. ANDROID MANIFEST
# ============================================================

section("3. ANDROID MANIFEST")

manifest_classes = []
if os.path.exists(manifest):
    content = read(manifest)
    subsection("Activities, Services, Receivers, Providers")

    for m in re.finditer(r'<activity\s+android:name="([^"]+)"', content):
        manifest_classes.append(("activity", m.group(1)))
    for m in re.finditer(r'<service\s+android:name="([^"]+)"', content):
        manifest_classes.append(("service", m.group(1)))
    for m in re.finditer(r'<receiver\s+android:name="([^"]+)"', content):
        manifest_classes.append(("receiver", m.group(1)))
    for m in re.finditer(r'<provider\s+android:name="([^"]+)"', content):
        manifest_classes.append(("provider", m.group(1)))

    for kind, name in manifest_classes:
        log(f"    [{kind:9s}] {name}")

    # Cek permission
    perms = re.findall(r'<uses-permission\s+android:name="([^"]+)"', content)
    subsection(f"Permissions ({len(perms)})")
    for p in perms: log(f"    - {p}")

    # Cek resource yang direferensi
    subsection("Resource references di manifest")
    for m in re.finditer(r'@(\w+)/(\w+)', content):
        kind, name = m.group(1), m.group(2)
        log(f"    @{kind}/{name}")

# ============================================================
# 4. KOTLIN ANALYSIS
# ============================================================

section("4. KOTLIN ANALYSIS")

# Kumpulkan class, method, findViewById, layout
declared_classes = {}
called_ids_by_class = {}
used_layouts = {}
called_activities = set()

for kt in kt_files:
    rel = os.path.relpath(kt, WORKDIR)
    src = read(kt)

    # Class declarations
    for m in re.finditer(r'(?:class|object|interface)\s+(\w+)', src):
        declared_classes[m.group(1)] = rel

    # findViewById
    ids = set()
    for m in re.finditer(r'findViewById<[^>]*>\(R\.id\.(\w+)\)', src):
        ids.add(m.group(1))
    for m in re.finditer(r'findViewById\(R\.id\.(\w+)\)', src):
        ids.add(m.group(1))
    if ids: called_ids_by_class[rel] = ids

    # setContentView
    for m in re.finditer(r'setContentView\(R\.layout\.(\w+)\)', src):
        used_layouts[rel] = m.group(1)

    # Intent ke class
    for m in re.finditer(r'Intent\([^,]+,\s*(\w+)::class', src):
        called_activities.add(m.group(1))

log(f"  Classes declared   : {len(declared_classes)}")
log(f"  Files using layout : {len(used_layouts)}")
log(f"  Intent targets     : {len(called_activities)}")

# Cek intent ke class yang tidak ada
subsection("Cek Intent reference")
for target in called_activities:
    if target not in declared_classes:
        issue(f"Intent ke '{target}' tapi class tidak ada",
              file="Kotlin", sev="error")
    else:
        log(f"  ✅ {target}")

# ============================================================
# 5. RESOURCE ANALYSIS
# ============================================================

section("5. RESOURCE ANALYSIS")

# Kumpulkan resource yang didefinisikan
defined_colors = set()
defined_strings = set()
defined_dimens = set()
defined_styles = set()
defined_drawables = set()
defined_mipmaps = set()
defined_layouts = set()
defined_xmls = set()

# values/*.xml
values_dir = os.path.join(RES_DIR, "values")
if os.path.exists(values_dir):
    for fn in os.listdir(values_dir):
        if not fn.endswith(".xml"): continue
        content = read(os.path.join(values_dir, fn))
        for m in re.finditer(r'<color name="(\w+)"', content):
            defined_colors.add(m.group(1))
        for m in re.finditer(r'<string name="(\w+)"', content):
            defined_strings.add(m.group(1))
        for m in re.finditer(r'<dimen name="(\w+)"', content):
            defined_dimens.add(m.group(1))
        for m in re.finditer(r'<style name="([\w.]+)"', content):
            defined_styles.add(m.group(1))

# Drawables & mipmaps
for folder in os.listdir(RES_DIR):
    full = os.path.join(RES_DIR, folder)
    if not os.path.isdir(full): continue

    if folder.startswith("drawable"):
        for fn in os.listdir(full):
            defined_drawables.add(fn.rsplit(".", 1)[0])
    elif folder.startswith("mipmap"):
        for fn in os.listdir(full):
            defined_mipmaps.add(fn.rsplit(".", 1)[0])
    elif folder == "layout":
        for fn in os.listdir(full):
            defined_layouts.add(fn.rsplit(".", 1)[0])
    elif folder == "xml":
        for fn in os.listdir(full):
            defined_xmls.add(fn.rsplit(".", 1)[0])

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

section("6. CROSS-CHECK RESOURCE REFERENCES")

unresolved = defaultdict(list)

# Semua XML di res/ (layout, drawable, values, xml, mipmap)
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
        for m in re.finditer(r'@layout/(\w+)', content):
            if m.group(1) not in defined_layouts:
                unresolved["layout"].append((rel, m.group(1)))
        for m in re.finditer(r'@xml/(\w+)', content):
            if m.group(1) not in defined_xmls:
                unresolved["xml"].append((rel, m.group(1)))

# Kotlin juga bisa referensi @color, @string
for kt in kt_files:
    src = read(kt)
    rel = os.path.relpath(kt, WORKDIR)
    for m in re.finditer(r'R\.color\.(\w+)', src):
        if m.group(1) not in defined_colors:
            unresolved["color"].append((rel, m.group(1)))
    for m in re.finditer(r'R\.string\.(\w+)', src):
        if m.group(1) not in defined_strings:
            unresolved["string"].append((rel, m.group(1)))

# Laporkan
for kind, entries in unresolved.items():
    if entries:
        log(f"\n  ❌ @{kind} unresolved ({len(entries)}):")
        for rel, name in entries:
            log(f"     {rel}: @{kind}/{name}")
            issue(f"@{kind}/{name} tidak ada", file=rel)
    else:
        log(f"  ✅ @{kind} — semua OK")

# ============================================================
# 7. KOTLIN vs LAYOUT MATCHING
# ============================================================

section("7. KOTLIN vs LAYOUT MATCHING")

for kt_rel, layout_name in used_layouts.items():
    if layout_name not in defined_layouts:
        issue(f"{kt_rel}: setContentView(R.layout.{layout_name}) — layout tidak ada",
              file=kt_rel, sev="error")
        continue

    # Baca layout
    layout_path = os.path.join(RES_DIR, "layout", f"{layout_name}.xml")
    layout_content = read(layout_path)

    # Collect IDs di layout
    layout_ids = set()
    for m in re.finditer(r'android:id="@\+id/(\w+)"', layout_content):
        layout_ids.add(m.group(1))
    # Include @+id/ dari @id/ (referensi)
    for m in re.finditer(r'android:id="@id/(\w+)"', layout_content):
        layout_ids.add(m.group(1))

    # Cek findViewById di Kotlin
    called_ids = called_ids_by_class.get(kt_rel, set())
    missing = called_ids - layout_ids

    if missing:
        log(f"  ❌ {kt_rel} → {layout_name}.xml — {len(missing)} ID hilang:")
        for mid in sorted(missing):
            log(f"       R.id.{mid}")
            issue(f"{kt_rel}: R.id.{mid} tidak ada di {layout_name}.xml",
                  file=kt_rel)
    else:
        log(f"  ✅ {kt_rel} → {layout_name}.xml — semua ID ada")

# ============================================================
# 8. ANDROIDMANIFEST vs CLASS
# ============================================================

section("8. ANDROIDMANIFEST vs CLASS")

for kind, name in manifest_classes:
    # Activity biasanya .MainActivity atau com.x.MainActivity
    short = name.split(".")[-1]
    if short not in declared_classes:
        issue(f"Manifest [{kind}] '{name}' tidak ada di Kotlin",
              file="AndroidManifest.xml")
    else:
        log(f"  ✅ [{kind}] {short}")

# ============================================================
# 9. AUTO-FIX
# ============================================================

section("9. AUTO-FIX")

if not AUTO_FIX:
    log("  ⚠️  Auto-fix disabled (AUTO_FIX=false)")
else:
    # --- Fix 1: Tambah warna yang hilang ke colors.xml ---
    colors_path = os.path.join(RES_DIR, "values", "colors.xml")
    missing_colors = set(n for _, n in unresolved.get("color", []))
    if missing_colors:
        log(f"\n  🔧 Fix 1: Tambah {len(missing_colors)} warna ke colors.xml")

        # Default color map
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

        # Baca yang sudah ada
        if os.path.exists(colors_path):
            existing = read(colors_path)
        else:
            existing = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>'

        # Extract nama yang sudah ada
        existing_names = set(re.findall(r'<color name="(\w+)"', existing))

        to_add = []
        for c in sorted(missing_colors):
            if c in existing_names: continue
            value = DEFAULT_COLOR_VALUES.get(c)
            if not value:
                # Tebak dari nama
                if "dark" in c: value = "#212121"
                elif "light" in c: value = "#F5F5F5"
                elif "bg" in c or "background" in c: value = "#FFFFFF"
                elif "text" in c: value = "#212121"
                else: value = "#607D8B"
            to_add.append(f'    <color name="{c}">{value}</color>')

        if to_add:
            # Sisipkan sebelum </resources>
            new_content = existing.replace("</resources>",
                "\n".join(to_add) + "\n</resources>")
            write(colors_path, new_content)
            fix(f"Tambah {len(to_add)} warna ke colors.xml")

    # --- Fix 2: Tambah dimens yang hilang ---
    dimens_path = os.path.join(RES_DIR, "values", "dimens.xml")
    missing_dimens = set(n for _, n in unresolved.get("dimen", []))
    if missing_dimens:
        log(f"\n  🔧 Fix 2: Tambah {len(missing_dimens)} dimens")
        if os.path.exists(dimens_path):
            existing = read(dimens_path)
        else:
            existing = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>'

        existing_names = set(re.findall(r'<dimen name="(\w+)"', existing))
        to_add = []
        for d in sorted(missing_dimens):
            if d in existing_names: continue
            to_add.append(f'    <dimen name="{d}">16dp</dimen>')

        if to_add:
            new_content = existing.replace("</resources>",
                "\n".join(to_add) + "\n</resources>")
            write(dimens_path, new_content)
            fix(f"Tambah {len(to_add)} dimens")

    # --- Fix 3: Tambah strings yang hilang ---
    strings_path = os.path.join(RES_DIR, "values", "strings.xml")
    missing_strings = set(n for _, n in unresolved.get("string", []))
    if missing_strings:
        log(f"\n  🔧 Fix 3: Tambah {len(missing_strings)} strings")
        if os.path.exists(strings_path):
            existing = read(strings_path)
        else:
            existing = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>'

        existing_names = set(re.findall(r'<string name="(\w+)"', existing))
        to_add = []
        for s in sorted(missing_strings):
            if s in existing_names: continue
            to_add.append(f'    <string name="{s}">{s.replace("_", " ").title()}</string>')

        if to_add:
            new_content = existing.replace("</resources>",
                "\n".join(to_add) + "\n</resources>")
            write(strings_path, new_content)
            fix(f"Tambah {len(to_add)} strings")

    # --- Fix 4: Buat style yang hilang ---
    missing_styles = set(n for _, n in unresolved.get("style", []))
    if missing_styles:
        log(f"\n  🔧 Fix 4: Cek style yang hilang")
        for s in missing_styles:
            if s.startswith("Theme."):
                # Buat theme
                themes_path = os.path.join(RES_DIR, "values", "themes.xml")
                if os.path.exists(themes_path):
                    existing = read(themes_path)
                else:
                    existing = ('<?xml version="1.0" encoding="utf-8"?>\n'
                                '<resources xmlns:tools="http://schemas.android.com/tools">\n'
                                '</resources>')
                if f'name="{s}"' not in existing:
                    style_xml = (f'\n    <style name="{s}" '
                                 f'parent="Theme.MaterialComponents.DayNight.NoActionBar">\n'
                                 f'        <item name="colorPrimary">#1A237E</item>\n'
                                 f'        <item name="colorSecondary">#FF9800</item>\n'
                                 f'    </style>')
                    new_content = existing.replace("</resources>",
                        style_xml + "\n</resources>")
                    write(themes_path, new_content)
                    fix(f"Buat style {s}")

    # --- Fix 5: Buat drawable yang hilang (sebagai shape sederhana) ---
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
# 10. FINAL CHECK
# ============================================================

section("10. FINAL CHECK")

# Re-scan setelah fix
if AUTO_FIX and all_fixes:
    log("  Re-scan setelah auto-fix...")

    defined_colors2 = set()
    colors_path = os.path.join(RES_DIR, "values", "colors.xml")
    if os.path.exists(colors_path):
        for m in re.finditer(r'<color name="(\w+)"', read(colors_path)):
            defined_colors2.add(m.group(1))

    defined_strings2 = set()
    strings_path = os.path.join(RES_DIR, "values", "strings.xml")
    if os.path.exists(strings_path):
        for m in re.finditer(r'<string name="(\w+)"', read(strings_path)):
            defined_strings2.add(m.group(1))

    defined_dimens2 = set()
    dimens_path = os.path.join(RES_DIR, "values", "dimens.xml")
    if os.path.exists(dimens_path):
        for m in re.finditer(r'<dimen name="(\w+)"', read(dimens_path)):
            defined_dimens2.add(m.group(1))

    # Re-scan
    still_unresolved = []
    for root, _, fs in os.walk(RES_DIR):
        for fn in fs:
            if not fn.endswith(".xml"): continue
            full = os.path.join(root, fn)
            content = read(full)
            rel = os.path.relpath(full, WORKDIR)

            for m in re.finditer(r'@color/(\w+)', content):
                if m.group(1) not in defined_colors2:
                    still_unresolved.append(f"{rel} → @color/{m.group(1)}")
            for m in re.finditer(r'@string/(\w+)', content):
                if m.group(1) not in defined_strings2:
                    still_unresolved.append(f"{rel} → @string/{m.group(1)}")
            for m in re.finditer(r'@dimen/(\w+)', content):
                if m.group(1) not in defined_dimens2:
                    still_unresolved.append(f"{rel} → @dimen/{m.group(1)}")

    if still_unresolved:
        log(f"  ⚠️  Masih ada {len(still_unresolved)} unresolved setelah fix:")
        for u in still_unresolved[:20]:
            log(f"     {u}")
    else:
        log("  ✅ Semua resource reference resolved!")

# ============================================================
# SUMMARY
# ============================================================

section("SUMMARY")

errors = [i for i in all_issues if i["severity"] == "error"]
warnings = [i for i in all_issues if i["severity"] == "warning"]
infos = [i for i in all_issues if i["severity"] == "info"]

log(f"  🔴 Errors   : {len(errors)}")
log(f"  🟡 Warnings : {len(warnings)}")
log(f"  🔵 Info     : {len(infos)}")
log(f"  🔧 Fixes    : {len(all_fixes)}")

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
        "issues": all_issues,
        "fixes": all_fixes,
        "summary": {
            "errors": len(errors),
            "warnings": len(warnings),
            "infos": len(infos),
            "fixes": len(all_fixes),
        },
        "timestamp": datetime.now().isoformat(),
    }, f, indent=2, ensure_ascii=False)

with open(ISSUES_TXT, "w", encoding="utf-8") as f:
    for i in all_issues:
        loc = f" [{i.get('file','?')}]" if i.get("file") else ""
        f.write(f"[{i['severity'].upper()}]{loc} {i['msg']}\n")

with open(FIXES_TXT, "w", encoding="utf-8") as f:
    for fx in all_fixes:
        f.write(f"✅ {fx}\n")

print()
print("=" * 60)
print("📄 OUTPUT FILES:")
for f in [REPORT_TXT, REPORT_JSON, ISSUES_TXT, FIXES_TXT]:
    if os.path.exists(f):
        print(f"   ✅ {f} ({os.path.getsize(f):,} bytes)")
print("=" * 60)

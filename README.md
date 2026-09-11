# YouTube Auto Pipeline + APK Android

Repo ini berisi:
1. **Pipeline YouTube** - auto upload video ke YouTube per part
2. **APK Android** - trigger workflow dari HP

## Fitur Pipeline
- Anti-Detect Bot
- Content Detection
- Watermark Remover (blur/inpaint)
- Split per Part
- **Video Size Preset** (12 platform)
- **Video Quality** (144p - 2160p)
- Auto Hashtag + Title per Part
- Auto Upload YouTube
- Pilih Mode: Remove / Skip Watermark
- Pilih Tipe Upload: Video / Reels

## Fitur APK
- Setup GitHub Token (input / upload file)
- Auto-verify token
- Simpan token (auto login)
- Input URL video
- Pilih semua opsi pipeline
- Run workflow langsung
- Cek status real-time
- Buka GitHub Actions

## Build APK
APK otomatis di-build via GitHub Actions saat:
- Push ke `main` (jika ada perubahan di `android/`)
- Manual run workflow `Build APK`

Download APK dari:
- Tab **Actions** → pilih run → Artifacts
- Tab **Releases** → pilih tag `apk-vX`

## Cara Pakai APK
1. Install APK
2. Buka app → Setup GitHub Token
3. Isi token atau upload file `token.txt`
4. Isi form pipeline
5. Klik **Run Workflow**
6. Cek status di GitHub Actions

---

Created By Yad

# Complete YouTube Pipeline - Per Part Upload

## Features
1. Anti-Detect Bot
2. Content Detection (Anime, Manhwa, Donghua, Manga, Gaming)
3. Watermark Remover (blur/inpaint)
4. Split per Part (custom duration)
5. Auto Hashtag (content-aware)
6. Auto Title per Part
7. Auto Upload per Part
8. Preserve Audio (FFmpeg merge)
9. Universal Downloader (YouTube, FB, TikTok, IG)
10. Auto Setup Secrets (upload token.txt & api_key.txt)
11. Pilih Tipe Upload: Reels/Shorts atau Video Biasa
12. PILIH MODE: Hapus Watermark atau Langsung Upload (BARU)
13. GitHub Actions Full Automation

## Process Mode (BARU)

### remove_watermark
- Hapus watermark dulu (blur/inpaint)
- Split per part
- Upload ke YouTube

### skip_watermark
- Download video
- Split per part langsung (tanpa hapus watermark)
- Upload ke YouTube
- Lebih cepat karena skip proses deteksi + remove

## Setup Awal

### 1. Upload token.txt (GitHub PAT)
- Buat di: https://github.com/settings/tokens
- Scope: repo, workflow

### 2. Upload api_key.txt (YouTube API Key)
- Buat di: https://console.cloud.google.com/apis/credentials
- Enable: YouTube Data API v3

### 3. Upload youtube_token.txt (Opsional)
- Base64 dari token.pickle untuk auto upload

## Cara Pakai
1. Buka Actions
2. Pilih "Complete Pipeline + Per-Part Upload"
3. Isi form:
   - video_url: URL video
   - process_mode: remove_watermark / skip_watermark
   - method: blur / inpaint (kalau remove_watermark)
   - part_duration: 300 (5 menit)
   - auto_upload: true/false
   - upload_type: video / reels
4. Klik Run workflow
5. Download dari Artifacts/Releases

## Tipe Upload

### Video Biasa
- Aspect ratio asli (16:9)

### Reels/Shorts
- Auto-convert ke 9:16 portrait (1080x1920)
- Max 60 detik
- Hashtag #Shorts #Reels otomatis

---

Created By Yad

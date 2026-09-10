#!/usr/bin/env python3
"""Upload YouTube per Part - Pilihan Reels / Video Biasa"""

import os
import sys
import glob
import pickle
import random
import time
import re
import subprocess
from auto_hashtag import AutoHashtag
from anti_detect import AntiDetectBot
from content_detector import ContentDetector

try:
    from google.auth.transport.requests import Request
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
    from google_auth_oauthlib.flow import InstalledAppFlow
except ImportError:
    print("[!] Install: pip install google-auth google-auth-oauthlib google-api-python-client")
    sys.exit(1)


SCOPES = ['https://www.googleapis.com/auth/youtube.upload']


def get_video_dimensions(path):
    try:
        result = subprocess.run(
            ['ffprobe', '-v', 'error', '-select_streams', 'v:0',
             '-show_entries', 'stream=width,height',
             '-of', 'csv=s=x:p=0', path],
            capture_output=True, text=True, timeout=15
        )
        dims = result.stdout.strip().split('x')
        if len(dims) == 2:
            return int(dims[0]), int(dims[1])
    except:
        pass
    return 0, 0


def convert_to_reels(input_path, output_path):
    print("[*] Convert ke format Reels (9:16)...")
    w, h = get_video_dimensions(input_path)
    if w == 0 or h == 0:
        print("[!] Gagal ambil dimensi")
        return input_path

    target_w, target_h = 1080, 1920
    if h > w and abs((w / h) - (9 / 16)) < 0.05:
        print("[+] Sudah 9:16, skip convert")
        return input_path

    vf = (
        "scale=" + str(target_w) + ":" + str(target_h) + ":force_original_aspect_ratio=increase,"
        "crop=" + str(target_w) + ":" + str(target_h) + ",setsar=1"
    )
    cmd = [
        'ffmpeg', '-i', input_path, '-vf', vf,
        '-c:v', 'libx264', '-preset', 'fast', '-crf', '23',
        '-c:a', 'aac', '-b:a', '192k',
        '-movflags', '+faststart', '-t', '60',
        output_path, '-y', '-loglevel', 'error'
    ]
    result = subprocess.run(cmd, capture_output=True, timeout=600)
    if result.returncode == 0 and os.path.exists(output_path):
        size = os.path.getsize(output_path) / (1024 * 1024)
        print("[+] Converted: " + str(round(size, 1)) + " MB")
        return output_path
    print("[!] Convert gagal, pakai original")
    return input_path


def get_service():
    creds = None
    if os.path.exists('token.pickle'):
        with open('token.pickle', 'rb') as f:
            try:
                creds = pickle.load(f)
            except:
                pass
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file('client_secrets.json', SCOPES)
            creds = flow.run_local_server(port=0)
        with open('token.pickle', 'wb') as f:
            pickle.dump(creds, f)
    return build('youtube', 'v3', credentials=creds)


def upload_single(youtube, video_path, title, description, tags, privacy='public'):
    body = {
        'snippet': {
            'title': title,
            'description': description,
            'tags': [t.strip('#') for t in tags.split()],
            'categoryId': '20'
        },
        'status': {
            'privacyStatus': privacy,
            'selfDeclaredMadeForKids': False
        }
    }
    media = MediaFileUpload(video_path, chunksize=-1, resumable=True)
    request = youtube.videos().insert(
        part=','.join(body.keys()), body=body, media_body=media
    )
    response = None
    while response is None:
        time.sleep(random.uniform(0.5, 2.0))
        status, response = request.next_chunk()
        if status:
            print("    Upload: " + str(int(status.progress() * 100)) + "%")
    return response['id']


def extract_part_from_filename(filename):
    patterns = [
        'part[-_ ]*([0-9]+)',
        '_part([0-9]+)',
        'part([0-9]+)',
    ]
    text = filename.lower()
    for p in patterns:
        m = re.search(p, text)
        if m:
            try:
                return int(m.group(1))
            except:
                pass
    return None


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-dir', default='output')
    parser.add_argument('--privacy', default='public')
    parser.add_argument('--start-part', type=int, default=1)
    parser.add_argument('--delay', type=int, default=60)
    parser.add_argument('--upload-type', default='video', choices=['video', 'reels'])
    args = parser.parse_args()

    print("=" * 70)
    print("  AUTO UPLOAD PER PART - Type: " + args.upload_type.upper())
    print("=" * 70)

    videos = sorted(glob.glob(os.path.join(args.input_dir, "*.mp4")))
    if not videos:
        print("[!] Tidak ada video di " + args.input_dir)
        return
    print("[*] Found " + str(len(videos)) + " videos")

    bot = AntiDetectBot()
    gen = AutoHashtag()
    detector = ContentDetector()
    youtube = get_service()

    uploaded = []
    failed = []

    for i, video_path in enumerate(videos, 1):
        filename = os.path.basename(video_path)
        part_num = extract_part_from_filename(filename) or i

        if part_num < args.start_part:
            print("[*] Skip part " + str(part_num))
            continue

        print("\n" + "=" * 70)
        print("  Upload " + str(i) + "/" + str(len(videos)) + ": " + filename)
        print("  PART " + str(part_num) + " | TYPE: " + args.upload_type.upper())
        print("=" * 70)

        actual_path = video_path
        if args.upload_type == 'reels':
            reels_path = video_path.replace('.mp4', '_reels.mp4')
            actual_path = convert_to_reels(video_path, reels_path)

        time.sleep(random.uniform(5.0, 15.0))

        title = gen.generate_title(filename, args.upload_type)
        hashtags = gen.generate(filename, title, upload_type=args.upload_type)
        description = gen.generate_description(filename, title, hashtags, args.upload_type)

        print("\nTitle: " + title)
        print("Hashtags: " + hashtags)

        try:
            vid = upload_single(youtube, actual_path, title, description, hashtags, args.privacy)
            if vid:
                url = "https://youtu.be/" + vid
                print("\n[+] Uploaded: " + url)
                uploaded.append({'part': part_num, 'url': url, 'type': args.upload_type})
            else:
                failed.append(filename)
        except Exception as e:
            print("\n[!] Error: " + str(e))
            failed.append(filename)

        if args.upload_type == 'reels' and actual_path != video_path:
            try:
                os.remove(actual_path)
            except:
                pass

        if i < len(videos):
            print("[*] Wait " + str(args.delay) + "s...")
            time.sleep(args.delay)

    print("\n" + "=" * 70)
    print("  COMPLETE - Berhasil: " + str(len(uploaded)))
    for u in uploaded:
        print("  Part " + str(u['part']) + " [" + u['type'] + "]: " + u['url'])
    if failed:
        print("[!] Gagal: " + str(len(failed)))


if __name__ == '__main__':
    main()

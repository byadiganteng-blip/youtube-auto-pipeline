#!/usr/bin/env python3
"""Upload YouTube per Part"""

import os
import sys
import glob
import pickle
import random
import time
import re
from auto_hashtag import AutoHashtag
from anti_detect import AntiDetectBot
from content_detector import ContentDetector

try:
    from google.auth.transport.requests import Request
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
except ImportError:
    print("[!] Install: pip install google-auth google-api-python-client")
    sys.exit(1)


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
    for p in ['part[-_ ]*([0-9]+)', '_part([0-9]+)', 'part([0-9]+)']:
        m = re.search(p, filename.lower())
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
    print("  AUTO UPLOAD - Type: " + args.upload_type.upper())
    print("=" * 70)

    videos = sorted(glob.glob(os.path.join(args.input_dir, "*.mp4")))
    if not videos:
        return
    print("[*] Found " + str(len(videos)) + " videos")

    bot = AntiDetectBot()
    gen = AutoHashtag()
    youtube = get_service()

    uploaded = []
    failed = []

    for i, video_path in enumerate(videos, 1):
        filename = os.path.basename(video_path)
        part_num = extract_part_from_filename(filename) or i

        if part_num < args.start_part:
            continue

        print("\n" + "=" * 70)
        print("  Upload " + str(i) + "/" + str(len(videos)) + ": " + filename)
        print("=" * 70)

        time.sleep(random.uniform(5.0, 15.0))

        title = gen.generate_title(filename, args.upload_type)
        hashtags = gen.generate(filename, title, upload_type=args.upload_type)
        description = gen.generate_description(filename, title, hashtags, args.upload_type)

        print("Title: " + title)

        try:
            vid = upload_single(youtube, video_path, title, description, hashtags, args.privacy)
            if vid:
                url = "https://youtu.be/" + vid
                print("[+] Uploaded: " + url)
                uploaded.append({'part': part_num, 'url': url})
            else:
                failed.append(filename)
        except Exception as e:
            print("[!] Error: " + str(e))
            failed.append(filename)

        if i < len(videos):
            time.sleep(args.delay)

    print("\n" + "=" * 70)
    print("  COMPLETE - Berhasil: " + str(len(uploaded)))


if __name__ == '__main__':
    main()

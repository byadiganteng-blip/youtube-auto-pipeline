#!/usr/bin/env python3
"""Upload YouTube per Part dengan judul sesuai part"""

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
    from google.oauth2.credentials import Credentials
    from google_auth_oauthlib.flow import InstalledAppFlow
    from google.auth.transport.requests import Request
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
except ImportError:
    print("[!] Install: pip install google-auth google-auth-oauthlib google-api-python-client")
    sys.exit(1)


SCOPES = ['https://www.googleapis.com/auth/youtube.upload']


def get_service():
    creds = None
    if os.path.exists('token.pickle'):
        with open('token.pickle', 'rb') as f:
            creds = pickle.load(f)
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
    """Upload 1 video ke YouTube"""
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
        part=','.join(body.keys()),
        body=body,
        media_body=media
    )
    response = None
    while response is None:
        time.sleep(random.uniform(0.5, 2.0))
        status, response = request.next_chunk()
        if status:
            print("    Upload: " + str(int(status.progress() * 100)) + "%")
    return response['id']


def extract_part_from_filename(filename):
    """Ekstrak nomor part dari nama file"""
    patterns = [
        r'part[_\-]*(\d+)',
        r'_part(\d+)',
        r'part(\d+)',
    ]
    
    text = filename.lower()
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            try:
                return int(match.group(1))
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
    args = parser.parse_args()
    
    print("=" * 70)
    print("  AUTO UPLOAD PER PART")
    print("=" * 70)
    
    # Cari semua video
    videos = sorted(glob.glob(os.path.join(args.input_dir, "*.mp4")))
    
    if not videos:
        print("[!] Tidak ada video di " + args.input_dir)
        return
    
    print("[*] Found " + str(len(videos)) + " videos")
    
    # Setup
    bot = AntiDetectBot()
    gen = AutoHashtag()
    detector = ContentDetector()
    youtube = get_service()
    
    uploaded = []
    failed = []
    
    for i, video_path in enumerate(videos, 1):
        filename = os.path.basename(video_path)
        
        # Ekstrak part number
        part_num = extract_part_from_filename(filename)
        
        if part_num is None:
            part_num = i
        
        print("\n" + "=" * 70)
        print("  Upload " + str(i) + "/" + str(len(videos)) + ": " + filename)
        print("  PART " + str(part_num))
        print("=" * 70)
        
        # Anti-detect delay
        delay = random.uniform(5.0, 15.0)
        print("[*] Anti-detect delay: " + str(round(delay, 1)) + "s")
        time.sleep(delay)
        
        # Generate metadata
        title = gen.generate_title(filename)
        hashtags = gen.generate(filename, title)
        description = gen.generate_description(filename, title, hashtags)
        
        # Display
        print("\n📝 Title: " + title)
        print("\n🔖 Hashtags: " + hashtags)
        print("\n📄 Description: " + description[:200] + "...")
        
        # Upload
        try:
            video_id = upload_single(
                youtube, video_path, title, description, hashtags, args.privacy
            )
            if video_id:
                url = "https://youtu.be/" + video_id
                print("\n✅ Uploaded: " + url)
                uploaded.append({
                    'part': part_num,
                    'filename': filename,
                    'url': url,
                    'title': title
                })
            else:
                print("\n❌ Upload failed")
                failed.append(filename)
        except Exception as e:
            print("\n❌ Error: " + str(e))
            failed.append(filename)
        
        # Delay antar part
        if i < len(videos):
            wait = args.delay
            print("\n[*] Waiting " + str(wait) + "s before next part...")
            time.sleep(wait)
    
    # Summary
    print("\n" + "=" * 70)
    print("  UPLOAD COMPLETE")
    print("=" * 70)
    print("\n✅ Berhasil: " + str(len(uploaded)))
    for u in uploaded:
        print("  Part " + str(u['part']) + ": " + u['url'])
    
    if failed:
        print("\n❌ Gagal: " + str(len(failed)))
        for f in failed:
            print("  - " + f)


if __name__ == '__main__':
    main()

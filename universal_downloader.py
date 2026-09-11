#!/usr/bin/env python3
import os, sys, argparse, subprocess

def download_video(url, output):
    os.makedirs(os.path.dirname(output), exist_ok=True)
    print("[*] Downloading: " + url)
    cmd = ['yt-dlp', '-f', 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best',
           '--merge-output-format', 'mp4', '-o', output, '--no-playlist', '--no-warnings', url]
    result = subprocess.run(cmd)
    if result.returncode == 0 and os.path.exists(output):
        print("[+] Downloaded: " + str(round(os.path.getsize(output) / (1024*1024), 1)) + " MB")
        return True
    return False

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--url', required=True)
    parser.add_argument('--output', default='input/video.mp4')
    args = parser.parse_args()
    if not download_video(args.url, args.output): sys.exit(1)

if __name__ == '__main__': main()

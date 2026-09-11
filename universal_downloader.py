#!/usr/bin/env python3
import os, sys, argparse, subprocess
def dl(url, out):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    return subprocess.run(['yt-dlp','-f','bestvideo[ext=mp4]+bestaudio[ext=m4a]/best','--merge-output-format','mp4','-o',out,'--no-playlist',url]).returncode == 0
def main():
    p = argparse.ArgumentParser(); p.add_argument('--url', required=True); p.add_argument('--output', default='input/video.mp4')
    a = p.parse_args()
    if not dl(a.url, a.output): sys.exit(1)
if __name__ == '__main__': main()

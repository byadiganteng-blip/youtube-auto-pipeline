#!/usr/bin/env python3
"""Watermark Remover + Split + Per-Part Output"""

import cv2
import numpy as np
import os
import sys
import argparse
import time
import subprocess
import tempfile
import shutil
import re


def get_duration(path):
    try:
        result = subprocess.run(
            ['ffprobe', '-v', 'error', '-show_entries', 'format=duration',
             '-of', 'default=noprint_wrappers=1:nokey=1', path],
            capture_output=True, text=True, timeout=30
        )
        return float(result.stdout.strip())
    except:
        return 0


def has_audio(path):
    try:
        result = subprocess.run(
            ['ffprobe', '-v', 'error', '-select_streams', 'a:0',
             '-show_entries', 'stream=codec_type',
             '-of', 'default=noprint_wrappers=1:nokey=1', path],
            capture_output=True, text=True, timeout=10
        )
        return 'audio' in result.stdout.lower()
    except:
        return False


def detect_watermarks(path, samples=30):
    print("[*] Detecting watermarks...")
    cap = cv2.VideoCapture(path)
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if total < 2:
        cap.release()
        return []
    step = max(1, total // samples)
    frames = []
    for i in range(0, total, step):
        cap.set(cv2.CAP_PROP_POS_FRAMES, i)
        ret, frame = cap.read()
        if ret:
            frames.append(frame)
        if len(frames) >= samples:
            break
    cap.release()
    
    if len(frames) < 2:
        return []
    
    h, w = frames[0].shape[:2]
    grays = [cv2.cvtColor(f, cv2.COLOR_BGR2GRAY) for f in frames]
    
    variance = np.var(np.stack(grays, axis=0), axis=0)
    static = (variance < 30).astype(np.uint8) * 255
    
    edges = [cv2.Canny(g, 50, 150) for g in grays]
    edge_var = np.var(np.stack(edges, axis=0), axis=0)
    stable = (edge_var < 100).astype(np.uint8) * 255
    
    bright = []
    for g in grays:
        _, b = cv2.threshold(g, 220, 255, cv2.THRESH_BINARY)
        bright.append(b)
    bright_mean = np.mean(np.stack(bright, axis=0), axis=0)
    bright_stable = (bright_mean > 200).astype(np.uint8) * 255
    
    combined = cv2.bitwise_or(static, stable)
    combined = cv2.bitwise_or(combined, bright_stable)
    kernel = np.ones((15, 15), np.uint8)
    combined = cv2.dilate(combined, kernel, iterations=2)
    
    contours, _ = cv2.findContours(combined, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    watermarks = []
    for cnt in contours:
        area = cv2.contourArea(cnt)
        if 200 < area < w * h * 0.15:
            x, y, cw, ch = cv2.boundingRect(cnt)
            watermarks.append({'bbox': (x, y, x + cw, y + ch), 'area': area})
    
    if not watermarks:
        cs = min(w, h) // 4
        corners = [
            (0, 0, cs, cs), (w - cs, 0, w, cs),
            (0, h - cs, cs, h), (w - cs, h - cs, w, h)
        ]
        for (x1, y1, x2, y2) in corners:
            if cv2.countNonZero(combined[y1:y2, x1:x2]) > 500:
                watermarks.append({'bbox': (x1, y1, x2, y2), 'area': 0})
    
    print("[+] Found " + str(len(watermarks)) + " watermark(s)")
    return watermarks


def remove_and_split(input_path, output_dir, watermarks, method='blur', part_duration=300):
    """Remove watermark DAN split per part"""
    
    cap = cv2.VideoCapture(input_path)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = cap.get(cv2.CAP_PROP_FPS)
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration = total / fps if fps > 0 else 0
    cap.release()
    
    print("[*] Video: " + str(width) + "x" + str(height) + " @ " + str(round(fps, 1)) + "fps")
    print("[*] Duration: " + str(round(duration, 1)) + "s")
    
    # Hitung jumlah part
    num_parts = int(np.ceil(duration / part_duration))
    frames_per_part = int(part_duration * fps)
    
    print("[*] Will create " + str(num_parts) + " parts")
    
    # Buat mask
    mask = np.zeros((height, width), dtype=np.uint8)
    for wm in watermarks:
        x1, y1, x2, y2 = wm['bbox']
        mask[y1:y2, x1:x2] = 255
    kernel = np.ones((10, 10), np.uint8)
    mask = cv2.dilate(mask, kernel, iterations=1)
    
    # Buka video
    cap = cv2.VideoCapture(input_path)
    
    part_num = 1
    frame_in_part = 0
    temp_video = None
    out = None
    start_time = time.time()
    
    # Nama base dari filename
    base_name = os.path.splitext(os.path.basename(input_path))[0]
    base_name = re.sub(r'[^a-zA-Z0-9_\-]', '_', base_name)
    
    while True:
        ret, frame = cap.read()
        if not ret:
            break
        
        # Mulai part baru
        if frame_in_part == 0:
            if temp_video and os.path.exists(temp_video):
                # Merge audio part sebelumnya
                final_path = os.path.join(output_dir, base_name + "_part" + str(part_num-1).zfill(3) + "_no_wm.mp4")
                if has_audio(input_path):
                    # Simpan temp untuk merge
                    pass
                if os.path.exists(final_path):
                    size = os.path.getsize(final_path) / (1024 * 1024)
                    print("    Part " + str(part_num-1) + ": " + str(round(size, 1)) + " MB")
            
            temp_video = tempfile.mktemp(suffix='_noaudio.mp4')
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            out = cv2.VideoWriter(temp_video, fourcc, fps, (width, height))
        
        # Remove watermark
        if method == 'blur':
            for wm in watermarks:
                x1, y1, x2, y2 = wm['bbox']
                roi = frame[y1:y2, x1:x2]
                if roi.size > 0:
                    frame[y1:y2, x1:x2] = cv2.GaussianBlur(roi, (25, 25), 0)
            result = frame
        elif method == 'inpaint':
            result = cv2.inpaint(frame, mask, 5, cv2.INPAINT_TELEA)
        else:
            result = frame
        
        out.write(result)
        frame_in_part += 1
        
        # Part selesai
        if frame_in_part >= frames_per_part:
            out.release()
            
            # Merge audio
            final_path = os.path.join(output_dir, base_name + "_part" + str(part_num).zfill(3) + "_no_wm.mp4")
            
            if has_audio(input_path):
                # Ambil audio segment
                start_sec = (part_num - 1) * part_sec
                cmd = [
                    'ffmpeg', '-i', temp_video,
                    '-ss', str(start_sec), '-t', str(part_duration),
                    '-i', input_path,
                    '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
                    '-map', '0:v:0', '-map', '1:a:0?', '-shortest',
                    '-movflags', '+faststart',
                    final_path, '-y', '-loglevel', 'error'
                ]
                subprocess.run(cmd, capture_output=True, timeout=300)
                if os.path.exists(temp_video):
                    os.remove(temp_video)
            else:
                shutil.move(temp_video, final_path)
            
            if os.path.exists(final_path):
                size = os.path.getsize(final_path) / (1024 * 1024)
                print("    Part " + str(part_num) + ": " + str(round(size, 1)) + " MB")
            
            part_num += 1
            frame_in_part = 0
            temp_video = None
            out = None
    
    # Handle part terakhir
    if out is not None:
        out.release()
        final_path = os.path.join(output_dir, base_name + "_part" + str(part_num).zfill(3) + "_no_wm.mp4")
        
        if temp_video and os.path.exists(temp_video):
            shutil.move(temp_video, final_path)
            size = os.path.getsize(final_path) / (1024 * 1024)
            print("    Part " + str(part_num) + ": " + str(round(size, 1)) + " MB")
    
    cap.release()
    elapsed = time.time() - start_time
    print("[+] Complete in " + str(round(elapsed, 1)) + "s")


# Fix variable
part_sec = 300


def main():
    global part_sec
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True)
    parser.add_argument('--output-dir', default='output')
    parser.add_argument('--method', default='blur')
    parser.add_argument('--samples', type=int, default=30)
    parser.add_argument('--part-duration', type=int, default=300)
    args = parser.parse_args()
    
    part_sec = args.part_duration
    os.makedirs(args.output_dir, exist_ok=True)
    
    watermarks = detect_watermarks(args.input, args.samples)
    if watermarks:
        remove_and_split(args.input, args.output_dir, watermarks, args.method, args.part_duration)
        print("[+] Output dir: " + args.output_dir)


if __name__ == '__main__':
    main()

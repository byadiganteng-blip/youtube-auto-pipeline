#!/usr/bin/env python3
"""Watermark Remover + Split + Resize + Quality"""

import os
import sys
import argparse
import time
import subprocess
import tempfile
import shutil


VIDEO_PRESETS = {
    'original': None,
    'yt_shorts': (1080, 1920),
    'tiktok': (1080, 1920),
    'ig_reels': (1080, 1920),
    'fb_reels': (1080, 1920),
    'whatsapp_status': (1080, 1920),
    'ig_feed_square': (1080, 1080),
    'ig_feed_portrait': (1080, 1350),
    'yt_landscape': (1920, 1080),
    'yt_4k': (3840, 2160),
    'fb_video': (1920, 1080),
    'twitter': (1280, 720),
}

VIDEO_QUALITY = {
    'original': None,
    '144p': {'width': 256, 'height': 144, 'bitrate': '100k'},
    '240p': {'width': 426, 'height': 240, 'bitrate': '300k'},
    '360p': {'width': 640, 'height': 360, 'bitrate': '500k'},
    '480p': {'width': 854, 'height': 480, 'bitrate': '1000k'},
    '720p': {'width': 1280, 'height': 720, 'bitrate': '2500k'},
    '1080p': {'width': 1920, 'height': 1080, 'bitrate': '5000k'},
    '1440p': {'width': 2560, 'height': 1440, 'bitrate': '10000k'},
    '2160p': {'width': 3840, 'height': 2160, 'bitrate': '20000k'},
}


def get_preset_size(preset_name):
    return VIDEO_PRESETS.get(preset_name)


def get_quality_size(quality_name):
    return VIDEO_QUALITY.get(quality_name)


def build_vf_filter(preset_name, quality_name):
    target_w = None
    target_h = None
    quality = get_quality_size(quality_name)
    if quality:
        target_w = quality['width']
        target_h = quality['height']
    else:
        preset = get_preset_size(preset_name)
        if preset:
            target_w, target_h = preset

    if target_w is None or target_h is None:
        return None

    return (
        "scale=" + str(target_w) + ":" + str(target_h) +
        ":force_original_aspect_ratio=decrease,"
        "pad=" + str(target_w) + ":" + str(target_h) +
        ":(ow-iw)/2:(oh-ih)/2:black,"
        "setsar=1"
    )


def get_duration(path):
    try:
        result = subprocess.run(
            ['ffprobe', '-v', 'error', '-show_entries', 'format=duration',
             '-of', 'default=noprint_wrappers=1:nokey=1', path],
            capture_output=True, text=True, timeout=15
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


def split_only(input_path, output_dir, part_duration=300, preset='original', quality='original'):
    print("[*] Mode: SKIP WATERMARK")
    print("[*] Preset: " + preset)
    print("[*] Quality: " + quality)

    vf = build_vf_filter(preset, quality)
    quality_info = get_quality_size(quality)

    duration = get_duration(input_path)
    if duration <= 0:
        sys.exit(1)

    num_parts = int((duration + part_duration - 1) // part_duration)
    print("[*] Will create " + str(num_parts) + " parts")

    base_name = os.path.splitext(os.path.basename(input_path))[0]
    clean_name = ""
    for ch in base_name:
        clean_name += ch if (ch.isalnum() or ch in '_-') else '_'
    base_name = clean_name

    start_time = time.time()

    for part_idx in range(1, num_parts + 1):
        start_sec = (part_idx - 1) * part_duration
        final_path = os.path.join(output_dir, base_name + "_part" + str(part_idx).zfill(3) + "_no_wm.mp4")

        print("[*] Part " + str(part_idx) + "/" + str(num_parts))

        if vf:
            cmd = ['ffmpeg', '-ss', str(start_sec), '-i', input_path,
                   '-t', str(part_duration), '-vf', vf]
            if quality_info:
                cmd.extend(['-b:v', quality_info['bitrate']])
            cmd.extend(['-c:v', 'libx264', '-preset', 'fast',
                       '-c:a', 'aac', '-b:a', '192k',
                       '-movflags', '+faststart',
                       final_path, '-y', '-loglevel', 'error'])
        else:
            cmd = ['ffmpeg', '-ss', str(start_sec), '-i', input_path,
                   '-t', str(part_duration), '-c', 'copy',
                   '-avoid_negative_ts', 'make_zero',
                   '-movflags', '+faststart',
                   final_path, '-y', '-loglevel', 'warning']

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=1800)

        if result.returncode == 0 and os.path.exists(final_path):
            size = os.path.getsize(final_path) / (1024 * 1024)
            print("    [+] Part " + str(part_idx) + ": " + str(round(size, 1)) + " MB")

    print("[+] Complete in " + str(round(time.time() - start_time, 1)) + "s")


def detect_watermarks(path, samples=30):
    import cv2
    import numpy as np
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

    combined = cv2.bitwise_or(static, stable)
    combined = cv2.dilate(combined, np.ones((15, 15), np.uint8), iterations=2)

    contours, _ = cv2.findContours(combined, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    watermarks = []
    for cnt in contours:
        area = cv2.contourArea(cnt)
        if 200 < area < w * h * 0.15:
            x, y, cw, ch = cv2.boundingRect(cnt)
            watermarks.append({'bbox': (x, y, x + cw, y + ch)})

    print("[+] Found " + str(len(watermarks)) + " watermark(s)")
    return watermarks


def remove_and_split(input_path, output_dir, watermarks, method='blur',
                     part_duration=300, preset='original', quality='original'):
    import cv2
    import numpy as np

    print("[*] Mode: REMOVE WATERMARK")
    print("[*] Method: " + method)
    print("[*] Preset: " + preset)
    print("[*] Quality: " + quality)

    cap = cv2.VideoCapture(input_path)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = cap.get(cv2.CAP_PROP_FPS)
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration = total / fps if fps > 0 else 0
    cap.release()

    num_parts = int((duration + part_duration - 1) // part_duration)
    frames_per_part = int(part_duration * fps)
    print("[*] Will create " + str(num_parts) + " parts")

    mask = np.zeros((height, width), dtype=np.uint8)
    for wm in watermarks:
        x1, y1, x2, y2 = wm['bbox']
        mask[y1:y2, x1:x2] = 255
    mask = cv2.dilate(mask, np.ones((10, 10), np.uint8), iterations=1)

    cap = cv2.VideoCapture(input_path)
    part_num = 1
    frame_in_part = 0
    temp_video = None
    out = None
    start_time = time.time()
    audio_ok = has_audio(input_path)

    vf = build_vf_filter(preset, quality)
    quality_info = get_quality_size(quality)

    base_name = os.path.splitext(os.path.basename(input_path))[0]
    clean_name = ""
    for ch in base_name:
        clean_name += ch if (ch.isalnum() or ch in '_-') else '_'
    base_name = clean_name

    def finalize_part(part_idx, temp_vid):
        final_path = os.path.join(output_dir, base_name + "_part" + str(part_idx).zfill(3) + "_no_wm.mp4")
        if vf:
            cmd = ['ffmpeg', '-i', temp_vid, '-vf', vf]
            if quality_info:
                cmd.extend(['-b:v', quality_info['bitrate']])
            if audio_ok:
                cmd.extend(['-c:v', 'libx264', '-preset', 'fast',
                           '-c:a', 'aac', '-b:a', '192k',
                           '-movflags', '+faststart',
                           final_path, '-y', '-loglevel', 'error'])
            else:
                cmd.extend(['-c:v', 'libx264', '-preset', 'fast',
                           '-an', '-movflags', '+faststart',
                           final_path, '-y', '-loglevel', 'error'])
            subprocess.run(cmd, capture_output=True, timeout=1800)
        else:
            if audio_ok:
                start_sec = (part_idx - 1) * part_duration
                cmd = ['ffmpeg', '-ss', str(start_sec), '-i', temp_vid,
                       '-t', str(part_duration), '-i', input_path,
                       '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
                       '-map', '0:v:0', '-map', '1:a:0?', '-shortest',
                       '-movflags', '+faststart',
                       final_path, '-y', '-loglevel', 'error']
                subprocess.run(cmd, capture_output=True, timeout=600)
            else:
                shutil.move(temp_vid, final_path)

        if os.path.exists(temp_vid):
            os.remove(temp_vid)

        if os.path.exists(final_path):
            size = os.path.getsize(final_path) / (1024 * 1024)
            print("    [+] Part " + str(part_idx) + ": " + str(round(size, 1)) + " MB")

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        if frame_in_part == 0:
            temp_video = tempfile.mktemp(suffix='_noaudio.mp4')
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            out = cv2.VideoWriter(temp_video, fourcc, fps, (width, height))

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

        if frame_in_part >= frames_per_part:
            out.release()
            finalize_part(part_num, temp_video)
            part_num += 1
            frame_in_part = 0
            temp_video = None
            out = None

    if out is not None:
        out.release()
        if temp_video and os.path.exists(temp_video):
            finalize_part(part_num, temp_video)

    cap.release()
    print("[+] Complete in " + str(round(time.time() - start_time, 1)) + "s")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True)
    parser.add_argument('--output-dir', default='output')
    parser.add_argument('--method', default='blur')
    parser.add_argument('--samples', type=int, default=30)
    parser.add_argument('--part-duration', type=int, default=300)
    parser.add_argument('--process-mode', default='remove_watermark',
                        choices=['remove_watermark', 'skip_watermark'])
    parser.add_argument('--video-size', default='original',
                        choices=list(VIDEO_PRESETS.keys()))
    parser.add_argument('--video-quality', default='original',
                        choices=list(VIDEO_QUALITY.keys()))
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)
    if not os.path.exists(args.input):
        sys.exit(1)

    print("[*] Video size: " + args.video_size)
    print("[*] Video quality: " + args.video_quality)

    if args.process_mode == 'skip_watermark':
        split_only(args.input, args.output_dir, args.part_duration,
                   args.video_size, args.video_quality)
    else:
        watermarks = detect_watermarks(args.input, args.samples)
        remove_and_split(args.input, args.output_dir, watermarks,
                        args.method, args.part_duration,
                        args.video_size, args.video_quality)

    out_files = [f for f in os.listdir(args.output_dir) if f.endswith('.mp4')]
    print("[*] Total output: " + str(len(out_files)))


if __name__ == '__main__':
    main()

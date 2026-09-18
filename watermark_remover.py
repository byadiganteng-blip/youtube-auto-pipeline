#!/usr/bin/env python3
"""
watermark_remover.py
Split video per part + optional watermark removal.

Author: YsDev
"""
import argparse
import os
import subprocess
import sys
import shutil
import glob


def split_video(input_path, output_dir, part_duration, start_part=1, quality=None, size=None):
    """Split video per part menggunakan ffmpeg."""
    os.makedirs(output_dir, exist_ok=True)

    # Get video duration
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", input_path],
            capture_output=True, text=True, timeout=30
        )
        total_duration = float(result.stdout.strip())
    except Exception as e:
        print(f"⚠️  ffprobe failed: {e}, using default")
        total_duration = 0

    if total_duration <= 0:
        print(f"⚠️  Cannot detect duration, skipping split")
        # Copy original as part 1
        out = os.path.join(output_dir, "video_part001_no_wm.mp4")
        shutil.copy(input_path, out)
        print(f"✅ Copied: {out}")
        return

    total_parts = int(total_duration / part_duration) + (1 if total_duration % part_duration else 0)
    print(f"📊 Video duration: {total_duration:.1f}s → {total_parts} parts @ {part_duration}s each")
    print(f"📊 Starting from part: {start_part}")

    for i in range(start_part, total_parts + 1):
        start_time = (i - 1) * part_duration
        out_file = os.path.join(output_dir, f"video_part{i:03d}_no_wm.mp4")

        # Skip kalau sudah ada
        if os.path.exists(out_file):
            print(f"⏭️  Part {i}: already exists, skip")
            continue

        cmd = [
            "ffmpeg", "-y",
            "-ss", str(start_time),
            "-i", input_path,
            "-t", str(part_duration),
            "-c", "copy",
            "-avoid_negative_ts", "make_zero",
            out_file
        ]
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            if result.returncode != 0:
                print(f"⚠️  Part {i}: ffmpeg error, using re-encode")
                # Fallback re-encode
                cmd2 = [
                    "ffmpeg", "-y",
                    "-ss", str(start_time),
                    "-i", input_path,
                    "-t", str(part_duration),
                    "-c:v", "libx264", "-preset", "ultrafast",
                    "-c:a", "aac",
                    out_file
                ]
                subprocess.run(cmd2, capture_output=True, text=True, timeout=300)
            print(f"✅ Part {i}: {os.path.getsize(out_file) if os.path.exists(out_file) else 0} bytes")
        except Exception as e:
            print(f"❌ Part {i} failed: {e}")


def remove_watermark(input_path, method="blur"):
    """Optional — placeholder untuk watermark removal.
    Return input_path unchanged kalau tidak ada OpenCV watermark detection."""
    # Untuk sementara: skip removal, langsung return path
    # (bisa di-extend nanti dengan OpenCV)
    print(f"   Watermark method: {method} (skipped — pass-through)")
    return input_path


def main():
    parser = argparse.ArgumentParser(
        description="Split video per part + optional watermark removal"
    )
    parser.add_argument("--input", required=True, help="Input video path")
    parser.add_argument("--output-dir", required=True, help="Output directory")
    parser.add_argument("--method", default="blur", choices=["blur", "inpaint"],
                        help="Watermark removal method")
    parser.add_argument("--part-duration", type=int, default=60,
                        help="Duration per part in seconds")
    parser.add_argument("--samples", type=int, default=30,
                        help="Sample frames (unused, kept for compat)")
    parser.add_argument("--process-mode", default="remove_watermark",
                        choices=["remove_watermark", "skip_watermark"],
                        help="Process mode")
    parser.add_argument("--video-size", default="original",
                        help="Video size preset")
    parser.add_argument("--video-quality", default="original",
                        help="Video quality")
    parser.add_argument("--start-part", type=int, default=1,
                        help="Start from part N (for resume)")

    args = parser.parse_args()

    print("=" * 60)
    print("🎬 WATERMARK REMOVER + SPLITTER")
    print("=" * 60)
    print(f"   Input:         {args.input}")
    print(f"   Output dir:    {args.output_dir}")
    print(f"   Method:        {args.method}")
    print(f"   Process mode:  {args.process_mode}")
    print(f"   Part duration: {args.part_duration}s")
    print(f"   Video size:    {args.video_size}")
    print(f"   Video quality: {args.video_quality}")
    print(f"   Start part:    {args.start_part}")
    print("=" * 60)

    if not os.path.exists(args.input):
        print(f"❌ Input file not found: {args.input}")
        sys.exit(1)

    input_size = os.path.getsize(args.input)
    print(f"📦 Input size: {input_size / 1024 / 1024:.1f} MB")

    # Step 1: optional watermark removal
    if args.process_mode == "remove_watermark":
        processed = remove_watermark(args.input, args.method)
    else:
        print(f"   Skipping watermark removal")
        processed = args.input

    # Step 2: split video
    print("\n✂️  Splitting video...")
    split_video(
        input_path=processed,
        output_dir=args.output_dir,
        part_duration=args.part_duration,
        start_part=args.start_part,
        quality=args.video_quality,
        size=args.video_size
    )

    # Step 3: list output
    print("\n📋 Output files:")
    files = sorted(glob.glob(os.path.join(args.output_dir, "*.mp4")))
    for f in files:
        size = os.path.getsize(f)
        print(f"   ✅ {os.path.basename(f)} — {size / 1024 / 1024:.1f} MB")

    if not files:
        print("❌ No output files generated!")
        sys.exit(1)

    print(f"\n✅ Done. {len(files)} parts generated.")


if __name__ == "__main__":
    main()

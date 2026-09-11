#!/usr/bin/env python3
import os, sys, argparse, subprocess, tempfile, shutil

PRESETS = {'original':None,'yt_shorts':(1080,1920),'tiktok':(1080,1920),'ig_reels':(1080,1920),
    'fb_reels':(1080,1920),'whatsapp_status':(1080,1920),'ig_feed_square':(1080,1080),
    'ig_feed_portrait':(1080,1350),'yt_landscape':(1920,1080),'yt_4k':(3840,2160),
    'fb_video':(1920,1080),'twitter':(1280,720)}
QUALITY = {'original':None,'144p':{'w':256,'h':144,'b':'100k'},'240p':{'w':426,'h':240,'b':'300k'},
    '360p':{'w':640,'h':360,'b':'500k'},'480p':{'w':854,'h':480,'b':'1000k'},
    '720p':{'w':1280,'h':720,'b':'2500k'},'1080p':{'w':1920,'h':1080,'b':'5000k'},
    '1440p':{'w':2560,'h':1440,'b':'10000k'},'2160p':{'w':3840,'h':2160,'b':'20000k'}}

def vf_filter(p, q):
    w = h = None
    qi = QUALITY.get(q)
    if qi: w, h = qi['w'], qi['h']
    else:
        ps = PRESETS.get(p)
        if ps: w, h = ps
    if w is None: return None
    return f"scale={w}:{h}:force_original_aspect_ratio=decrease,pad={w}:{h}:(ow-iw)/2:(oh-ih)/2:black,setsar=1"

def dur(path):
    try:
        r = subprocess.run(['ffprobe','-v','error','-show_entries','format=duration','-of','default=noprint_wrappers=1:nokey=1',path],capture_output=True,text=True,timeout=15)
        return float(r.stdout.strip())
    except: return 0

def has_audio(path):
    try:
        r = subprocess.run(['ffprobe','-v','error','-select_streams','a:0','-show_entries','stream=codec_type','-of','default=noprint_wrappers=1:nokey=1',path],capture_output=True,text=True,timeout=10)
        return 'audio' in r.stdout.lower()
    except: return False

def split_only(inp, outd, pd=300, preset='original', q='original'):
    print("[*] Mode: SKIP WATERMARK")
    vf = vf_filter(preset, q)
    qi = QUALITY.get(q)
    d = dur(inp)
    if d <= 0: sys.exit(1)
    n = int((d + pd - 1) // pd)
    print(f"[*] Will create {n} parts")
    bn = "".join(c if (c.isalnum() or c in '_-') else '_' for c in os.path.splitext(os.path.basename(inp))[0])
    for i in range(1, n+1):
        ss = (i-1) * pd
        fp = os.path.join(outd, f"{bn}_part{i:03d}_no_wm.mp4")
        print(f"[*] Part {i}/{n}")
        if vf:
            cmd = ['ffmpeg','-ss',str(ss),'-i',inp,'-t',str(pd),'-vf',vf]
            if qi: cmd += ['-b:v', qi['b']]
            cmd += ['-c:v','libx264','-preset','fast','-c:a','aac','-b:a','192k','-movflags','+faststart',fp,'-y','-loglevel','error']
        else:
            cmd = ['ffmpeg','-ss',str(ss),'-i',inp,'-t',str(pd),'-c','copy','-avoid_negative_ts','make_zero','-movflags','+faststart',fp,'-y','-loglevel','warning']
        subprocess.run(cmd, capture_output=True, timeout=1800)
        if os.path.exists(fp): print(f"    [+] {round(os.path.getsize(fp)/(1024*1024),1)} MB")

def detect_wm(path, samples=30):
    import cv2, numpy as np
    print("[*] Detecting watermarks...")
    cap = cv2.VideoCapture(path)
    t = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if t < 2: cap.release(); return []
    step = max(1, t // samples)
    frames = []
    for i in range(0, t, step):
        cap.set(cv2.CAP_PROP_POS_FRAMES, i)
        ret, fr = cap.read()
        if ret: frames.append(fr)
        if len(frames) >= samples: break
    cap.release()
    if len(frames) < 2: return []
    h, w = frames[0].shape[:2]
    gs = [cv2.cvtColor(f, cv2.COLOR_BGR2GRAY) for f in frames]
    var = np.var(np.stack(gs, axis=0), axis=0)
    st = (var < 30).astype(np.uint8) * 255
    e = [cv2.Canny(g, 50, 150) for g in gs]
    ev = np.var(np.stack(e, axis=0), axis=0)
    sb = (ev < 100).astype(np.uint8) * 255
    comb = cv2.bitwise_or(st, sb)
    comb = cv2.dilate(comb, np.ones((15,15),np.uint8), iterations=2)
    conts, _ = cv2.findContours(comb, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    wms = []
    for c in conts:
        a = cv2.contourArea(c)
        if 200 < a < w*h*0.15:
            x, y, cw, ch = cv2.boundingRect(c)
            wms.append({'bbox': (x,y,x+cw,y+ch)})
    print(f"[+] Found {len(wms)} watermark(s)")
    return wms

def remove_and_split(inp, outd, wms, method='blur', pd=300, preset='original', q='original'):
    import cv2, numpy as np
    print("[*] Mode: REMOVE WATERMARK")
    cap = cv2.VideoCapture(inp)
    w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)); h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = cap.get(cv2.CAP_PROP_FPS); t = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    d = t/fps if fps > 0 else 0
    cap.release()
    n = int((d + pd - 1) // pd); fpp = int(pd * fps)
    print(f"[*] Will create {n} parts")
    mask = np.zeros((h,w), dtype=np.uint8)
    for wm in wms:
        x1,y1,x2,y2 = wm['bbox']; mask[y1:y2,x1:x2] = 255
    mask = cv2.dilate(mask, np.ones((10,10),np.uint8), iterations=1)
    cap = cv2.VideoCapture(inp)
    pn = 1; fip = 0; tv = None; out = None
    ao = has_audio(inp)
    vf = vf_filter(preset, q); qi = QUALITY.get(q)
    bn = "".join(c if (c.isalnum() or c in '_-') else '_' for c in os.path.splitext(os.path.basename(inp))[0])

    def fin(pi, tv):
        fp = os.path.join(outd, f"{bn}_part{pi:03d}_no_wm.mp4")
        if vf:
            cmd = ['ffmpeg','-i',tv,'-vf',vf]
            if qi: cmd += ['-b:v', qi['b']]
            cmd += ['-c:v','libx264','-preset','fast','-c:a','aac','-b:a','192k','-movflags','+faststart',fp,'-y','-loglevel','error'] if ao else ['-c:v','libx264','-preset','fast','-an','-movflags','+faststart',fp,'-y','-loglevel','error']
            subprocess.run(cmd, capture_output=True, timeout=1800)
        else:
            if ao:
                ss = (pi-1)*pd
                cmd = ['ffmpeg','-ss',str(ss),'-i',tv,'-t',str(pd),'-i',inp,'-c:v','copy','-c:a','aac','-b:a','192k','-map','0:v:0','-map','1:a:0?','-shortest','-movflags','+faststart',fp,'-y','-loglevel','error']
                subprocess.run(cmd, capture_output=True, timeout=600)
            else: shutil.move(tv, fp)
        if os.path.exists(tv): os.remove(tv)
        if os.path.exists(fp): print(f"    [+] Part {pi}: {round(os.path.getsize(fp)/(1024*1024),1)} MB")

    while True:
        ret, fr = cap.read()
        if not ret: break
        if fip == 0:
            tv = tempfile.mktemp(suffix='_na.mp4')
            out = cv2.VideoWriter(tv, cv2.VideoWriter_fourcc(*'mp4v'), fps, (w,h))
        if method == 'blur':
            for wm in wms:
                x1,y1,x2,y2 = wm['bbox']
                roi = fr[y1:y2, x1:x2]
                if roi.size > 0: fr[y1:y2, x1:x2] = cv2.GaussianBlur(roi, (25,25), 0)
            res = fr
        elif method == 'inpaint': res = cv2.inpaint(fr, mask, 5, cv2.INPAINT_TELEA)
        else: res = fr
        out.write(res); fip += 1
        if fip >= fpp:
            out.release(); fin(pn, tv); pn += 1; fip = 0; tv = None; out = None
    if out is not None:
        out.release()
        if tv and os.path.exists(tv): fin(pn, tv)
    cap.release()

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--input', required=True); p.add_argument('--output-dir', default='output')
    p.add_argument('--method', default='blur'); p.add_argument('--samples', type=int, default=30)
    p.add_argument('--part-duration', type=int, default=300)
    p.add_argument('--process-mode', default='remove_watermark')
    p.add_argument('--video-size', default='original'); p.add_argument('--video-quality', default='original')
    a = p.parse_args()
    os.makedirs(a.output_dir, exist_ok=True)
    if a.process_mode == 'skip_watermark':
        split_only(a.input, a.output_dir, a.part_duration, a.video_size, a.video_quality)
    else:
        wms = detect_wm(a.input, a.samples)
        remove_and_split(a.input, a.output_dir, wms, a.method, a.part_duration, a.video_size, a.video_quality)

if __name__ == '__main__': main()

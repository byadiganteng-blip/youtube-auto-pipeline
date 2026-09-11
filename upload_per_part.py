#!/usr/bin/env python3
import os, sys, glob, pickle, random, time, re
from auto_hashtag import AutoHashtag
try:
    from google.auth.transport.requests import Request
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
except: sys.exit(1)

def svc():
    c = None
    if os.path.exists('token.pickle'):
        with open('token.pickle','rb') as f:
            try: c = pickle.load(f)
            except: pass
    if not c or not c.valid:
        if c and c.expired and c.refresh_token: c.refresh(Request())
    return build('youtube','v3',credentials=c)

def up(yt, path, title, desc, tags, priv='public'):
    body = {'snippet':{'title':title,'description':desc,'tags':[t.strip('#') for t in tags.split()],'categoryId':'20'},
            'status':{'privacyStatus':priv,'selfDeclaredMadeForKids':False}}
    m = MediaFileUpload(path, chunksize=-1, resumable=True)
    r = yt.videos().insert(part=','.join(body.keys()),body=body,media_body=m)
    resp = None
    while resp is None:
        time.sleep(random.uniform(0.5,2.0))
        s, resp = r.next_chunk()
        if s: print(f"    Upload: {int(s.progress()*100)}%")
    return resp['id']

def pn(f):
    for p in ['part[-_ ]*([0-9]+)','_part([0-9]+)','part([0-9]+)']:
        m = re.search(p, f.lower())
        if m:
            try: return int(m.group(1))
            except: pass
    return None

def main():
    import argparse
    p = argparse.ArgumentParser()
    p.add_argument('--input-dir', default='output'); p.add_argument('--privacy', default='public')
    p.add_argument('--start-part', type=int, default=1); p.add_argument('--delay', type=int, default=60)
    p.add_argument('--upload-type', default='video')
    a = p.parse_args()
    vids = sorted(glob.glob(os.path.join(a.input_dir, "*.mp4")))
    if not vids: return
    print(f"[*] Found {len(vids)} videos")
    g = AutoHashtag(); yt = svc()
    ups = []
    for i, vp in enumerate(vids, 1):
        fn = os.path.basename(vp); pn_ = pn(fn) or i
        if pn_ < a.start_part: continue
        print(f"\nUpload {i}/{len(vids)}: {fn}")
        time.sleep(random.uniform(5.0,15.0))
        t = g.generate_title(fn, a.upload_type)
        h = g.generate(fn, t, upload_type=a.upload_type)
        d = g.generate_description(fn, t, h, a.upload_type)
        try:
            v = up(yt, vp, t, d, h, a.privacy)
            if v: print(f"[+] Uploaded: https://youtu.be/{v}"); ups.append(v)
        except Exception as e: print(f"[!] {e}")
        if i < len(vids): time.sleep(a.delay)
    print(f"\nCOMPLETE - {len(ups)} uploaded")

if __name__ == '__main__': main()

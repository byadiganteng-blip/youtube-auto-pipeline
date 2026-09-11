#!/usr/bin/env python3
import time, random, requests
class AntiDetectBot:
    UA = ["Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0", "Mozilla/5.0 (Macintosh; Intel Mac OS X) Safari/605.1.15"]
    def __init__(self, use_proxy=False, proxy_list=None):
        self.session = requests.Session(); self._r()
    def _r(self):
        self.session.headers.update({'User-Agent': random.choice(self.UA), 'Accept': 'text/html,*/*;q=0.8'})
    def get(self, url, **kwargs):
        self._r(); time.sleep(random.uniform(2, 8))
        try: return self.session.get(url, **kwargs)
        except: return None

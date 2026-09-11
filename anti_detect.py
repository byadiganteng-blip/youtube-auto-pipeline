#!/usr/bin/env python3
import time, random, requests
class AntiDetectBot:
    USER_AGENTS = ["Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"]
    def __init__(self, use_proxy=False, proxy_list=None):
        self.session = requests.Session()
        self._rotate_headers()
    def _rotate_headers(self):
        self.session.headers.update({'User-Agent': random.choice(self.USER_AGENTS),
            'Accept': 'text/html,*/*;q=0.8', 'Accept-Language': 'en-US,en;q=0.9'})
    def _random_delay(self, min_sec=2.0, max_sec=8.0): time.sleep(random.uniform(min_sec, max_sec))
    def get(self, url, **kwargs):
        self._rotate_headers(); self._random_delay()
        try: return self.session.get(url, **kwargs)
        except: return None

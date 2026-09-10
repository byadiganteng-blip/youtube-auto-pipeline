#!/usr/bin/env python3
"""Anti-Detect Bot Module"""

import time
import random
import string
import hashlib
import requests


class AntiDetectBot:
    USER_AGENTS = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
    ]
    ACCEPT_HEADERS = [
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    ]
    LANGUAGES = [
        "en-US,en;q=0.9",
        "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
    ]
    TIMEZONES = ["Asia/Jakarta", "Asia/Singapore", "America/New_York", "Europe/London"]

    def __init__(self, use_proxy=False, proxy_list=None):
        self.use_proxy = use_proxy
        self.proxy_list = proxy_list or []
        self.session = None
        self.fingerprint = None
        self._init_session()
        self._generate_fingerprint()

    def _init_session(self):
        self.session = requests.Session()
        self._rotate_headers()

    def _generate_fingerprint(self):
        self.fingerprint = {
            'user_agent': random.choice(self.USER_AGENTS),
            'screen_resolution': random.choice(['1920x1080', '1366x768', '2560x1440']),
            'timezone': random.choice(self.TIMEZONES),
            'language': random.choice(self.LANGUAGES),
            'platform': random.choice(['Win32', 'MacIntel', 'Linux x86_64']),
            'hardware_concurrency': random.choice([4, 8, 12, 16]),
            'device_memory': random.choice([4, 8, 16, 32]),
        }

    def _rotate_headers(self):
        self.session.headers.update({
            'User-Agent': random.choice(self.USER_AGENTS),
            'Accept': random.choice(self.ACCEPT_HEADERS),
            'Accept-Language': random.choice(self.LANGUAGES),
            'Accept-Encoding': 'gzip, deflate, br',
            'DNT': '1',
            'Connection': 'keep-alive',
        })

    def _random_delay(self, min_sec=2.0, max_sec=8.0):
        time.sleep(random.uniform(min_sec, max_sec))

    def get(self, url, **kwargs):
        self._rotate_headers()
        self._random_delay()
        try:
            return self.session.get(url, **kwargs)
        except Exception as e:
            print("[!] Request error: " + str(e))
            return None


if __name__ == '__main__':
    bot = AntiDetectBot()
    for k, v in bot.fingerprint.items():
        print("  " + k + ": " + str(v))

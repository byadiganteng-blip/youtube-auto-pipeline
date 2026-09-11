#!/usr/bin/env python3
"""Anti-Detect Bot Module"""

import time
import random
import requests


class AntiDetectBot:
    USER_AGENTS = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
    ]
    ACCEPT_HEADERS = [
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    ]
    LANGUAGES = ["en-US,en;q=0.9", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"]

    def __init__(self, use_proxy=False, proxy_list=None):
        self.session = requests.Session()
        self._rotate_headers()

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
    print("AntiDetectBot initialized")

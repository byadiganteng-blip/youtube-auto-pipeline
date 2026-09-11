#!/usr/bin/env python3
import random, re
from content_detector import ContentDetector
class AutoHashtag:
    def __init__(self):
        self.detector = ContentDetector()
        self.H = {
            'viral': ["#FYP","#Viral","#Trending","#Shorts"],
            'anime': ["#Anime","#AnimeViral"], 'manhwa': ["#Manhwa","#SoloLeveling"],
            'gaming': ["#Gaming","#Gamer"], 'mlbb': ["#MLBB"],
            'engagement': ["#Subscribe","#Like"], 'reels': ["#Reels","#ShortsViral"],
        }
    def generate(self, filename, title="", count=15, upload_type="video"):
        tags = list(random.sample(self.H['viral'], 3))
        if upload_type == "reels": tags += self.H['reels']
        tags += self.H['gaming'] + self.H['engagement']
        return ' '.join(list(dict.fromkeys(tags))[:count])
    def _pn(self, f):
        for p in ['part[-_ ]*([0-9]+)', '_([0-9]+)[.]mp4$']:
            m = re.search(p, f.lower())
            if m:
                try: return int(m.group(1))
                except: pass
        return None
    def generate_title(self, filename, upload_type="video"):
        pn = self._pn(filename)
        pre = "#Shorts " if upload_type == "reels" else ""
        base = random.choice(["Gameplay Epic!","Viral Moment!","Highlight Terbaik!"])
        return f"{pre}PART {pn or 1} - {base}"
    def generate_description(self, filename, title, h, upload_type="video"):
        pn = self._pn(filename)
        return f"{title}\n\nPART {pn or 1}\n\nSubscribe!\n\n{h}\n\nCreated By KARYADI"

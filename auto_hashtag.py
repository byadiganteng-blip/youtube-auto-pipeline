#!/usr/bin/env python3
import sys, random, re
from content_detector import ContentDetector

class AutoHashtag:
    def __init__(self):
        self.detector = ContentDetector()
        self.hashtags = {
            'viral': ["#FYP", "#Viral", "#Trending", "#Shorts"],
            'anime': ["#Anime", "#AnimeViral", "#Otaku", "#Wibu"],
            'manhwa': ["#Manhwa", "#Webtoon", "#SoloLeveling"],
            'manga': ["#Manga"],
            'gaming': ["#Gaming", "#Gamer", "#GamingIndonesia"],
            'mlbb': ["#MLBB", "#MobileLegends"],
            'freefire': ["#FreeFire", "#FFIndonesia"],
            'topup': ["#TopUpGame", "#TopUpMurah"],
            'engagement': ["#Subscribe", "#Like", "#Comment"],
            'indonesia': ["#Indonesia", "#GamersID"],
            'reels': ["#Reels", "#ReelsVideo", "#ReelsFYP"],
        }
        self.branded = ["#YadStore"]

    def generate(self, filename, title="", count=15, upload_type="video"):
        categories = self.detector.detect_multiple(filename + " " + title, max_cat=3)
        if not categories: categories = ['gaming']
        tags = list(random.sample(self.hashtags['viral'], 4))
        if upload_type == "reels": tags.extend(random.sample(self.hashtags['reels'], 3))
        primary = categories[0]
        if primary in self.hashtags:
            avail = [t for t in self.hashtags[primary] if t not in tags]
            tags.extend(random.sample(avail, min(7, len(avail))))
        tags.extend(random.sample(self.hashtags['engagement'], 2))
        tags.extend(random.sample(self.hashtags['indonesia'], 2))
        tags.append(random.choice(self.branded))
        tags = list(dict.fromkeys(tags))
        return ' '.join(tags[:count])

    def _extract_part_number(self, filename):
        for p in ['part[-_ ]*([0-9]+)', '_([0-9]+)[.]mp4$']:
            m = re.search(p, filename.lower())
            if m:
                try: return int(m.group(1))
                except: pass
        return None

    def generate_title(self, filename, upload_type="video"):
        part_num = self._extract_part_number(filename)
        prefix = "#Shorts " if upload_type == "reels" else ""
        base = random.choice(["Gameplay Epic!", "Viral Moment!", "Highlight Terbaik!"])
        return (prefix + "PART " + str(part_num or 1) + " - " + base)

    def generate_description(self, filename, title, hashtags, upload_type="video"):
        part_num = self._extract_part_number(filename)
        return title + "\n\nPART " + str(part_num or 1) + "\n\nSubscribe!\n\n" + hashtags + "\n\nCreated By Yad"

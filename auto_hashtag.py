#!/usr/bin/env python3
"""Auto Hashtag"""

import sys
import random
import re
from content_detector import ContentDetector


class AutoHashtag:
    def __init__(self):
        self.detector = ContentDetector()
        self.hashtags = {
            'viral': ["#FYP", "#Viral", "#Trending", "#Shorts", "#YouTubeShorts",
                     "#ShortsViral", "#ForYouPage", "#Explore", "#ViralIndonesia"],
            'anime': ["#Anime", "#AnimeViral", "#AnimeEdit", "#AnimeIndonesia",
                     "#Otaku", "#Wibu", "#JujutsuKaisen", "#DemonSlayer"],
            'manhwa': ["#Manhwa", "#ManhwaViral", "#Webtoon", "#SoloLeveling", "#TBATE"],
            'manga': ["#Manga", "#MangaViral"],
            'donghua': ["#Donghua", "#SoulLand", "#BTTH"],
            'manhua': ["#Manhua", "#ManhuaViral"],
            'gaming': ["#Gaming", "#Gamer", "#GamingIndonesia", "#GameShorts"],
            'mlbb': ["#MLBB", "#MobileLegends", "#DiamondMLBB"],
            'freefire': ["#FreeFire", "#FFIndonesia", "#FreeFireBooyah"],
            'pubg': ["#PUBG", "#PUBGIndonesia"],
            'genshin': ["#GenshinImpact", "#GenshinIndonesia"],
            'valorant': ["#Valorant", "#ValorantIndonesia"],
            'topup': ["#TopUpGame", "#TopUpMurah", "#DiamondMurah"],
            'engagement': ["#Subscribe", "#Like", "#Comment", "#Share"],
            'indonesia': ["#Indonesia", "#GamersID", "#KreatorIndonesia"],
            'reels': ["#Reels", "#ReelsVideo", "#ReelsIndonesia", "#ReelsFYP"],
        }
        self.branded = ["#YadStore", "#YadGaming"]

    def generate(self, filename, title="", count=15, upload_type="video"):
        categories = self.detector.detect_multiple(filename + " " + title, max_cat=3)
        if not categories: categories = ['gaming']
        print("[*] Detected: " + str(categories))

        tags = list(random.sample(self.hashtags['viral'], 4))
        if upload_type == "reels":
            tags.extend(random.sample(self.hashtags['reels'], 3))

        primary = categories[0]
        if primary in self.hashtags:
            avail = [t for t in self.hashtags[primary] if t not in tags]
            tags.extend(random.sample(avail, min(7, len(avail))))

        for cat in categories[1:]:
            if cat in self.hashtags and len(tags) < 14:
                avail = [t for t in self.hashtags[cat] if t not in tags]
                if avail:
                    tags.extend(random.sample(avail, min(3, len(avail))))

        tags.extend(random.sample(self.hashtags['engagement'], 2))
        tags.extend(random.sample(self.hashtags['indonesia'], 2))
        tags.append(random.choice(self.branded))
        tags = list(dict.fromkeys(tags))
        return ' '.join(tags[:count])

    def _extract_part_number(self, filename):
        patterns = ['part[-_ ]*([0-9]+)', 'episode[-_ ]*([0-9]+)',
                    'ep[-_ ]*([0-9]+)', '_([0-9]+)[.]mp4$']
        text = filename.lower()
        for p in patterns:
            m = re.search(p, text)
            if m:
                try: return int(m.group(1))
                except: pass
        return None

    def generate_title(self, filename, upload_type="video"):
        part_num = self._extract_part_number(filename)
        cats = self.detector.detect_multiple(filename, max_cat=1)
        cat = cats[0] if cats else 'gaming'
        titles = {
            'anime': ["Anime Edit Viral!", "Momen Epic Anime!"],
            'manhwa': ["Manhwa Recommendation!", "Manhwa Panel Epic!"],
            'manga': ["Manga Panel Epic!"],
            'donghua': ["Donghua Viral!", "Donghua Epic!"],
            'gaming': ["Gameplay Epic!", "Gaming Highlight Terbaik!"],
        }
        base = random.choice(titles.get(cat, titles['gaming']))
        prefix = "#Shorts " if upload_type == "reels" else ""
        if part_num:
            return prefix + "PART " + str(part_num) + " - " + base
        return prefix + "PART 1 - " + base

    def generate_description(self, filename, title, hashtags, upload_type="video"):
        part_num = self._extract_part_number(filename)
        desc = "Video: " + title + "\n\n"
        if part_num: desc += "PART " + str(part_num) + "\n\n"
        desc += "Format: " + ("Reels / Shorts" if upload_type == "reels" else "Video") + "\n\n"
        desc += "Subscribe! Like & Comment!\n\n"
        desc += "HASHTAG:\n" + hashtags + "\n\nCreated By Yad\n"
        return desc


if __name__ == '__main__':
    gen = AutoHashtag()
    filename = sys.argv[1] if len(sys.argv) > 1 else "video.mp4"
    upload_type = sys.argv[2] if len(sys.argv) > 2 else "video"
    print("TITLE: " + gen.generate_title(filename, upload_type))
    print("HASHTAGS: " + gen.generate(filename))

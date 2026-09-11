#!/usr/bin/env python3
import sys, random, re
from content_detector import ContentDetector

class AutoHashtag:
    def __init__(self):
        self.detector = ContentDetector()
        self.hashtags = {
            'viral': ["#FYP", "#Viral", "#Trending", "#Shorts", "#YouTubeShorts", "#ViralIndonesia"],
            'anime': ["#Anime", "#AnimeViral", "#AnimeEdit", "#Otaku", "#Wibu"],
            'manhwa': ["#Manhwa", "#Webtoon", "#SoloLeveling", "#TBATE"],
            'manga': ["#Manga", "#MangaViral"],
            'donghua': ["#Donghua", "#SoulLand", "#BTTH"],
            'gaming': ["#Gaming", "#Gamer", "#GamingIndonesia"],
            'mlbb': ["#MLBB", "#MobileLegends", "#DiamondMLBB"],
            'freefire': ["#FreeFire", "#FFIndonesia", "#FreeFireBooyah"],
            'engagement': ["#Subscribe", "#Like", "#Comment", "#Share"],
            'indonesia': ["#Indonesia", "#GamersID", "#KreatorIndonesia"],
            'reels': ["#Reels", "#ReelsVideo", "#ReelsIndonesia", "#ReelsFYP"],
        }
        self.branded = ["#YadStore", "#YadGaming"]

    def generate(self, filename, title="", count=15, upload_type="video"):
        categories = self.detector.detect_multiple(filename + " " + title, max_cat=3)
        if not categories: categories = ['gaming']
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
                if avail: tags.extend(random.sample(avail, min(3, len(avail))))
        tags.extend(random.sample(self.hashtags['engagement'], 2))
        tags.extend(random.sample(self.hashtags['indonesia'], 2))
        tags.append(random.choice(self.branded))
        tags = list(dict.fromkeys(tags))
        return ' '.join(tags[:count])

    def _extract_part_number(self, filename):
        patterns = ['part[-_ ]*([0-9]+)', 'episode[-_ ]*([0-9]+)', 'ep[-_ ]*([0-9]+)', '_([0-9]+)[.]mp4$']
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
            'gaming': ["Gameplay Epic!", "Gaming Highlight Terbaik!"],
        }
        base = random.choice(titles.get(cat, titles['gaming']))
        prefix = "#Shorts " if upload_type == "reels" else ""
        if part_num: return prefix + "PART " + str(part_num) + " - " + base
        return prefix + "PART 1 - " + base

    def generate_description(self, filename, title, hashtags, upload_type="video"):
        part_num = self._extract_part_number(filename)
        desc = title + "\n\n"
        if part_num: desc += "PART " + str(part_num) + "\n\n"
        desc += "Format: " + ("Reels" if upload_type == "reels" else "Video") + "\n\n"
        desc += "Subscribe!\nLike & Comment!\n\nHASHTAG:\n" + hashtags + "\n\nCreated By Yad\n"
        return desc

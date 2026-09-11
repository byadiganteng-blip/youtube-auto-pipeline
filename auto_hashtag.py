#!/usr/bin/env python3
"""Auto Hashtag - Content + Part Aware + Reels Support"""

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
                     "#Otaku", "#Wibu", "#JujutsuKaisen", "#DemonSlayer",
                     "#OnePiece", "#Naruto", "#Bleach"],
            'manhwa': ["#Manhwa", "#ManhwaViral", "#Webtoon", "#ManhwaIndonesia",
                      "#SoloLeveling", "#TombRaiderKing", "#TBATE", "#ORV"],
            'manga': ["#Manga", "#MangaViral", "#MangaIndonesia"],
            'donghua': ["#Donghua", "#DonghuaViral", "#SoulLand", "#BTTH"],
            'manhua': ["#Manhua", "#ManhuaViral"],
            'gaming': ["#Gaming", "#Gamer", "#GamingIndonesia", "#GameShorts"],
            'mlbb': ["#MLBB", "#MobileLegends", "#MLBBIndonesia", "#DiamondMLBB"],
            'freefire': ["#FreeFire", "#FFIndonesia", "#FreeFireBooyah"],
            'pubg': ["#PUBG", "#PUBGMobile", "#PUBGIndonesia"],
            'genshin': ["#GenshinImpact", "#GenshinIndonesia"],
            'valorant': ["#Valorant", "#ValorantIndonesia"],
            'topup': ["#TopUpGame", "#TopUpMurah", "#DiamondMurah"],
            'engagement': ["#Subscribe", "#Like", "#Comment", "#Share"],
            'indonesia': ["#Indonesia", "#GamersID", "#KreatorIndonesia"],
            'reels': ["#Reels", "#ReelsVideo", "#ReelsIndonesia", "#ReelsViral",
                     "#ReelsFYP", "#InstagramReels", "#FacebookReels"],
        }
        self.branded = ["#YadStore", "#YadGaming"]

    def generate(self, filename, title="", count=15, upload_type="video"):
        categories = self.detector.detect_multiple(filename + " " + title, max_cat=3)
        if not categories:
            categories = ['gaming']
        print("[*] Detected: " + str(categories) + " | Type: " + upload_type)

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
        patterns = [
            'part[-_ ]*([0-9]+)',
            'episode[-_ ]*([0-9]+)',
            'ep[-_ ]*([0-9]+)',
            '_([0-9]+)[.]mp4$',
            '[_ -]([0-9]+)[_ -]',
        ]
        text = filename.lower()
        for p in patterns:
            m = re.search(p, text)
            if m:
                try:
                    return int(m.group(1))
                except:
                    pass
        return None

    def generate_title(self, filename, upload_type="video"):
        part_num = self._extract_part_number(filename)
        cats = self.detector.detect_multiple(filename, max_cat=1)
        cat = cats[0] if cats else 'gaming'

        titles = {
            'anime': ["Anime Edit Viral! Wajib Tonton!", "Momen Epic Anime! Auto FYP!"],
            'manhwa': ["Manhwa Recommendation! Terbaik 2026!", "Manhwa Panel Epic!"],
            'manga': ["Manga Panel Epic! Wajib Tonton!"],
            'donghua': ["Donghua Viral! Wajib Tonton!", "Donghua Epic Moment!"],
            'manhua': ["Manhua Review! Terbaik 2026!"],
            'mlbb': ["Top Up Diamond MLBB Murah!", "MLBB Savage! Epic Moment!"],
            'freefire': ["Free Fire Booyah Terus!", "FF Pro Player Moment!"],
            'pubg': ["PUBG Highlight! Momen Epic!", "PUBG Chicken Dinner!"],
            'genshin': ["Genshin Impact Wish! Gacha Epic!", "Genshin Build Terbaik!"],
            'valorant': ["Valorant Highlight! Clutch Epic!"],
            'topup': ["Top Up Game Murah & Terpercaya!"],
            'tutorial': ["Tutorial Game! Tips & Trik Pro!"],
            'review': ["Review Jujur! Wajib Tonton!"],
            'gaming': ["Gameplay Epic! Momen Viral!", "Gaming Highlight Terbaik!"],
        }
        base = random.choice(titles.get(cat, titles['gaming']))
        prefix = "#Shorts " if upload_type == "reels" else ""
        if part_num:
            return prefix + "PART " + str(part_num) + " - " + base
        part_hash = (hash(filename) % 30) + 1
        return prefix + "PART " + str(part_hash) + " - " + base

    def generate_description(self, filename, title, hashtags, upload_type="video"):
        part_num = self._extract_part_number(filename)
        desc = "\U0001F3AC " + title + "\n\n"
        if part_num:
            desc += "\U0001F4FA PART " + str(part_num) + "\n\n"
        desc += "\U0001F39E Format: " + ("Reels / Shorts" if upload_type == "reels" else "Video Biasa") + "\n\n"
        desc += "-" * 20 + "\n"
        desc += "Jangan lupa Subscribe!\n"
        desc += "Like & Comment!\n"
        desc += "Share ke teman!\n"
        desc += "-" * 20 + "\n\n"
        desc += "HASHTAG:\n" + hashtags + "\n\n"
        desc += "-" * 20 + "\nCreated By Yad\n"
        return desc


if __name__ == '__main__':
    gen = AutoHashtag()
    filename = sys.argv[1] if len(sys.argv) > 1 else "solo_leveling_part_01.mp4"
    upload_type = sys.argv[2] if len(sys.argv) > 2 else "video"
    title = gen.generate_title(filename, upload_type)
    hashtags = gen.generate(filename, title, upload_type=upload_type)
    description = gen.generate_description(filename, title, hashtags, upload_type)
    print("TITLE: " + title)
    print("HASHTAGS: " + hashtags)

#!/usr/bin/env python3
"""Auto Hashtag - Content Aware + Part Aware"""

import sys
import random
import re
from content_detector import ContentDetector


class AutoHashtag:
    def __init__(self):
        self.detector = ContentDetector()
        
        self.hashtags = {
            'viral': [
                "#FYP", "#FYPシ", "#Viral", "#Trending", "#TrendingNow",
                "#Shorts", "#YouTubeShorts", "#ShortsViral", "#ShortsFeed",
                "#ForYouPage", "#Explore", "#ViralVideo", "#ViralIndonesia"
            ],
            'anime': [
                "#Anime", "#AnimeViral", "#AnimeEdit", "#AnimeFYP",
                "#AnimeIndonesia", "#AnimeShorts", "#AnimeLover",
                "#Otaku", "#OtakuIndonesia", "#Wibu", "#WibuIndonesia",
                "#JujutsuKaisen", "#DemonSlayer", "#ChainsawMan",
                "#SpyXFamily", "#BlueLock", "#MHA", "#AOT",
                "#OnePiece", "#Naruto", "#Bleach", "#DragonBall"
            ],
            'manhwa': [
                "#Manhwa", "#ManhwaViral", "#ManhwaFYP",
                "#ManhwaIndonesia", "#ManhwaEdit", "#ManhwaLover",
                "#Webtoon", "#WebtoonViral", "#WebtoonIndonesia",
                "#SoloLeveling", "#TombRaiderKing", "#TBATE",
                "#OmniscientReader", "#ORV", "#NanoMachine",
                "#Lookism", "#TrueBeauty", "#SweetHome"
            ],
            'manga': [
                "#Manga", "#MangaViral", "#MangaIndonesia",
                "#MangaEdit", "#MangaLover", "#MangaDaily"
            ],
            'donghua': [
                "#Donghua", "#DonghuaViral", "#DonghuaIndonesia",
                "#ChineseAnime", "#SoulLand", "#BTTH", "#DouluoDalu",
                "#PerfectWorld", "#ThroneOfSeal", "#SwallowedStar"
            ],
            'manhua': [
                "#Manhua", "#ManhuaViral", "#ManhuaIndonesia",
                "#ChineseComic"
            ],
            'gaming': [
                "#Gaming", "#Gamer", "#GamingIndonesia", "#GamingViral",
                "#GameViral", "#GamersID", "#GameShorts"
            ],
            'mlbb': [
                "#MLBB", "#MobileLegends", "#MLBBIndonesia", "#MLBBViral",
                "#MLBBFYP", "#MLBBShorts", "#DiamondMLBB", "#MLBBHighlight",
                "#MLBBSavage", "#MLBBMythic"
            ],
            'freefire': [
                "#FreeFire", "#FFIndonesia", "#FreeFireViral", "#FFShorts",
                "#FreeFireHighlight", "#DiamondFF", "#FreeFireBooyah"
            ],
            'pubg': [
                "#PUBG", "#PUBGMobile", "#PUBGIndonesia", "#PUBGViral"
            ],
            'genshin': [
                "#GenshinImpact", "#GenshinIndonesia", "#GenshinViral",
                "#GenshinShorts", "#GenshinWish", "#GachaGenshin"
            ],
            'valorant': [
                "#Valorant", "#ValorantIndonesia", "#ValorantViral",
                "#ValorantShorts"
            ],
            'topup': [
                "#TopUpGame", "#TopUpMurah", "#TopUpCepat",
                "#DiamondMurah", "#VoucherGame", "#TopUpTerpercaya",
                "#YadStore", "#JualDiamond"
            ],
            'engagement': [
                "#Subscribe", "#Like", "#Comment", "#Share",
                "#SubscribeNow", "#SupportMe", "#LikeAndSubscribe"
            ],
            'indonesia': [
                "#Indonesia", "#IndoGaming", "#AnakGaming",
                "#GamersID", "#KreatorIndonesia", "#IndonesiaViral"
            ]
        }
        
        self.branded = ["#YadStore", "#YadGaming"]
    
    def generate(self, filename, title="", count=15):
        categories = self.detector.detect_multiple(filename + " " + title, max_cat=3)
        if not categories:
            categories = ['gaming']
        
        print("[*] Detected: " + str(categories))
        
        tags = []
        tags.extend(random.sample(self.hashtags['viral'], 4))
        
        primary = categories[0]
        if primary in self.hashtags:
            available = [t for t in self.hashtags[primary] if t not in tags]
            n = min(7, len(available))
            tags.extend(random.sample(available, n))
        
        for cat in categories[1:]:
            if cat in self.hashtags and len(tags) < 12:
                available = [t for t in self.hashtags[cat] if t not in tags]
                if available:
                    n = min(3, len(available))
                    tags.extend(random.sample(available, n))
        
        tags.extend(random.sample(self.hashtags['engagement'], 2))
        tags.extend(random.sample(self.hashtags['indonesia'], 2))
        tags.append(random.choice(self.branded))
        
        tags = list(dict.fromkeys(tags))
        return ' '.join(tags[:count])
    
    def generate_title(self, filename):
        """Generate title dengan deteksi part"""
        
        # Deteksi nomor part dari filename
        part_num = self._extract_part_number(filename)
        
        categories = self.detector.detect_multiple(filename, max_cat=1)
        cat = categories[0] if categories else 'gaming'
        
        # Template title per kategori
        titles = {
            'anime': [
                "Anime Edit Viral! Wajib Tonton!",
                "Momen Epic Anime! Auto FYP!",
                "Anime Terbaik 2026! Subscribe!"
            ],
            'manhwa': [
                "Manhwa Recommendation! Terbaik 2026!",
                "Manhwa Panel Epic! Wajib Tonton!",
                "Solo Leveling Chapter Terbaru!"
            ],
            'manga': [
                "Manga Panel Epic! Wajib Tonton!",
                "Manga Recommendation! Terbaik!"
            ],
            'donghua': [
                "Donghua Viral! Wajib Tonton!",
                "Donghua Epic Moment! Auto FYP!"
            ],
            'manhua': [
                "Manhua Review! Terbaik 2026!",
                "Manhua Panel Epic!"
            ],
            'mlbb': [
                "Top Up Diamond MLBB Murah & Cepat!",
                "MLBB Savage! Epic Moment!"
            ],
            'freefire': [
                "Free Fire Booyah Terus! Highlight Epic!",
                "Free Fire Pro Player Moment!"
            ],
            'pubg': [
                "PUBG Highlight! Momen Epic!",
                "PUBG Chicken Dinner!"
            ],
            'genshin': [
                "Genshin Impact Wish! Gacha Epic!",
                "Genshin Build Terbaik!"
            ],
            'valorant': [
                "Valorant Highlight! Clutch Epic!",
                "Valorant Ace! Pro Moment!"
            ],
            'topup': [
                "Top Up Game Murah & Terpercaya!",
                "Cara Top Up Diamond Termurah!"
            ],
            'tutorial': [
                "Tutorial Game! Tips & Trik Pro!",
                "Cara Main Pro Player!"
            ],
            'review': [
                "Review Jujur! Wajib Tonton!",
                "Unboxing Terbaru!"
            ],
            'gaming': [
                "Gameplay Epic! Momen Viral!",
                "Gaming Highlight Terbaik!"
            ]
        }
        
        # Pilih base title
        cat_titles = titles.get(cat, titles['gaming'])
        base_title = random.choice(cat_titles)
        
        # Tambahkan PART number jika terdeteksi
        if part_num:
            return "PART " + str(part_num) + " - " + base_title
        else:
            # Auto-increment part dari hash
            part_hash = (hash(filename) % 30) + 1
            return "PART " + str(part_hash) + " - " + base_title
    
    def _extract_part_number(self, filename):
        """Ekstrak nomor part dari filename"""
        
        # Coba pattern umum
        patterns = [
            r'part[_\-\s]*(\d+)',
            r'episode[_\-\s]*(\d+)',
            r'ep[_\-\s]*(\d+)',
            r'_(\d+)\.mp4$',
            r'_(\d+)\.avi$',
            r'_(\d+)\.mov$',
            r'[_\-](\d+)[_\-]',
        ]
        
        text = filename.lower()
        
        for pattern in patterns:
            match = re.search(pattern, text)
            if match:
                try:
                    return int(match.group(1))
                except:
                    pass
        
        return None
    
    def generate_description(self, filename, title, hashtags):
        """Generate description dengan info part"""
        
        part_num = self._extract_part_number(filename)
        
        desc = "🎬 " + title + "\n\n"
        
        if part_num:
            desc += "📺 PART " + str(part_num) + "\n\n"
        
        desc += "━━━━━━━━━━━━━━━━━━━━\n"
        desc += "🔔 Jangan lupa Subscribe!\n"
        desc += "👍 Like & Comment!\n"
        desc += "📤 Share ke teman!\n"
        desc += "━━━━━━━━━━━━━━━━━━━━\n\n"
        desc += "📌 HASHTAG:\n"
        desc += hashtags + "\n\n"
        desc += "━━━━━━━━━━━━━━━━━━━━\n"
        desc += "Created By Yad\n"
        
        return desc


if __name__ == '__main__':
    gen = AutoHashtag()
    filename = sys.argv[1] if len(sys.argv) > 1 else "solo_leveling_part_01.mp4"
    
    title = gen.generate_title(filename)
    hashtags = gen.generate(filename)
    description = gen.generate_description(filename, title, hashtags)
    
    print("=" * 60)
    print("  FILENAME: " + filename)
    print("=" * 60)
    print("\nTITLE:")
    print("  " + title)
    print("\nHASHTAGS:")
    print("  " + hashtags)
    print("\nDESCRIPTION:")
    print(description)

#!/usr/bin/env python3
"""Content Detector"""


class ContentDetector:
    KEYWORDS = {
        'anime': {
            'strong': ['anime', 'otaku', 'wibu', 'waifu', 'senpai', 'kawaii'],
            'characters': ['naruto', 'sasuke', 'luffy', 'zoro', 'goku', 'vegeta',
                          'tanjiro', 'nezuko', 'gojo', 'yuji', 'eren', 'mikasa'],
            'titles': ['one piece', 'naruto', 'jujutsu kaisen', 'demon slayer',
                      'attack on titan', 'my hero academia', 'chainsaw man'],
        },
        'manhwa': {
            'strong': ['manhwa', 'webtoon', 'korean comic'],
            'characters': ['sung jinwoo', 'cha hae in', 'beru', 'igris'],
            'titles': ['solo leveling', 'tomb raider king', 'tbate',
                      'omniscient reader', 'orv', 'nano machine'],
        },
        'manga': {'strong': ['manga', 'japanese comic']},
        'donghua': {
            'strong': ['donghua', 'chinese anime'],
            'characters': ['tang san', 'xiao wu', 'xiao yan', 'han li'],
            'titles': ['soul land', 'douluo dalu', 'battle through the heavens'],
        },
        'manhua': {'strong': ['manhua', 'chinese comic']},
        'mlbb': {
            'strong': ['mlbb', 'mobile legends'],
            'characters': ['layla', 'miya', 'alucard', 'fanny', 'gusion'],
        },
        'freefire': {'strong': ['free fire', 'freefire', 'booyah']},
        'pubg': {'strong': ['pubg', 'pubg mobile']},
        'genshin': {'strong': ['genshin', 'genshin impact']},
        'valorant': {'strong': ['valorant', 'valo']},
        'topup': {'strong': ['top up', 'topup', 'diamond murah']},
        'gaming': {'strong': ['gameplay', 'highlight', 'gaming']},
        'tutorial': {'strong': ['tutorial', 'cara', 'how to', 'guide']},
        'review': {'strong': ['review', 'unboxing']},
    }

    def detect_from_filename(self, filename):
        text = filename.lower()
        for ch in ['_', '-', '.', '(', ')', '[', ']']:
            text = text.replace(ch, ' ')
        results = []
        for cat, data in self.KEYWORDS.items():
            score = 0
            for kw in data.get('strong', []):
                if kw in text:
                    score += 5
            for c in data.get('characters', []):
                if c in text:
                    score += 4
            for t in data.get('titles', []):
                if t in text:
                    score += 3
            if score > 0:
                results.append((cat, score))
        results.sort(key=lambda x: x[1], reverse=True)
        return results

    def detect_multiple(self, filename, max_cat=3):
        return [cat for cat, _ in self.detect_from_filename(filename)[:max_cat]]

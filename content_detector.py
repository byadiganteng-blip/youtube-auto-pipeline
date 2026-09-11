#!/usr/bin/env python3
class ContentDetector:
    KEYWORDS = {
        'anime': {'strong': ['anime', 'otaku', 'wibu', 'waifu'],
                  'characters': ['naruto', 'sasuke', 'luffy', 'zoro', 'gojo', 'yuji', 'eren', 'mikasa'],
                  'titles': ['one piece', 'naruto', 'jujutsu kaisen', 'demon slayer', 'attack on titan']},
        'manhwa': {'strong': ['manhwa', 'webtoon'],
                   'characters': ['sung jinwoo', 'cha hae in'],
                   'titles': ['solo leveling', 'tomb raider king', 'tbate', 'omniscient reader', 'orv']},
        'manga': {'strong': ['manga']},
        'donghua': {'strong': ['donghua'], 'characters': ['tang san', 'xiao wu', 'xiao yan'],
                    'titles': ['soul land', 'douluo dalu', 'battle through the heavens', 'btth']},
        'mlbb': {'strong': ['mlbb', 'mobile legends']},
        'freefire': {'strong': ['free fire', 'freefire', 'booyah']},
        'pubg': {'strong': ['pubg']},
        'genshin': {'strong': ['genshin']},
        'valorant': {'strong': ['valorant']},
        'gaming': {'strong': ['gameplay', 'highlight', 'gaming']},
        'tutorial': {'strong': ['tutorial', 'cara', 'how to']},
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
                if kw in text: score += 5
            for c in data.get('characters', []):
                if c in text: score += 4
            for t in data.get('titles', []):
                if t in text: score += 3
            if score > 0: results.append((cat, score))
        results.sort(key=lambda x: x[1], reverse=True)
        return results
    def detect_multiple(self, filename, max_cat=3):
        return [cat for cat, _ in self.detect_from_filename(filename)[:max_cat]]

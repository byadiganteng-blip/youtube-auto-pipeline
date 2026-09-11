#!/usr/bin/env python3
class ContentDetector:
    KEYWORDS = {
        'anime': {'strong': ['anime', 'otaku', 'wibu'], 'characters': ['naruto', 'luffy', 'gojo'], 'titles': ['one piece', 'naruto']},
        'manhwa': {'strong': ['manhwa', 'webtoon'], 'characters': ['sung jinwoo'], 'titles': ['solo leveling', 'orv']},
        'manga': {'strong': ['manga']},
        'gaming': {'strong': ['gameplay', 'highlight', 'gaming']},
        'mlbb': {'strong': ['mlbb', 'mobile legends']},
        'freefire': {'strong': ['free fire', 'booyah']},
        'genshin': {'strong': ['genshin']},
        'topup': {'strong': ['top up', 'topup', 'diamond murah']},
    }
    def detect_from_filename(self, filename):
        text = filename.lower()
        for ch in ['_', '-', '.', '(', ')']: text = text.replace(ch, ' ')
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

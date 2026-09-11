#!/usr/bin/env python3
class ContentDetector:
    KW = {
        'anime': {'s': ['anime','otaku'], 'c': ['naruto','luffy','gojo'], 't': ['one piece']},
        'manhwa': {'s': ['manhwa','webtoon'], 't': ['solo leveling']},
        'manga': {'s': ['manga']}, 'donghua': {'s': ['donghua']},
        'mlbb': {'s': ['mlbb']}, 'freefire': {'s': ['free fire']},
        'gaming': {'s': ['gameplay','highlight']},
    }
    def detect_from_filename(self, filename):
        text = filename.lower()
        results = []
        for cat, d in self.KW.items():
            score = sum(5 for k in d.get('s', []) if k in text)
            score += sum(4 for c in d.get('c', []) if c in text)
            score += sum(3 for t in d.get('t', []) if t in text)
            if score > 0: results.append((cat, score))
        results.sort(key=lambda x: x[1], reverse=True)
        return results
    def detect_multiple(self, filename, max_cat=3):
        return [c for c, _ in self.detect_from_filename(filename)[:max_cat]]

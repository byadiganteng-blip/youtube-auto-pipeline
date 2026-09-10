#!/usr/bin/env python3
"""Content Detector - Auto detect konten"""

import re
from typing import List, Tuple


class ContentDetector:
    KEYWORDS = {
        'anime': {
            'strong': ['anime', 'otaku', 'wibu', 'waifu', 'senpai', 'kawaii', 'subtitle indo'],
            'characters': [
                'naruto', 'sasuke', 'sakura', 'kakashi', 'itachi',
                'luffy', 'zoro', 'sanji', 'nami', 'chopper',
                'goku', 'vegeta', 'gohan', 'bulma', 'frieza',
                'ichigo', 'rukia', 'aizen',
                'tanjiro', 'nezuko', 'zenitsu', 'inosuke', 'rengoku',
                'gojo', 'yuji', 'megumi', 'nobara', 'sukuna',
                'eren', 'mikasa', 'levi', 'armin',
                'deku', 'bakugo', 'todoroki', 'uraraka',
                'denji', 'power', 'aki', 'makima',
                'anya', 'loid', 'yor',
                'killua', 'gon', 'hisoka',
            ],
            'titles': [
                'one piece', 'naruto', 'boruto', 'jujutsu kaisen',
                'demon slayer', 'kimetsu no yaiba', 'attack on titan',
                'shingeki no kyojin', 'my hero academia',
                'chainsaw man', 'spy x family', 'blue lock',
                'mushoku tensei', 're zero', 'konosuba',
                'tokyo revengers', 'haikyuu', 'bleach',
                'dragon ball', 'fairy tail', 'black clover',
                'hunter x hunter', 'death note', 'code geass',
            ]
        },
        'manhwa': {
            'strong': ['manhwa', 'webtoon', 'korean comic', 'manhwa sub'],
            'characters': [
                'sung jinwoo', 'cha hae in', 'beru', 'igris',
                'tower of god', 'bam', 'rachel', 'khun',
                'noblesse', 'frankenstein', 'lookism',
                'true beauty', 'sweet home', 'viral hit',
                'weak hero', 'eleceed', 'nano machine',
                'volcanic age', 'peerless dad',
            ],
            'titles': [
                'solo leveling', 'tomb raider king', 'tbate',
                'the beginning after the end', 'omniscient reader',
                'orv', 'second life ranker', 'sss rank',
                'ranker who lives again', 'return of disaster',
                'nano machine', 'volcanic age', 'peerless dad',
                'lookism', 'true beauty', 'sweet home',
                'noblesse', 'tower of god', 'god of high school',
                'viral hit', 'weak hero', 'eleceed',
            ]
        },
        'manga': {
            'strong': ['manga', 'japanese comic'],
        },
        'donghua': {
            'strong': ['donghua', 'chinese anime', 'chinese animation'],
            'characters': [
                'tang san', 'xiao wu', 'dai mubai',
                'xiao yan', 'xun er', 'yao chen',
                'shi hao', 'wang dong', 'luo feng',
                'han li', 'wang lin', 'li muwan',
            ],
            'titles': [
                'soul land', 'douluo dalu', 'battle through the heavens',
                'btth', 'perfect world', 'throne of seal',
                'swallowed star', 'renegade immortal', 'xian ni',
                'against the gods', 'the legend of sword domain',
                'stellar transformations', 'a record of mortal',
                'the great ruler', 'wu dong qian kun',
            ]
        },
        'manhua': {
            'strong': ['manhua', 'chinese comic'],
        },
        'mlbb': {
            'strong': ['mlbb', 'mobile legends', 'diamond ml'],
            'characters': ['layla', 'miya', 'alucard', 'tigreal', 'fanny', 'gusion', 'lancelot', 'chou', 'kagura', 'hayabusa'],
        },
        'freefire': {
            'strong': ['free fire', 'freefire', 'booyah', 'ff indo'],
        },
        'pubg': {
            'strong': ['pubg', 'pubg mobile'],
        },
        'genshin': {
            'strong': ['genshin', 'genshin impact'],
            'characters': ['zhongli', 'hutao', 'raiden', 'kazuha', 'ayaka', 'ganyu', 'xiao', 'venti', 'klee'],
        },
        'valorant': {
            'strong': ['valorant', 'valo'],
        },
        'topup': {
            'strong': ['top up', 'topup', 'diamond murah', 'voucher game'],
        },
        'gaming': {
            'strong': ['gameplay', 'highlight', 'montage', 'gaming'],
        },
        'tutorial': {
            'strong': ['tutorial', 'cara', 'how to', 'guide', 'tips'],
        },
        'review': {
            'strong': ['review', 'unboxing'],
        },
    }
    
    def detect_from_filename(self, filename):
        text = filename.lower()
        text = re.sub(r'[_\-\.\(\)\[\]]', ' ', text)
        results = []
        for category, data in self.KEYWORDS.items():
            score = 0
            for kw in data.get('strong', []):
                if kw in text:
                    score += 5
            for char in data.get('characters', []):
                if char in text:
                    score += 4
            for title in data.get('titles', []):
                if title in text:
                    score += 3
            if score > 0:
                results.append((category, score))
        results.sort(key=lambda x: x[1], reverse=True)
        return results
    
    def detect_primary_category(self, filename):
        results = self.detect_from_filename(filename)
        if results:
            return results[0][0]
        return 'general'
    
    def detect_multiple(self, filename, max_cat=3):
        results = self.detect_from_filename(filename)
        return [cat for cat, score in results[:max_cat]]

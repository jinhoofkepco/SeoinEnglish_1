# 단어 JSON 규칙

## 프롬프트

단어장 사진 또는 단어 목록을 보고, 아래 문법에 맞춰 `vocabulary` JSON만 만들어줘.
사진에 있는 단어는 빠뜨리지 말고, 뜻과 예문이 있으면 그대로 반영해줘.
설명은 짧고 정확하게 써줘.

---

## 문법

### vocabulary

```json
{
  "vocabulary": [
    {
      "id": "w_word",
      "word": "word",
      "lemma": "word",
      "forms": ["words"],
      "partOfSpeech": "noun",
      "meaningKo": "한국어 뜻",
      "simpleKo": "짧은 한국어 설명",
      "easyEnglish": "short easy English meaning",
      "easyEnglishLong": "long easy English explanation for TTS",
      "examples": ["Example sentence."],
      "highlight": true,
      "highlightStyle": "known_vocab",
      "comic": {},
      "quiz": {}
    }
  ]
}
```

### 필드 규칙

- `id`: `w_단어` 형식 권장. 공백 없이 소문자와 `_` 사용.
- `word`: 화면에 표시할 기본 단어.
- `lemma`: 원형.
- `forms`: 본문에 나올 수 있는 변형. 없으면 `[]`.
- `partOfSpeech`: `noun`, `verb`, `adjective`, `adverb` 등.
- `meaningKo`: 한국어 뜻.
- `simpleKo`: 아주 짧은 한국어 설명.
- `easyEnglish`: 짧은 영어 뜻. 단어 설명 화면에서 사용.
- `easyEnglishLong`: TTS가 읽을 자세한 영어 설명.
- `examples`: 예문 배열. 없으면 `[]`.
- `highlight`: 본문에서 강조할 단어면 `true`.
- `highlightStyle`: 기본값은 `"known_vocab"` 권장.
- `comic`: 단어 설명 만화 데이터. 없으면 `{}`.
- `quiz`: 단어 그림 퀴즈용 한 장면. 없으면 `{}`.

### quiz

```json
{
  "quiz": {
    "bg": "room",
    "sprites": [
      {
        "char": "A",
        "x": 50,
        "y": 65,
        "scale": 1.0,
        "rotate": 0,
        "flip": false,
        "anim": "none"
      }
    ]
  }
}
```

### comic

```json
{
  "comic": {
    "word": "word",
    "meaning": "short meaning",
    "panels": [
      {
        "bg": "room",
        "caption": "Short caption.",
        "sprites": [],
        "bubble": {
          "anchor": 0,
          "text": "Short speech."
        }
      }
    ]
  }
}
```

### 허용값

- `bg`: `snow`, `sky`, `forest`, `desert`, `lava`, `ocean`, `night`, `room`, `sunset`, `plain`
- `anim`: `none`, `bounce`, `shiver`, `float`, `dash`, `roll`, `jump`, `sway`, `spin`

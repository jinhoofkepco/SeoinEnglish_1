# JSON Rules Table V3 - Comic Study + Emoji Choice Game

이 문서는 현재 Android 앱 구조 기준의 `lesson.json` 작성 규칙입니다.

현재 단어 흐름은 아래 순서입니다.

1. 콘텐츠 진입
2. `vocabulary[]` 단어별 4컷 만화 표시
3. 각 컷 `caption`을 TTS로 순서대로 읽기
4. 마지막에 단어 설명 TTS 읽기
5. 모든 단어가 끝나면 4지선다 이모지 장면 선택 게임 진행
6. 이후 본문 학습 단계 진행

## 1. Root

| Field | Type | Required | App Usage | Notes |
|---|---:|---:|---|---|
| `schemaVersion` | number | yes | schema version | 현재 `1` |
| `id` | string | yes | lesson id, save key | 폴더명과 맞추는 것을 권장 |
| `title` | string | yes | lesson list/header | 화면 표시 제목 |
| `subtitle` | string | no | lesson list/header | 부제 |
| `level` | string | no | metadata | A1 등 |
| `language` | string | no | TTS/audio hint | 보통 `en` |
| `defaultAudioId` | string | no | audio playback | 음원 없으면 비워도 됨 |
| `defaultChunkSetId` | string | no | body chunk mode | 보통 `short` 또는 `sentence` |
| `imageAssets` | array | no | images | 단어장 사진, 기존 이미지 만화 등 |
| `audioAssets` | array | no | audio | 음원 없으면 빈 배열 가능 |
| `chunkProfiles` | array | no | reader mode labels | short/long/natural 등 |
| `vocabularySources` | array | no | source mapping | 단어장 사진/OCR 원본 연결 |
| `paragraphs` | array | yes | body reading | 본문 문장 |
| `vocabulary` | array | yes | word study + game | 단어 학습 대상 |
| `explanations` | array | no | popup/TTS supplement | 단어/문장 설명 |
| `comprehensionChecks` | array | no | body question checks | 질문 확인 |

## 2. Vocabulary

`vocabulary[]`는 앱의 단어 학습과 단어게임 대상입니다.

| Field | Type | Required | App Usage | Notes |
|---|---:|---:|---|---|
| `id` | string | yes | word id | 예: `w_organize` |
| `word` | string | yes | display/TTS/game | 화면에 나오는 단어 |
| `lemma` | string | no | metadata | 기본형 |
| `forms` | array | no | matching helper | 복수/변형 |
| `partOfSpeech` | string | no | game result | noun, verb 등 |
| `easyEnglish` | string | yes | short definition | 단어 설명 기본값 |
| `easyEnglishLong` | string | no | long TTS explanation | 있으면 마지막 설명에 같이 읽음 |
| `meaningKo` | string | no | note/popup | 한국어 뜻 |
| `simpleKo` | string | no | note/popup | 쉬운 한국어 설명 |
| `examples` | array | no | TTS/example | 첫 예문을 설명에 붙임 |
| `highlight` | boolean | no | body highlight | 본문 단어 표시 |
| `highlightStyle` | string | no | body highlight style | 보통 `known_vocab` |
| `sourceRefs` | array | no | source mapping | 단어장 사진 행 연결 |
| `explanationIds` | array | no | explanation link | `explanations[].id` 참조 |
| `comic` | object | strongly recommended | comic study | 새 단어 흐름에서는 권장 |

## 3. New Comic Object

현재 새 학습 화면은 `vocabulary[].comic.panels[]`가 있으면 이것을 우선 사용합니다.

| Field | Type | Required | App Usage | Notes |
|---|---:|---:|---|---|
| `word` | string | no | comic title fallback | 보통 `vocabulary[].word`와 동일 |
| `meaning` | string | no | comic meaning line | 없으면 `easyEnglish` 사용 |
| `panels` | array | yes | 4-panel comic renderer | 4개 권장 |
| `readDefinitionAfter` | boolean | no | old overlay mode | 새 학습 화면에서는 설명을 기본으로 읽음 |

기존 이미지 기반 만화 호환 필드는 남아 있습니다.

| Field | Type | Required | App Usage | Notes |
|---|---:|---:|---|---|
| `imageAssetId` | string | no | old image comic | `panels`가 없을 때만 주로 사용 |
| `panelCount` | number | no | old image comic | 기존 이미지 분할 수 |
| `layout` | string | no | old image comic | `horizontal` 또는 `vertical` |
| `focusSteps` | array | no | old image comic | 카메라/반짝임 포커스 |

## 4. Comic Panels

`comic.panels[]`는 앱이 직접 그리는 4컷 만화 데이터입니다.

| Field | Type | Required | App Usage | Notes |
|---|---:|---:|---|---|
| `bg` | string | yes | panel background | 허용값만 사용 |
| `caption` | string | yes | TTS + caption | 컷 설명, 영어 권장 |
| `sprites` | array | yes | emoji/image objects | 2-4개 권장 |
| `bubble` | object | no | speech bubble | 짧은 말풍선 |

Allowed `bg` values:

```text
snow, sky, forest, desert, lava, ocean, night, room, sunset, plain
```

알 수 없는 `bg`는 앱에서 `plain`으로 처리합니다.

## 5. Comic Sprites

`sprites[]`는 각 컷 안의 이모지 또는 이미지 요소입니다.

| Field | Type | Required | App Usage | Notes |
|---|---:|---:|---|---|
| `char` | string | yes if no `src` | emoji sprite | 예: `🐱`, `📚` |
| `src` | string | no | future image sprite | 현재는 주로 emoji 사용 |
| `x` | number | yes | horizontal percent | 0-100, 권장 15-85 |
| `y` | number | yes | vertical percent | 0-100, 권장 25-80 |
| `scale` | number | no | sprite size | 권장 0.6-1.4 |
| `rotate` | number | no | rotation degrees | 권장 -30~30 |
| `flip` | boolean | no | horizontal flip | true/false |
| `anim` | string | no | animation | 허용값만 사용 |

Allowed `anim` values:

```text
none, bounce, shiver, float, dash, roll, jump, sway, spin
```

알 수 없는 `anim`은 앱에서 `none`으로 처리합니다.

## 6. Comic Bubble

| Field | Type | Required | App Usage | Notes |
|---|---:|---:|---|---|
| `anchor` | number | yes | sprite index | `sprites` 배열의 0-based index |
| `text` | string | yes | bubble text | 짧게 유지 |

## 7. Minimal Vocabulary Comic Example

```json
{
  "id": "w_organize",
  "word": "organize",
  "lemma": "organize",
  "partOfSpeech": "verb",
  "easyEnglish": "put things in order",
  "easyEnglishLong": "When you organize things, you put them in the right places so they are easy to find.",
  "meaningKo": "정리하다",
  "examples": [
    "I organize my toys and books."
  ],
  "highlight": true,
  "highlightStyle": "known_vocab",
  "comic": {
    "word": "organize",
    "meaning": "put things in order",
    "panels": [
      {
        "bg": "room",
        "caption": "Kibu sees a messy desk.",
        "sprites": [
          { "char": "🐱", "x": 28, "y": 70, "scale": 1.0, "rotate": -4, "flip": false, "anim": "sway" },
          { "char": "📚", "x": 58, "y": 72, "scale": 1.0, "rotate": -12, "flip": false, "anim": "none" },
          { "char": "🧸", "x": 74, "y": 72, "scale": 0.9, "rotate": 8, "flip": false, "anim": "none" }
        ],
        "bubble": { "anchor": 0, "text": "Messy!" }
      },
      {
        "bg": "room",
        "caption": "Kibu wants to organize the desk.",
        "sprites": [
          { "char": "🐱", "x": 33, "y": 68, "scale": 1.0, "rotate": 0, "flip": false, "anim": "bounce" },
          { "char": "📦", "x": 58, "y": 74, "scale": 1.0, "rotate": 0, "flip": false, "anim": "none" },
          { "char": "✨", "x": 76, "y": 36, "scale": 0.8, "rotate": 0, "flip": false, "anim": "float" }
        ],
        "bubble": { "anchor": 0, "text": "I can organize!" }
      },
      {
        "bg": "room",
        "caption": "Kibu puts each thing in the right place.",
        "sprites": [
          { "char": "😺", "x": 30, "y": 70, "scale": 1.0, "rotate": 0, "flip": false, "anim": "jump" },
          { "char": "📚", "x": 58, "y": 66, "scale": 1.0, "rotate": 0, "flip": false, "anim": "none" },
          { "char": "🧸", "x": 74, "y": 70, "scale": 0.9, "rotate": 0, "flip": false, "anim": "none" }
        ]
      },
      {
        "bg": "room",
        "caption": "Now the desk is organized.",
        "sprites": [
          { "char": "😺", "x": 32, "y": 70, "scale": 1.0, "rotate": 0, "flip": false, "anim": "bounce" },
          { "char": "📚", "x": 58, "y": 72, "scale": 0.9, "rotate": 0, "flip": false, "anim": "none" },
          { "char": "🏠", "x": 76, "y": 69, "scale": 0.9, "rotate": 0, "flip": false, "anim": "none" },
          { "char": "✨", "x": 78, "y": 35, "scale": 0.8, "rotate": 0, "flip": false, "anim": "float" }
        ],
        "bubble": { "anchor": 0, "text": "Great!" }
      }
    ]
  }
}
```

## 8. Emoji Choice Game

별도 게임 JSON은 아직 필요 없습니다.

앱은 현재 `vocabulary[]`를 사용해 4지선다 이모지 장면 선택 게임을 자동 생성합니다.

| Source | Usage |
|---|---|
| `vocabulary[].id` | save key |
| `vocabulary[].word` | target word |
| `vocabulary[].partOfSpeech` | result text |
| `vocabulary[].easyEnglish` | answer definition |
| `vocabulary[].easyEnglishLong` | longer review text |
| `vocabulary[].comic.panels` | prior study stage |

단어가 4개 이상이면 레슨의 실제 단어 목록이 게임 deck이 됩니다.
단어가 4개 미만이면 앱의 기본 샘플 단어가 fallback으로 사용됩니다.

## 9. Validation Checklist

- `lesson.json`이 JSON 문법상 유효한가?
- `vocabulary[].id`가 중복되지 않는가?
- `vocabulary[].word`가 비어 있지 않은가?
- `vocabulary[].easyEnglish` 또는 `comic.meaning`이 있는가?
- `comic.panels`가 있으면 4개인가?
- 각 panel에 `bg`, `caption`, `sprites`가 있는가?
- `bg`가 allowed list 안에 있는가?
- `sprites[].anim`이 allowed list 안에 있는가?
- `sprites[].x`, `sprites[].y`가 0-100 범위인가?
- `bubble.anchor`가 `sprites` 배열 범위 안인가?
- 본문 하이라이트가 있으면 `annotations[].wordId`가 `vocabulary[].id`를 가리키는가?

## 10. Prompt Note For JSON Generation

LLM에게 요청할 때는 아래처럼 말하면 됩니다.

```text
For each vocabulary word, create `vocabulary[].comic.panels` using exactly 4 panels.
Use only these bg values: snow, sky, forest, desert, lava, ocean, night, room, sunset, plain.
Use only these anim values: none, bounce, shiver, float, dash, roll, jump, sway, spin.
Each panel must have bg, caption, sprites.
Each panel should use 2-4 sprites.
Sprites use x/y percent coordinates. Keep x between 15 and 85, y between 25 and 80 when possible.
Use short English captions because the app reads captions aloud with TTS.
Return valid JSON only.
```

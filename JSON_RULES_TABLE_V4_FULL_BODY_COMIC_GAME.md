# JSON Rules Table V4 - Full Lesson + Comic Study + Emoji Choice Game

현재 Android 앱 기준 통합 `lesson.json` 규칙입니다.

이 문서는 아래 전체 흐름을 한 번에 만들기 위한 규칙입니다.

1. 단어별 4컷 만화 학습
2. 단어 설명 TTS
3. 4지선다 이모지 장면 선택 게임
4. 본문 읽기
5. 청크 단위 듣기/말하기
6. 질문 확인: 본문 안의 chunk를 눌러 답 선택

중요 원칙:

- 단어는 `vocabulary[]`에 둡니다.
- 본문은 `paragraphs[].sentences[]`에 둡니다.
- 본문 청크는 각 문장의 `chunkSets[]`에 둡니다.
- 질문 확인의 정답은 반드시 `chunkSets[].chunks[]` 안의 한 chunk여야 합니다.
- 질문 확인은 사용자가 본문 안의 chunk를 직접 눌러 고르는 방식입니다. 별도 객관식 텍스트 답이 아닙니다.

## 1. Root Structure

| Field | Type | Required | Usage |
|---|---:|---:|---|
| `schemaVersion` | number | yes | 현재 `1` |
| `id` | string | yes | lesson id, 저장 key |
| `title` | string | yes | lesson list/header |
| `subtitle` | string | no | 부제 |
| `level` | string | no | A1, A2 등 |
| `language` | string | no | 보통 `en` |
| `defaultAudioId` | string | no | 음원 있을 때 기본 audio id |
| `defaultChunkSetId` | string | yes | 질문/본문 기본 chunk set, 보통 `short` 또는 `meaning` |
| `imageAssets` | array | no | 단어장 사진, 본문 사진, 기존 이미지 만화 |
| `audioAssets` | array | no | 음원 파일. 없으면 빈 배열 가능 |
| `chunkProfiles` | array | yes | 앱에서 쓸 chunk mode 목록 |
| `vocabularySources` | array | no | 단어장 사진/OCR 원본 연결 |
| `paragraphs` | array | yes | 본문 |
| `vocabulary` | array | yes | 단어 학습/게임 대상 |
| `explanations` | array | no | 단어/문장/표현 설명 |
| `comprehensionChecks` | array | no | 본문 질문 확인 |

Minimal root:

```json
{
  "schemaVersion": 1,
  "id": "lesson_001",
  "title": "Lesson Title",
  "subtitle": "",
  "level": "A1",
  "language": "en",
  "defaultAudioId": "",
  "defaultChunkSetId": "short",
  "imageAssets": [],
  "audioAssets": [],
  "chunkProfiles": [],
  "vocabularySources": [],
  "paragraphs": [],
  "vocabulary": [],
  "explanations": [],
  "comprehensionChecks": []
}
```

## 2. Current App Flow

| Stage | Source JSON | Behavior |
|---|---|---|
| Word study | `vocabulary[].comic.panels[]` | 4컷 만화를 보여주고 caption을 TTS로 읽음 |
| Word explanation | `comic.meaning`, `easyEnglish`, `easyEnglishLong`, `examples` | 만화 뒤 단어 설명을 TTS로 읽음 |
| Word game | `vocabulary[]` | 레슨 단어로 4지선다 이모지 장면 게임 자동 생성 |
| Body reading | `paragraphs[].sentences[]` | 책처럼 이어진 본문 표시 |
| Chunk reading | `sentences[].chunkSets[]` | 청크 단위 pause/말하기 진행 |
| Question check | `comprehensionChecks[]` + `chunkSets[]` | 질문을 보고 본문 chunk를 눌러 정답 선택 |

## 3. Body Paragraphs

본문은 `paragraphs > sentences` 구조입니다.

| Field | Type | Required | Notes |
|---|---:|---:|---|
| `paragraphs[].id` | string | yes | 예: `p1` |
| `paragraphs[].type` | string | yes | 보통 `story` |
| `paragraphs[].sentences` | array | yes | 문장 목록 |

Rules:

- 본문 사진/OCR에서 이야기 본문만 넣습니다.
- 문제, 지시문, 페이지 번호, 그림 설명, 선생님용 문구는 `paragraphs`에 넣지 않습니다.
- 문장 순서는 원문 순서를 유지합니다.
- `sentences[].text`는 앱에 표시되는 최종 본문입니다.
- 단어장 단어 목록을 본문에 섞어 넣지 않습니다.

Example:

```json
"paragraphs": [
  {
    "id": "p1",
    "type": "story",
    "sentences": [
      {
        "id": "s1",
        "text": "Long ago, a group of people called the Phoenicians ruled the Mediterranean Sea.",
        "audioId": "",
        "chunkSets": [],
        "annotations": []
      }
    ]
  }
]
```

## 4. Sentences

| Field | Type | Required | Notes |
|---|---:|---:|---|
| `id` | string | yes | 고유 문장 id |
| `text` | string | yes | 정확한 문장 텍스트 |
| `audioId` | string | no | 음원 없으면 `""` 가능 |
| `startMs` | number | no | 음원 timestamp 있을 때만 |
| `endMs` | number | no | 음원 timestamp 있을 때만 |
| `chunkSets` | array | yes | 최소 1개 권장 |
| `annotations` | array | no | 단어/표현 하이라이트 |

Text rules:

- `startChar`, `endChar` 기준은 반드시 `sentence.text`입니다.
- 문장 안 따옴표, 쉼표, 마침표는 가능한 원문 그대로 보존합니다.
- OCR이 줄바꿈 때문에 문장을 쪼갰다면 자연스러운 문장 단위로 합칩니다.
- 단, 원문에 없는 내용을 추가하지 않습니다.

## 5. Chunk Profiles

`chunkProfiles`는 앱에서 사용할 chunk mode 목록입니다.

```json
"chunkProfiles": [
  {
    "id": "short",
    "label": "Meaning chunks",
    "description": "short meaning-based chunks",
    "pauseBehavior": "after_each_chunk"
  },
  {
    "id": "sentence",
    "label": "Sentence",
    "description": "one sentence at a time",
    "pauseBehavior": "after_each_sentence"
  },
  {
    "id": "natural",
    "label": "Natural",
    "description": "continuous sentence",
    "pauseBehavior": "none"
  }
]
```

Recommended ids:

| id | Use |
|---|---|
| `short` | 질문 확인과 말하기 연습에 쓰는 의미 단위 chunk |
| `sentence` | 문장 전체 1개 chunk |
| `natural` | 자연스럽게 읽기용 문장 전체 chunk |

## 6. Chunk Sets

각 문장은 `chunkSets[]`를 가집니다.

```json
"chunkSets": [
  {
    "id": "short",
    "label": "Meaning chunks",
    "chunks": [
      {
        "id": "s1_short_c1",
        "text": "Long ago,",
        "startChar": 0,
        "endChar": 9
      },
      {
        "id": "s1_short_c2",
        "text": "a group of people",
        "startChar": 10,
        "endChar": 27
      }
    ]
  }
]
```

Chunk fields:

| Field | Type | Required | Notes |
|---|---:|---:|---|
| `id` | string | yes | 예: `s1_short_c1` |
| `text` | string | yes | 가능하면 substring과 정확히 일치 |
| `startChar` | number | yes | inclusive |
| `endChar` | number | yes | exclusive |
| `startMs` | number | no | 음원 timestamp 있을 때만 |
| `endMs` | number | no | 음원 timestamp 있을 때만 |

Character rules:

- `startChar` is inclusive.
- `endChar` is exclusive.
- `sentence.text.substring(startChar, endChar)`가 `chunk.text`와 같아야 합니다.
- 공백, 쉼표, 마침표 위치까지 확인합니다.

Meaning chunk rules:

- 청크는 의미 단위로 자릅니다.
- 너무 짧게 기능어만 남기지 않습니다.
- 질문의 답이 될 수 있는 명사구, 장소, 이유, 목적, 행동 단위가 chunk 안에 온전히 들어가야 합니다.
- `They`, `It`, `So`, `Then`, `Thus`, `Because` 같은 단어만으로 된 chunk는 질문 정답 후보로 좋지 않습니다.
- 정답이 한 chunk를 넘어가면 질문을 만들지 말고, chunk를 조정하거나 다른 chunkSet을 사용합니다.

Bad:

```json
{ "text": "the", "startChar": 10, "endChar": 13 }
```

Good:

```json
{ "text": "the Mediterranean Sea", "startChar": 62, "endChar": 83 }
```

## 7. Question Check Rule - Answer Must Be A Chunk

이 규칙이 가장 중요합니다.

앱의 질문 확인 화면은 선택지를 따로 만들지 않습니다.
앱은 `comprehensionChecks[].chunkSetId`가 가리키는 `chunkSets[].chunks[]`를 본문 위에 선택 가능한 chunk로 표시합니다.
사용자는 본문 안의 chunk를 누릅니다.

따라서:

- `answerChunkId`는 반드시 실제 chunk id여야 합니다.
- `answerChunkId`가 가리키는 chunk는 `sentenceId` 또는 `scopeSentenceIds` 범위 안에 있어야 합니다.
- `answerText`는 그 chunk의 `text`와 같게 두는 것을 권장합니다.
- 정답이 chunk 안에 없으면 사용자가 답을 고를 수 없습니다.
- 정답이 여러 chunk에 걸치면 그 질문은 만들지 않습니다.

## 8. Comprehension Checks

```json
"comprehensionChecks": [
  {
    "id": "cq_s1_when",
    "afterSentenceId": "s1",
    "sentenceId": "s1",
    "chunkSetId": "short",
    "question": "When?",
    "answerChunkId": "s1_short_c1",
    "answerText": "Long ago,",
    "scopeSentenceIds": ["s1"],
    "promptNote": "Ask for the time expression."
  }
]
```

Fields:

| Field | Type | Required | Notes |
|---|---:|---:|---|
| `id` | string | yes | question id |
| `afterSentenceId` | string | yes | 이 문장 뒤에 질문 표시 |
| `sentenceId` | string | yes | 정답 chunk가 들어 있는 문장 |
| `chunkSetId` | string | yes | 선택지로 쓸 chunk set |
| `question` | string | yes | 화면/TTS 질문 |
| `answerChunkId` | string | yes | 정답 chunk id |
| `answerText` | string | strongly recommended | 정답 chunk text |
| `scopeSentenceIds` | array | no | 선택지를 가져올 문장 범위 |
| `promptNote` | string | no | GPT voice helper note |

Question writing rules:

- 2-3문장마다 질문을 넣을 수 있습니다.
- 긴 문장은 1문장마다 질문 가능.
- 질문은 짧게 씁니다.
- 답은 정보가 있는 chunk만 사용합니다.
- 정답이 대명사, 접속사, 조동사, 기능어 중심이면 질문을 만들지 않습니다.
- 수량만 묻는 질문은 가치가 낮으면 생략합니다.

Recommended question forms:

| Pattern | Example |
|---|---|
| time | `When?` |
| subject | `Who ruled?` |
| object | `What did they develop?` |
| place | `Where was it made?` |
| reason | `Why was it valued?` |
| purpose | `Why did they use it?` |
| name/result | `Called what?` |

## 9. Good Question Example

Sentence:

```text
It was made in the city of Tyre, so it was called Tyrian purple.
```

Chunks:

```json
{
  "id": "short",
  "chunks": [
    {
      "id": "s3_short_c1",
      "text": "It was made",
      "startChar": 0,
      "endChar": 11
    },
    {
      "id": "s3_short_c2",
      "text": "in the city of Tyre,",
      "startChar": 12,
      "endChar": 33
    },
    {
      "id": "s3_short_c3",
      "text": "so it was called Tyrian purple.",
      "startChar": 34,
      "endChar": 65
    }
  ]
}
```

Questions:

```json
[
  {
    "id": "cq_s3_where",
    "afterSentenceId": "s3",
    "sentenceId": "s3",
    "chunkSetId": "short",
    "question": "Where was it made?",
    "answerChunkId": "s3_short_c2",
    "answerText": "in the city of Tyre,",
    "scopeSentenceIds": ["s3"]
  },
  {
    "id": "cq_s3_called",
    "afterSentenceId": "s3",
    "sentenceId": "s3",
    "chunkSetId": "short",
    "question": "Called what?",
    "answerChunkId": "s3_short_c3",
    "answerText": "so it was called Tyrian purple.",
    "scopeSentenceIds": ["s3"]
  }
]
```

Note:

- 두 질문 모두 정답이 실제 chunk 안에 들어 있습니다.
- 사용자는 본문 위에 표시된 chunk를 눌러 답할 수 있습니다.

## 10. Bad Question Example

Bad:

```json
{
  "question": "What was it called?",
  "answerChunkId": "",
  "answerText": "Tyrian purple"
}
```

문제:

- `Tyrian purple`가 독립 chunk가 아니면 사용자가 선택할 수 없습니다.
- 앱은 본문 chunk를 누르게 되어 있으므로 `answerChunkId`가 필요합니다.

Fix option 1:

```json
{
  "question": "Called what?",
  "answerChunkId": "s3_short_c3",
  "answerText": "so it was called Tyrian purple."
}
```

Fix option 2:

chunk를 아래처럼 조정합니다.

```json
{
  "id": "s3_short_c3",
  "text": "Tyrian purple",
  "startChar": 51,
  "endChar": 65
}
```

단, chunk가 너무 인위적으로 쪼개져 읽기 흐름을 망치면 Fix option 1을 우선합니다.

## 11. Annotations

본문 안의 단어/표현 하이라이트입니다.

```json
"annotations": [
  {
    "id": "ann_s1_friend",
    "type": "vocab",
    "wordId": "w_friend",
    "text": "friend",
    "startChar": 8,
    "endChar": 14,
    "highlightStyle": "known_vocab",
    "explanationIds": ["exp_w_friend_basic"]
  }
]
```

Rules:

- `type: "vocab"`이면 `wordId`로 `vocabulary[].id`를 연결합니다.
- `startChar/endChar`는 `sentence.text` 기준입니다.
- 단어장 사진/표 단어와 본문 하이라이트 단어는 같은 `vocabulary[].id`로 연결합니다.
- 단어장에 없는 단어를 단지 하이라이트 때문에 `vocabulary`에 많이 추가하지 않습니다.

## 12. Vocabulary

현재 단어 학습/게임의 기준입니다.

| Field | Type | Required | Usage |
|---|---:|---:|---|
| `id` | string | yes | word id |
| `word` | string | yes | display/TTS/game |
| `lemma` | string | no | base word |
| `forms` | array | no | word forms |
| `partOfSpeech` | string | no | result display |
| `easyEnglish` | string | yes | short definition |
| `easyEnglishLong` | string | no | longer TTS explanation |
| `meaningKo` | string | no | Korean meaning |
| `simpleKo` | string | no | simple Korean note |
| `examples` | array | no | example TTS |
| `highlight` | boolean | no | body highlight |
| `highlightStyle` | string | no | usually `known_vocab` |
| `sourceRefs` | array | no | vocab source mapping |
| `explanationIds` | array | no | explanation links |
| `comic` | object | strongly recommended | 4-panel word comic |

Rules:

- 단어장 사진/표가 있으면 `vocabulary`에는 그 사진/표 단어만 넣습니다.
- 단어장 사진/표가 없으면 본문에서 난이도 있는 핵심 단어 5-10개 정도를 고릅니다.
- `vocabulary`에 들어간 단어는 단어 학습과 단어 게임 대상입니다.

## 13. Vocabulary Comic

현재 단어 학습은 `vocabulary[].comic.panels[]`를 우선 사용합니다.

```json
"comic": {
  "word": "organize",
  "meaning": "put things in order",
  "panels": []
}
```

Comic fields:

| Field | Type | Required | Usage |
|---|---:|---:|---|
| `word` | string | no | comic word |
| `meaning` | string | no | comic meaning line |
| `panels` | array | yes | 4-panel renderer |
| `readDefinitionAfter` | boolean | no | old mode compatibility |

Existing image comic compatibility:

| Field | Type | Usage |
|---|---:|---|
| `imageAssetId` | string | old image comic |
| `panelCount` | number | old image comic |
| `layout` | string | `horizontal` or `vertical` |
| `focusSteps` | array | old camera/sparkle focus |

If `panels` exists, the app uses the new encoded comic renderer first.

## 14. Comic Panels

```json
{
  "bg": "room",
  "caption": "Kibu sees a messy desk.",
  "sprites": [
    { "char": "🐱", "x": 28, "y": 70, "scale": 1.0, "rotate": -4, "flip": false, "anim": "sway" },
    { "char": "📚", "x": 58, "y": 72, "scale": 1.0, "rotate": -12, "flip": false, "anim": "none" }
  ],
  "bubble": { "anchor": 0, "text": "Messy!" }
}
```

Panel fields:

| Field | Type | Required | Usage |
|---|---:|---:|---|
| `bg` | string | yes | background |
| `caption` | string | yes | caption + TTS |
| `sprites` | array | yes | emoji/image objects |
| `bubble` | object | no | speech bubble |

Allowed `bg`:

```text
snow, sky, forest, desert, lava, ocean, night, room, sunset, plain
```

## 15. Comic Sprites

| Field | Type | Required | Notes |
|---|---:|---:|---|
| `char` | string | yes if no `src` | emoji |
| `src` | string | no | future image asset |
| `x` | number | yes | 0-100, recommended 15-85 |
| `y` | number | yes | 0-100, recommended 25-80 |
| `scale` | number | no | recommended 0.6-1.4 |
| `rotate` | number | no | -30 to 30 |
| `flip` | boolean | no | true/false |
| `anim` | string | no | animation |

Allowed `anim`:

```text
none, bounce, shiver, float, dash, roll, jump, sway, spin
```

Unknown `bg` becomes `plain`.
Unknown `anim` becomes `none`.

## 16. Bubble

| Field | Type | Required | Notes |
|---|---:|---:|---|
| `anchor` | number | yes | sprite index, 0-based |
| `text` | string | yes | short speech |

Rule:

- `anchor` must be inside the `sprites` array range.

## 17. Explanations

```json
"explanations": [
  {
    "id": "exp_w_friend_basic",
    "targetType": "vocab",
    "targetId": "w_friend",
    "title": "friend",
    "easyEnglish": "A person you like and know well.",
    "easyEnglishLong": "A friend is someone you enjoy spending time with.",
    "textKo": "friend는 친구라는 뜻입니다.",
    "examples": ["My friend helps me."]
  }
]
```

Current priority for word explanation:

1. `comic.meaning`
2. `vocabulary[].easyEnglish`
3. linked `explanations[].easyEnglish`
4. `meaningKo`

Long explanation:

- `vocabulary[].easyEnglishLong` or `explanations[].easyEnglishLong`

## 18. Full Mini Example

```json
{
  "schemaVersion": 1,
  "id": "mini_lesson",
  "title": "Mini Lesson",
  "language": "en",
  "defaultAudioId": "",
  "defaultChunkSetId": "short",
  "audioAssets": [],
  "imageAssets": [],
  "chunkProfiles": [
    {
      "id": "short",
      "label": "Meaning chunks",
      "description": "short meaning chunks",
      "pauseBehavior": "after_each_chunk"
    }
  ],
  "paragraphs": [
    {
      "id": "p1",
      "type": "story",
      "sentences": [
        {
          "id": "s1",
          "text": "Long ago, the Phoenicians ruled the Mediterranean Sea.",
          "audioId": "",
          "chunkSets": [
            {
              "id": "short",
              "chunks": [
                {
                  "id": "s1_short_c1",
                  "text": "Long ago,",
                  "startChar": 0,
                  "endChar": 9
                },
                {
                  "id": "s1_short_c2",
                  "text": "the Phoenicians",
                  "startChar": 10,
                  "endChar": 24
                },
                {
                  "id": "s1_short_c3",
                  "text": "ruled the Mediterranean Sea.",
                  "startChar": 25,
                  "endChar": 53
                }
              ]
            }
          ],
          "annotations": []
        }
      ]
    }
  ],
  "vocabulary": [
    {
      "id": "w_organize",
      "word": "organize",
      "partOfSpeech": "verb",
      "easyEnglish": "put things in order",
      "easyEnglishLong": "When you organize things, you put them in the right places.",
      "meaningKo": "정리하다",
      "examples": ["I organize my desk."],
      "comic": {
        "word": "organize",
        "meaning": "put things in order",
        "panels": [
          {
            "bg": "room",
            "caption": "Kibu sees a messy desk.",
            "sprites": [
              { "char": "🐱", "x": 28, "y": 70, "scale": 1.0, "rotate": -4, "flip": false, "anim": "sway" },
              { "char": "📚", "x": 58, "y": 72, "scale": 1.0, "rotate": -12, "flip": false, "anim": "none" }
            ],
            "bubble": { "anchor": 0, "text": "Messy!" }
          },
          {
            "bg": "room",
            "caption": "Kibu wants to organize the desk.",
            "sprites": [
              { "char": "🐱", "x": 33, "y": 68, "scale": 1.0, "rotate": 0, "flip": false, "anim": "bounce" },
              { "char": "📦", "x": 58, "y": 74, "scale": 1.0, "rotate": 0, "flip": false, "anim": "none" }
            ]
          },
          {
            "bg": "room",
            "caption": "Kibu puts each thing in the right place.",
            "sprites": [
              { "char": "😺", "x": 30, "y": 70, "scale": 1.0, "rotate": 0, "flip": false, "anim": "jump" },
              { "char": "📚", "x": 58, "y": 66, "scale": 1.0, "rotate": 0, "flip": false, "anim": "none" }
            ]
          },
          {
            "bg": "room",
            "caption": "Now the desk is organized.",
            "sprites": [
              { "char": "😺", "x": 32, "y": 70, "scale": 1.0, "rotate": 0, "flip": false, "anim": "bounce" },
              { "char": "✨", "x": 78, "y": 35, "scale": 0.8, "rotate": 0, "flip": false, "anim": "float" }
            ],
            "bubble": { "anchor": 0, "text": "Great!" }
          }
        ]
      }
    }
  ],
  "explanations": [],
  "comprehensionChecks": [
    {
      "id": "cq_s1_when",
      "afterSentenceId": "s1",
      "sentenceId": "s1",
      "chunkSetId": "short",
      "question": "When?",
      "answerChunkId": "s1_short_c1",
      "answerText": "Long ago,",
      "scopeSentenceIds": ["s1"]
    },
    {
      "id": "cq_s1_who",
      "afterSentenceId": "s1",
      "sentenceId": "s1",
      "chunkSetId": "short",
      "question": "Who ruled?",
      "answerChunkId": "s1_short_c2",
      "answerText": "the Phoenicians",
      "scopeSentenceIds": ["s1"]
    }
  ]
}
```

## 19. Generation Prompt For GPT

```text
Create a valid lesson.json for the Android English study app.

Use this structure:
- root fields
- vocabulary with comic.panels
- paragraphs with sentences and chunkSets
- comprehensionChecks

Body rules:
- Put only the story body in paragraphs.
- Do not include workbook questions, directions, page labels, or vocabulary table text in paragraphs.
- Preserve story sentence wording.
- Each sentence needs chunkSets.
- Use chunkSetId "short" for meaning chunks.
- Each chunk must have id, text, startChar, endChar.
- startChar is inclusive and endChar is exclusive.
- chunk.text should match sentence.text.substring(startChar, endChar).

Question rules:
- comprehensionChecks answers must be selectable chunks.
- answerChunkId must point to an existing chunk in the selected chunkSetId.
- answerText should equal that chunk text.
- Do not make a question if the answer crosses multiple chunks.
- Do not use pronoun-only or connector-only chunks as answers.
- Make short questions such as When?, Who ruled?, What did they develop?, Where was it made?, Why did they use it?

Comic rules:
- Each vocabulary word should have comic.panels with exactly 4 panels.
- Use only bg values: snow, sky, forest, desert, lava, ocean, night, room, sunset, plain.
- Use only anim values: none, bounce, shiver, float, dash, roll, jump, sway, spin.
- Each panel needs bg, caption, sprites.
- Each panel should have 2-4 sprites.
- Sprites use x/y percent coordinates.
- Keep x between 15 and 85 and y between 25 and 80 when possible.
- Captions should be short English because the app reads them aloud.

Return valid JSON only.
```

## 20. Validation Checklist

- Root has `id`, `title`, `defaultChunkSetId`, `paragraphs`, `vocabulary`.
- Every sentence has a unique `id`.
- Every sentence has at least one `chunkSets[]`.
- `chunkProfiles[].id` matches used `chunkSets[].id`.
- Every chunk id is unique within the lesson.
- Every chunk `startChar/endChar` points to the correct substring.
- Every `comprehensionChecks[].answerChunkId` exists.
- Every `answerChunkId` belongs to `chunkSetId`.
- Every question answer is contained in one chunk.
- Every `scopeSentenceIds[]` value exists.
- Every `annotations[].wordId` exists in `vocabulary[].id`.
- Every `vocabulary[].comic.panels[]` has 4 panels when possible.
- Every comic panel has allowed `bg`.
- Every sprite has allowed `anim`.
- Every `bubble.anchor` is a valid sprite index.

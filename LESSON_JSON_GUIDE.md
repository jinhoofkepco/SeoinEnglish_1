# 서인이 영어 앱 Lesson JSON 가이드

이 문서는 다른 앱이나 Python 스크립트에서 서인이 영어 앱용 `lesson.json`을 만들 때 맞춰야 할 표준 양식입니다.

목표는 네 가지입니다.

- 본문은 책처럼 자연스럽게 보여준다.
- 오디오는 문장/chunk 타임스탬프로 부드럽게 따라간다.
- chunk 구분은 짧게/길게/문장 단위 등 여러 버전을 지원한다.
- 단어, 문장, chunk 설명은 텍스트와 오디오를 나중에 쉽게 붙일 수 있게 한다.

## 파일 구조

권장 구조:

```text
assets/lessons/manifest.json
assets/lessons/lesson_001/lesson.json
assets/lessons/lesson_001/audio.mp3
assets/lessons/lesson_001/explanations_ko.mp3
assets/lessons/lesson_001/vocab/page_001.jpg
assets/lessons/lesson_001/cover.jpg
```

`cover.jpg`, `explanations_ko.mp3`, `vocab/page_001.jpg`는 선택입니다.

앱 안에서는 모든 파일 경로를 `lesson.json`이 있는 폴더 기준의 상대 경로로 다루는 것을 권장합니다. Windows 절대 경로는 넣지 않습니다.

## manifest.json

여러 음원을 앱 첫 화면에 리스트업하기 위한 파일입니다.

```json
{
  "schemaVersion": 1,
  "lessons": [
    {
      "id": "lesson_001",
      "title": "Vuvuzela Saves the Day!",
      "subtitle": "Part 1",
      "level": "A1",
      "path": "lessons/lesson_001/lesson.json",
      "coverFile": "lessons/lesson_001/cover.jpg"
    }
  ]
}
```

필수 필드:

- `schemaVersion`: 현재는 `1`
- `lessons[].id`: 레슨 고유 ID
- `lessons[].title`: 앱에 표시할 제목
- `lessons[].path`: `assets` 기준 lesson JSON 경로

선택 필드:

- `subtitle`: Part 1, Day 3 등
- `level`: A1, A2 등
- `coverFile`: 표지 이미지

참고:

- 앱은 호환을 위해 `lessonFile`도 읽지만, 새 JSON은 `path`를 기본으로 씁니다.

## lesson.json 전체 구조

권장 최상위 구조:

```json
{
  "schemaVersion": 1,
  "id": "lesson_001",
  "title": "Vuvuzela Saves the Day!",
  "subtitle": "Part 1",
  "level": "A1",
  "language": "en",
  "defaultAudioId": "main_en",
  "defaultChunkSetId": "short",
  "imageAssets": [],
  "audioAssets": [],
  "chunkProfiles": [],
  "vocabularySources": [],
  "paragraphs": [],
  "vocabulary": [],
  "explanations": []
}
```

필수 필드:

- `schemaVersion`
- `id`
- `title`
- `audioAssets`
- `paragraphs`

권장 필드:

- `defaultAudioId`: 본문 기본 오디오
- `defaultChunkSetId`: 앱 첫 진입 시 사용할 chunk 버전
- `imageAssets`: 표지, 단어장 사진 같은 이미지 파일 목록
- `chunkProfiles`: UI에서 보여줄 chunk 모드 목록
- `vocabularySources`: 단어장 사진/OCR/외부 단어장과 앱 단어의 연결
- `vocabulary`: 단어 뜻 사전
- `explanations`: 단어/문장/chunk 설명

## audioAssets

레슨에서 사용하는 모든 큰 오디오 파일을 등록합니다.

```json
"audioAssets": [
  {
    "id": "main_en",
    "file": "audio.mp3",
    "role": "main_narration",
    "language": "en",
    "durationMs": 71379
  },
  {
    "id": "explain_ko_all",
    "file": "explanations_ko.mp3",
    "role": "explanation",
    "language": "ko"
  },
  {
    "id": "slow_en",
    "file": "audio_slow.mp3",
    "role": "slow_narration",
    "language": "en"
  }
]
```

필수 필드:

- `id`: 오디오를 참조할 때 쓰는 고유 ID
- `file`: `lesson.json` 기준 상대 경로

권장 필드:

- `role`: `main_narration`, `explanation`, `slow_narration`, `example` 등
- `language`: `en`, `ko`
- `durationMs`: 전체 길이, 알 수 있으면 넣기

주의:

- 시간은 항상 정수 밀리초입니다. `3.419`초가 아니라 `3419`로 넣습니다.
- Android asset 재생 안정성을 위해 오디오는 `mp3`를 우선 권장합니다.
- 같은 파일 안의 특정 구간을 재생할 때도 먼저 `audioAssets`에 큰 파일을 등록하고, 아래의 `audioRef`에서 `startMs`, `endMs`로 구간을 지정합니다.

## imageAssets

레슨에서 사용하는 이미지 파일을 등록합니다. 표지, 단어장 사진, 본문 사진을 모두 이곳에 둘 수 있습니다.

```json
"imageAssets": [
  {
    "id": "vocab_page_001",
    "file": "vocab/page_001.jpg",
    "role": "vocabulary_source",
    "label": "Workbook p.12",
    "page": 1
  },
  {
    "id": "cover",
    "file": "cover.jpg",
    "role": "cover",
    "label": "Cover"
  }
]
```

필수 필드:

- `id`: 다른 곳에서 참조할 이미지 ID
- `file`: `lesson.json` 기준 상대 경로

권장 필드:

- `role`: `cover`, `vocabulary_source`, `body_source`, `worksheet` 등
- `label`: 사람이 보기 쉬운 이름
- `page`: 원본 교재 페이지나 사진 순서

단어장 사진이 없으면 `imageAssets` 자체를 생략하거나 빈 배열로 둡니다.

## vocabularySources

단어장을 사진, OCR 결과, 별도 표로 받았을 때 원본과 앱 단어를 연결하는 영역입니다. 앱 학습에는 `vocabulary`가 기준이고, `vocabularySources`는 “이 단어가 어느 사진/행에서 왔는지”를 보존하는 보조 정보입니다.

사진이 있을 때:

```json
"vocabularySources": [
  {
    "id": "vocab_sheet_001",
    "type": "image",
    "label": "Workbook vocabulary page 1",
    "imageId": "vocab_page_001",
    "items": [
      {
        "rowId": "vs001_r001",
        "vocabId": "w_friend",
        "sourceText": "friend",
        "meaningText": "A person you like and know well.",
        "rowIndex": 1,
        "box": { "x": 0.08, "y": 0.12, "w": 0.78, "h": 0.05 }
      }
    ]
  }
]
```

사진 없이 단어 표만 있을 때:

```json
"vocabularySources": [
  {
    "id": "vocab_table_001",
    "type": "table",
    "label": "Provided vocabulary list",
    "items": [
      {
        "rowId": "vt001_r001",
        "vocabId": "w_friend",
        "sourceText": "friend",
        "meaningText": "A person you like and know well.",
        "rowIndex": 1
      }
    ]
  }
]
```

규칙:

- `items[].vocabId`는 반드시 `vocabulary[].id` 중 하나와 같게 둡니다.
- 단어장 사진 좌표 `box`는 이미지 전체를 `0~1`로 본 상대 좌표입니다. 픽셀 좌표보다 기기 해상도 변화에 강합니다.
- OCR이 불확실하면 `sourceText`, `meaningText`에 원본 추정값을 두고, 앱에 표시할 최종 값은 `vocabulary`에 정리합니다.
- 단어장 사진이 없으면 이 섹션도 생략해도 됩니다.

## 단어 선정 원칙

`vocabulary`는 앱의 단어장/단어 테스트에 실제로 나올 학습 단어 목록입니다. 본문에서 찾을 수 있는 모든 단어를 넣는 사전이 아닙니다.

단어장 사진 또는 별도 단어표가 있을 때:

- `vocabulary`에는 사진/표에 명시된 단어만 넣습니다.
- 본문에서 해당 단어가 나오면 `annotations[].wordId`로 같은 `vocabulary[].id`에 연결합니다.
- 사진/표에 없는 단어는 아무리 어려워 보여도 자동으로 `vocabulary`에 추가하지 않습니다.
- 사진/표 원본 연결은 `imageAssets` + `vocabularySources` + `vocabulary[].sourceRefs`에 보존합니다.
- 사진 OCR이 불확실하면 원본 추정값은 `vocabularySources[].items[].sourceText`에 두고, 앱 표시용 최종 값은 `vocabulary[].word`, `easyEnglish`, `meaningKo`, `simpleKo`에 정리합니다.

단어장 사진 또는 별도 단어표가 없을 때:

- 본문에서 난이도 있는 핵심 단어만 골라 `vocabulary`에 넣습니다.
- 너무 쉬운 기능어, 대명사, 관사, 기본 동사는 제외합니다. 예: `the`, `a`, `I`, `you`, `is`, `are`, `have`, `do`, `go`, `make`, `get`
- 본문 이해에 꼭 필요하거나 아이가 막힐 가능성이 큰 단어를 우선합니다.
- 권장 개수는 짧은 글 8~15개, 긴 글 15~25개입니다.
- 같은 단어의 복수형/과거형은 가능하면 하나의 `lemma` 아래에 묶습니다. 예: `liquid`, `liquids`
- 자동 선정 단어도 반드시 본문 annotation과 연결합니다.

본문 하이라이트는 하고 싶지만 단어장 학습에는 넣고 싶지 않을 때:

- 그 항목을 `vocabulary`에 넣지 않습니다.
- 필요하면 `annotations`에서 `type: "phrase"` 또는 `type: "grammar"`로 표시하고, `explanationIds`만 연결합니다.
- 앱 단어장에 나오게 할 단어만 `type: "vocab"` + `wordId` + `vocabulary` 조합으로 만듭니다.

## chunkProfiles

앱에서 사용자가 선택할 chunk 구분 방식을 정의합니다.

```json
"chunkProfiles": [
  {
    "id": "short",
    "label": "짧게",
    "description": "3~4 words per chunk",
    "pauseBehavior": "after_each_chunk"
  },
  {
    "id": "long",
    "label": "길게",
    "description": "7+ words per chunk",
    "pauseBehavior": "after_each_chunk"
  },
  {
    "id": "sentence",
    "label": "문장",
    "description": "one sentence at a time",
    "pauseBehavior": "after_each_sentence"
  },
  {
    "id": "natural",
    "label": "자연스럽게",
    "description": "continuous playback",
    "pauseBehavior": "none"
  }
]
```

권장 `pauseBehavior` 값:

- `none`: 자연스럽게 이어 듣기
- `after_each_chunk`: chunk마다 멈춤
- `after_each_sentence`: 문장마다 멈춤
- `manual_next`: 사용자가 탭할 때 다음 구간 재생

## paragraphs

본문은 `paragraphs > sentences` 구조로 만듭니다. 앱은 이 구조를 책처럼 이어진 본문으로 보여줍니다.

```json
"paragraphs": [
  {
    "id": "p1",
    "type": "story",
    "sentences": []
  },
  {
    "id": "p2",
    "type": "vocabulary",
    "sentences": []
  }
]
```

권장 `type` 값:

- `story`: 이야기 본문
- `vocabulary`: 단어 코너
- `instruction`: 방법 설명
- `quiz`: 퀴즈
- `note`: 참고 설명

처음에는 `story`만 써도 됩니다.

## sentences

문장은 읽기 UI의 기본 단위입니다. 사용자가 본문을 누르면 해당 문장 전체를 먼저 읽어주는 것을 앱의 기본 동작으로 봅니다.

```json
{
  "id": "s1",
  "text": "The thief was scared and ran into the street.",
  "audioId": "main_en",
  "startMs": 99,
  "endMs": 2720,
  "chunkSets": [],
  "annotations": [],
  "explanationIds": ["exp_s1_basic"]
}
```

필수 필드:

- `id`
- `text`

권장 필드:

- `audioId`: 생략하면 `defaultAudioId` 사용
- `startMs`, `endMs`: 문장 전체 오디오 구간
- `chunkSets`: 여러 chunk 구분 버전
- `annotations`: 단어/표현/문법 표시 위치
- `explanationIds`: 문장 설명 연결

주의:

- `sentence.text`가 앱 화면에 보이는 원문입니다.
- 타임스탬프 기준도 `sentence.text`와 맞아야 합니다.
- 줄바꿈은 문단 단위로 처리하고, 한 문장 안에는 되도록 넣지 않습니다.

## chunkSets

같은 문장을 여러 방식으로 쪼갤 수 있게 합니다.

```json
"chunkSets": [
  {
    "id": "short",
    "label": "짧게",
    "chunks": [
      {
        "id": "s1_short_c1",
        "text": "The thief was",
        "startChar": 0,
        "endChar": 13,
        "startMs": 99,
        "endMs": 950
      },
      {
        "id": "s1_short_c2",
        "text": "scared and ran",
        "startChar": 14,
        "endChar": 28,
        "startMs": 950,
        "endMs": 1700
      },
      {
        "id": "s1_short_c3",
        "text": "into the street.",
        "startChar": 29,
        "endChar": 45,
        "startMs": 1700,
        "endMs": 2720
      }
    ]
  },
  {
    "id": "long",
    "label": "길게",
    "chunks": [
      {
        "id": "s1_long_c1",
        "text": "The thief was scared and ran",
        "startChar": 0,
        "endChar": 28,
        "startMs": 99,
        "endMs": 1700
      },
      {
        "id": "s1_long_c2",
        "text": "into the street.",
        "startChar": 29,
        "endChar": 45,
        "startMs": 1700,
        "endMs": 2720
      }
    ]
  }
]
```

필수 필드:

- `chunkSets[].id`
- `chunkSets[].chunks[].id`
- `chunkSets[].chunks[].text`

권장 필드:

- `startChar`, `endChar`
- `startMs`, `endMs`
- `audioId`: 특수한 경우에만 사용, 생략하면 문장의 `audioId` 사용
- `explanationIds`: chunk 설명이 있으면 연결

문자 위치 규칙:

- `startChar`는 포함입니다.
- `endChar`는 미포함입니다.
- 기준 문자열은 반드시 같은 문장의 `sentence.text`입니다.
- 예: `"The thief was"`는 `startChar: 0`, `endChar: 13`

주의:

- chunk의 `text`는 가능하면 `sentence.text.substring(startChar, endChar)`와 정확히 같게 만듭니다.
- 짧은 chunk, 긴 chunk 모두 같은 오디오 타임라인을 바라보게 하면 모드 전환이 자연스럽습니다.
- chunk끼리 시간 구간이 심하게 겹치거나 비면 하이라이트가 부자연스러울 수 있습니다.
- 문장 전체 재생은 `sentence.startMs ~ sentence.endMs`, 쉬어 듣기는 선택된 `chunkSet`의 chunk 구간을 사용합니다.

## annotations

본문 안에서 단어, 표현, 문법 포인트 등을 표시하기 위한 위치 정보입니다.

```json
"annotations": [
  {
    "id": "ann_s1_thief",
    "type": "vocab",
    "wordId": "w_thief",
    "text": "thief",
    "startChar": 4,
    "endChar": 9,
    "highlightStyle": "known_vocab",
    "explanationIds": ["exp_w_thief_basic"]
  },
  {
    "id": "ann_s1_ran_into",
    "type": "phrase",
    "text": "ran into",
    "startChar": 25,
    "endChar": 33,
    "highlightStyle": "phrase",
    "explanationIds": ["exp_phrase_ran_into"]
  }
]
```

권장 `type` 값:

- `vocab`: 단어
- `phrase`: 표현
- `grammar`: 문법
- `sentence_focus`: 문장 전체 포인트

`vocab` 타입은 가능하면 `wordId`로 `vocabulary` 항목에 연결합니다.

주의:

- 단어 뜻이 이미 준비된 항목은 `highlightStyle: "known_vocab"`로 두면 앱에서 초록색 하이라이트 대상으로 처리하기 좋습니다.
- 사용자가 직접 모르는 단어로 표시한 것과, 제작자가 미리 뜻을 넣어둔 단어는 스타일을 분리하는 것이 좋습니다.

## vocabulary

앱의 단어장/단어 테스트에 실제로 나올 학습 단어 목록입니다. 같은 단어가 여러 문장에 나와도 뜻 정보는 한 번만 둡니다.

중요:

- 단어장 사진/표가 있으면 `vocabulary`에는 그 사진/표에 있는 단어만 넣습니다.
- 단어장 사진/표가 없으면 본문에서 난이도 있는 핵심 단어만 추려 넣습니다.
- 본문에 하이라이트하고 싶다는 이유만으로 모든 단어를 `vocabulary`에 넣지 않습니다.
- `vocabulary`에 들어간 단어는 앱에서 단어장 학습과 단어 테스트 대상으로 간주합니다.

```json
"vocabulary": [
  {
    "id": "w_friend",
    "word": "friend",
    "lemma": "friend",
    "forms": ["friend", "friends"],
    "partOfSpeech": "noun",
    "easyEnglish": "A person you like and know well.",
    "meaningKo": "친구",
    "simpleKo": "좋아하고 잘 아는 사람",
    "examples": [
      "My best friend Paul and I were excited.",
      "A friend can help you do the right thing."
    ],
    "highlight": true,
    "highlightStyle": "known_vocab",
    "sourceRefs": [
      {
        "sourceId": "vocab_sheet_001",
        "rowId": "vs001_r001",
        "rowIndex": 1,
        "box": { "x": 0.08, "y": 0.12, "w": 0.78, "h": 0.05 }
      }
    ],
    "explanationIds": ["exp_w_friend_basic"]
  }
]
```

필수 권장 필드:

- `id`
- `word`
- `easyEnglish` 또는 `meaningKo`

확장 필드:

- `lemma`
- `forms`
- `partOfSpeech`
- `easyEnglish`
- `simpleKo`
- `examples`
- `synonyms`
- `antonyms`
- `sourceRefs`
- `explanationIds`

앱 동작 예시:

- `highlight: true`인 단어는 초록색으로 미리 표시
- 사용자가 길게 누르면 작은 팝업으로 `meaningKo`, `simpleKo`, `examples` 표시
- 팝업 안의 재생 버튼을 누르면 연결된 설명 오디오 재생

단어장 사진과 본문 하이라이트 연결 규칙:

- 본문 하이라이트는 `annotations[].wordId`로 `vocabulary[].id`를 바라봅니다.
- 단어장 사진/표는 `vocabularySources[].items[].vocabId`로 같은 `vocabulary[].id`를 바라봅니다.
- 즉, `annotations[].wordId == vocabulary[].id == vocabularySources[].items[].vocabId`가 되면 세 영역이 연결됩니다.
- 예: 본문에서 `friend`를 하이라이트한 annotation도 `w_friend`, 단어장 사진의 friend 행도 `w_friend`를 씁니다.
- 단어장 사진이 없으면 `sourceRefs`는 생략합니다. 이 경우 `vocabulary`는 자동 선정한 난이도 있는 단어 목록입니다.
- 단어장 사진/표가 있으면 `sourceRefs`가 없는 자동 선정 단어를 `vocabulary`에 섞지 않습니다.

## explanations

단어, 표현, 문장, chunk에 붙는 설명입니다. 설명 텍스트와 설명 오디오는 여기서 관리합니다.

```json
"explanations": [
  {
    "id": "exp_w_thief_basic",
    "targetType": "vocab",
    "targetId": "w_thief",
    "title": "thief 뜻",
    "textKo": "thief는 다른 사람의 물건을 몰래 가져가는 사람, 즉 도둑이라는 뜻이에요.",
    "audio": {
      "type": "segment",
      "audioId": "explain_ko_all",
      "startMs": 1200,
      "endMs": 5300
    }
  },
  {
    "id": "exp_s1_basic",
    "targetType": "sentence",
    "targetId": "s1",
    "title": "문장 설명",
    "textKo": "이 문장은 도둑이 겁을 먹고 거리로 뛰어나갔다는 뜻이에요.",
    "audio": {
      "type": "file",
      "file": "explanations/s1_basic.mp3"
    }
  }
]
```

권장 `targetType` 값:

- `vocab`
- `phrase`
- `sentence`
- `chunk`
- `paragraph`

설명 오디오 방식은 두 가지를 모두 지원하도록 설계합니다.

별도 파일:

```json
"audio": {
  "type": "file",
  "file": "explanations/w_thief.mp3"
}
```

한 오디오 파일 안의 특정 구간:

```json
"audio": {
  "type": "segment",
  "audioId": "explain_ko_all",
  "startMs": 1200,
  "endMs": 5300
}
```

주의:

- 설명 오디오가 한 파일에 몰려 있어도 괜찮습니다. `audioAssets`에 파일을 등록하고 각 설명에서 구간만 찍으면 됩니다.
- 별도 파일 방식과 구간 방식은 한 레슨 안에서 섞어 써도 됩니다.
- 설명 텍스트만 있고 오디오가 없을 수도 있습니다. 이 경우 `audio`를 생략합니다.

## 전체 예시

```json
{
  "schemaVersion": 1,
  "id": "lesson_001",
  "title": "Vuvuzela Saves the Day!",
  "subtitle": "Part 2",
  "level": "A1",
  "language": "en",
  "defaultAudioId": "main_en",
  "defaultChunkSetId": "short",
  "audioAssets": [
    {
      "id": "main_en",
      "file": "audio.mp3",
      "role": "main_narration",
      "language": "en",
      "durationMs": 71379
    },
    {
      "id": "explain_ko_all",
      "file": "explanations_ko.mp3",
      "role": "explanation",
      "language": "ko"
    }
  ],
  "chunkProfiles": [
    {
      "id": "short",
      "label": "짧게",
      "description": "3~4 words per chunk",
      "pauseBehavior": "after_each_chunk"
    },
    {
      "id": "long",
      "label": "길게",
      "description": "7+ words per chunk",
      "pauseBehavior": "after_each_chunk"
    },
    {
      "id": "natural",
      "label": "자연스럽게",
      "description": "continuous playback",
      "pauseBehavior": "none"
    }
  ],
  "paragraphs": [
    {
      "id": "p1",
      "type": "story",
      "sentences": [
        {
          "id": "s1",
          "text": "The thief was scared and ran into the street.",
          "audioId": "main_en",
          "startMs": 99,
          "endMs": 2720,
          "chunkSets": [
            {
              "id": "short",
              "label": "짧게",
              "chunks": [
                {
                  "id": "s1_short_c1",
                  "text": "The thief was",
                  "startChar": 0,
                  "endChar": 13,
                  "startMs": 99,
                  "endMs": 950
                },
                {
                  "id": "s1_short_c2",
                  "text": "scared and ran",
                  "startChar": 14,
                  "endChar": 28,
                  "startMs": 950,
                  "endMs": 1700
                },
                {
                  "id": "s1_short_c3",
                  "text": "into the street.",
                  "startChar": 29,
                  "endChar": 45,
                  "startMs": 1700,
                  "endMs": 2720
                }
              ]
            },
            {
              "id": "long",
              "label": "길게",
              "chunks": [
                {
                  "id": "s1_long_c1",
                  "text": "The thief was scared and ran",
                  "startChar": 0,
                  "endChar": 28,
                  "startMs": 99,
                  "endMs": 1700
                },
                {
                  "id": "s1_long_c2",
                  "text": "into the street.",
                  "startChar": 29,
                  "endChar": 45,
                  "startMs": 1700,
                  "endMs": 2720
                }
              ]
            }
          ],
          "annotations": [
            {
              "id": "ann_s1_thief",
              "type": "vocab",
              "wordId": "w_thief",
              "text": "thief",
              "startChar": 4,
              "endChar": 9,
              "highlightStyle": "known_vocab",
              "explanationIds": ["exp_w_thief_basic"]
            }
          ],
          "explanationIds": ["exp_s1_basic"]
        }
      ]
    }
  ],
  "vocabulary": [
    {
      "id": "w_thief",
      "word": "thief",
      "lemma": "thief",
      "partOfSpeech": "noun",
      "easyEnglish": "A person who steals things.",
      "meaningKo": "도둑",
      "simpleKo": "다른 사람의 물건을 몰래 가져가는 사람",
      "examples": [
        "The thief ran away."
      ],
      "highlight": true,
      "highlightStyle": "known_vocab",
      "explanationIds": ["exp_w_thief_basic"]
    }
  ],
  "explanations": [
    {
      "id": "exp_w_thief_basic",
      "targetType": "vocab",
      "targetId": "w_thief",
      "title": "thief 뜻",
      "textKo": "thief는 다른 사람의 물건을 몰래 가져가는 사람, 즉 도둑이라는 뜻이에요.",
      "audio": {
        "type": "segment",
        "audioId": "explain_ko_all",
        "startMs": 1200,
        "endMs": 5300
      }
    },
    {
      "id": "exp_s1_basic",
      "targetType": "sentence",
      "targetId": "s1",
      "title": "문장 설명",
      "textKo": "이 문장은 도둑이 겁을 먹고 거리로 뛰어나갔다는 뜻이에요."
    }
  ]
}
```

## 최소 양식

처음 테스트용으로는 아래만 있어도 됩니다.

```json
{
  "schemaVersion": 1,
  "id": "lesson_001",
  "title": "Sample Lesson",
  "defaultAudioId": "main_en",
  "defaultChunkSetId": "short",
  "audioAssets": [
    {
      "id": "main_en",
      "file": "audio.mp3",
      "role": "main_narration",
      "language": "en"
    }
  ],
  "chunkProfiles": [
    {
      "id": "short",
      "label": "짧게",
      "pauseBehavior": "after_each_chunk"
    },
    {
      "id": "natural",
      "label": "자연스럽게",
      "pauseBehavior": "none"
    }
  ],
  "paragraphs": [
    {
      "id": "p1",
      "type": "story",
      "sentences": [
        {
          "id": "s1",
          "text": "The thief was scared.",
          "startMs": 0,
          "endMs": 1800,
          "chunkSets": [
            {
              "id": "short",
              "chunks": [
                {
                  "id": "s1_short_c1",
                  "text": "The thief was scared.",
                  "startChar": 0,
                  "endChar": 22,
                  "startMs": 0,
                  "endMs": 1800
                }
              ]
            }
          ],
          "annotations": []
        }
      ]
    }
  ],
  "vocabulary": [],
  "explanations": []
}
```

## 앱 동작 기준

앱은 이 JSON을 다음처럼 해석하는 것을 목표로 합니다.

- 본문 표시는 `paragraphs[].sentences[].text`를 책처럼 이어서 보여준다.
- 사용자가 문장을 누르면 해당 문장의 `startMs ~ endMs`를 한 번 재생한다.
- 재생 버튼을 누르면 현재 선택된 문장 위치부터 레슨 끝까지 진행한다.
- 처음부터 듣기 버튼은 `0ms` 또는 첫 문장 시작점부터 재생한다.
- 자연스럽게 모드는 chunk 정지를 하지 않는다.
- 쉬어 듣기 모드는 현재 선택된 `chunkProfile`과 `chunkSet`을 기준으로 멈춘다.
- 현재 재생 위치에 맞는 chunk는 밑줄 또는 약한 하이라이트로 표시한다.
- 문장 전체는 더 옅은 배경으로 표시한다.
- `vocabulary.highlight == true`인 단어는 초록색 계열로 미리 표시한다.
- 단어를 길게 누르면 `vocabulary`와 `explanations`를 이용해 팝업을 보여준다.
- 단어장 단계는 `vocabulary[].word`, `easyEnglish`, `meaningKo`, `simpleKo`, `examples`를 우선 사용한다.
- 단어장 사진/표가 있으면 `vocabulary`에는 사진/표 단어만 들어간다.
- 단어장 사진/표가 없으면 `vocabulary`에는 자동 선정한 난이도 있는 핵심 단어만 들어간다.
- 단어장 사진이 있으면 `imageAssets`와 `vocabularySources`로 원본 연결을 보존한다.
- 설명 오디오는 별도 파일이든 한 파일의 구간이든 같은 재생 UI로 처리한다.

## 제작 시 주의사항

JSON 자체:

- 반드시 UTF-8로 저장합니다.
- 유효한 JSON이어야 합니다. trailing comma, 깨진 따옴표, 닫히지 않은 문자열이 있으면 앱에서 읽을 수 없습니다.
- 큰따옴표 안의 큰따옴표는 `\"`로 escape합니다.
- 줄바꿈은 JSON 문자열 안에서 `\n`으로 넣습니다.

텍스트:

- 앱에 보일 최종 원문은 `sentence.text`입니다.
- 생성 후 `sentence.text`를 수정하면 `startChar`, `endChar`, annotation 위치를 다시 계산해야 합니다.
- 스마트 따옴표, 깨진 인코딩 문자, 이상한 물음표 문자는 생성 단계에서 정리하는 것을 권장합니다.

시간:

- 모든 시간은 정수 밀리초입니다.
- `startMs < endMs`를 지켜야 합니다.
- 같은 오디오 안에서 문장 시간이 뒤로 되돌아가지 않게 합니다.
- chunk 시간이 문장 시간 범위를 크게 벗어나지 않게 합니다.
- 타임스탬프가 없는 레슨도 만들 수 있지만, 앱의 하이라이트 품질은 떨어집니다.

ID:

- `id`는 한 레슨 안에서 안정적으로 유지합니다.
- 앱이 학습 기록, 표시한 단어, 복습 상태를 저장할 때 ID를 기준으로 연결할 수 있습니다.
- 같은 단어라도 뜻이 다르면 `w_match_game`, `w_match_fire`처럼 분리합니다.

경로:

- 오디오/이미지는 절대 경로 대신 lesson 폴더 기준 상대 경로를 사용합니다.
- 예: `audio.mp3`, `explanations/w_thief.mp3`
- 예외적으로 `manifest.json`의 `path`는 assets 기준 경로를 사용합니다.

단어장 사진:

- 사진이 있으면 `imageAssets`에 이미지 파일을 등록하고, `vocabularySources`로 각 행을 `vocabId`에 연결합니다.
- 사진이 없으면 `imageAssets`, `vocabularySources`, `sourceRefs`를 모두 생략해도 됩니다.
- 사진 또는 별도 단어표가 있으면 그 단어들만 `vocabulary`에 넣습니다.
- 사진 또는 별도 단어표가 없을 때만 본문에서 난이도 있는 단어를 자동 선정합니다.
- 사진 좌표는 `{ "x": 0.0~1.0, "y": 0.0~1.0, "w": 0.0~1.0, "h": 0.0~1.0 }` 형식의 상대 좌표를 권장합니다.
- 최종 앱 표시용 뜻은 OCR 원문이 아니라 `vocabulary[].easyEnglish`, `meaningKo`, `simpleKo`에 정리합니다.

chunk:

- 여러 chunk 버전을 만들 때 `short`, `long`, `sentence` 같은 ID를 일관되게 사용합니다.
- `chunkProfiles[].id`와 각 문장의 `chunkSets[].id`는 맞아야 합니다.
- 모든 문장에 모든 chunkSet이 없을 수도 있지만, 가능하면 기본 chunkSet은 모든 문장에 넣습니다.

annotation:

- `startChar`, `endChar`는 `sentence.text` 기준입니다.
- `endChar`는 미포함입니다.
- 위치 계산이 불안하면 `text`를 함께 넣어 앱이나 검증기가 한 번 더 확인할 수 있게 합니다.
- 같은 단어가 한 문장에 여러 번 나오면 annotation을 각각 따로 만듭니다.

설명:

- 설명 텍스트만 먼저 넣고 오디오는 나중에 붙여도 됩니다.
- 설명 오디오를 한 파일에 몰아서 만들 경우, 각 설명의 시작/끝 시간이 정확해야 합니다.
- 설명 오디오 구간 앞뒤에 100~200ms 정도 여유를 두면 잘림이 덜 느껴집니다.

## 생성기 검증 체크리스트

다른 앱이나 Python에서 JSON을 만든 뒤 최소한 아래를 확인하세요.

- `json.loads()` 또는 동등한 파서로 정상 파싱되는가?
- `schemaVersion`, `id`, `title`, `audioAssets`, `paragraphs`가 있는가?
- 모든 `audioId`가 `audioAssets[].id`에 존재하는가?
- 모든 `explanationIds`가 `explanations[].id`에 존재하는가?
- 모든 `wordId`가 `vocabulary[].id`에 존재하는가?
- 모든 `vocabularySources[].items[].vocabId`가 `vocabulary[].id`에 존재하는가?
- 모든 `vocabulary[].sourceRefs[].sourceId`가 `vocabularySources[].id`에 존재하는가?
- 모든 `vocabularySources[].imageId`가 비어 있거나 `imageAssets[].id`에 존재하는가?
- 단어장 사진/표가 있을 때 `vocabulary`에 사진/표 밖의 자동 선정 단어가 섞이지 않았는가?
- 단어장 사진/표가 없을 때 `vocabulary`가 너무 쉬운 단어까지 과하게 포함하지 않았는가?
- 모든 `startMs`, `endMs`가 정수이고 `startMs < endMs`인가?
- 모든 `startChar`, `endChar`가 문장 길이 안에 있는가?
- `sentence.text.substring(startChar, endChar)`가 annotation/chunk의 `text`와 대체로 일치하는가?
- `chunkProfiles[].id`와 `chunkSets[].id`가 서로 대응되는가?
- 오디오 파일이 실제로 lesson 폴더에 존재하는가?

## 향후 확장 후보

나중에 필요하면 아래 필드를 추가할 수 있습니다.

```json
"review": {
  "difficulty": 2,
  "tags": ["story", "past-tense"],
  "quizIds": ["q1"]
}
```

```json
"recording": {
  "allowUserRecording": true,
  "compareTargetAudioId": "main_en"
}
```

```json
"translation": {
  "ko": "도둑은 겁을 먹고 거리로 뛰어나갔어요."
}
```

처음부터 모든 확장 필드를 만들 필요는 없습니다. 핵심은 `paragraphs > sentences > chunkSets`, `vocabulary`, `explanations`, `audioAssets` 네 축을 안정적으로 유지하는 것입니다.

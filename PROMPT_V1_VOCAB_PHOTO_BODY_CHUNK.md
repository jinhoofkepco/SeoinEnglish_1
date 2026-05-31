# Prompt V1: Vocab Photo + Body Only + Meaning Chunks

이 버전은 다음 상황 전용입니다.

- 단어장 사진을 그대로 단어 학습 기준으로 쓴다.
- 본문은 이야기/설명 본문 부분만 가져온다.
- 문제, 질문, 지시문, 첨부 설명, 사진 캡션, 페이지 안내 문구는 본문에서 제외한다.
- 타임스탬프는 만들지 않는다.
- 청크는 짧은/긴 버전으로 나누지 않고 `meaning` 한 종류만 만든다.
- 오디오는 생략한다.

최종 앱용 `lesson.json`은 아래 조각들을 합쳐 만든다.

```json
{
  "schemaVersion": 1,
  "id": "lesson_id_here",
  "title": "Title Here",
  "subtitle": "",
  "level": "A1",
  "language": "en",
  "defaultAudioId": "",
  "defaultChunkSetId": "meaning",
  "audioAssets": [],
  "imageAssets": [],
  "vocabularySources": [],
  "chunkProfiles": [
    {
      "id": "meaning",
      "label": "의미 단위",
      "description": "meaning-based chunks",
      "pauseBehavior": "manual_next"
    }
  ],
  "paragraphs": [],
  "vocabulary": [],
  "explanations": []
}
```

## 1단계: 단어 작업

단어장 사진 또는 단어장 OCR 결과를 GPT에 넣고 아래 프롬프트를 붙입니다.

```text
아래 단어장 사진/OCR 내용을 바탕으로 서인이 영어 앱용 단어 JSON 조각을 만들어줘.

목표:
- 단어장 사진에 있는 단어를 그대로 앱 단어장 학습 대상으로 만든다.
- 사진/OCR에 없는 단어는 절대 추가하지 않는다.
- 본문에서 어려워 보이는 단어를 예측해서 추가하지 않는다.
- 단어장 제목, 페이지 번호, 장식 문구는 단어로 넣지 않는다.

출력 JSON 구조:
{
  "imageAssets": [],
  "vocabularySources": [],
  "vocabulary": [],
  "explanations": []
}

규칙:
- 단어장 사진 파일명이 있으면 imageAssets에 넣는다.
- 사진 파일명이 없거나 OCR 텍스트만 있으면 imageAssets는 빈 배열로 둔다.
- vocabularySources에는 원본 단어장 행 정보를 넣는다.
- vocabularySources[].items[].vocabId는 반드시 vocabulary[].id와 같아야 한다.
- vocabulary[].id는 w_기본형 형식으로 만든다. 예: w_matter, w_container
- vocabulary[].word는 단어장에 보이는 단어 그대로 쓴다.
- vocabulary[].lemma는 기본형으로 쓴다.
- vocabulary[].forms는 단어장과 본문에서 연결될 수 있는 변형을 넣는다. 예: ["liquid", "liquids"]
- vocabulary[].easyEnglish는 아이가 이해할 쉬운 영어 뜻으로 쓴다.
- vocabulary[].meaningKo는 짧은 한국어 뜻으로 쓴다.
- vocabulary[].simpleKo는 쉬운 한국어 설명으로 쓴다.
- vocabulary[].examples는 있으면 1~2개만 넣고, 없으면 빈 배열로 둔다.
- vocabulary[].highlight는 true로 둔다.
- vocabulary[].highlightStyle은 "known_vocab"로 둔다.
- 각 단어마다 explanations에 targetType "vocab" 설명을 하나 만든다.
- explanation id는 exp_w_단어_basic 형식으로 만든다.

출력은 설명 없이 JSON 코드블록 하나만 줘.
JSON은 반드시 유효해야 하며 trailing comma를 쓰지 마.
```

권장 출력 예시:

```json
{
  "imageAssets": [
    {
      "id": "vocab_page_001",
      "file": "vocab/page_001.jpg",
      "role": "vocabulary_source",
      "label": "Vocabulary page 1",
      "page": 1
    }
  ],
  "vocabularySources": [
    {
      "id": "vocab_sheet_001",
      "type": "image",
      "label": "Vocabulary page 1",
      "imageId": "vocab_page_001",
      "items": [
        {
          "rowId": "vs001_r001",
          "vocabId": "w_matter",
          "sourceText": "matter",
          "meaningText": "solids, liquids, and gases",
          "rowIndex": 1
        }
      ]
    }
  ],
  "vocabulary": [
    {
      "id": "w_matter",
      "word": "matter",
      "lemma": "matter",
      "forms": ["matter"],
      "partOfSpeech": "noun",
      "easyEnglish": "What things are made of.",
      "meaningKo": "물질",
      "simpleKo": "공간을 차지하고 무게가 있는 것이에요.",
      "examples": [],
      "highlight": true,
      "highlightStyle": "known_vocab",
      "sourceRefs": [
        {
          "sourceId": "vocab_sheet_001",
          "rowId": "vs001_r001",
          "rowIndex": 1
        }
      ],
      "explanationIds": ["exp_w_matter_basic"]
    }
  ],
  "explanations": [
    {
      "id": "exp_w_matter_basic",
      "targetType": "vocab",
      "targetId": "w_matter",
      "title": "matter 뜻",
      "easyEnglish": "Matter is what things are made of.",
      "textKo": "matter는 물질이라는 뜻이에요. 공간을 차지하고 무게가 있는 것을 말해요.",
      "examples": []
    }
  ]
}
```

## 2단계: 본문 작업

본문 사진 또는 OCR 결과와 1단계에서 받은 `vocabulary`를 GPT에 넣고 아래 프롬프트를 붙입니다.

```text
아래 본문 사진/OCR 내용과 기존 vocabulary를 바탕으로 서인이 영어 앱용 본문 JSON 조각을 만들어줘.

목표:
- 본문에는 이야기/설명 본문만 넣는다.
- 단어장, 제목 "NEW WORDS", 문제, 질문, 지시문, 첨부 설명, 사진 캡션, 페이지 안내 문구는 paragraphs에 넣지 않는다.
- 본문 문장 안에 기존 vocabulary 단어가 나오면 annotations로 연결한다.
- 하이라이트해야 할 핵심 단어가 기존 vocabulary에 없으면 vocabularyAdditions와 explanationAdditions에 따로 추가한다.
- 단어장에 없다는 이유만으로 모든 어려운 단어를 추가하지 말고, 본문 이해에 꼭 필요한 핵심 단어만 추가한다.

출력 JSON 구조:
{
  "paragraphs": [],
  "vocabularyAdditions": [],
  "explanationAdditions": []
}

본문 규칙:
- paragraphs[].type은 "story"로 둔다.
- 문장은 sentences[]에 문장 단위로 나눈다.
- 문장 id는 s1, s2, s3 순서로 만든다.
- 타임스탬프 startMs/endMs는 만들지 않는다.
- chunkSets는 아직 만들지 않는다. 3단계에서 만든다.
- annotations는 단어 위치에 정확히 넣는다.
- startChar/endChar는 sentence.text 기준이고, endChar는 미포함이다.
- annotation.text는 sentence.text.substring(startChar, endChar)와 정확히 같아야 한다.
- 기존 vocabulary에 있는 단어는 annotations[].wordId로 해당 id를 쓴다.
- 단어의 복수형/과거형이 forms에 있으면 같은 wordId로 연결한다.

제외 규칙:
- "Answer the questions", "Look and write", "Circle", "Read and answer" 같은 문제/지시문은 제외한다.
- 글 마지막의 comprehension questions는 제외한다.
- 그림 설명, 출처, 페이지 번호, 단원 안내 문구는 제외한다.
- 단어장 행 자체는 제외한다.

출력은 설명 없이 JSON 코드블록 하나만 줘.
JSON은 반드시 유효해야 하며 trailing comma를 쓰지 마.
```

권장 출력 예시:

```json
{
  "paragraphs": [
    {
      "id": "p1",
      "type": "story",
      "sentences": [
        {
          "id": "s1",
          "text": "All things are made of matter.",
          "audioId": "",
          "chunkSets": [],
          "annotations": [
            {
              "id": "ann_s1_matter_1",
              "type": "vocab",
              "wordId": "w_matter",
              "text": "matter",
              "startChar": 23,
              "endChar": 29,
              "highlightStyle": "known_vocab",
              "explanationIds": ["exp_w_matter_basic"]
            }
          ]
        }
      ]
    }
  ],
  "vocabularyAdditions": [],
  "explanationAdditions": []
}
```

## 3단계: 청크 작업

2단계에서 받은 `paragraphs`를 GPT에 넣고 아래 프롬프트를 붙입니다.

```text
아래 paragraphs JSON의 각 sentence에 의미 단위 chunkSets를 추가해줘.

목표:
- 청크는 짧은/긴 버전으로 나누지 않는다.
- chunkSets는 오직 id "meaning" 한 종류만 만든다.
- 타임스탬프 startMs/endMs는 만들지 않는다.
- 각 청크는 의미 단위로 자연스럽게 끊는다.

출력 JSON 구조:
{
  "chunkProfiles": [
    {
      "id": "meaning",
      "label": "의미 단위",
      "description": "meaning-based chunks",
      "pauseBehavior": "manual_next"
    }
  ],
  "paragraphs": []
}

청크 규칙:
- 각 sentence.chunkSets에 아래 구조를 넣는다.
  {
    "id": "meaning",
    "label": "의미 단위",
    "chunks": []
  }
- chunk id는 문장 id를 포함해서 만든다. 예: s1_meaning_c1
- chunk.text는 반드시 sentence.text의 일부와 정확히 같아야 한다.
- startChar/endChar는 sentence.text 기준이고, endChar는 미포함이다.
- 청크 사이에 문자가 빠지거나 겹치면 안 된다.
- 문장부호는 앞 의미 단위에 붙이는 것을 기본으로 한다.

의미 단위로 자를 때 주의:
- 관사, 전치사, 소유격, 형용사만 따로 떼지 않는다.
- 주어와 짧은 동사는 가능하면 함께 둔다.
- 전치사구는 너무 짧게 쪼개지 않는다. 예: "in your classroom"은 한 덩어리로 둔다.
- 숙어/구동사는 쪼개지 않는다. 예: "take up", "fill up", "spread out"
- 명사구는 가능하면 유지한다. 예: "three different states", "a shape of its own"
- 의미가 죽을 정도로 길게 만들지는 않는다.
- 초등 학습용이므로 한 청크는 보통 2~7단어 정도가 좋지만, 의미가 우선이다.

출력은 설명 없이 JSON 코드블록 하나만 줘.
JSON은 반드시 유효해야 하며 trailing comma를 쓰지 마.
```

권장 출력 예시:

```json
{
  "chunkProfiles": [
    {
      "id": "meaning",
      "label": "의미 단위",
      "description": "meaning-based chunks",
      "pauseBehavior": "manual_next"
    }
  ],
  "paragraphs": [
    {
      "id": "p1",
      "type": "story",
      "sentences": [
        {
          "id": "s1",
          "text": "All things are made of matter.",
          "audioId": "",
          "chunkSets": [
            {
              "id": "meaning",
              "label": "의미 단위",
              "chunks": [
                {
                  "id": "s1_meaning_c1",
                  "text": "All things",
                  "startChar": 0,
                  "endChar": 10
                },
                {
                  "id": "s1_meaning_c2",
                  "text": "are made of matter.",
                  "startChar": 11,
                  "endChar": 30
                }
              ]
            }
          ],
          "annotations": [
            {
              "id": "ann_s1_matter_1",
              "type": "vocab",
              "wordId": "w_matter",
              "text": "matter",
              "startChar": 23,
              "endChar": 29,
              "highlightStyle": "known_vocab",
              "explanationIds": ["exp_w_matter_basic"]
            }
          ]
        }
      ]
    }
  ]
}
```

## 합치기 규칙

최종 `lesson.json`을 만들 때는 다음처럼 합칩니다.

- 1단계의 `imageAssets`, `vocabularySources`, `vocabulary`, `explanations`를 사용한다.
- 2단계의 `vocabularyAdditions`가 있으면 `vocabulary` 뒤에 추가한다.
- 2단계의 `explanationAdditions`가 있으면 `explanations` 뒤에 추가한다.
- 3단계의 `chunkProfiles`, `paragraphs`를 사용한다.
- `audioAssets`는 빈 배열로 둔다.
- `defaultAudioId`는 빈 문자열로 둔다.
- `defaultChunkSetId`는 `"meaning"`으로 둔다.


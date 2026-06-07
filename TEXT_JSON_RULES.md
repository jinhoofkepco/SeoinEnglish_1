# 지문 JSON 규칙

## 프롬프트

본문 사진에서 이야기 본문만 뽑아 아래 문법에 맞춰 `paragraphs` JSON을 만들어줘.
문제, 지시문, 해설, 페이지 번호, 그림 설명은 제외해줘.
각 문장은 의미 단위로 `short` chunk를 나누고, `startChar`와 `endChar`를 정확히 맞춰줘.
질문은 내용 있는 chunk만 정답으로 골라 `comprehensionChecks`에 넣어줘.

---

## 문법

### paragraphs

```json
{
  "paragraphs": [
    {
      "id": "p1",
      "type": "story",
      "sentences": [
        {
          "id": "s1",
          "text": "Dad hung a small white object on the wall.",
          "audioId": "",
          "chunkSets": [],
          "annotations": []
        }
      ]
    }
  ]
}
```

### sentence 필드 규칙

- `id`: `s1`, `s2`, `s3`처럼 본문 순서대로.
- `text`: 문장 원문.
- `audioId`: 오디오가 없으면 `""`.
- `chunkSets`: 문장 단위와 의미 단위 chunk.
- `annotations`: 단어 하이라이트 연결.

### chunkSets

```json
{
  "chunkSets": [
    {
      "id": "short",
      "label": "Meaning chunks",
      "chunks": [
        {
          "id": "s1_short_c1",
          "text": "Dad",
          "startChar": 0,
          "endChar": 3
        },
        {
          "id": "s1_short_c2",
          "text": "hung",
          "startChar": 4,
          "endChar": 8
        }
      ]
    },
    {
      "id": "sentence",
      "label": "Sentence",
      "chunks": [
        {
          "id": "s1_sentence_c1",
          "text": "Dad hung a small white object on the wall.",
          "startChar": 0,
          "endChar": 45
        }
      ]
    }
  ]
}
```

### chunk 규칙

- `startChar`는 포함.
- `endChar`는 미포함.
- `text.substring(startChar, endChar)`가 chunk의 `text`와 정확히 같아야 한다.
- 공백, 쉼표, 마침표도 문자 수에 포함한다.
- `short` chunk는 의미 단위로 자른다.
- `sentence` chunk는 문장 전체 하나로 만든다.

### annotations

```json
{
  "annotations": [
    {
      "id": "ann_s1_word",
      "type": "vocab",
      "wordId": "w_word",
      "text": "word",
      "startChar": 4,
      "endChar": 8,
      "explanationIds": []
    }
  ]
}
```

### annotation 규칙

- `wordId`는 `vocabulary[].id`와 같아야 한다.
- 본문에 단어장 단어가 나오면 연결한다.
- 단어장에 없는 중요 단어를 하이라이트하려면 `vocabulary`에 먼저 추가한다.

### comprehensionChecks

```json
{
  "comprehensionChecks": [
    {
      "id": "q_s1_1",
      "question": "What did Dad hang?",
      "sentenceId": "s1",
      "afterSentenceId": "s1",
      "chunkSetId": "short",
      "answerChunkId": "s1_short_c3",
      "answerText": "a small white object",
      "scopeSentenceIds": ["s1"],
      "promptNote": ""
    }
  ]
}
```

### 질문 규칙

- `answerChunkId`는 실제 존재하는 chunk id여야 한다.
- `chunkSetId`는 보통 `"short"`를 사용한다.
- 정답은 내용 있는 chunk만 사용한다.
- 대명사, 접속사, 조동사 단독 chunk는 정답으로 쓰지 않는다.
- `sentenceId`: 질문이 걸리는 대표 문장.
- `afterSentenceId`: 이 문장 뒤에 질문을 띄운다.
- `scopeSentenceIds`: 답 후보로 보여줄 문장 범위.
- `answerText`: 정답 chunk의 텍스트와 같게 쓴다.

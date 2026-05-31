아래 자료를 바탕으로 서인이 영어 앱용 `lesson.json`을 만들어줘.

중요한 단어 선정 규칙:

1. 내가 단어장 사진 또는 별도 단어표를 같이 주면, `vocabulary`에는 그 단어장/표에 명시된 단어만 넣어.
2. 단어장 사진/표가 있을 때는 본문에서 어려워 보이는 단어를 자동으로 추가하지 마.
3. 단어장 사진/표가 없을 때만 본문에서 난이도 있는 핵심 단어를 골라 `vocabulary`에 넣어.
4. 자동 선정할 때는 너무 쉬운 기능어, 대명사, 관사, 기본 동사를 빼. 예: `the`, `a`, `I`, `you`, `is`, `are`, `have`, `do`, `go`, `make`, `get`
5. 자동 선정 권장 개수는 짧은 글 8~15개, 긴 글 15~25개야.
6. `vocabulary`에 들어간 단어는 앱의 단어장 학습과 단어 테스트 대상으로 간주해.
7. 본문에서 하이라이트하고 싶지만 단어장 학습에는 넣고 싶지 않은 항목은 `vocabulary`에 넣지 말고, 필요하면 `annotations`의 `type: "phrase"` 또는 `type: "grammar"`와 `explanationIds`로만 처리해.

JSON 구조 규칙:

- 최상위 필드는 `schemaVersion`, `id`, `title`, `subtitle`, `level`, `language`, `defaultAudioId`, `defaultChunkSetId`, `audioAssets`, `chunkProfiles`, `paragraphs`, `vocabulary`, `explanations`를 사용해.
- 음원 파일이 없으면 `audioAssets`는 빈 배열로 둬. 없는 `audio.mp3`를 참조하지 마.
- 단어장 사진이 있으면 `imageAssets`, `vocabularySources`, `vocabulary[].sourceRefs`를 사용해 원본 사진/행과 단어를 연결해.
- 단어장 사진이 없으면 `imageAssets`, `vocabularySources`, `sourceRefs`는 생략하거나 빈 배열로 둬.
- 본문은 `paragraphs[].sentences[].text`에 문장 단위로 넣어.
- 단어장 제목, "NEW WORDS", 단어장 행 자체는 본문 story 문장에 섞지 마. 단어장 정보는 `vocabulary`와 `vocabularySources`에만 넣어.
- `annotations[].wordId`는 반드시 `vocabulary[].id`와 일치해야 해.
- 단어장 사진/표가 있는 경우, 사진/표의 각 단어 `vocabId`도 같은 `vocabulary[].id`를 바라보게 해.
- 모든 `startChar`, `endChar`는 해당 `sentence.text` 기준이고, `endChar`는 미포함이야.
- 타임스탬프가 없으면 `startMs`, `endMs`는 생략해도 돼.
- `chunkSets`는 최소한 `sentence` 단위로 만들어줘. 타임스탬프가 없으면 `startMs`, `endMs` 없이 `startChar`, `endChar`, `text`만 넣어.

단어 항목 규칙:

- `id`: `w_lemma` 형식. 예: `w_matter`, `w_container`
- `word`: 앱에 보여줄 단어
- `lemma`: 기본형
- `forms`: 본문에 나온 변형들
- `partOfSpeech`: 가능하면 작성
- `easyEnglish`: 아이가 이해할 쉬운 영어 뜻
- `meaningKo`: 짧은 한국어 뜻
- `simpleKo`: 쉬운 한국어 설명
- `examples`: 본문 또는 쉬운 예문 1~2개
- `highlight`: true
- `highlightStyle`: "known_vocab"
- `explanationIds`: 해당 단어 설명 ID

출력 형식:

- 설명 없이 `lesson.json` 내용만 코드블록으로 출력해.
- JSON은 반드시 유효해야 해. trailing comma 금지.
- 한국어와 영어는 UTF-8 문자열로 자연스럽게 써.

이제 내가 아래에 본문과, 있을 경우 단어장 사진/OCR/단어표 내용을 붙일게.

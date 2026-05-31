const fs = require("fs");
const path = require("path");

const sourceDir = process.argv[2];
const outputDir = process.argv[3];
const lessonId = process.argv[4] || "vuvuzela_175616";

if (!sourceDir || !outputDir) {
  console.error("Usage: node tools/generate_lesson_from_aligned_chunks.js <sourceDir> <outputDir> [lessonId]");
  process.exit(1);
}

const sourceLesson = JSON.parse(fs.readFileSync(path.join(sourceDir, "lesson.json"), "utf8"));
const timestamps = JSON.parse(fs.readFileSync(path.join(sourceDir, "timestamps_from_web.json"), "utf8"));
const timedWords = (timestamps.items || []).map((item, index) => ({
  index,
  text: String(item.text || ""),
  startMs: Math.round(Number(item.start || 0) * 1000),
  endMs: Math.round(Number(item.end || 0) * 1000)
}));

const vocabSeeds = [
  ["match", "match", "noun", "경기", "a sports game"],
  ["practice", "practice", "verb", "연습하다", "to try to get better at doing something"],
  ["suddenly", "suddenly", "adverb", "갑자기", "happening at once"],
  ["neighbor", "neighbor", "noun", "이웃", "the person who lives near you"],
  ["thief", "thief", "noun", "도둑", "a person who takes things from others"],
  ["adult", "adult", "noun", "어른", "a man or a woman; not a child"],
  ["save", "save", "verb", "구하다", "to stop something bad from happening"],
  ["saved", "save", "verb", "구했다", "stopped something bad from happening"],
  ["hero", "hero", "noun", "영웅", "someone who does amazing things"],
  ["heroes", "hero", "noun", "영웅들", "people who do amazing things"],
  ["village", "village", "noun", "마을", "a small town"],
  ["villagers", "villager", "noun", "마을 사람들", "people who live in a village"],
  ["vuvuzela", "vuvuzela", "noun", "부부젤라", "a long horn that makes a loud sound"],
  ["imagine", "imagine", "verb", "상상하다", "to make a picture in your mind"],
  ["breathe", "breathe", "verb", "숨 쉬다", "to take air in and out"],
  ["blow", "blow", "verb", "불다", "to push air out of your mouth"],
  ["narrow", "narrow", "adjective", "좁은", "not wide"],
  ["cheeks", "cheek", "noun", "볼", "the soft parts of your face"],
  ["quiz", "quiz", "noun", "퀴즈", "a short test"],
  ["beginning", "beginning", "noun", "처음", "the first part"]
];

const vocabulary = vocabSeeds.map(([surface, lemma, partOfSpeech, meaningKo, easyEnglish]) => {
  const id = `w_${lemma}`;
  return {
    id,
    word: surface,
    lemma,
    partOfSpeech,
    meaningKo,
    simpleKo: meaningKo,
    easyEnglish,
    highlight: true,
    highlightStyle: "known_vocab",
    explanationIds: [`exp_${id}_basic`]
  };
});

const vocabBySurface = new Map();
for (const entry of vocabulary) {
  vocabBySurface.set(normalizeWord(entry.word), entry);
  vocabBySurface.set(normalizeWord(entry.lemma), entry);
}

function normalizeWord(text) {
  return String(text || "")
    .toLowerCase()
    .replace(/[“”]/g, "\"")
    .replace(/^[^a-z0-9']+/, "")
    .replace(/[^a-z0-9']+$/, "")
    .replace("'s", "");
}

function displayTokens(text) {
  const tokens = [];
  const re = /[A-Za-z0-9]+(?:'[A-Za-z0-9]+)?/g;
  let match;
  while ((match = re.exec(text))) {
    tokens.push({
      text: match[0],
      startChar: match.index,
      endChar: match.index + match[0].length
    });
  }
  return tokens;
}

function wordsForChunk(chunk) {
  const startMs = Math.round(Number(chunk.start_time_seconds) * 1000);
  const endMs = Math.round(Number(chunk.end_time_seconds) * 1000);
  return timedWords.filter((word) => word.startMs >= startMs - 20 && word.endMs <= endMs + 20);
}

function chunkByDisplayWords(sentenceId, sentenceText, tokenWords, audioWords, profileId, size, sentenceStartMs, sentenceEndMs) {
  const chunks = [];
  for (let i = 0; i < tokenWords.length; i += size) {
    const displayGroup = tokenWords.slice(i, i + size);
    const audioGroup = audioWords.slice(i, i + size);
    const firstDisplay = displayGroup[0];
    const lastDisplay = displayGroup[displayGroup.length - 1];
    const firstAudio = audioGroup[0] || audioWords[0];
    const lastAudio = audioGroup[audioGroup.length - 1] || audioWords[audioWords.length - 1];
    chunks.push({
      id: `${sentenceId}_${profileId}_c${chunks.length + 1}`,
      text: sentenceText.slice(firstDisplay.startChar, lastDisplay.endChar),
      startChar: firstDisplay.startChar,
      endChar: lastDisplay.endChar,
      startMs: firstAudio ? firstAudio.startMs : sentenceStartMs,
      endMs: lastAudio ? lastAudio.endMs : sentenceEndMs,
      timingQuality: audioGroup.length === displayGroup.length ? "word_aligned" : "chunk_fallback"
    });
  }
  return chunks;
}

function annotationsForSentence(sentenceId, tokens) {
  const counts = new Map();
  const annotations = [];
  for (const token of tokens) {
    const key = normalizeWord(token.text);
    const vocab = vocabBySurface.get(key);
    if (!vocab) continue;
    const count = (counts.get(key) || 0) + 1;
    counts.set(key, count);
    annotations.push({
      id: `ann_${sentenceId}_${vocab.id}_${count}`,
      type: "vocab",
      wordId: vocab.id,
      text: token.text,
      startChar: token.startChar,
      endChar: token.endChar,
      highlightStyle: "known_vocab",
      explanationIds: [`exp_${vocab.id}_basic`]
    });
  }
  return annotations;
}

function shouldStartParagraph(chunk, previousChunk) {
  if (!previousChunk) return true;
  const text = String(chunk.text || "");
  const gapMs = Math.round((Number(chunk.start_time_seconds) - Number(previousChunk.end_time_seconds)) * 1000);
  return gapMs >= 900 || /^How to\b/i.test(text) || /^TIME FOR A QUIZ/i.test(text);
}

const paragraphs = [];
let paragraph = null;
let previousChunk = null;

(sourceLesson.chunks || []).forEach((chunk, index) => {
  if (shouldStartParagraph(chunk, previousChunk)) {
    paragraph = {
      id: `p${paragraphs.length + 1}`,
      type: /^TIME FOR A QUIZ|Where did|Find answer/i.test(String(chunk.text || "")) ? "quiz" : "story",
      sentences: []
    };
    paragraphs.push(paragraph);
  }

  const id = `s${index + 1}`;
  const text = String(chunk.text || "");
  const startMs = Math.round(Number(chunk.start_time_seconds) * 1000);
  const endMs = Math.round(Number(chunk.end_time_seconds) * 1000);
  const tokens = displayTokens(text);
  const audioWords = wordsForChunk(chunk);

  paragraph.sentences.push({
    id,
    text,
    audioId: "main_en",
    startMs,
    endMs,
    matchQuality: chunk.match_quality || "",
    chunkSets: [
      {
        id: "short",
        label: "2단어",
        chunks: chunkByDisplayWords(id, text, tokens, audioWords, "short", 2, startMs, endMs)
      },
      {
        id: "long",
        label: "4단어",
        chunks: chunkByDisplayWords(id, text, tokens, audioWords, "long", 4, startMs, endMs)
      },
      {
        id: "sentence",
        label: "문장",
        chunks: [
          {
            id: `${id}_sentence_c1`,
            text,
            startChar: 0,
            endChar: text.length,
            startMs,
            endMs,
            timingQuality: chunk.match_quality || "sentence"
          }
        ]
      }
    ],
    annotations: annotationsForSentence(id, tokens),
    explanationIds: []
  });

  previousChunk = chunk;
});

const explanations = vocabulary.map((entry) => ({
  id: `exp_${entry.id}_basic`,
  targetType: "vocab",
  targetId: entry.id,
  title: `${entry.word} 뜻`,
  easyEnglish: entry.easyEnglish,
  textKo: `${entry.word}: ${entry.meaningKo}. ${entry.easyEnglish}`
}));

const durationMs = timedWords.length ? timedWords[timedWords.length - 1].endMs : null;

const lesson = {
  schemaVersion: 1,
  id: lessonId,
  title: "Lerato and Her Vuvuzela",
  subtitle: "Aligned chunk test",
  level: "A1",
  language: "en",
  defaultAudioId: "main_en",
  defaultChunkSetId: "short",
  audioAssets: [
    {
      id: "main_en",
      file: "audio.mp3",
      role: "main_narration",
      language: "en",
      durationMs
    }
  ],
  chunkProfiles: [
    {
      id: "natural",
      label: "자연스럽게",
      description: "continuous playback",
      pauseBehavior: "none"
    },
    {
      id: "short",
      label: "2단어",
      description: "two display words per chunk for timing checks",
      pauseBehavior: "after_each_chunk"
    },
    {
      id: "long",
      label: "4단어",
      description: "four display words per chunk for timing checks",
      pauseBehavior: "after_each_chunk"
    },
    {
      id: "sentence",
      label: "문장",
      description: "one aligned source chunk at a time",
      pauseBehavior: "after_each_sentence"
    }
  ],
  paragraphs,
  vocabulary,
  explanations,
  source: {
    sourceDir,
    sourceLesson: "lesson.json",
    timestamps: "timestamps_from_web.json",
    chunkBasis: "aligned source lesson chunks",
    shortChunkWords: 2,
    longChunkWords: 4
  }
};

fs.mkdirSync(outputDir, { recursive: true });
fs.copyFileSync(path.join(sourceDir, "audio.mp3"), path.join(outputDir, "audio.mp3"));
fs.writeFileSync(path.join(outputDir, "lesson.json"), `${JSON.stringify(lesson, null, 2)}\n`, "utf8");

console.log(`Wrote ${path.join(outputDir, "lesson.json")}`);
console.log(`Paragraphs: ${paragraphs.length}`);
console.log(`Source chunks as sentences: ${(sourceLesson.chunks || []).length}`);
console.log(`Timed words: ${timedWords.length}`);

const fs = require("fs");
const path = require("path");

const sourceDir = process.argv[2];
const outputDir = process.argv[3];

if (!sourceDir || !outputDir) {
  console.error("Usage: node tools/generate_lesson_from_timestamps.js <sourceDir> <outputDir>");
  process.exit(1);
}

const timestampsPath = path.join(sourceDir, "timestamps_from_web.json");
const timestamps = JSON.parse(fs.readFileSync(timestampsPath, "utf8"));
const rawItems = timestamps.items || [];

const vocabularySeeds = [
  ["friend", "friend", "noun", "친구", "A person you like and know well."],
  ["excited", "excited", "adjective", "신난", "Very happy about something that will happen."],
  ["school", "school", "noun", "학교", "A place where children learn."],
  ["park", "park", "noun", "공원", "A place outside where people can play or rest."],
  ["fort", "fort", "noun", "놀이 요새", "A small strong place or play structure."],
  ["department", "department", "noun", "부서", "A group that does a special job."],
  ["choose", "choose", "verb", "고르다", "To pick one thing from two or more things."],
  ["equipment", "equipment", "noun", "장비", "Things people use for an activity."],
  ["build", "build", "verb", "만들다", "To make something by putting parts together."],
  ["votes", "vote", "noun", "표", "Choices people make by voting."],
  ["raced", "race", "verb", "경주했다", "Ran fast to try to win."],
  ["tower", "tower", "noun", "탑", "A tall part of a building or playground."],
  ["champion", "champion", "noun", "우승자", "A person who wins."],
  ["shouted", "shout", "verb", "외쳤다", "Said something in a loud voice."],
  ["someone", "someone", "pronoun", "누군가", "A person, but we do not know who."],
  ["newest", "new", "adjective", "가장 새로운", "The most new."],
  ["raised", "raise", "verb", "올렸다", "Moved something up."],
  ["eyebrows", "eyebrow", "noun", "눈썹", "The hair above your eyes."],
  ["thoughtful", "thoughtful", "adjective", "생각이 깊은", "Thinking carefully."],
  ["responsibility", "responsibility", "noun", "책임", "Something you should do."],
  ["return", "return", "verb", "돌려주다", "To give something back."],
  ["duty", "duty", "noun", "의무", "Something you should do because it is right."],
  ["saying", "saying", "noun", "속담", "A short sentence people often say."],
  ["rights", "right", "noun", "권리", "Things you are allowed to do or have."],
  ["claiming", "claim", "verb", "주장하는", "Saying something is yours."],
  ["wrong", "wrong", "adjective", "옳지 않은", "Not right or not fair."],
  ["issues", "issue", "noun", "문제", "Problems or things to solve."],
  ["advice", "advice", "noun", "조언", "Helpful ideas about what to do."],
  ["volunteered", "volunteer", "verb", "자진해서 말했다", "Offered to do or say something."],
  ["offered", "offer", "verb", "제안했다", "Said you would give or do something."],
  ["determined", "determine", "verb", "결정했다", "Decided after thinking."],
  ["delighted", "delighted", "adjective", "기쁜", "Very happy."],
  ["owner", "owner", "noun", "주인", "The person something belongs to."],
  ["hopeless", "hopeless", "adjective", "절망한", "Very sad because there seems to be no hope."],
  ["wailed", "wail", "verb", "울부짖었다", "Cried or said something loudly and sadly."],
  ["careful", "careful", "adjective", "조심하는", "Trying not to make a mistake."],
  ["returned", "return", "verb", "돌려주었다", "Gave something back."],
  ["promise", "promise", "noun", "약속", "A serious statement that you will do something."],
  ["vow", "vow", "noun", "맹세", "A strong promise."],
  ["citizens", "citizen", "noun", "시민", "People who belong to a community."]
];

const vocabBySurface = new Map();
const vocabulary = vocabularySeeds.map(([word, lemma, partOfSpeech, meaningKo, easyEnglish]) => {
  const id = `w_${word.toLowerCase().replace(/[^a-z0-9]+/g, "_")}`;
  vocabBySurface.set(word.toLowerCase(), { id, word, lemma, partOfSpeech, meaningKo, easyEnglish });
  return {
    id,
    word,
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

function normalizeWord(text) {
  return text
    .toLowerCase()
    .replace(/^[^a-z0-9']+/, "")
    .replace(/[^a-z0-9']+$/, "")
    .replace("'s", "");
}

function isSentenceEnd(text) {
  return /[.!?]["')\]]*$/.test(text);
}

function sentenceTextAndRanges(words) {
  let text = "";
  const ranged = [];
  for (const word of words) {
    const spacer = text.length === 0 ? "" : " ";
    const startChar = text.length + spacer.length;
    text += spacer + word.text;
    const endChar = text.length;
    ranged.push({ ...word, startChar, endChar });
  }
  return { text, ranged };
}

function chunkByWordCount(sentenceId, sentenceText, words, profileId, size) {
  const chunks = [];
  for (let i = 0; i < words.length; i += size) {
    const group = words.slice(i, i + size);
    const first = group[0];
    const last = group[group.length - 1];
    chunks.push({
      id: `${sentenceId}_${profileId}_c${chunks.length + 1}`,
      text: sentenceText.slice(first.startChar, last.endChar),
      startChar: first.startChar,
      endChar: last.endChar,
      startMs: first.startMs,
      endMs: last.endMs,
      timingQuality: "word_aligned"
    });
  }
  return chunks;
}

function annotationsForSentence(sentenceId, words) {
  const annotations = [];
  const counts = new Map();
  for (const word of words) {
    const key = normalizeWord(word.text);
    const vocab = vocabBySurface.get(key);
    if (!vocab) continue;
    const next = (counts.get(key) || 0) + 1;
    counts.set(key, next);
    annotations.push({
      id: `ann_${sentenceId}_${vocab.id}_${next}`,
      type: "vocab",
      wordId: vocab.id,
      text: word.text.replace(/^[^a-zA-Z0-9']+|[^a-zA-Z0-9']+$/g, ""),
      startChar: word.startChar,
      endChar: word.endChar,
      highlightStyle: "known_vocab",
      explanationIds: [`exp_${vocab.id}_basic`]
    });
  }
  return annotations;
}

const sentences = [];
let current = [];
for (const item of rawItems) {
  const text = String(item.text || "").trim();
  if (!text) continue;
  current.push({
    text,
    startMs: Math.round(Number(item.start) * 1000),
    endMs: Math.round(Number(item.end) * 1000)
  });
  if (isSentenceEnd(text)) {
    sentences.push(current);
    current = [];
  }
}
if (current.length) sentences.push(current);

const paragraphs = [];
let paragraph = null;
let previousSentenceEnd = null;

sentences.forEach((words, index) => {
  const id = `s${index + 1}`;
  const { text, ranged } = sentenceTextAndRanges(words);
  const startMs = ranged[0].startMs;
  const endMs = ranged[ranged.length - 1].endMs;
  const shouldStartParagraph =
    !paragraph || (previousSentenceEnd !== null && startMs - previousSentenceEnd >= 650);

  if (shouldStartParagraph) {
    paragraph = {
      id: `p${paragraphs.length + 1}`,
      type: /connections|question/i.test(text) ? "quiz" : "story",
      sentences: []
    };
    paragraphs.push(paragraph);
  }

  paragraph.sentences.push({
    id,
    text,
    audioId: "main_en",
    startMs,
    endMs,
    chunkSets: [
      {
        id: "short",
        label: "짧게",
        chunks: chunkByWordCount(id, text, ranged, "short", 4)
      },
      {
        id: "long",
        label: "길게",
        chunks: chunkByWordCount(id, text, ranged, "long", 8)
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
            timingQuality: "sentence"
          }
        ]
      }
    ],
    annotations: annotationsForSentence(id, ranged),
    explanationIds: []
  });
  previousSentenceEnd = endMs;
});

const explanations = vocabulary.map((entry) => ({
  id: `exp_${entry.id}_basic`,
  targetType: "vocab",
  targetId: entry.id,
  title: `${entry.word} 뜻`,
  easyEnglish: entry.easyEnglish,
  textKo: `${entry.word}는 '${entry.meaningKo}'라는 뜻이에요. ${entry.easyEnglish}`
}));

const durationMs = rawItems.length ? Math.round(Number(rawItems[rawItems.length - 1].end) * 1000) : null;

const lesson = {
  schemaVersion: 1,
  id: "gamemaster_promise",
  title: "A Vow I Can Keep",
  subtitle: "GameMaster at the Park",
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
      label: "짧게",
      description: "3~4 words per chunk",
      pauseBehavior: "after_each_chunk"
    },
    {
      id: "long",
      label: "길게",
      description: "larger chunks",
      pauseBehavior: "after_each_chunk"
    },
    {
      id: "sentence",
      label: "문장",
      description: "one sentence at a time",
      pauseBehavior: "after_each_sentence"
    }
  ],
  paragraphs,
  vocabulary,
  explanations
};

fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(path.join(outputDir, "lesson.json"), JSON.stringify(lesson, null, 2), "utf8");
console.log(`Wrote ${path.join(outputDir, "lesson.json")}`);
console.log(`Sentences: ${sentences.length}, paragraphs: ${paragraphs.length}, vocabulary: ${vocabulary.length}`);

import fs from "node:fs";
import path from "node:path";

const FUNCTION_WORDS = new Set([
  "a", "an", "the", "and", "or", "but", "to", "of", "in", "on", "at",
  "for", "with", "from", "by", "as", "if", "so", "that", "after",
  "before", "because", "whenever", "is", "are", "was", "were", "be", "been"
]);

function normalizeWord(text) {
  return String(text || "")
    .toLowerCase()
    .replace(/[“”]/g, "\"")
    .replace(/^[^a-z0-9']+/, "")
    .replace(/[^a-z0-9']+$/, "")
    .replace("'s", "");
}

function isSentenceEnd(text) {
  return /[.!?]["'”’)\]]*$/.test(String(text || ""));
}

function sentenceTextAndRanges(words) {
  let text = "";
  const ranged = [];
  for (const word of words) {
    const spacer = text.length === 0 ? "" : " ";
    const startChar = text.length + spacer.length;
    text += spacer + word.text;
    ranged.push({ ...word, startChar, endChar: text.length });
  }
  return { text, ranged };
}

function gapAfter(words, index) {
  if (index >= words.length - 1) return 1000;
  return Math.max(0, words[index + 1].startMs - words[index].endMs);
}

function boundaryScore(words, index, targetEnd) {
  const word = normalizeWord(words[index].text);
  const raw = String(words[index].text || "");
  const gap = gapAfter(words, index);
  let score = gap * 4 - Math.abs(index - targetEnd) * 42;
  if (/[,:;]["'”’)\]]*$/.test(raw)) score += 120;
  if (/[.!?]["'”’)\]]*$/.test(raw)) score += 180;
  if (FUNCTION_WORDS.has(word)) score -= 150;
  if (gap < 45) score -= 120;
  if (gap >= 90) score += 70;
  return score;
}

function chunkGapAware(sentenceId, sentenceText, words, profileId, config) {
  const chunks = [];
  let start = 0;
  while (start < words.length) {
    const remaining = words.length - start;
    if (remaining <= config.maxWords) {
      const group = words.slice(start);
      chunks.push(makeChunk(sentenceId, sentenceText, group, profileId, chunks.length + 1));
      break;
    }

    const minEnd = Math.min(words.length - 1, start + config.minWords - 1);
    const maxEnd = Math.min(words.length - 2, start + config.maxWords - 1);
    const targetEnd = Math.min(words.length - 2, start + config.targetWords - 1);
    let bestEnd = minEnd;
    let bestScore = Number.NEGATIVE_INFINITY;

    const candidates = [];
    for (let i = minEnd; i <= maxEnd; i += 1) candidates.push(i);
    const naturalCandidates = candidates.filter((i) => !FUNCTION_WORDS.has(normalizeWord(words[i].text)));
    const pool = naturalCandidates.length ? naturalCandidates : candidates;

    for (const i of pool) {
      const score = boundaryScore(words, i, targetEnd);
      if (score > bestScore) {
        bestScore = score;
        bestEnd = i;
      }
    }

    const group = words.slice(start, bestEnd + 1);
    chunks.push({
      ...makeChunk(sentenceId, sentenceText, group, profileId, chunks.length + 1),
      gapAfterMs: gapAfter(words, bestEnd),
      boundaryScore: Math.round(bestScore),
      timingQuality: "gap_aware_word_aligned"
    });
    start = bestEnd + 1;
  }
  return chunks;
}

function makeChunk(sentenceId, sentenceText, group, profileId, ordinal) {
  const first = group[0];
  const last = group[group.length - 1];
  return {
    id: `${sentenceId}_${profileId}_c${ordinal}`,
    text: sentenceText.slice(first.startChar, last.endChar),
    startChar: first.startChar,
    endChar: last.endChar,
    startMs: first.startMs,
    endMs: last.endMs,
    timingQuality: "word_aligned"
  };
}

function annotationsForSentence(sentenceId, words, vocabBySurface) {
  const counts = new Map();
  const annotations = [];
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

function sentenceGroupsFromWords(rawItems) {
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
  return sentences;
}

export function generateGapAwareLesson({ sourceDir, outputDir, lessonId = "gamemaster_gap_aware" }) {
  const seedLessonPath = path.join(sourceDir, "lesson.json");
  const seedLesson = fs.existsSync(seedLessonPath)
    ? JSON.parse(fs.readFileSync(seedLessonPath, "utf8"))
    : {};
  const timestamps = JSON.parse(fs.readFileSync(path.join(sourceDir, "timestamps_from_web.json"), "utf8"));
  const rawItems = timestamps.items || [];
  const vocabulary = seedLesson.vocabulary || [];
  const explanations = seedLesson.explanations || [];
  const vocabBySurface = new Map();

  for (const entry of vocabulary) {
    vocabBySurface.set(normalizeWord(entry.word), entry);
    vocabBySurface.set(normalizeWord(entry.lemma), entry);
  }

  const paragraphs = [];
  let paragraph = null;
  let previousSentenceEnd = null;

  sentenceGroupsFromWords(rawItems).forEach((words, index) => {
    const id = `s${index + 1}`;
    const { text, ranged } = sentenceTextAndRanges(words);
    const startMs = ranged[0].startMs;
    const endMs = ranged[ranged.length - 1].endMs;
    const shouldStartParagraph =
      !paragraph || (previousSentenceEnd !== null && startMs - previousSentenceEnd >= 700);

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
          label: "자동 짧게",
          chunks: chunkGapAware(id, text, ranged, "short", {
            minWords: 2,
            targetWords: 4,
            maxWords: 6
          })
        },
        {
          id: "long",
          label: "자동 길게",
          chunks: chunkGapAware(id, text, ranged, "long", {
            minWords: 5,
            targetWords: 8,
            maxWords: 11
          })
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
      annotations: annotationsForSentence(id, ranged, vocabBySurface),
      explanationIds: [`exp_${id}_structure`].filter((expId) => explanations.some((exp) => exp.id === expId))
    });
    previousSentenceEnd = endMs;
  });

  const durationMs = rawItems.length ? Math.round(Number(rawItems[rawItems.length - 1].end) * 1000) : null;
  const lesson = {
    schemaVersion: 1,
    id: lessonId,
    title: "A Vow I Can Keep",
    subtitle: "Gap-aware chunk test",
    level: seedLesson.level || "A1",
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
        label: "자동 짧게",
        description: "2-6 words, snapped to nearby larger timestamp gaps",
        pauseBehavior: "after_each_chunk"
      },
      {
        id: "long",
        label: "자동 길게",
        description: "5-11 words, snapped to nearby larger timestamp gaps",
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
    explanations,
    source: {
      sourceDir,
      timestamps: "timestamps_from_web.json",
      chunkBasis: "gap-aware word timestamps",
      note: "Boundaries are chosen near larger inter-word timestamp gaps; no audio range padding is applied."
    }
  };

  fs.mkdirSync(outputDir, { recursive: true });
  fs.copyFileSync(path.join(sourceDir, "audio.mp3"), path.join(outputDir, "audio.mp3"));
  fs.writeFileSync(path.join(outputDir, "lesson.json"), `${JSON.stringify(lesson, null, 2)}\n`, "utf8");
  return {
    outputDir,
    paragraphs: paragraphs.length,
    sentences: paragraphs.reduce((total, p) => total + p.sentences.length, 0),
    shortChunks: paragraphs.reduce((total, p) => total + p.sentences.reduce((sum, s) => sum + s.chunkSets[0].chunks.length, 0), 0),
    longChunks: paragraphs.reduce((total, p) => total + p.sentences.reduce((sum, s) => sum + s.chunkSets[1].chunks.length, 0), 0),
    durationMs
  };
}

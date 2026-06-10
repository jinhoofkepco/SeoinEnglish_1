package com.seoin.englishstudy.model

import android.graphics.RectF
import android.widget.TextView

internal data class LessonMeta(val id: String, val title: String, val subtitle: String?, val level: String?, val path: String)
internal data class ImageAsset(val id: String, val file: String, val role: String, val label: String, val page: Int)
internal data class AudioAsset(val id: String, val file: String, val role: String, val language: String)
internal data class ChunkProfile(val id: String, val label: String, val pauseBehavior: String)
internal data class SourceBox(val x: Float, val y: Float, val w: Float, val h: Float)
internal data class PixelBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun toRectF(): RectF = RectF(x, y, x + w, y + h)
}
internal data class VocabSourceRef(val sourceId: String, val rowId: String, val rowIndex: Int, val box: SourceBox?)
internal data class VocabularySourceItem(val vocabId: String, val sourceText: String, val meaningText: String, val rowIndex: Int, val box: SourceBox?)
internal data class VocabularySource(val id: String, val type: String, val label: String, val imageId: String, val items: List<VocabularySourceItem>)
internal data class Vocab(
    val id: String,
    val word: String,
    val lemma: String,
    val forms: List<String>,
    val partOfSpeech: String,
    val meaningKo: String,
    val simpleKo: String,
    val easyEnglish: String,
    val easyEnglishLong: String,
    val examples: List<String>,
    val comic: VocabComic?,
    val quizPanel: ComicPanel?,
    val highlight: Boolean,
    val highlightStyle: String,
    val sourceRefs: List<VocabSourceRef>,
    val explanationIds: List<String>
)
internal data class VocabComic(
    val word: String,
    val meaning: String,
    val imageAssetId: String,
    val backgroundImageAssetId: String,
    val backgroundColumns: Int,
    val backgroundRows: Int,
    val panelCount: Int,
    val layout: String,
    val words: List<String>,
    val wordExplanations: Map<String, String>,
    val narrations: List<String>,
    val panels: List<ComicPanel>,
    val focusSteps: List<VocabComicFocusStep>,
    val readDefinitionAfter: Boolean
)
internal data class ComicPanel(
    val sceneId: String,
    val bg: String,
    val bgFrame: Int,
    val mood: String,
    val shot: String,
    val climax: Boolean,
    val fx: String,
    val zoom: ComicZoom?,
    val sfx: List<ComicSfx>,
    val caption: String,
    val sprites: List<ComicSprite>,
    val bubble: ComicBubble?
)
internal data class ComicZoom(val type: String, val scale: Float, val originX: Float, val originY: Float)
internal data class ComicSfx(val text: String, val x: Float, val y: Float, val size: Float, val rotate: Float, val color: Int)
internal data class ComicSprite(
    val char: String,
    val src: String,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotate: Float,
    val flip: Boolean,
    val anim: String
)
internal data class ComicBubble(val anchor: Int, val x: Float, val y: Float, val text: String)
internal data class VocabComicFocusStep(
    val id: String,
    val panelIndex: Int,
    val narration: String,
    val sourceBox: PixelBox?,
    val zoomScale: Float,
    val focusMode: String,
    val dimAlpha: Int,
    val transitionMs: Int,
    val glow: Boolean = false,
    val choices: List<VocabComicChoice> = emptyList()
)
internal data class VocabComicChoice(val text: String, val correct: Boolean, val feedback: String, val voiceText: String)
internal data class Explanation(val id: String, val targetType: String, val targetId: String, val title: String, val textKo: String, val easyEnglish: String, val easyEnglishLong: String, val examples: List<String>)
internal data class VocabStudyItem(val id: String, val word: String, val meaning: String, val longMeaning: String, val note: String, val examples: List<String>, val comic: VocabComic?)
internal data class VocabReflexGame(
    val version: String,
    val lessonId: String,
    val title: String,
    val rules: VocabReflexRules,
    val timing: VocabReflexTiming,
    val targetWords: List<VocabReflexTargetWord>,
    val sets: List<VocabReflexSet>
)
internal data class VocabReflexRules(val moveNextDelayMs: Int)
internal data class VocabReflexTiming(val fastMsMax: Int, val okayMsMax: Int, val slowMsMin: Int)
internal data class VocabReflexTargetWord(val wordId: String, val word: String, val coreCue: String, val readingBridge: String)
internal data class VocabReflexSet(val id: String, val roundType: String, val sourceSetId: String, val cards: List<VocabReflexCard>)
internal data class VocabReflexCard(
    val id: String,
    val targetWordId: String,
    val targetWord: String,
    val options: List<VocabReflexOption>,
    val answerOptionId: String,
    val cardType: String,
    val feedbackCorrect: String,
    val feedbackWrong: String,
    val readingBridge: String,
    val echoCardId: String,
    val echoOfCardId: String
)
internal data class VocabReflexOption(
    val id: String,
    val text: String,
    val isCorrect: Boolean,
    val cueType: String,
    val distractorStrategy: String,
    val linkedWordId: String,
    val errorTag: String
)
internal data class VocabReflexPlayCard(val setId: String, val roundType: String, val card: VocabReflexCard)
internal data class VocabReflexResult(
    val cardId: String,
    val targetWordId: String,
    val targetWord: String,
    val chosenCue: String,
    val correctCue: String,
    val reactionMs: Int,
    val status: String,
    val cardType: String,
    val errorTag: String,
    val feedbackWrong: String,
    val readingBridge: String,
    val correct: Boolean
)
internal data class VocabReflexWordStats(
    val attempts: Int,
    val averageMs: Int,
    val lastAverageMs: Int,
    val wrong: Int,
    val slow: Int,
    val passed: Boolean,
    val lastErrorTags: String
)
internal data class KittyWord(
    val id: String,
    val word: String,
    val pos: String,
    val def: String,
    val prompt: String,
    val sceneId: String,
    val cardPanel: ComicPanel?
)
internal data class Annotation(val id: String, val type: String, val wordId: String, val text: String, val startChar: Int, val endChar: Int, val explanationIds: List<String>)
internal data class Chunk(
    val id: String,
    val text: String,
    val startChar: Int,
    val endChar: Int,
    var startMs: Int,
    var endMs: Int,
    val originalStartMs: Int = startMs,
    val originalEndMs: Int = endMs
)
internal data class LessonSentence(val id: String, val text: String, val audioId: String, val startMs: Int, val endMs: Int, val chunkSets: Map<String, List<Chunk>>, val annotations: List<Annotation>, val chunkActivity: String, val coachMode: String)
internal data class LessonParagraph(val id: String, val type: String, val sentences: List<LessonSentence>)
internal data class ComprehensionCheck(
    val id: String,
    val question: String,
    val sentenceId: String,
    val afterSentenceId: String,
    val chunkSetId: String,
    val answerChunkId: String,
    val answerText: String,
    val correctFeedback: String,
    val scopeSentenceIds: List<String>,
    val promptNote: String
)
internal data class ComprehensionOption(val sentenceId: String, val chunkId: String, val text: String)
internal data class ComprehensionBubble(val range: IntRange, val color: Int, val pressed: Boolean)
internal data class ManualChunkBubble(val range: IntRange, val color: Int, val boundary: Boolean)
internal data class ManualChunkHint(val range: IntRange)
internal data class Lesson(
    val id: String,
    val title: String,
    val subtitle: String,
    val level: String,
    val basePath: String,
    val defaultAudioId: String,
    val defaultChunkSetId: String,
    val imageAssets: Map<String, ImageAsset>,
    val audioAssets: Map<String, AudioAsset>,
    val vocabularySources: Map<String, VocabularySource>,
    val profiles: Map<String, ChunkProfile>,
    val paragraphs: List<LessonParagraph>,
    val vocabulary: Map<String, Vocab>,
    val explanations: Map<String, Explanation>,
    val vocabReflexGame: VocabReflexGame?,
    val cinematicComic: VocabComic?,
    val comprehensionChecks: List<ComprehensionCheck>
)
internal data class SentenceRef(val paragraphIndex: Int, val sentence: LessonSentence)
internal data class SentenceRange(val sentenceIndex: Int, val start: Int, val end: Int)
internal data class ParagraphBinding(val textView: TextView, val paragraphIndex: Int, val text: String, val ranges: List<SentenceRange>) {
    fun sentenceAt(offset: Int): Int? = ranges.firstOrNull { offset in it.start until it.end }?.sentenceIndex
    fun localOffset(sentenceIndex: Int, offset: Int): Int? = ranges.firstOrNull { it.sentenceIndex == sentenceIndex }?.let { offset - it.start }
}
internal data class TtsSegment(
    val sentenceIndex: Int,
    val chunkId: String?,
    val text: String,
    val startMs: Int,
    val endMs: Int
)
internal data class MasterSettings(
    val vocabEnabled: Boolean = true,
    val quizEnabled: Boolean = true,
    val vocabGameMode2Enabled: Boolean = true,
    val quizMode: String = "choice",
    val manualChunkEnabled: Boolean = true,
    val firstListenPauseMs: Int = 1500,
    val firstListenRate: Float = 0.9f,
    val speakChunkPauseMs: Int = 700,
    val firstListenNextAlwaysEnabled: Boolean = false,
    val readerTextSizeSp: Float = 19f,
    val readerLetterSpacing: Float = 0f,
    val readerSpaceScale: Float = 1f,
    val readerLineSpacingDp: Float = 6f
)
internal data class QuizItem(val word: String, val answer: String, val options: List<String>)
internal data class WordToken(val text: String, val startChar: Int, val endChar: Int)
internal data class ManualChunk(val startWord: Int, val endWord: Int)
internal data class UnknownUnderline(val startChar: Int, val endChar: Int)
internal data class UnknownDraft(val binding: ParagraphBinding, val startOffset: Int, var currentOffset: Int)
internal data class CompactDialogGeometry(val x: Int, val y: Int, val width: Int)
internal data class PlaybackBoundary(val endMs: Int, val nextStartMs: Int, val nextSentenceIndex: Int?)
internal data class AudioProfile(val samples: FloatArray, val windowMs: Int, val durationMs: Int)
internal data class AdjustSelection(
    val sentenceIndex: Int,
    val chunkIndex: Int,
    val sentence: LessonSentence,
    val chunk: Chunk,
    val chunks: List<Chunk>
)

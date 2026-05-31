package com.seoin.englishstudy

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())

    private var lessonMetas: List<LessonMeta> = emptyList()
    private var currentLesson: Lesson? = null
    private var flatSentences: List<SentenceRef> = emptyList()
    private var paragraphBindings: List<ParagraphBinding> = emptyList()
    private var player: ExoPlayer? = null
    private var audioProfile: AudioProfile? = null
    private var tts: TextToSpeech? = null

    private var activeMode = "natural"
    private var useTts = false
    private var isTtsReady = false
    private var isTtsSpeaking = false
    private var ttsLastUtteranceId: String? = null
    private var masterSettings = MasterSettings()
    private var vocabIndex = 0
    private var quizItems: List<QuizItem> = emptyList()
    private var quizIndex = 0
    private var manualSentenceIndex = 0
    private var manualBodyMode = false
    private var firstListenMode = false
    private var unknownUnderlineMode = false
    private var unknownDraft: UnknownDraft? = null
    private var firstListenStarted = false
    private var firstListenCompleted = false
    private var selectedSentenceIndex = 0
    private var currentSentenceIndex = 0
    private var currentChunkId: String? = null
    private var explicitSegmentEndMs: Int? = null
    private var explicitSegmentNextStartMs: Int? = null
    private var pendingStartMs: Int? = null
    private var isSeeking = false
    private var waveformOffsetMs = 0

    private var playButton: ImageButton? = null
    private var ttsModeButton: TextView? = null
    private var unknownButton: TextView? = null
    private var firstListenNextButton: TextView? = null
    private var seekBar: SeekBar? = null
    private var timeLabel: TextView? = null
    private var modeLabel: TextView? = null
    private var modeButtons: MutableMap<String, TextView> = mutableMapOf()
    private var readerScroll: ScrollView? = null
    private var adjustPanel: LinearLayout? = null
    private var adjustTitle: TextView? = null
    private var adjustText: TextView? = null
    private var adjustRange: TextView? = null
    private var waveformOffsetLabel: TextView? = null
    private var startProfileView: BoundaryProfileView? = null
    private var endProfileView: BoundaryProfileView? = null
    private var manualChunkView: ManualChunkView? = null
    private val ttsUtterances = ConcurrentHashMap<String, TtsSegment>()
    private var ttsDoneCallback: (() -> Unit)? = null
    private var flowAutoToken = 0L

    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 25L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
        lessonMetas = loadManifest()
        showLessonList()
    }

    override fun onDestroy() {
        releasePlayer()
        releaseTts()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentLesson != null) showLessonList() else super.onBackPressed()
    }

    private fun showLessonList() {
        releasePlayer()
        currentLesson = null
        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))

        val scroller = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(96))
        }
        scroller.addView(content, matchWrap())
        root.addView(scroller, matchFrame())
        addMasterButton()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_wave)
            background = rounded(color(R.color.skin_surface_alt), dp(16))
            isEnabled = false
        }, fixed(dp(52), dp(52)))
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(text("서인이 영어", 26f, color(R.color.skin_ink), Typeface.BOLD))
            addView(text("책처럼 읽고, 필요한 만큼 끊어 들어요", 14f, color(R.color.skin_muted)))
        }, weightWrap())
        content.addView(header, matchWrap().withBottom(dp(24)))

        if (lessonMetas.isEmpty()) {
            content.addView(text("레슨 목록이 비어 있습니다.", 16f, color(R.color.skin_muted)))
            return
        }

        lessonMetas.forEach { meta ->
            val lesson = runCatching { loadLesson(meta) }.getOrNull()
            val sentenceCount = lesson?.allSentences()?.size ?: 0
            val profileCount = lesson?.profiles?.size ?: 0
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val fresh = runCatching { loadLesson(meta) }.getOrElse {
                        toast("레슨을 열 수 없습니다: ${it.message}")
                        return@setOnClickListener
                    }
                    startLessonFlow(fresh)
                }
            }
            card.addView(text(meta.title, 20f, color(R.color.skin_ink), Typeface.BOLD))
            card.addView(text("${meta.subtitle.orEmpty()} · $sentenceCount sentences · $profileCount modes", 14f, color(R.color.skin_muted)).apply {
                setPadding(0, dp(4), 0, 0)
            })
            val chips = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, 0)
                addView(chip("책 읽기"))
                addView(chip("타임스탬프").also { addMargin(it, left = dp(8)) })
                addView(chip("단어 팝업").also { addMargin(it, left = dp(8)) })
            }
            card.addView(chips)
            content.addView(card, matchWrap().withBottom(dp(14)))
        }
    }

    private fun startLessonFlow(lesson: Lesson) {
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)
        if (masterSettings.vocabEnabled && lesson.vocabulary.isNotEmpty()) {
            showVocabStage(lesson, 0)
        } else {
            showAfterVocabStage(lesson)
        }
    }

    private fun showAfterVocabStage(lesson: Lesson) {
        if (masterSettings.quizEnabled && lesson.vocabulary.isNotEmpty()) {
            showQuizStage(lesson)
        } else {
            showFirstListenStage(lesson)
        }
    }

    private fun showAfterBodyStage(lesson: Lesson) {
        showAfterFirstListenStage(lesson)
    }

    private fun showAfterFirstListenStage(lesson: Lesson) {
        if (masterSettings.manualChunkEnabled && flatSentences.isNotEmpty()) {
            showManualChunkMode(lesson, 0)
        } else {
            showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
        }
    }

    private fun showFirstListenStage(lesson: Lesson) {
        releasePlayer()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)
        manualBodyMode = false
        firstListenMode = true
        activeMode = "sentence"
        useTts = true
        firstListenStarted = false
        firstListenCompleted = false
        selectedSentenceIndex = 0
        currentSentenceIndex = 0
        currentChunkId = null
        pendingStartMs = flatSentences.firstOrNull()?.sentence?.startMs

        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))
        modeButtons.clear()
        paragraphBindings = emptyList()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()

        val pauseText = decimalSeconds(masterSettings.firstListenPauseMs)
        page.addView(flowHeader(lesson, "1차 전체 듣기", "문장 pause ${pauseText}초 · 속도 ${masterSettings.firstListenRate}"), matchWrap().withBottom(dp(10)))
        page.addView(text("본문 전체를 한 번 듣습니다. 문장마다 잠깐 쉬면서 자연스럽게 따라갈 수 있게 했어요.", 15f, color(R.color.skin_muted)).apply {
            setPadding(0, 0, 0, dp(10))
        }, matchWrap())
        page.addView(unknownMarkBar(), matchWrap().withBottom(dp(10)))

        val scroll = ScrollView(this).apply {
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnTouchListener { _, event ->
                if (unknownUnderlineMode) return@setOnTouchListener false
                if (!firstListenStarted && event.action == MotionEvent.ACTION_UP) {
                    toggleFirstListen(lesson)
                    true
                } else {
                    false
                }
            }
        }
        readerScroll = scroll
        val paper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(18))
        }
        scroll.addView(paper, matchWrap())
        buildParagraphs(paper, lesson)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(10)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }
        row.addView(pill("전체 듣기 시작").apply {
            setOnClickListener { toggleFirstListen(lesson) }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).withRightMargin(dp(8)))
        row.addView(pill("다음 활동").apply {
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener { showAfterFirstListenStage(lesson) }
        }, LinearLayout.LayoutParams(0, dp(50), 0.8f))
        (row.getChildAt(0) as? TextView)?.text = "재생 / 일시멈춤"
        firstListenNextButton = row.getChildAt(1) as? TextView
        firstListenNextButton?.text = "청크 활동으로"
        firstListenNextButton?.visibility = View.GONE
        page.addView(row, matchWrap())

        val nextRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface_alt), dp(18), color(R.color.skin_line), dp(1))
        }
        nextRow.addView(text("1차를 다 들으면 다음 단계로 넘어갈 수 있어요.", 14f, color(R.color.skin_muted)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        firstListenNextButton = pill("청크 활동으로").apply {
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener { showAfterFirstListenStage(lesson) }
        }
        nextRow.addView(firstListenNextButton, fixed(dp(148), dp(46)))
        page.addView(nextRow, matchWrap().withTop(dp(8)))

        handler.post(tick)
        refreshAllParagraphs()
        updateFirstListenNextButton()
        updatePlayIcon()
    }

    private fun toggleFirstListen(lesson: Lesson) {
        if (isTtsSpeaking) {
            stopTts()
            return
        }
        if (firstListenCompleted) {
            firstListenCompleted = false
            selectedSentenceIndex = 0
            currentSentenceIndex = 0
            currentChunkId = null
            pendingStartMs = flatSentences.firstOrNull()?.sentence?.startMs
            refreshAllParagraphs()
            scrollToSentence(0)
            updateFirstListenNextButton()
        }
        firstListenStarted = true
        playFirstListen(lesson)
    }

    private fun playFirstListen(lesson: Lesson) {
        val startIndex = currentSentenceIndex.coerceIn(0, max(0, flatSentences.lastIndex))
        val segments = (startIndex..flatSentences.lastIndex).mapNotNull { index -> sentenceTtsSegment(index) }
        if (segments.isEmpty()) {
            firstListenCompleted = true
            updateFirstListenNextButton()
            return
        }
        speakTtsSegments(
            segments = segments,
            pauseAfterMs = masterSettings.firstListenPauseMs,
            speechRate = masterSettings.firstListenRate,
            onDone = {
                firstListenCompleted = true
                selectedSentenceIndex = flatSentences.lastIndex.coerceAtLeast(0)
                currentSentenceIndex = selectedSentenceIndex
                currentChunkId = null
                updateFirstListenNextButton()
                refreshAllParagraphs()
            }
        )
    }

    private fun updateFirstListenNextButton() {
        val enabled = firstListenCompleted || masterSettings.firstListenNextAlwaysEnabled
        firstListenNextButton?.apply {
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.42f
            text = if (masterSettings.manualChunkEnabled) "청크 활동으로" else "2차로"
        }
    }

    private fun showSpeakListenStage(lesson: Lesson, pass: Int, sentenceIndex: Int) {
        releasePlayer()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)
        manualBodyMode = false
        firstListenMode = false
        activeMode = if (pass == 2) "short" else "sentence"
        useTts = true
        selectedSentenceIndex = sentenceIndex.coerceIn(0, max(0, flatSentences.lastIndex))
        currentSentenceIndex = selectedSentenceIndex
        currentChunkId = null
        pendingStartMs = flatSentences.getOrNull(selectedSentenceIndex)?.sentence?.startMs

        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))
        modeButtons.clear()
        paragraphBindings = emptyList()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()

        val title = if (pass == 2) "2차 말하며 듣기" else "3차 말하며 듣기"
        val detail = if (pass == 2) {
            "짧은 청크 pause ${decimalSeconds(masterSettings.speakChunkPauseMs)}초"
        } else {
            "청크 pause 없음"
        }
        page.addView(flowHeader(lesson, title, "${selectedSentenceIndex + 1}/${flatSentences.size} · $detail"), matchWrap().withBottom(dp(10)))
        page.addView(text("한 문장을 들은 뒤 멈춥니다. 따라 말한 다음 빈 공간이나 다음 버튼을 눌러 다음 문장으로 넘어가요.", 15f, color(R.color.skin_muted)).apply {
            setPadding(0, 0, 0, dp(10))
        }, matchWrap())
        page.addView(unknownMarkBar(), matchWrap().withBottom(dp(10)))

        val scroll = ScrollView(this).apply {
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnTouchListener { _, event ->
                if (unknownUnderlineMode) return@setOnTouchListener false
                if (event.action == MotionEvent.ACTION_UP) {
                    advanceSpeakListenSentence(lesson, pass)
                    true
                } else {
                    false
                }
            }
        }
        readerScroll = scroll
        val paper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(18))
        }
        scroll.addView(paper, matchWrap())
        buildParagraphs(paper, lesson)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(10)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }
        row.addView(pill("다시 듣기").apply {
            setOnClickListener { toggleSpeakListenSentence(pass, selectedSentenceIndex) }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).withRightMargin(dp(8)))
        row.addView(pill("다음 문장").apply {
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener { advanceSpeakListenSentence(lesson, pass) }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        (row.getChildAt(0) as? TextView)?.text = "재생 / 일시멈춤"
        (row.getChildAt(1) as? TextView)?.text = "다음 문장"
        page.addView(row, matchWrap())

        val nextStageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface_alt), dp(18), color(R.color.skin_line), dp(1))
        }
        nextStageRow.addView(text(if (pass == 2) "2차를 충분히 연습했으면 3차로 넘어갑니다." else "3차가 끝나면 자유 읽기 화면으로 넘어갑니다.", 14f, color(R.color.skin_muted)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nextStageRow.addView(pill(if (pass == 2) "3차로" else "완료").apply {
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener { goNextSpeakListenStage(lesson, pass) }
        }, fixed(dp(126), dp(46)))
        page.addView(nextStageRow, matchWrap().withTop(dp(8)))

        handler.post(tick)
        refreshAllParagraphs()
        updatePlayIcon()
    }

    private fun advanceSpeakListenSentence(lesson: Lesson, pass: Int) {
        stopTts()
        val next = selectedSentenceIndex + 1
        if (next <= flatSentences.lastIndex) {
            selectedSentenceIndex = next
            currentSentenceIndex = next
            currentChunkId = null
            pendingStartMs = flatSentences.getOrNull(next)?.sentence?.startMs
            refreshAllParagraphs()
            scrollToSentence(next)
            playSpeakListenSentence(pass, next)
            return
        }
        toast("마지막 문장이에요. 다음 단계 버튼을 눌러 주세요.")
    }

    private fun goNextSpeakListenStage(lesson: Lesson, pass: Int) {
        stopTts()
        if (pass == 2) {
            showSpeakListenStage(lesson, pass = 3, sentenceIndex = 0)
        } else {
            showReader(lesson)
        }
    }

    private fun toggleSpeakListenSentence(pass: Int, sentenceIndex: Int) {
        if (isTtsSpeaking) {
            stopTts()
        } else {
            playSpeakListenSentence(pass, sentenceIndex, resume = true)
        }
    }

    private fun playSpeakListenSentence(pass: Int, sentenceIndex: Int, resume: Boolean = false) {
        val segments = speakListenSegments(pass, sentenceIndex, resume)
        if (segments.isEmpty()) return
        speakTtsSegments(
            segments = segments,
            pauseAfterMs = if (pass == 2) masterSettings.speakChunkPauseMs else 0,
            speechRate = masterSettings.firstListenRate
        )
    }

    private fun speakListenSegments(pass: Int, sentenceIndex: Int, resume: Boolean = false): List<TtsSegment> {
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return emptyList()
        if (pass != 2) return listOfNotNull(sentenceTtsSegment(sentenceIndex))
        val chunks = sentence.chunkSets["short"].orEmpty()
        if (chunks.isEmpty()) return listOfNotNull(sentenceTtsSegment(sentenceIndex))
        val startChunkIndex = if (resume && currentSentenceIndex == sentenceIndex) {
            chunks.indexOfFirst { it.id == currentChunkId }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        return chunks.drop(startChunkIndex).map { chunkTtsSegment(sentenceIndex, it) }
    }

    private fun showVocabStage(lesson: Lesson, index: Int) {
        showVocabTableStage(lesson, index.coerceAtLeast(0), reviewComplete = false, wrongIds = null)
    }

    private fun showVocabTableStage(
        lesson: Lesson,
        focusIndex: Int,
        reviewComplete: Boolean,
        wrongIds: Set<String>?
    ) {
        stopAllPlayback()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)

        val items = vocabStudyItems(lesson)
        if (items.isEmpty()) {
            showAfterVocabStage(lesson)
            return
        }
        val safeIndex = focusIndex.coerceIn(0, items.lastIndex)
        val testingWrongOnly = wrongIds != null

        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()

        val progress = when {
            testingWrongOnly -> "틀린 단어 ${wrongIds!!.size}개"
            reviewComplete -> "복습 준비"
            else -> "${safeIndex + 1}/${items.size}"
        }
        page.addView(flowHeader(lesson, "단어장", progress), matchWrap().withBottom(dp(10)))

        val meaningCells = mutableListOf<View>()
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), dp(6))
        }
        header.addView(text("Word", 13f, color(R.color.skin_muted), Typeface.BOLD), LinearLayout.LayoutParams(0, dp(42), 0.34f))
        val hideButton = pill("뜻 가리기").apply {
            textSize = 13f
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> meaningCells.forEach { it.visibility = View.INVISIBLE }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> meaningCells.forEach { it.visibility = View.VISIBLE }
                }
                true
            }
        }
        header.addView(hideButton, LinearLayout.LayoutParams(0, dp(42), 0.66f))
        if (reviewComplete) {
            header.addView(text("", 1f, color(R.color.skin_muted)), fixed(dp(72), dp(42)))
        }
        table.addView(header, matchWrap())

        var focusedRow: View? = null
        items.forEachIndexed { index, item ->
            val isFocused = !reviewComplete && index == safeIndex
            val isCorrectAfterTest = testingWrongOnly && item.id !in wrongIds!!
            val isWrongAfterTest = testingWrongOnly && item.id in wrongIds!!
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(82)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(
                    when {
                        isFocused -> color(R.color.skin_highlight)
                        isCorrectAfterTest -> 0xFFE7EBEA.toInt()
                        else -> color(R.color.skin_surface)
                    },
                    dp(14),
                    color(R.color.skin_line),
                    dp(1)
                )
                if (isCorrectAfterTest) alpha = 0.62f
            }
            if (isFocused) focusedRow = row
            row.addView(text(item.word, 21f, if (isCorrectAfterTest) color(R.color.skin_muted) else color(R.color.skin_ink), Typeface.BOLD).apply {
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.34f))

            val meaningBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(10), 0)
            }
            meaningBox.addView(text(item.meaning, 16f, if (isCorrectAfterTest) color(R.color.skin_muted) else color(R.color.skin_primary_dark), Typeface.BOLD))
            if (item.note.isNotBlank()) {
                meaningBox.addView(text(item.note, 13f, color(R.color.skin_muted)).apply {
                    setPadding(0, dp(3), 0, 0)
                })
            }
            item.examples.take(2).forEach { example ->
                meaningBox.addView(text("ex. $example", 13f, color(R.color.skin_muted)).apply {
                    setPadding(0, dp(3), 0, 0)
                })
            }
            meaningCells.add(meaningBox)
            row.addView(meaningBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.66f))

            if (reviewComplete) {
                val rowButton = pill(if (isWrongAfterTest || !testingWrongOnly) "듣기" else "완료").apply {
                    textSize = 13f
                    if (isCorrectAfterTest) {
                        visibility = View.INVISIBLE
                        isEnabled = false
                    } else {
                        setOnClickListener { speakVocabStudyItem(item) }
                    }
                }
                row.addView(rowButton, LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    leftMargin = dp(8)
                })
            }
            table.addView(row, matchWrap().withBottom(dp(8)))
        }

        val scroller = ScrollView(this)
        scroller.addView(table, matchWrap())
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        body.addView(scroller, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        var nextButton: TextView? = null
        if (!reviewComplete) {
            nextButton = pill(if (safeIndex == items.lastIndex) "복습" else "다음").apply {
                textSize = 16f
                isEnabled = false
                alpha = 0.45f
                setOnClickListener {
                    if (!isEnabled) return@setOnClickListener
                    if (safeIndex < items.lastIndex) {
                        showVocabTableStage(lesson, safeIndex + 1, reviewComplete = false, wrongIds = null)
                    } else {
                        showVocabTableStage(lesson, 0, reviewComplete = true, wrongIds = null)
                    }
                }
            }
            body.addView(nextButton, LinearLayout.LayoutParams(dp(88), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                leftMargin = dp(10)
            })
        }
        page.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(10)))

        if (reviewComplete) {
            val testTargets = wrongIds?.takeIf { it.isNotEmpty() }
            page.addView(pill(if (testTargets == null) "테스트 보기" else "틀린 단어 다시 테스트 (${testTargets.size})").apply {
                textSize = 16f
                background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
                setOnClickListener { showVocabTestStage(lesson, testTargets) }
            }, matchWrap())
        } else {
            handler.post {
                focusedRow?.let { row ->
                    scroller.smoothScrollTo(0, max(0, row.top - dp(12)))
                }
            }
            val button = nextButton
            speakVocabStudyItem(items[safeIndex]) {
                button?.isEnabled = true
                button?.alpha = 1f
            }
        }
    }

    private fun showVocabTestStage(
        lesson: Lesson,
        targetIds: Set<String>?,
        testIndex: Int = 0,
        wrongIds: MutableSet<String> = mutableSetOf()
    ) {
        stopAllPlayback()
        val allItems = vocabStudyItems(lesson)
        val testItems = targetIds?.let { targets -> allItems.filter { it.id in targets } } ?: allItems
        if (testItems.isEmpty()) {
            showFirstListenStage(lesson)
            return
        }
        val item = testItems.getOrNull(testIndex)
        if (item == null) {
            if (wrongIds.isEmpty()) {
                testItems.forEach { saveVocabKnown(lesson.id, it.id, true) }
                toast("단어 테스트 완료")
                showFirstListenStage(lesson)
            } else {
                showVocabTableStage(lesson, 0, reviewComplete = true, wrongIds = wrongIds.toSet())
            }
            return
        }

        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()
        page.addView(flowHeader(lesson, "단어 테스트", "${testIndex + 1}/${testItems.size}"), matchWrap().withBottom(dp(14)))

        page.addView(text("단어에 맞는 뜻을 고르세요", 15f, color(R.color.skin_muted), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, matchWrap().withBottom(dp(8)))
        page.addView(text(item.word, 34f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(24), dp(18), dp(24))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }, matchWrap().withBottom(dp(18)))

        val options = vocabMeaningOptions(allItems, item)
        options.forEach { option ->
            page.addView(pill(option).apply {
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(12), dp(18), dp(12))
                setOnClickListener {
                    val correct = option == item.meaning
                    if (correct) {
                        saveVocabKnown(lesson.id, item.id, true)
                        toast("정답")
                    } else {
                        wrongIds.add(item.id)
                        saveVocabKnown(lesson.id, item.id, false)
                        toast("다시 볼게요")
                    }
                    handler.postDelayed({
                        showVocabTestStage(lesson, targetIds, testIndex + 1, wrongIds)
                    }, 250L)
                }
            }, matchWrap().withBottom(dp(10)))
        }
        speakPopupText(item.word)
    }

    private fun vocabStudyItems(lesson: Lesson): List<VocabStudyItem> {
        return lesson.vocabulary.values.map { vocab ->
            val explanationsById = vocab.explanationIds.mapNotNull { lesson.explanations[it] }
            val explanations = if (explanationsById.isNotEmpty()) {
                explanationsById
            } else {
                lesson.explanations.values.filter { it.targetType == "vocab" && it.targetId == vocab.id }
            }
            val explanationEnglish = explanations.mapNotNull { it.easyEnglish.takeIf { value -> value.isNotBlank() } }.firstOrNull().orEmpty()
            val meaning = vocab.easyEnglish.ifBlank { explanationEnglish }.ifBlank { vocab.meaningKo }.ifBlank { "No definition yet." }
            val note = vocab.meaningKo
                .takeIf { it.isNotBlank() && it != meaning }
                ?: vocab.simpleKo.takeIf { it.isNotBlank() }
                ?: explanations.mapNotNull { it.textKo.takeIf { value -> value.isNotBlank() && value != meaning } }.firstOrNull().orEmpty()
            VocabStudyItem(
                id = vocab.id,
                word = vocab.word,
                meaning = meaning,
                note = note,
                examples = (vocab.examples + explanations.flatMap { it.examples }).distinct()
            )
        }
    }

    private fun vocabMeaningOptions(allItems: List<VocabStudyItem>, item: VocabStudyItem): List<String> {
        val distractors = allItems
            .filter { it.id != item.id }
            .map { it.meaning }
            .filter { it.isNotBlank() && it != item.meaning }
            .distinct()
            .shuffled()
            .take(2)
        return (listOf(item.meaning) + distractors).distinct().shuffled()
    }

    private fun speakVocabStudyItem(item: VocabStudyItem, onDone: (() -> Unit)? = null) {
        val example = item.examples.firstOrNull()
        val spoken = buildString {
            append(item.word)
            append(". ")
            append(item.meaning)
            if (!example.isNullOrBlank()) {
                append(". Example. ")
                append(example)
            }
        }
        speakPopupText(spoken, onDone)
    }

    private fun showQuizStage(lesson: Lesson) {
        stopAllPlayback()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)
        quizItems = buildQuizItems(lesson)
        quizIndex = 0
        if (quizItems.isEmpty()) {
            showFirstListenStage(lesson)
            return
        }
        showQuizItem(lesson)
    }

    private fun showQuizItem(lesson: Lesson) {
        val item = quizItems.getOrNull(quizIndex) ?: run {
            showFirstListenStage(lesson)
            return
        }
        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()
        page.addView(flowHeader(lesson, "단어 퀴즈", "${quizIndex + 1}/${quizItems.size}"), matchWrap().withBottom(dp(14)))
        page.addView(text(item.word, 34f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(18))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }, matchWrap().withBottom(dp(16)))
        if (masterSettings.quizMode != "choice") {
            page.addView(text("뜻을 직접 말한 뒤 확인해요.\n\n${item.answer}", 22f, color(R.color.skin_primary_dark), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(24), dp(18), dp(24))
                background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(12)))
            page.addView(pill("다음").apply {
                setOnClickListener {
                    quizIndex += 1
                    showQuizItem(lesson)
                }
            }, matchWrap())
            speakPopupText(item.word)
            return
        }
        item.options.forEach { option ->
            page.addView(pill(option).apply {
                textSize = 17f
                setOnClickListener {
                    val correct = option == item.answer
                    toast(if (correct) "정답" else "다시 확인")
                    if (correct) {
                        quizIndex += 1
                        showQuizItem(lesson)
                    }
                }
            }, matchWrap().withBottom(dp(10)))
        }
        page.addView(pill("본문으로 넘어가기").apply {
            setOnClickListener { showFirstListenStage(lesson) }
        }, matchWrap().withTop(dp(8)))
        speakPopupText(item.word)
    }

    private fun flowHeader(lesson: Lesson, stage: String, progress: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(lesson.title, 22f, color(R.color.skin_ink), Typeface.BOLD))
            addView(text("$stage · $progress", 15f, color(R.color.skin_muted)).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun buildQuizItems(lesson: Lesson): List<QuizItem> {
        val vocabList = lesson.vocabulary.values.filter { it.meaningKo.isNotBlank() || it.easyEnglish.isNotBlank() }
        val bodyDistractors = lesson.allSentences()
            .flatMap { sentenceRef -> wordTokens(sentenceRef.sentence.text).map { it.text.trim('.', ',', '"') } }
            .filter { it.length >= 4 }
            .distinct()
            .shuffled()
        return vocabList.mapIndexed { index, vocab ->
            val answer = vocab.meaningKo.ifBlank { vocab.easyEnglish }
            val vocabOptions = vocabList
                .filter { it.id != vocab.id }
                .map { other -> other.meaningKo.ifBlank { other.easyEnglish } }
                .filter { it.isNotBlank() }
                .shuffled()
                .take(2)
            val bodyOptions = bodyDistractors.drop(index * 2).take(2).map { "본문 단어: $it" }
            val options = (listOf(answer) + vocabOptions + bodyOptions + listOf("본문에서 다시 찾기", "소리 내어 말하기", "뜻을 다시 보기"))
                .distinct()
                .take(4)
                .shuffled()
            QuizItem(vocab.word, answer, options)
        }
    }

    private fun saveVocabKnown(lessonId: String, vocabId: String, known: Boolean) {
        getSharedPreferences("vocab_known", MODE_PRIVATE).edit()
            .putBoolean("$lessonId|$vocabId", known)
            .apply()
    }

    private fun showManualChunkMode(lesson: Lesson, sentenceIndex: Int) {
        stopAllPlayback()
        releasePlayer()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)
        firstListenMode = false
        val practiceIndices = manualPracticeSentenceIndices(lesson)
        if (practiceIndices.isEmpty()) {
            showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
            return
        }
        val practicePosition = sentenceIndex.coerceIn(0, practiceIndices.lastIndex)
        manualSentenceIndex = practiceIndices[practicePosition]
        val sentence = flatSentences.getOrNull(manualSentenceIndex)?.sentence ?: run {
            showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
            return
        }
        val tokens = wordTokens(sentence.text)
        saveManualChunks(lesson.id, sentence.id, sentence.text, tokens, emptyList())
        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()
        page.addView(flowHeader(lesson, "직접 청크", "${practicePosition + 1}/${practiceIndices.size}"), matchWrap().withBottom(dp(12)))
        page.addView(text("단어 수가 긴 5문장만 골랐어요. 버블을 끌어 단어 묶음을 만들면 TTS가 청크마다 끊어서 읽어줍니다.", 15f, color(R.color.skin_muted)).apply {
            setPadding(0, 0, 0, dp(10))
        }, matchWrap())

        manualChunkView = ManualChunkView(this).apply manualView@ {
            setSentence(sentence.text, tokens, emptyList())
            onChanged = { chunks -> saveManualChunks(lesson.id, sentence.id, sentence.text, tokens, chunks) }
            var completed = false
            onCompleted = { chunks ->
                if (!completed) {
                completed = true
                this@manualView.isEnabled = false
                saveManualChunks(lesson.id, sentence.id, sentence.text, tokens, chunks)
                speakManualChunks(sentence.text, tokens, chunks) {
                    if (practicePosition == practiceIndices.lastIndex) {
                        showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
                    } else {
                        showManualChunkMode(lesson, practicePosition + 1)
                    }
                }
                toast("청크 저장 완료")
                }
            }
        }
        page.addView(manualChunkView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(12)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(pill("초기화").apply {
            setOnClickListener {
                manualChunkView?.clearChunks()
                saveManualChunks(lesson.id, sentence.id, sentence.text, tokens, emptyList())
            }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).withRightMargin(dp(8)))
        row.addView(pill("청크 듣기").apply {
            setOnClickListener {
                val chunks = manualChunkView?.currentChunks().orEmpty()
                if (chunks.isEmpty()) toast("청크를 먼저 만들어 주세요.") else speakManualChunks(sentence.text, tokens, chunks)
            }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).withRightMargin(dp(8)))
        row.addView(pill(if (practicePosition == practiceIndices.lastIndex) "본문으로" else "다음 문장").apply {
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener {
                if (practicePosition == practiceIndices.lastIndex) showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0) else showManualChunkMode(lesson, practicePosition + 1)
            }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        page.addView(row, matchWrap())
    }

    private fun manualPracticeSentenceIndices(lesson: Lesson): List<Int> {
        if (flatSentences.isEmpty()) return emptyList()
        val titleIndex = flatSentences.indexOfFirst {
            it.sentence.text.trim().equals(lesson.title.trim(), ignoreCase = true)
        }
        val startIndex = if (titleIndex >= 0) titleIndex + 1 else 0
        val candidates = flatSentences.mapIndexedNotNull { index, ref ->
            if (index < startIndex) return@mapIndexedNotNull null
            val value = ref.sentence.text.trim()
            val wordCount = wordTokens(value).size
            val isHeading = value.isNotBlank() && value.all { !it.isLetter() || it.isUpperCase() }
            if (wordCount < 5 || isHeading) null else index to wordCount
        }
        return candidates
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
            .take(5)
            .map { it.first }
            .sorted()
            .ifEmpty { flatSentences.indices.take(min(5, flatSentences.size)).toList() }
    }

    private fun wordTokens(value: String): List<WordToken> {
        return Regex("\\S+").findAll(value).map { match ->
            WordToken(match.value, match.range.first, match.range.last + 1)
        }.toList()
    }

    private fun chunkText(sentenceText: String, tokens: List<WordToken>, chunk: ManualChunk): String {
        val start = tokens.getOrNull(chunk.startWord)?.startChar ?: return ""
        val end = tokens.getOrNull(chunk.endWord)?.endChar ?: return ""
        return sentenceText.substring(start, end)
    }

    private fun speakManualChunks(sentenceText: String, tokens: List<WordToken>, chunks: List<ManualChunk>, onDone: (() -> Unit)? = null) {
        val segments = chunks.mapIndexedNotNull { index, chunk ->
            val text = chunkText(sentenceText, tokens, chunk).takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            TtsSegment(
                sentenceIndex = manualSentenceIndex,
                chunkId = "manual_$index",
                text = text,
                startMs = index,
                endMs = index + 1
            )
        }
        speakTtsSegments(segments, pauseAfterMs = 350, speechRate = masterSettings.firstListenRate, onDone = onDone)
    }

    private fun saveManualChunks(lessonId: String, sentenceId: String, sentenceText: String, tokens: List<WordToken>, chunks: List<ManualChunk>) {
        val arr = JSONArray()
        chunks.forEach { chunk ->
            arr.put(
                JSONObject()
                    .put("startWord", chunk.startWord)
                    .put("endWord", chunk.endWord)
                    .put("text", chunkText(sentenceText, tokens, chunk))
            )
        }
        getSharedPreferences("manual_chunks", MODE_PRIVATE).edit()
            .putString("$lessonId|$sentenceId", arr.toString())
            .apply()
    }

    private fun loadManualChunks(lessonId: String, sentenceId: String): List<ManualChunk> {
        val raw = getSharedPreferences("manual_chunks", MODE_PRIVATE).getString("$lessonId|$sentenceId", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { index ->
                val item = arr.getJSONObject(index)
                ManualChunk(item.getInt("startWord"), item.getInt("endWord"))
            }
        }.getOrDefault(emptyList())
    }

    private fun showReader(lesson: Lesson, manualMode: Boolean = false, startSentenceIndex: Int = 0) {
        releasePlayer()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)
        manualBodyMode = manualMode
        firstListenMode = false
        activeMode = if (manualBodyMode) "sentence" else "natural"
        useTts = manualBodyMode || shouldUseTtsForMode(activeMode, lesson)
        selectedSentenceIndex = startSentenceIndex.coerceIn(0, max(0, flatSentences.lastIndex))
        currentSentenceIndex = selectedSentenceIndex
        manualSentenceIndex = selectedSentenceIndex
        currentChunkId = null
        explicitSegmentEndMs = null
        explicitSegmentNextStartMs = null
        waveformOffsetMs = loadWaveformOffset(lesson.id)
        pendingStartMs = flatSentences.getOrNull(selectedSentenceIndex)?.sentence?.startMs

        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))
        modeButtons.clear()
        paragraphBindings = emptyList()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(92))
        }
        root.addView(page, matchFrame())
        addMasterButton()

        page.addView(topBar(lesson), matchWrap().withBottom(dp(10)))
        page.addView(unknownMarkBar(), matchWrap().withBottom(dp(10)))
        if (manualBodyMode) {
            page.addView(text("큰 본문에서 청크의 마지막 단어를 탭하세요. 저장된 청크는 바로 본문 위에 표시됩니다.", 15f, color(R.color.skin_muted)).apply {
                setPadding(0, 0, 0, dp(8))
            }, matchWrap())
        } else {
            page.addView(modeBar(lesson), matchWrap().withBottom(dp(10)))
            page.addView(progressBar(), matchWrap().withBottom(dp(10)))
        }

        val scroll = ScrollView(this).apply {
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        readerScroll = scroll
        val paper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(18))
        }
        scroll.addView(paper, matchWrap())
        buildParagraphs(paper, lesson)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(10)))
        page.addView(if (manualBodyMode) manualBodyControls(lesson) else controls(), matchWrap().withBottom(dp(8)))
        if (masterSettings.manualChunkEnabled && !manualBodyMode) {
            page.addView(flowNextPanel(lesson), matchWrap())
        }

        if (!manualBodyMode && lessonHasAudio(lesson)) preparePlayer(lesson) else handler.post(tick)
        refreshAllParagraphs()
        refreshMode()
    }

    private fun topBar(lesson: Lesson): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(iconButton(R.drawable.ic_arrow_back, "뒤로").apply {
            setOnClickListener { showLessonList() }
        }, fixed(dp(44), dp(44)))
        bar.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(text(lesson.title, 19f, color(R.color.skin_ink), Typeface.BOLD))
            addView(text(lesson.subtitle.ifBlank { "${flatSentences.size} sentences" }, 13f, color(R.color.skin_muted)))
        }, weightWrap())
        ttsModeButton = pill("Audio").apply {
            textSize = 13f
            isClickable = false
            isFocusable = false
        }
        bar.addView(ttsModeButton, fixed(dp(72), dp(38)))
        updateTtsModeButton()
        return bar
    }

    private fun modeBar(lesson: Lesson): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val modes = listOf("natural" to "자연스럽게", "short" to "짧게", "long" to "길게", "sentence" to "문장")
        modes.forEach { (id, fallbackLabel) ->
            val profile = lesson.profiles[id]
            val label = profile?.label?.ifBlank { fallbackLabel } ?: fallbackLabel
            val button = pill(label).apply {
                setOnClickListener {
                    activeMode = id
                    useTts = shouldUseTtsForMode(id, lesson)
                    if (useTts) player?.pause() else stopTts()
                    explicitSegmentEndMs = null
                    explicitSegmentNextStartMs = null
                    refreshMode()
                    refreshAllParagraphs()
                }
            }
            modeButtons[id] = button
            row.addView(button, wrapWrap().withRightMargin(dp(8)))
        }
        scroller.addView(row, matchWrap())
        modeLabel = text("", 13f, color(R.color.skin_muted))
        box.addView(scroller, matchWrap())
        box.addView(modeLabel, matchWrap().withTop(dp(7)))
        return box
    }

    private fun unknownMarkBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(color(R.color.skin_surface_alt), dp(16))
        }
        row.addView(text("모르는 부분은 버튼을 켜고 본문 위를 드래그해서 물결 밑줄로 남겨요.", 13f, color(R.color.skin_muted)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        unknownButton = pill("").apply {
            textSize = 13f
            setOnClickListener { toggleUnknownUnderlineMode() }
        }
        row.addView(unknownButton, fixed(dp(126), dp(40)))
        updateUnknownButton()
        return row
    }

    private fun toggleUnknownUnderlineMode() {
        unknownUnderlineMode = !unknownUnderlineMode
        unknownDraft = null
        if (unknownUnderlineMode) {
            stopAllPlayback()
            toast("줄긋기 모드")
        } else {
            toast("줄긋기 종료")
        }
        updateUnknownButton()
        refreshAllParagraphs()
    }

    private fun updateUnknownButton() {
        unknownButton?.apply {
            text = if (unknownUnderlineMode) "줄긋기 ON" else "모르는 부분"
            background = rounded(
                if (unknownUnderlineMode) color(R.color.skin_mark) else color(R.color.skin_surface),
                dp(16),
                if (unknownUnderlineMode) color(R.color.skin_primary) else color(R.color.skin_line),
                dp(1)
            )
        }
    }

    private fun progressBar(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(color(R.color.skin_surface_alt), dp(16))
        }
        timeLabel = text("00:00 / 00:00", 13f, color(R.color.skin_muted))
        seekBar = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        pendingStartMs = progress
                        explicitSegmentNextStartMs = null
                        if (useTts) {
                            stopTts()
                        } else {
                            player?.seekTo(progress.toLong())
                        }
                        syncCurrentPosition(progress, scroll = true)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isSeeking = true
                    explicitSegmentEndMs = null
                    explicitSegmentNextStartMs = null
                    if (useTts) stopTts()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isSeeking = false
                }
            })
        }
        box.addView(timeLabel)
        box.addView(seekBar)
        return box
    }

    private fun buildParagraphs(parent: LinearLayout, lesson: Lesson) {
        val bindings = mutableListOf<ParagraphBinding>()
        lesson.paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val builder = StringBuilder()
            val ranges = mutableListOf<SentenceRange>()
            paragraph.sentences.forEach { sentence ->
                val sentenceIndex = flatSentences.indexOfFirst { it.sentence.id == sentence.id }
                if (sentenceIndex < 0) return@forEach
                if (builder.isNotEmpty()) builder.append(' ')
                val start = builder.length
                builder.append(sentence.text)
                val end = builder.length
                ranges.add(SentenceRange(sentenceIndex, start, end))
            }

            val paragraphText = builder.toString()
            val tv = WavyTextView(this).apply {
                textSize = when {
                    manualBodyMode -> 26f
                    paragraph.type == "quiz" -> 17f
                    else -> 19f
                }
                setTextColor(color(R.color.skin_ink))
                includeFontPadding = true
                setLineSpacing(dp(if (manualBodyMode) 10 else 6).toFloat(), if (manualBodyMode) 1.12f else 1.08f)
                setPadding(dp(6), dp(6), dp(6), dp(12))
            }
            val binding = ParagraphBinding(tv, paragraphIndex, paragraphText, ranges)
            tv.setOnTouchListener(paragraphGesture(binding))
            bindings.add(binding)
            parent.addView(tv, matchWrap().withBottom(if (paragraph.type == "quiz") dp(10) else dp(18)))
        }
        paragraphBindings = bindings
    }

    private fun paragraphGesture(binding: ParagraphBinding): View.OnTouchListener {
        if (firstListenMode) {
            val lesson = currentLesson
            val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (!firstListenStarted && lesson != null) toggleFirstListen(lesson)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    stopAllPlayback()
                    val offset = offsetFor(binding.textView, e) ?: return
                    val sentenceIndex = binding.sentenceAt(offset) ?: return
                    selectedSentenceIndex = sentenceIndex
                    val sentence = flatSentences[sentenceIndex].sentence
                    showSentencePopup(sentence)
                }
            })
            return View.OnTouchListener { _, event ->
                if (unknownUnderlineMode) {
                    handleUnknownUnderlineTouch(binding, event)
                    return@OnTouchListener true
                }
                detector.onTouchEvent(event)
                true
            }
        }
        if (manualBodyMode) {
            return View.OnTouchListener { _, event ->
                if (unknownUnderlineMode) {
                    handleUnknownUnderlineTouch(binding, event)
                    return@OnTouchListener true
                }
                if (event.action == MotionEvent.ACTION_UP) {
                    val offset = offsetFor(binding.textView, event) ?: return@OnTouchListener true
                    handleManualBodyChunkTap(binding, offset)
                }
                true
            }
        }
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val offset = offsetFor(binding.textView, e) ?: return true
                val sentenceIndex = binding.sentenceAt(offset) ?: return true
                if (activeMode != "natural") {
                    playChunkAtOffset(sentenceIndex, binding.localOffset(sentenceIndex, offset))
                } else {
                    playSentenceOnce(sentenceIndex)
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val offset = offsetFor(binding.textView, e) ?: return true
                val sentenceIndex = binding.sentenceAt(offset) ?: return true
                selectedSentenceIndex = sentenceIndex
                val sentence = flatSentences[sentenceIndex].sentence
                showSentencePopup(sentence)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val offset = offsetFor(binding.textView, e) ?: return
                val sentenceIndex = binding.sentenceAt(offset) ?: return
                val localOffset = binding.localOffset(sentenceIndex, offset) ?: return
                val sentence = flatSentences[sentenceIndex].sentence
                val annotation = sentence.annotations.firstOrNull { localOffset in it.startChar until it.endChar }
                if (annotation != null) {
                    showAnnotation(annotation)
                } else {
                    selectedSentenceIndex = sentenceIndex
                    pendingStartMs = sentence.startMs
                    refreshAllParagraphs()
                    toast("문장을 선택했어요")
                }
            }
        })
        return View.OnTouchListener { _, event ->
            if (unknownUnderlineMode) {
                handleUnknownUnderlineTouch(binding, event)
                return@OnTouchListener true
            }
            detector.onTouchEvent(event)
            true
        }
    }

    private fun handleUnknownUnderlineTouch(binding: ParagraphBinding, event: MotionEvent) {
        val offset = offsetFor(binding.textView, event) ?: return
        binding.textView.parent?.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopAllPlayback()
                unknownDraft = UnknownDraft(binding, offset, offset)
                refreshAllParagraphs()
            }
            MotionEvent.ACTION_MOVE -> {
                unknownDraft?.takeIf { it.binding === binding }?.currentOffset = offset
                refreshAllParagraphs()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                unknownDraft?.takeIf { it.binding === binding }?.let { draft ->
                    addUnknownUnderline(binding, draft.startOffset, draft.currentOffset)
                }
                unknownDraft = null
                binding.textView.parent?.requestDisallowInterceptTouchEvent(false)
                refreshAllParagraphs()
            }
        }
    }

    private fun addUnknownUnderline(binding: ParagraphBinding, startOffset: Int, endOffset: Int) {
        val lesson = currentLesson ?: return
        val start = min(startOffset, endOffset)
        val end = max(startOffset, endOffset)
        if (end - start <= 1) {
            val sentenceIndex = binding.sentenceAt(start) ?: return
            val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return
            val local = binding.localOffset(sentenceIndex, start) ?: return
            val wordRange = wordRangeAt(sentence.text, local) ?: return
            saveUnknownUnderlines(
                lesson.id,
                sentence.id,
                loadUnknownUnderlines(lesson.id, sentence.id) + UnknownUnderline(wordRange.startChar, wordRange.endChar)
            )
            return
        }
        binding.ranges.forEach { range ->
            val overlapStart = max(start, range.start)
            val overlapEnd = min(end, range.end)
            if (overlapEnd <= overlapStart) return@forEach
            val sentence = flatSentences.getOrNull(range.sentenceIndex)?.sentence ?: return@forEach
            val localStart = overlapStart - range.start
            val localEnd = overlapEnd - range.start
            saveUnknownUnderlines(
                lesson.id,
                sentence.id,
                loadUnknownUnderlines(lesson.id, sentence.id) + UnknownUnderline(localStart, localEnd)
            )
        }
    }

    private fun wordRangeAt(value: String, offset: Int): UnknownUnderline? {
        val tokens = wordTokens(value)
        val index = tokens.indexOfFirst { offset in it.startChar until it.endChar }
            .takeIf { it >= 0 }
            ?: tokens.indexOfLast { it.startChar <= offset }.takeIf { it >= 0 }
            ?: return null
        val token = tokens[index]
        return UnknownUnderline(token.startChar, token.endChar)
    }

    private fun loadUnknownUnderlines(lessonId: String, sentenceId: String): List<UnknownUnderline> {
        val raw = getSharedPreferences("unknown_underlines", MODE_PRIVATE).getString("$lessonId|$sentenceId", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { index ->
                val item = arr.getJSONObject(index)
                UnknownUnderline(item.getInt("startChar"), item.getInt("endChar"))
            }
        }.getOrDefault(emptyList())
    }

    private fun saveUnknownUnderlines(lessonId: String, sentenceId: String, ranges: List<UnknownUnderline>) {
        val normalized = ranges
            .filter { it.endChar > it.startChar }
            .sortedBy { it.startChar }
            .fold(mutableListOf<UnknownUnderline>()) { acc, item ->
                val last = acc.lastOrNull()
                if (last != null && item.startChar <= last.endChar + 1) {
                    acc[acc.lastIndex] = UnknownUnderline(last.startChar, max(last.endChar, item.endChar))
                } else {
                    acc.add(item)
                }
                acc
            }
        val arr = JSONArray()
        normalized.forEach { mark ->
            arr.put(JSONObject().put("startChar", mark.startChar).put("endChar", mark.endChar))
        }
        getSharedPreferences("unknown_underlines", MODE_PRIVATE).edit()
            .putString("$lessonId|$sentenceId", arr.toString())
            .apply()
    }

    private fun handleManualBodyChunkTap(binding: ParagraphBinding, offset: Int) {
        val lesson = currentLesson ?: return
        val sentenceIndex = binding.sentenceAt(offset) ?: return
        val localOffset = binding.localOffset(sentenceIndex, offset) ?: return
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return
        val tokens = wordTokens(sentence.text)
        if (tokens.isEmpty()) return
        val wordIndex = tokens.indexOfFirst { localOffset in it.startChar until it.endChar }.takeIf { it >= 0 }
            ?: tokens.indexOfLast { it.startChar <= localOffset }.coerceAtLeast(0)

        manualSentenceIndex = sentenceIndex
        selectedSentenceIndex = sentenceIndex
        currentSentenceIndex = sentenceIndex
        pendingStartMs = sentence.startMs

        val chunks = loadManualChunks(lesson.id, sentence.id).toMutableList()
        val editedIndex = chunks.indexOfFirst { wordIndex in it.startWord..it.endWord }
        if (editedIndex >= 0) {
            val old = chunks[editedIndex]
            chunks[editedIndex] = ManualChunk(old.startWord, wordIndex)
            while (chunks.size > editedIndex + 1) chunks.removeAt(chunks.lastIndex)
        } else {
            val nextStart = (chunks.maxOfOrNull { it.endWord } ?: -1) + 1
            if (wordIndex < nextStart) {
                toast("다음 청크 끝 단어를 눌러주세요.")
                refreshAllParagraphs()
                scrollToSentence(sentenceIndex)
                return
            }
            chunks.add(ManualChunk(nextStart, wordIndex))
        }

        val normalized = normalizeManualChunks(tokens, chunks)
        saveManualChunks(lesson.id, sentence.id, sentence.text, tokens, normalized)
        refreshAllParagraphs()
        scrollToSentence(sentenceIndex)

        if ((normalized.maxOfOrNull { it.endWord } ?: -1) >= tokens.lastIndex) {
            toast("문장 청크 저장 완료")
            speakManualChunks(sentence.text, tokens, normalized)
        }
    }

    private fun normalizeManualChunks(tokens: List<WordToken>, chunks: List<ManualChunk>): List<ManualChunk> {
        if (tokens.isEmpty()) return emptyList()
        val sorted = chunks.sortedBy { it.startWord }
        val normalized = mutableListOf<ManualChunk>()
        var nextStart = 0
        sorted.forEach { chunk ->
            val end = chunk.endWord.coerceIn(nextStart, tokens.lastIndex)
            if (nextStart <= end) {
                normalized.add(ManualChunk(nextStart, end))
                nextStart = end + 1
            }
        }
        return normalized
    }

    private fun manualBodyControls(lesson: Lesson): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }
        row.addView(pill("초기화").apply {
            setOnClickListener {
                val sentence = flatSentences.getOrNull(manualSentenceIndex)?.sentence ?: return@setOnClickListener
                saveManualChunks(lesson.id, sentence.id, sentence.text, wordTokens(sentence.text), emptyList())
                refreshAllParagraphs()
                toast("현재 문장 청크를 지웠어요.")
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).withRightMargin(dp(8)))
        row.addView(pill("청크 듣기").apply {
            setOnClickListener {
                val sentence = flatSentences.getOrNull(manualSentenceIndex)?.sentence ?: return@setOnClickListener
                val tokens = wordTokens(sentence.text)
                val chunks = loadManualChunks(lesson.id, sentence.id)
                if (chunks.isEmpty()) toast("본문에서 청크 끝 단어를 먼저 눌러주세요.") else speakManualChunks(sentence.text, tokens, chunks)
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1.1f).withRightMargin(dp(8)))
        row.addView(pill("다음 문장").apply {
            setOnClickListener { moveManualBodySentence(lesson, 1) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).withRightMargin(dp(8)))
        row.addView(pill("완료").apply {
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener {
                manualBodyMode = false
                showReader(lesson)
            }
        }, LinearLayout.LayoutParams(0, dp(48), 0.9f))
        return row
    }

    private fun moveManualBodySentence(lesson: Lesson, delta: Int) {
        manualSentenceIndex = (manualSentenceIndex + delta).coerceIn(0, max(0, flatSentences.lastIndex))
        selectedSentenceIndex = manualSentenceIndex
        currentSentenceIndex = manualSentenceIndex
        pendingStartMs = flatSentences.getOrNull(manualSentenceIndex)?.sentence?.startMs
        refreshAllParagraphs()
        scrollToSentence(manualSentenceIndex)
    }

    private fun controls(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }
        row.addView(pill("처음부터").apply {
            setOnClickListener { playFromBeginning() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).withRightMargin(dp(8)))
        row.addView(iconButton(R.drawable.ic_previous, "이전 문장").apply {
            setOnClickListener { moveSentence(-1, play = true) }
        }, fixed(dp(48), dp(48)).withRightMargin(dp(8)))
        playButton = iconButton(R.drawable.ic_play, "재생").apply {
            background = rounded(color(R.color.skin_accent), dp(24))
            setOnClickListener { playOrPause() }
        }
        row.addView(playButton, fixed(dp(56), dp(56)).withRightMargin(dp(8)))
        row.addView(iconButton(R.drawable.ic_next, "다음 문장").apply {
            setOnClickListener { moveSentence(1, play = true) }
        }, fixed(dp(48), dp(48)).withRightMargin(dp(8)))
        row.addView(iconButton(R.drawable.ic_chat, "GPT에 묻기").apply {
            setOnClickListener { askGpt() }
        }, fixed(dp(48), dp(48)))
        return row
    }

    private fun chunkAdjustPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }
        adjustPanel = panel
        adjustTitle = text("청크 조정", 14f, color(R.color.skin_ink), Typeface.BOLD)
        adjustText = text("자동 짧게/길게에서 본문 청크를 탭하면 여기서 시간을 조정할 수 있어요.", 13f, color(R.color.skin_muted))
        adjustRange = text("", 12f, color(R.color.skin_muted))
        panel.addView(adjustTitle, matchWrap())
        panel.addView(adjustText, matchWrap().withTop(dp(4)))
        panel.addView(adjustRange, matchWrap().withTop(dp(3)))
        panel.addView(waveformCalibrationRow(), matchWrap().withTop(dp(8)))

        startProfileView = BoundaryProfileView(this, editsStart = true)
        endProfileView = BoundaryProfileView(this, editsStart = false)
        panel.addView(boundaryEditor("시작", start = true, startProfileView!!), matchWrap().withTop(dp(8)))
        panel.addView(boundaryEditor("끝", start = false, endProfileView!!), matchWrap().withTop(dp(8)))

        val markRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        markRow.addView(pill("현재→시작").apply {
            setOnClickListener { setCurrentPositionAsBoundary(start = true) }
        }, LinearLayout.LayoutParams(0, dp(40), 1f).withRightMargin(dp(6)))
        markRow.addView(pill("현재→끝").apply {
            setOnClickListener { setCurrentPositionAsBoundary(start = false) }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        panel.addView(markRow, matchWrap().withTop(dp(8)))

        val snapRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        snapRow.addView(pill("시작 스냅").apply {
            setOnClickListener { snapCurrentChunkToQuietPoint(start = true) }
        }, LinearLayout.LayoutParams(0, dp(40), 1f).withRightMargin(dp(6)))
        snapRow.addView(pill("끝 스냅").apply {
            setOnClickListener { snapCurrentChunkToQuietPoint(start = false) }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        panel.addView(snapRow, matchWrap().withTop(dp(6)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(pill("이전").apply { setOnClickListener { moveChunk(-1) } }, LinearLayout.LayoutParams(0, dp(40), 1f).withRightMargin(dp(6)))
        actions.addView(pill("다시 듣기").apply { setOnClickListener { replayAdjustChunk() } }, LinearLayout.LayoutParams(0, dp(40), 1.4f).withRightMargin(dp(6)))
        actions.addView(pill("다음").apply { setOnClickListener { moveChunk(1) } }, LinearLayout.LayoutParams(0, dp(40), 1f).withRightMargin(dp(6)))
        actions.addView(pill("초기화").apply { setOnClickListener { resetAdjustChunk() } }, LinearLayout.LayoutParams(0, dp(40), 1.1f))
        panel.addView(actions, matchWrap().withTop(dp(8)))

        val exportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        exportRow.addView(pill("조정값 복사").apply {
            setOnClickListener { exportAdjustments() }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        panel.addView(exportRow, matchWrap().withTop(dp(6)))
        return panel
    }

    private fun waveformCalibrationRow(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(color(R.color.skin_surface_alt), dp(12))
        }
        waveformOffsetLabel = text("", 13f, color(R.color.skin_ink), Typeface.BOLD)
        box.addView(waveformOffsetLabel, matchWrap())
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(-100, -25, 25, 100).forEach { delta ->
            val title = if (delta > 0) "+$delta" else "$delta"
            row.addView(pill(title).apply {
                setOnClickListener { adjustWaveformOffset(delta) }
            }, LinearLayout.LayoutParams(0, dp(36), 1f).withRightMargin(dp(6)))
        }
        row.addView(pill("0").apply {
            setOnClickListener { resetWaveformOffset() }
        }, LinearLayout.LayoutParams(0, dp(36), 0.8f))
        box.addView(row, matchWrap().withTop(dp(6)))
        return box
    }

    private fun boundaryEditor(label: String, start: Boolean, profileView: BoundaryProfileView): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(color(R.color.skin_surface_alt), dp(12))
        }
        box.addView(text("$label 지점", 13f, color(R.color.skin_ink), Typeface.BOLD), matchWrap())
        box.addView(profileView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)).withTop(dp(4)))
        box.addView(adjustRow(label, start), matchWrap().withTop(dp(6)))
        return box
    }

    private fun adjustRow(label: String, start: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(-100, -25, 25, 100).forEach { delta ->
            val title = if (delta > 0) "+$delta" else "$delta"
            row.addView(pill(title).apply {
                setOnClickListener { adjustCurrentChunk(start = start, deltaMs = delta) }
            }, LinearLayout.LayoutParams(0, dp(36), 1f).withRightMargin(dp(6)))
        }
        return row
    }

    private fun adjustWaveformOffset(deltaMs: Int) {
        waveformOffsetMs += deltaMs
        saveWaveformOffset()
        updateAdjustPanel()
    }

    private fun resetWaveformOffset() {
        waveformOffsetMs = 0
        saveWaveformOffset()
        updateAdjustPanel()
    }

    private fun lessonHasAudio(lesson: Lesson): Boolean {
        return lesson.audioAssets.isNotEmpty()
    }

    private fun shouldUseTtsForMode(mode: String, lesson: Lesson): Boolean {
        return mode != "natural" || !lessonHasAudio(lesson)
    }

    private fun flowNextPanel(lesson: Lesson): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }
        row.addView(text("단어 수가 긴 5문장을 골라 별도 창에서 청크를 만들어 볼 수 있어요.", 14f, color(R.color.skin_muted)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pill("청크 5문장").apply {
            setOnClickListener { showManualChunkMode(lesson, 0) }
        }, fixed(dp(150), dp(42)))
        return row
    }

    private fun addMasterButton() {
        val button = text("M", 18f, color(R.color.skin_primary_dark), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(color(R.color.skin_mark), dp(24), color(R.color.skin_primary), dp(1))
            elevation = dp(6).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { requestMasterPassword() }
        }
        val lp = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(18)
            bottomMargin = dp(150)
        }
        root.addView(button, lp)
    }

    private fun requestMasterPassword() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "1451"
        }
        AlertDialog.Builder(this)
            .setTitle("마스터 모드")
            .setMessage("비밀번호를 입력하세요.")
            .setView(input)
            .setPositiveButton("열기") { _, _ ->
                if (input.text.toString() == "1451") showMasterSettingsDialog() else toast("비밀번호가 달라요.")
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showMasterSettingsDialog() {
        val lesson = currentLesson
        val universal = loadMasterSettings("universal")
        val active = if (lesson != null) activeMasterSettings(lesson.id) else universal
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        val vocab = CheckBox(this).apply { text = "단어장 진행"; isChecked = active.vocabEnabled }
        val quiz = CheckBox(this).apply { text = "단어 퀴즈 진행"; isChecked = active.quizEnabled }
        val choice = CheckBox(this).apply { text = "퀴즈는 사지선다"; isChecked = active.quizMode == "choice" }
        val manual = CheckBox(this).apply { text = "본문에서 직접 청크 모드"; isChecked = active.manualChunkEnabled }
        val alwaysNext = CheckBox(this).apply { text = "1차 다음 단계 항상 활성화"; isChecked = active.firstListenNextAlwaysEnabled }
        listOf(vocab, quiz, choice, manual, alwaysNext).forEach { box.addView(it, matchWrap()) }

        val firstPause = numberSettingInput("1차 문장 pause(초)", decimalSeconds(active.firstListenPauseMs), box)
        val firstRate = numberSettingInput("1차/말하기 TTS 속도", String.format(Locale.US, "%.2f", active.firstListenRate), box)
        val speakPause = numberSettingInput("2차 청크 pause(초)", decimalSeconds(active.speakChunkPauseMs), box)

        fun dialogSettings(): MasterSettings {
            return MasterSettings(
                vocabEnabled = vocab.isChecked,
                quizEnabled = quiz.isChecked,
                quizMode = if (choice.isChecked) "choice" else "speak",
                manualChunkEnabled = manual.isChecked,
                firstListenPauseMs = secondsInputMs(firstPause, 1500),
                firstListenRate = firstRate.text.toString().toFloatOrNull()?.coerceIn(0.5f, 1.5f) ?: 0.9f,
                speakChunkPauseMs = secondsInputMs(speakPause, 700),
                firstListenNextAlwaysEnabled = alwaysNext.isChecked
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("진행 스킴")
            .setView(box)
            .setPositiveButton("전체 저장") { _, _ ->
                val settings = dialogSettings()
                saveMasterSettings("universal", settings)
                masterSettings = lesson?.let { activeMasterSettings(it.id) } ?: settings
                toast("전체 설정 저장")
            }
            .setNegativeButton("닫기", null)
            .create()
        if (lesson != null) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "이 콘텐츠 저장") { _, _ ->
                val settings = dialogSettings()
                getSharedPreferences("master_settings", MODE_PRIVATE).edit()
                    .putBoolean("${lesson.id}|override", true)
                    .apply()
                saveMasterSettings(lesson.id, settings)
                masterSettings = settings
                toast("콘텐츠별 설정 저장")
            }
        }
        dialog.show()
    }

    private fun numberSettingInput(label: String, value: String, parent: LinearLayout): EditText {
        parent.addView(text(label, 13f, color(R.color.skin_muted), Typeface.BOLD).apply {
            setPadding(0, dp(10), 0, dp(3))
        }, matchWrap())
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(value)
            textSize = 16f
            setSingleLine(true)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(color(R.color.skin_surface), dp(12), color(R.color.skin_line), dp(1))
            parent.addView(this, matchWrap())
        }
    }

    private fun secondsInputMs(input: EditText, fallbackMs: Int): Int {
        val seconds = input.text.toString().toFloatOrNull() ?: return fallbackMs
        return (seconds.coerceIn(0f, 5f) * 1000f).roundToInt()
    }

    private fun activeMasterSettings(lessonId: String): MasterSettings {
        val prefs = getSharedPreferences("master_settings", MODE_PRIVATE)
        return if (prefs.getBoolean("$lessonId|override", false)) {
            loadMasterSettings(lessonId)
        } else {
            loadMasterSettings("universal")
        }
    }

    private fun loadMasterSettings(scope: String): MasterSettings {
        val prefs = getSharedPreferences("master_settings", MODE_PRIVATE)
        return MasterSettings(
            vocabEnabled = prefs.getBoolean("$scope|vocab", true),
            quizEnabled = prefs.getBoolean("$scope|quiz", true),
            quizMode = prefs.getString("$scope|quizMode", "choice") ?: "choice",
            manualChunkEnabled = prefs.getBoolean("$scope|manual", true),
            firstListenPauseMs = prefs.getInt("$scope|firstPauseMs", 1500),
            firstListenRate = prefs.getFloat("$scope|firstRate", 0.9f),
            speakChunkPauseMs = prefs.getInt("$scope|speakChunkPauseMs", 700),
            firstListenNextAlwaysEnabled = prefs.getBoolean("$scope|firstNextAlways", false)
        )
    }

    private fun saveMasterSettings(scope: String, settings: MasterSettings) {
        getSharedPreferences("master_settings", MODE_PRIVATE).edit()
            .putBoolean("$scope|vocab", settings.vocabEnabled)
            .putBoolean("$scope|quiz", settings.quizEnabled)
            .putString("$scope|quizMode", settings.quizMode)
            .putBoolean("$scope|manual", settings.manualChunkEnabled)
            .putInt("$scope|firstPauseMs", settings.firstListenPauseMs)
            .putFloat("$scope|firstRate", settings.firstListenRate)
            .putInt("$scope|speakChunkPauseMs", settings.speakChunkPauseMs)
            .putBoolean("$scope|firstNextAlways", settings.firstListenNextAlwaysEnabled)
            .apply()
    }

    private fun toggleTtsMode() {
        useTts = !useTts
        explicitSegmentEndMs = null
        explicitSegmentNextStartMs = null
        if (useTts) {
            player?.pause()
            ensureTts()
            toast("TTS mode")
        } else {
            stopTts()
            toast("Audio mode")
        }
        updateTtsModeButton()
        updatePlayIcon()
        refreshMode()
    }

    private fun updateTtsModeButton() {
        ttsModeButton?.apply {
            text = if (useTts) "TTS" else "Audio"
            background = rounded(
                if (useTts) color(R.color.skin_mark) else color(R.color.skin_surface),
                dp(16),
                if (useTts) color(R.color.skin_primary) else color(R.color.skin_line),
                dp(1)
            )
        }
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(this) { status ->
            runOnUiThread {
                isTtsReady = status == TextToSpeech.SUCCESS
                if (isTtsReady) {
                    tts?.language = Locale.US
                    tts?.setSpeechRate(0.9f)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            val id = utteranceId ?: return
                            val segment = ttsUtterances[id] ?: return
                            runOnUiThread { applyTtsSegment(segment, scroll = true) }
                        }

                        override fun onDone(utteranceId: String?) {
                            val id = utteranceId ?: return
                            if (id == ttsLastUtteranceId) {
                                val segment = ttsUtterances[id]
                                runOnUiThread {
                                    val doneCallback = ttsDoneCallback
                                    isTtsSpeaking = false
                                    pendingStartMs = segment?.startMs ?: pendingStartMs
                                    ttsUtterances.clear()
                                    ttsLastUtteranceId = null
                                    ttsDoneCallback = null
                                    updatePlayIcon()
                                    refreshMode()
                                    doneCallback?.invoke()
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            onError(utteranceId, TextToSpeech.ERROR)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            runOnUiThread {
                                val doneCallback = ttsDoneCallback
                                isTtsSpeaking = false
                                ttsDoneCallback = null
                                updatePlayIcon()
                                toast("TTS error")
                                doneCallback?.invoke()
                            }
                        }
                    })
                } else {
                    toast("TTS is not available")
                    useTts = false
                }
                updateTtsModeButton()
                updatePlayIcon()
            }
        }
    }

    private fun speakTtsSegments(
        segments: List<TtsSegment>,
        pauseAfterMs: Int = 0,
        speechRate: Float = masterSettings.firstListenRate,
        onDone: (() -> Unit)? = null,
        retry: Int = 0
    ) {
        if (unknownUnderlineMode) {
            stopTts()
            return
        }
        if (segments.isEmpty()) return
        ensureTts()
        if (!isTtsReady) {
            if (retry < 12) {
                val token = flowAutoToken
                handler.postDelayed({
                    if (token == flowAutoToken) speakTtsSegments(segments, pauseAfterMs, speechRate, onDone, retry + 1)
                }, 250L)
            } else {
                toast("TTS preparing")
                onDone?.invoke()
            }
            return
        }
        val engine = tts ?: return
        engine.setSpeechRate(speechRate)
        player?.pause()
        explicitSegmentEndMs = null
        explicitSegmentNextStartMs = null
        stopTts()

        ttsUtterances.clear()
        ttsLastUtteranceId = null
        ttsDoneCallback = onDone
        segments.forEachIndexed { index, segment ->
            val utteranceId = "tts-${System.nanoTime()}-$index"
            ttsUtterances[utteranceId] = segment
            var lastQueuedId = utteranceId
            val result = engine.speak(
                segment.text,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                utteranceId
            )
            if (result == TextToSpeech.ERROR) {
                ttsUtterances.remove(utteranceId)
                if (index == 0) {
                    toast("TTS error")
                    ttsDoneCallback = null
                    onDone?.invoke()
                    return
                }
            }
            if (pauseAfterMs > 0 && index < segments.lastIndex) {
                val pauseId = "tts-pause-${System.nanoTime()}-$index"
                if (engine.playSilentUtterance(pauseAfterMs.toLong(), TextToSpeech.QUEUE_ADD, pauseId) != TextToSpeech.ERROR) {
                    lastQueuedId = pauseId
                }
            }
            ttsLastUtteranceId = lastQueuedId
        }

        isTtsSpeaking = true
        applyTtsSegment(segments.first(), scroll = true)
        updatePlayIcon()
    }

    private fun speakTtsFrom(startMs: Int) {
        speakTtsSegments(ttsSegmentsFrom(startMs))
    }

    private fun ttsSegmentsFrom(startMs: Int): List<TtsSegment> {
        if (flatSentences.isEmpty()) return emptyList()
        val startSentenceIndex = findSentenceIndex(startMs)
        val segments = mutableListOf<TtsSegment>()
        if (activeMode == "natural" || activeMode == "sentence") {
            for (index in startSentenceIndex..flatSentences.lastIndex) {
                sentenceTtsSegment(index)?.let { segments.add(it) }
            }
            return segments
        }

        for (sentenceIndex in startSentenceIndex..flatSentences.lastIndex) {
            val sentence = flatSentences[sentenceIndex].sentence
            val chunks = sentence.chunkSets[activeMode].orEmpty()
            if (chunks.isEmpty()) {
                sentenceTtsSegment(sentenceIndex)?.let { segments.add(it) }
                continue
            }
            val firstChunkIndex = if (sentenceIndex == startSentenceIndex) {
                chunks.indexOfFirst { startMs <= it.endMs }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }
            for (chunkIndex in firstChunkIndex..chunks.lastIndex) {
                segments.add(chunkTtsSegment(sentenceIndex, chunks[chunkIndex]))
            }
        }
        return segments
    }

    private fun sentenceTtsSegment(sentenceIndex: Int): TtsSegment? {
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return null
        val sentenceChunk = sentence.chunkSets["sentence"].orEmpty().firstOrNull()
        return TtsSegment(
            sentenceIndex = sentenceIndex,
            chunkId = null,
            text = sentence.text,
            startMs = sentenceChunk?.startMs ?: sentence.startMs,
            endMs = sentenceChunk?.endMs ?: sentence.endMs
        )
    }

    private fun chunkTtsSegment(sentenceIndex: Int, chunk: Chunk): TtsSegment {
        return TtsSegment(
            sentenceIndex = sentenceIndex,
            chunkId = chunk.id,
            text = chunk.text,
            startMs = chunk.startMs,
            endMs = chunk.endMs
        )
    }

    private fun applyTtsSegment(segment: TtsSegment, scroll: Boolean) {
        selectedSentenceIndex = segment.sentenceIndex
        currentSentenceIndex = segment.sentenceIndex
        currentChunkId = segment.chunkId
        pendingStartMs = segment.startMs
        if (!isSeeking) seekBar?.progress = segment.startMs.coerceAtMost(max(1, playerDurationMs()))
        refreshAllParagraphs()
        if (scroll) scrollToSentence(segment.sentenceIndex)
        refreshMode()
        updateAdjustPanel()
    }

    private fun speakPopupText(value: String, onDone: (() -> Unit)? = null, retry: Int = 0) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        ensureTts()
        if (!isTtsReady) {
            if (retry < 8) {
                handler.postDelayed({ speakPopupText(trimmed, onDone, retry + 1) }, 250L)
            } else {
                onDone?.invoke()
            }
            return
        }
        player?.pause()
        stopTts()
        val utteranceId = "popup-${System.nanoTime()}"
        ttsLastUtteranceId = utteranceId
        ttsDoneCallback = onDone
        val result = tts?.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.ERROR) {
            isTtsSpeaking = true
            updatePlayIcon()
        } else {
            ttsDoneCallback = null
            onDone?.invoke()
        }
    }

    private fun stopTts() {
        tts?.stop()
        isTtsSpeaking = false
        ttsUtterances.clear()
        ttsLastUtteranceId = null
        ttsDoneCallback = null
        updatePlayIcon()
    }

    private fun stopAllPlayback() {
        player?.pause()
        explicitSegmentEndMs = null
        explicitSegmentNextStartMs = null
        stopTts()
        updatePlayIcon()
    }

    private fun releaseTts() {
        stopTts()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }

    private fun preparePlayer(lesson: Lesson) {
        try {
            val audio = lesson.audioAssets[lesson.defaultAudioId] ?: lesson.audioAssets.values.first()
            val extractorsFactory = DefaultExtractorsFactory()
                .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
            val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)
            val exoPlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

            player = exoPlayer
            exoPlayer.setSeekParameters(SeekParameters.EXACT)
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val duration = playerDurationMs()
                        seekBar?.max = max(1, duration)
                        timeLabel?.text = "00:00 / ${formatTime(duration)}"
                    } else if (playbackState == Player.STATE_ENDED) {
                        explicitSegmentEndMs = null
                        pendingStartMs = flatSentences.firstOrNull()?.sentence?.startMs
                        updatePlayIcon()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayIcon()
                }
            })

            val mediaItem = MediaItem.fromUri(Uri.parse("asset:///${lesson.basePath}/${audio.file}"))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            handler.post(tick)
        } catch (error: Exception) {
            toast("음원을 열 수 없습니다: ${error.message}")
        }
    }

    private fun loadAudioProfile(lesson: Lesson) {
        audioProfile = null
        updateAdjustPanel()
        Thread {
            val profile = runCatching { decodeAudioProfile(lesson) }.getOrNull()
            runOnUiThread {
                if (currentLesson?.id == lesson.id) {
                    audioProfile = profile
                    updateAdjustPanel()
                }
            }
        }.start()
    }

    private fun decodeAudioProfile(lesson: Lesson): AudioProfile {
        val audio = lesson.audioAssets[lesson.defaultAudioId] ?: lesson.audioAssets.values.first()
        val afd = assets.openFd("${lesson.basePath}/${audio.file}")
        val extractor = MediaExtractor()
        val codec: MediaCodec
        var sampleRate = 44_100
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("audio track not found")
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("audio mime not found")
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
        } finally {
            afd.close()
        }

        val windowMs = 20
        val samplesPerWindow = max(1, sampleRate * windowMs / 1000)
        val bufferInfo = MediaCodec.BufferInfo()
        val profile = mutableListOf<Float>()
        var sawInputEnd = false
        var sawOutputEnd = false
        var squareSum = 0.0
        var sampleCount = 0

        fun pushSample(value: Short) {
            val normalized = value.toDouble() / Short.MAX_VALUE.toDouble()
            squareSum += normalized * normalized
            sampleCount += 1
            if (sampleCount >= samplesPerWindow) {
                profile.add(sqrt(squareSum / sampleCount).toFloat())
                squareSum = 0.0
                sampleCount = 0
            }
        }

        try {
            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val pcm = outputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                            pcm.position(bufferInfo.offset)
                            pcm.limit(bufferInfo.offset + bufferInfo.size)
                            while (pcm.remaining() >= 2) pushSample(pcm.getShort())
                        }
                        sawOutputEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            if (sampleCount > 0) profile.add(sqrt(squareSum / sampleCount).toFloat())
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        val durationMs = profile.size * windowMs
        return AudioProfile(profile.toFloatArray(), windowMs, durationMs)
    }

    private fun playSentenceOnce(sentenceIndex: Int) {
        if (unknownUnderlineMode) {
            stopAllPlayback()
            return
        }
        if (useTts) {
            sentenceTtsSegment(sentenceIndex)?.let { speakTtsSegments(listOf(it)) }
            return
        }
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return
        val sentenceChunk = sentence.chunkSets["sentence"].orEmpty().firstOrNull()
        val startMs = sentenceChunk?.startMs ?: sentence.startMs
        val endMs = sentenceChunk?.endMs ?: sentence.endMs
        selectedSentenceIndex = sentenceIndex
        currentSentenceIndex = sentenceIndex
        pendingStartMs = startMs
        explicitSegmentEndMs = endMs
        explicitSegmentNextStartMs = startMs
        player?.seekTo(startMs.toLong())
        player?.play()
        updatePlayIcon()
        syncCurrentPosition(startMs, scroll = true)
        refreshAllParagraphs()
    }

    private fun playChunkAtOffset(sentenceIndex: Int, localOffset: Int?) {
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return
        val chunks = sentence.chunkSets[activeMode].orEmpty()
        val chunkIndex = localOffset
            ?.let { offset -> chunks.indexOfFirst { offset in it.startChar until it.endChar } }
            ?.takeIf { it >= 0 }
            ?: chunks.indexOfFirst { it.id == currentChunkId }.takeIf { it >= 0 }
            ?: 0
        playChunkOnce(sentenceIndex, chunkIndex)
    }

    private fun playChunkOnce(sentenceIndex: Int, chunkIndex: Int) {
        if (unknownUnderlineMode) {
            stopAllPlayback()
            return
        }
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return
        val chunks = sentence.chunkSets[activeMode].orEmpty()
        val chunk = chunks.getOrNull(chunkIndex) ?: return
        if (useTts) {
            speakTtsSegments(listOf(chunkTtsSegment(sentenceIndex, chunk)))
            updateAdjustPanel()
            return
        }
        val nextStart = chunks.getOrNull(chunkIndex + 1)?.startMs
            ?: flatSentences.getOrNull(sentenceIndex + 1)?.sentence?.startMs
            ?: chunk.startMs
        selectedSentenceIndex = sentenceIndex
        currentSentenceIndex = sentenceIndex
        currentChunkId = chunk.id
        pendingStartMs = chunk.startMs
        explicitSegmentEndMs = chunk.endMs
        explicitSegmentNextStartMs = nextStart
        player?.seekTo(chunk.startMs.toLong())
        player?.play()
        updatePlayIcon()
        syncCurrentPosition(chunk.startMs, scroll = true)
        refreshAllParagraphs()
        updateAdjustPanel()
    }

    private fun activeAdjustSelection(): AdjustSelection? {
        if (activeMode == "natural") return null
        val sentenceIndex = currentSentenceIndex.coerceIn(0, max(0, flatSentences.lastIndex))
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return null
        val chunks = sentence.chunkSets[activeMode].orEmpty()
        if (chunks.isEmpty()) return null
        val chunkIndex = chunks.indexOfFirst { it.id == currentChunkId }
            .takeIf { it >= 0 }
            ?: chunks.indexOfFirst { pendingStartMs != null && pendingStartMs!! in it.startMs..it.endMs }.takeIf { it >= 0 }
            ?: 0
        return AdjustSelection(sentenceIndex, chunkIndex, sentence, chunks[chunkIndex], chunks)
    }

    private fun adjustCurrentChunk(start: Boolean, deltaMs: Int) {
        val selection = activeAdjustSelection() ?: run {
            toast("자동 짧게/길게에서 청크를 먼저 탭해 주세요.")
            return
        }
        val current = if (start) selection.chunk.startMs else selection.chunk.endMs
        setBoundaryToTime(selection, start, current + deltaMs, preview = true)
    }

    private fun setCurrentPositionAsBoundary(start: Boolean) {
        val selection = activeAdjustSelection() ?: run {
            toast("조정할 청크를 먼저 선택해 주세요.")
            return
        }
        val position = playerPositionMs()
        setBoundaryToTime(selection, start, position, preview = true)
    }

    private fun setBoundaryToTime(selection: AdjustSelection, start: Boolean, requestedMs: Int, preview: Boolean) {
        val chunk = selection.chunk
        val maxMs = editableDurationMs()
        if (start) {
            val lower = 0
            val upper = max(0, chunk.endMs - 1)
            chunk.startMs = requestedMs.coerceIn(lower, upper)
            pendingStartMs = chunk.startMs
        } else {
            val oldEnd = chunk.endMs
            val lower = min(maxMs, chunk.startMs + 1)
            val upper = maxMs
            chunk.endMs = requestedMs.coerceIn(lower, upper)
            val delta = chunk.endMs - oldEnd
            if (delta != 0) shiftNextChunkStart(selection, delta)
            explicitSegmentEndMs = chunk.endMs
        }
        saveChunkAdjustment(chunk)
        currentChunkId = chunk.id
        refreshAllParagraphs()
        updateAdjustPanel()
        if (preview) previewBoundary(selection, start)
    }

    private fun shiftNextChunkStart(selection: AdjustSelection, deltaMs: Int) {
        val nextChunk = selection.chunks.getOrNull(selection.chunkIndex + 1)
            ?: flatSentences.getOrNull(selection.sentenceIndex + 1)
                ?.sentence
                ?.chunkSets
                ?.get(activeMode)
                ?.firstOrNull()
            ?: return
        val maxMs = editableDurationMs()
        nextChunk.startMs = (nextChunk.startMs + deltaMs).coerceIn(0, max(0, maxMs - 1))
        sanitizeChunkTiming(nextChunk, maxMs)
        saveChunkAdjustment(nextChunk)
    }

    private fun editableDurationMs(): Int {
        return max(
            max(playerDurationMs(), audioProfile?.durationMs ?: 0),
            flatSentences.lastOrNull()?.sentence?.endMs ?: 0
        )
    }

    private fun previewBoundary(selection: AdjustSelection, start: Boolean) {
        val chunk = selection.chunk
        val previewStart = if (start) {
            chunk.startMs
        } else {
            max(chunk.startMs, chunk.endMs - 1000)
        }
        val previewEnd = if (start) {
            min(chunk.endMs, chunk.startMs + 1000)
        } else {
            chunk.endMs
        }
        if (previewEnd <= previewStart) return
        selectedSentenceIndex = selection.sentenceIndex
        currentSentenceIndex = selection.sentenceIndex
        currentChunkId = chunk.id
        pendingStartMs = chunk.startMs
        explicitSegmentEndMs = previewEnd
        explicitSegmentNextStartMs = chunk.startMs
        player?.seekTo(previewStart.toLong())
        player?.play()
        updatePlayIcon()
        syncCurrentPosition(previewStart, scroll = false)
    }

    private fun snapCurrentChunkToQuietPoint(start: Boolean) {
        val selection = activeAdjustSelection() ?: run {
            toast("조정할 청크를 먼저 선택해 주세요.")
            return
        }
        val chunk = selection.chunk
        val center = if (start) chunk.startMs else chunk.endMs
        val lower = if (start) 0 else chunk.startMs + 1
        val upper = if (start) chunk.endMs - 1 else editableDurationMs()
        val candidate = quietPointNear(center, lower, upper)
        if (candidate == null) {
            toast("아직 음원 프로파일이 준비되지 않았어요.")
            return
        }
        setBoundaryToTime(selection, start, candidate, preview = true)
    }

    private fun moveChunk(delta: Int) {
        val selection = activeAdjustSelection() ?: run {
            toast("조정할 청크를 먼저 선택해 주세요.")
            return
        }
        var sentenceIndex = selection.sentenceIndex
        var chunkIndex = selection.chunkIndex + delta
        var sentence = selection.sentence
        var chunks = selection.chunks

        if (chunkIndex < 0) {
            sentenceIndex -= 1
            sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return
            chunks = sentence.chunkSets[activeMode].orEmpty()
            chunkIndex = chunks.lastIndex
        } else if (chunkIndex > chunks.lastIndex) {
            sentenceIndex += 1
            sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return
            chunks = sentence.chunkSets[activeMode].orEmpty()
            chunkIndex = 0
        }
        if (chunks.isEmpty() || chunkIndex !in chunks.indices) return
        playChunkOnce(sentenceIndex, chunkIndex)
    }

    private fun replayAdjustChunk() {
        val selection = activeAdjustSelection() ?: run {
            toast("다시 들을 청크를 먼저 선택해 주세요.")
            return
        }
        playChunkOnce(selection.sentenceIndex, selection.chunkIndex)
    }

    private fun resetAdjustChunk() {
        val selection = activeAdjustSelection() ?: run {
            toast("초기화할 청크를 먼저 선택해 주세요.")
            return
        }
        selection.chunk.startMs = selection.chunk.originalStartMs
        selection.chunk.endMs = selection.chunk.originalEndMs
        clearChunkAdjustment(selection.chunk)
        updateAdjustPanel()
        previewBoundary(selection, start = true)
    }

    private fun playFromBeginning() {
        val first = flatSentences.firstOrNull()?.sentence ?: return
        selectedSentenceIndex = 0
        pendingStartMs = first.startMs
        explicitSegmentEndMs = null
        explicitSegmentNextStartMs = null
        startContinuous(first.startMs)
    }

    private fun playOrPause() {
        if (unknownUnderlineMode) {
            stopAllPlayback()
            toast("줄긋기 모드에서는 재생을 멈춰요.")
            return
        }
        if (useTts) {
            if (isTtsSpeaking) {
                stopTts()
                return
            }
            val start = pendingStartMs
                ?: flatSentences.getOrNull(selectedSentenceIndex)?.sentence?.startMs
                ?: playerPositionMs()
            speakTtsFrom(start)
            return
        }
        val exoPlayer = player ?: return
        if (exoPlayer.isPlaying) {
            pendingStartMs = playerPositionMs()
            explicitSegmentEndMs = null
            explicitSegmentNextStartMs = null
            exoPlayer.pause()
            updatePlayIcon()
            return
        }
        val start = pendingStartMs
            ?: flatSentences.getOrNull(selectedSentenceIndex)?.sentence?.startMs
            ?: playerPositionMs()
        explicitSegmentEndMs = null
        explicitSegmentNextStartMs = null
        startContinuous(start)
    }

    private fun startContinuous(startMs: Int) {
        if (unknownUnderlineMode) {
            stopAllPlayback()
            return
        }
        if (useTts) {
            speakTtsFrom(startMs)
            return
        }
        player?.seekTo(startMs.toLong())
        player?.play()
        pendingStartMs = startMs
        syncCurrentPosition(startMs, scroll = true)
        updatePlayIcon()
    }

    private fun moveSentence(delta: Int, play: Boolean) {
        if (flatSentences.isEmpty()) return
        val next = (selectedSentenceIndex + delta).coerceIn(0, flatSentences.lastIndex)
        selectedSentenceIndex = next
        pendingStartMs = flatSentences[next].sentence.startMs
        if (play) playSentenceOnce(next) else refreshAllParagraphs()
    }

    private fun updateProgress() {
        val exoPlayer = player
        if (exoPlayer == null) {
            val position = pendingStartMs
                ?: flatSentences.getOrNull(currentSentenceIndex)?.sentence?.startMs
                ?: 0
            val duration = editableDurationMs()
            if (!isSeeking) seekBar?.progress = position.coerceAtMost(max(1, duration))
            timeLabel?.text = "${if (useTts) "TTS " else ""}${formatTime(position)} / ${formatTime(duration)}"
            updatePlayIcon()
            return
        }
        if (useTts && !exoPlayer.isPlaying) {
            val position = pendingStartMs
                ?: flatSentences.getOrNull(currentSentenceIndex)?.sentence?.startMs
                ?: 0
            val duration = playerDurationMs()
            if (!isSeeking) seekBar?.progress = position.coerceAtMost(max(1, duration))
            timeLabel?.text = "TTS ${formatTime(position)} / ${formatTime(duration)}"
            updatePlayIcon()
            return
        }
        val position = playerPositionMs()
        val duration = playerDurationMs()
        if (!isSeeking) seekBar?.progress = position.coerceAtMost(max(1, duration))
        timeLabel?.text = "${formatTime(position)} / ${formatTime(duration)}"

        val explicitEnd = explicitSegmentEndMs
        if (explicitEnd != null && exoPlayer.isPlaying && position >= explicitEnd) {
            exoPlayer.pause()
            exoPlayer.seekTo(explicitEnd.toLong())
            pendingStartMs = explicitSegmentNextStartMs
                ?: flatSentences.getOrNull(selectedSentenceIndex)?.sentence?.startMs
            explicitSegmentEndMs = null
            explicitSegmentNextStartMs = null
            updatePlayIcon()
            syncCurrentPosition(explicitEnd, scroll = false)
            return
        }

        syncCurrentPosition(position, scroll = exoPlayer.isPlaying)

        if (exoPlayer.isPlaying && activeMode != "natural") {
            val boundary = currentBoundary(position)
            if (boundary != null && position >= boundary.endMs) {
                exoPlayer.pause()
                exoPlayer.seekTo(boundary.endMs.toLong())
                pendingStartMs = boundary.nextStartMs
                boundary.nextSentenceIndex?.let {
                    selectedSentenceIndex = it
                    currentSentenceIndex = it
                }
                updatePlayIcon()
                syncCurrentPosition(boundary.endMs, scroll = true)
            }
        }
        updatePlayIcon()
    }

    private fun syncCurrentPosition(position: Int, scroll: Boolean) {
        val sentenceIndex = findSentenceIndex(position)
        val chunk = findChunk(sentenceIndex, position)
        val changed = sentenceIndex != currentSentenceIndex || chunk?.id != currentChunkId
        currentSentenceIndex = sentenceIndex
        currentChunkId = chunk?.id
        if (changed) {
            refreshAllParagraphs()
            if (scroll) scrollToSentence(sentenceIndex)
        }
        refreshMode()
    }

    private fun currentBoundary(position: Int): PlaybackBoundary? {
        val sentenceIndex = findSentenceIndex(position)
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return null
        return if (activeMode == "sentence") {
            val chunk = sentence.chunkSets["sentence"].orEmpty().firstOrNull()
            val next = flatSentences.getOrNull(sentenceIndex + 1)?.sentence
            val nextChunk = next?.chunkSets?.get("sentence").orEmpty().firstOrNull()
            val endMs = chunk?.endMs ?: sentence.endMs
            PlaybackBoundary(endMs, nextChunk?.startMs ?: next?.startMs ?: endMs, if (next != null) sentenceIndex + 1 else sentenceIndex)
        } else {
            val chunks = sentence.chunkSets[activeMode].orEmpty()
            val chunkIndex = chunks.indexOfFirst { position in it.startMs..it.endMs }.let {
                if (it >= 0) it else chunks.indexOfLast { chunk -> chunk.startMs <= position }.coerceAtLeast(0)
            }
            val chunk = chunks.getOrNull(chunkIndex) ?: return null
            val nextChunk = chunks.getOrNull(chunkIndex + 1)
            val nextSentence = flatSentences.getOrNull(sentenceIndex + 1)?.sentence
            PlaybackBoundary(
                chunk.endMs,
                nextChunk?.startMs ?: nextSentence?.startMs ?: chunk.endMs,
                if (nextChunk == null && nextSentence != null) sentenceIndex + 1 else sentenceIndex
            )
        }
    }

    private fun refreshAllParagraphs() {
        paragraphBindings.forEach { binding ->
            binding.textView.text = styledParagraph(binding)
            (binding.textView as? WavyTextView)?.setWavyRanges(wavyRangesFor(binding))
        }
    }

    private fun wavyRangesFor(binding: ParagraphBinding): List<IntRange> {
        val lesson = currentLesson ?: return emptyList()
        val ranges = mutableListOf<IntRange>()
        binding.ranges.forEach { range ->
            val sentence = flatSentences.getOrNull(range.sentenceIndex)?.sentence ?: return@forEach
            loadUnknownUnderlines(lesson.id, sentence.id).forEach { mark ->
                val start = (range.start + mark.startChar).coerceIn(range.start, range.end)
                val end = (range.start + mark.endChar).coerceIn(start, range.end)
                if (end > start) ranges.add(start until end)
            }
        }
        val draft = unknownDraft
        if (draft != null && draft.binding === binding) {
            val start = min(draft.startOffset, draft.currentOffset).coerceIn(0, binding.text.length)
            val end = max(draft.startOffset, draft.currentOffset).coerceIn(start, binding.text.length)
            if (end > start) ranges.add(start until end)
        }
        return ranges
    }

    private fun styledParagraph(binding: ParagraphBinding): SpannableString {
        val span = SpannableString(binding.text)
        val lesson = currentLesson ?: return span
        binding.ranges.forEach { range ->
            val sentence = flatSentences[range.sentenceIndex].sentence
            if (range.sentenceIndex == currentSentenceIndex) {
                span.setSpan(BackgroundColorSpan(color(R.color.skin_highlight)), range.start, range.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (range.sentenceIndex == selectedSentenceIndex) {
                span.setSpan(BackgroundColorSpan(color(R.color.skin_surface_alt)), range.start, range.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (manualBodyMode) {
                val tokens = wordTokens(sentence.text)
                val manualChunks = loadManualChunks(lesson.id, sentence.id)
                manualChunks.forEachIndexed { index, chunk ->
                    val startChar = tokens.getOrNull(chunk.startWord)?.startChar ?: return@forEachIndexed
                    val endChar = tokens.getOrNull(chunk.endWord)?.endChar ?: return@forEachIndexed
                    val start = range.start + startChar
                    val end = min(range.start + endChar, range.end)
                    if (start in 0 until end && end <= span.length) {
                        span.setSpan(
                            BackgroundColorSpan(if (index % 2 == 0) color(R.color.skin_mark) else color(R.color.skin_highlight)),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        span.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                if (range.sentenceIndex == manualSentenceIndex && tokens.isNotEmpty()) {
                    val nextStartWord = ((manualChunks.maxOfOrNull { it.endWord } ?: -1) + 1).coerceIn(0, tokens.lastIndex)
                    val nextToken = tokens.getOrNull(nextStartWord)
                    if (nextToken != null && (manualChunks.maxOfOrNull { it.endWord } ?: -1) < tokens.lastIndex) {
                        val start = range.start + nextToken.startChar
                        val end = min(range.start + nextToken.endChar, range.end)
                        if (start in 0 until end && end <= span.length) {
                            span.setSpan(ForegroundColorSpan(color(R.color.skin_primary_dark)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            span.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            span.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            }

            sentence.annotations.forEach { annotation ->
                val start = range.start + annotation.startChar
                val end = min(range.start + annotation.endChar, range.end)
                if (start in 0 until end && end <= span.length) {
                    span.setSpan(ForegroundColorSpan(color(R.color.skin_primary_dark)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    span.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    span.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            val currentChunk = sentence.chunkSets[activeMode].orEmpty().firstOrNull { it.id == currentChunkId }
            if (range.sentenceIndex == currentSentenceIndex && currentChunk != null) {
                val start = range.start + currentChunk.startChar
                val end = min(range.start + currentChunk.endChar, range.end)
                if (start in 0 until end && end <= span.length) {
                    span.setSpan(BackgroundColorSpan(color(R.color.skin_mark)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    span.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return span
    }

    private fun refreshMode() {
        val label = currentLesson?.profiles?.get(activeMode)?.label ?: activeMode
        val modeText = when (activeMode) {
            "natural" -> "자연스럽게: 멈추지 않고 이어서 재생합니다."
            "short" -> "$label: chunk마다 쉬어갑니다."
            "long" -> "$label: chunk마다 쉬어갑니다."
            "sentence" -> "문장: 한 문장씩 쉬어갑니다."
            else -> activeMode
        }
        val sourceText = if (useTts) "TTS" else "Audio"
        modeLabel?.text = "$modeText · $sourceText · ${selectedSentenceIndex + 1}/${flatSentences.size}"
        modeButtons.forEach { (id, view) ->
            view.background = rounded(
                if (id == activeMode) color(R.color.skin_mark) else color(R.color.skin_surface),
                dp(16),
                if (id == activeMode) color(R.color.skin_primary) else color(R.color.skin_line),
                dp(1)
            )
        }
        updateTtsModeButton()
        updateAdjustPanel()
    }

    private fun updateAdjustPanel() {
        val selection = activeAdjustSelection()
        val visible = selection != null
        adjustPanel?.alpha = if (visible) 1f else 0.58f
        if (selection == null) {
            adjustTitle?.text = "청크 조정"
            adjustText?.text = "문장/자동 짧게/자동 길게에서 본문을 탭하면 시작/끝 시간을 바로 조정할 수 있어요."
            adjustRange?.text = ""
            waveformOffsetLabel?.text = "파형 싱크 ${if (waveformOffsetMs >= 0) "+" else ""}${waveformOffsetMs}ms · 레슨별 저장"
            startProfileView?.setBoundary(null, "시작", 0, null, 0, 0)
            endProfileView?.setBoundary(null, "끝", 0, null, 0, 0)
            return
        }

        val label = currentLesson?.profiles?.get(activeMode)?.label ?: activeMode
        val chunk = selection.chunk
        val startLower = 0
        val startUpper = chunk.endMs - 1
        val endLower = chunk.startMs + 1
        val endUpper = editableDurationMs()
        val startCandidate = quietPointNear(chunk.startMs, startLower, startUpper)
        val endCandidate = quietPointNear(chunk.endMs, endLower, endUpper)
        adjustTitle?.text = "청크 조정 · $label · ${selection.chunkIndex + 1}/${selection.chunks.size}"
        adjustText?.text = chunk.text
        val changed = if (chunk.startMs != chunk.originalStartMs || chunk.endMs != chunk.originalEndMs) " · 저장됨" else ""
        adjustRange?.text = "시작 ${formatTimeMs(chunk.startMs)}  끝 ${formatTimeMs(chunk.endMs)}  길이 ${chunk.endMs - chunk.startMs}ms$changed"
        waveformOffsetLabel?.text = "파형 싱크 ${if (waveformOffsetMs >= 0) "+" else ""}${waveformOffsetMs}ms · 레슨별 저장"
        startProfileView?.setBoundary(audioProfile, "시작", chunk.startMs, startCandidate, startLower, startUpper)
        endProfileView?.setBoundary(audioProfile, "끝", chunk.endMs, endCandidate, endLower, endUpper)
    }

    private fun quietPointNear(centerMs: Int, lowerMs: Int, upperMs: Int): Int? {
        val profile = audioProfile ?: return null
        if (profile.samples.isEmpty() || lowerMs > upperMs) return null
        val centerProfileMs = centerMs + waveformOffsetMs
        val lowerProfileMs = lowerMs + waveformOffsetMs
        val upperProfileMs = upperMs + waveformOffsetMs
        val searchStart = max(lowerProfileMs, centerProfileMs - 550)
        val searchEnd = min(upperProfileMs, centerProfileMs + 550)
        if (searchStart > searchEnd) return null
        val startIndex = (searchStart / profile.windowMs).coerceIn(0, profile.samples.lastIndex)
        val endIndex = (searchEnd / profile.windowMs).coerceIn(startIndex, profile.samples.lastIndex)
        var bestIndex = startIndex
        var bestScore = Float.MAX_VALUE
        for (index in startIndex..endIndex) {
            val time = index * profile.windowMs
            val amplitude = profile.samples[index]
            val distancePenalty = abs(time - centerProfileMs).toFloat() / 550f * 0.18f
            val score = amplitude + distancePenalty
            if (score < bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        return (bestIndex * profile.windowMs - waveformOffsetMs).coerceIn(lowerMs, upperMs)
    }

    private fun saveChunkAdjustment(chunk: Chunk) {
        val lessonId = currentLesson?.id ?: return
        saveChunkAdjustment(lessonId, chunk)
    }

    private fun saveChunkAdjustment(lessonId: String, chunk: Chunk) {
        val key = "$lessonId|${chunk.id}"
        getSharedPreferences("chunk_adjustments", MODE_PRIVATE)
            .edit()
            .putInt("$key|start", chunk.startMs)
            .putInt("$key|end", chunk.endMs)
            .apply()
    }

    private fun sanitizeChunkTiming(chunk: Chunk, maxMs: Int = 0) {
        chunk.startMs = chunk.startMs.coerceAtLeast(0)
        if (maxMs > 0) chunk.startMs = chunk.startMs.coerceAtMost(max(0, maxMs - 1))
        if (chunk.endMs > chunk.startMs) return

        val originalDuration = max(1, chunk.originalEndMs - chunk.originalStartMs)
        val restoredEnd = max(chunk.originalEndMs, chunk.startMs + originalDuration)
        chunk.endMs = if (maxMs > 0) {
            restoredEnd.coerceIn(chunk.startMs + 1, max(chunk.startMs + 1, maxMs))
        } else {
            restoredEnd
        }
    }

    private fun clearChunkAdjustment(chunk: Chunk) {
        val lessonId = currentLesson?.id ?: return
        val key = "$lessonId|${chunk.id}"
        getSharedPreferences("chunk_adjustments", MODE_PRIVATE)
            .edit()
            .remove("$key|start")
            .remove("$key|end")
            .apply()
    }

    private fun loadWaveformOffset(lessonId: String): Int {
        return getSharedPreferences("waveform_offsets", MODE_PRIVATE)
            .getInt(lessonId, 0)
    }

    private fun saveWaveformOffset() {
        val lessonId = currentLesson?.id ?: return
        getSharedPreferences("waveform_offsets", MODE_PRIVATE)
            .edit()
            .putInt(lessonId, waveformOffsetMs)
            .apply()
    }

    private fun applySavedAdjustments(lesson: Lesson) {
        val prefs = getSharedPreferences("chunk_adjustments", MODE_PRIVATE)
        val prefix = "${lesson.id}|"
        lesson.paragraphs.forEach { paragraph ->
            paragraph.sentences.forEach { sentence ->
                sentence.chunkSets.values.flatten().forEach { chunk ->
                    val key = "$prefix${chunk.id}"
                    if (prefs.contains("$key|start")) chunk.startMs = prefs.getInt("$key|start", chunk.startMs)
                    if (prefs.contains("$key|end")) chunk.endMs = prefs.getInt("$key|end", chunk.endMs)
                    val beforeStart = chunk.startMs
                    val beforeEnd = chunk.endMs
                    sanitizeChunkTiming(chunk)
                    if (chunk.startMs != beforeStart || chunk.endMs != beforeEnd) saveChunkAdjustment(lesson.id, chunk)
                }
            }
        }
    }

    private fun exportAdjustments() {
        val lesson = currentLesson ?: return
        val prefs = getSharedPreferences("chunk_adjustments", MODE_PRIVATE)
        val prefix = "${lesson.id}|"
        val items = JSONArray()
        lesson.paragraphs.forEach { paragraph ->
            paragraph.sentences.forEach { sentence ->
                sentence.chunkSets.forEach { (mode, chunks) ->
                    chunks.forEach { chunk ->
                        val key = "$prefix${chunk.id}"
                        if (prefs.contains("$key|start") || prefs.contains("$key|end")) {
                            items.put(
                                JSONObject()
                                    .put("lessonId", lesson.id)
                                    .put("mode", mode)
                                    .put("sentenceId", sentence.id)
                                    .put("chunkId", chunk.id)
                                    .put("text", chunk.text)
                                    .put("startMs", chunk.startMs)
                                    .put("endMs", chunk.endMs)
                                    .put("originalStartMs", chunk.originalStartMs)
                                    .put("originalEndMs", chunk.originalEndMs)
                            )
                        }
                    }
                }
            }
        }
        val json = JSONObject()
            .put("lessonId", lesson.id)
            .put("waveformOffsetMs", waveformOffsetMs)
            .put("adjustments", items)
            .toString(2)
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("chunk-adjustments-${lesson.id}.json", json))
        toast("조정값을 클립보드에 복사했어요.")
    }

    private fun showAnnotation(annotation: Annotation) {
        stopAllPlayback()
        val lesson = currentLesson ?: return
        val vocab = lesson.vocabulary[annotation.wordId]
        val explanation = annotation.explanationIds.firstOrNull()?.let { lesson.explanations[it] }
        val title = vocab?.word ?: annotation.text
        val message = buildString {
            vocab?.meaningKo?.takeIf { it.isNotBlank() }?.let { append("뜻: ").append(it).append("\n\n") }
            vocab?.easyEnglish?.takeIf { it.isNotBlank() }?.let { append("Easy English: ").append(it).append("\n\n") }
            explanation?.textKo?.takeIf { it.isNotBlank() }?.let { append(it) }
        }.ifBlank { "아직 연결된 설명이 없습니다." }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("닫기", null)
            .setNegativeButton("GPT 질문") { _, _ ->
                copyPrompt(title, message)
            }
            .show()
        speakPopupText(title)
        dialog.setOnDismissListener { stopAllPlayback() }
    }

    private fun showSentencePopup(sentence: LessonSentence) {
        stopAllPlayback()
        val dialog = AlertDialog.Builder(this)
            .setTitle("문장")
            .setMessage(sentence.text)
            .setPositiveButton("닫기", null)
            .setNegativeButton("GPT 질문") { _, _ ->
                copyPrompt(sentence.text, "이 문장을 초등학생 영어 학습자에게 쉽게 설명해줘.")
            }
            .show()
        speakPopupText(sentence.text)
        dialog.setOnDismissListener { stopAllPlayback() }
    }

    private fun askGpt() {
        val sentence = flatSentences.getOrNull(selectedSentenceIndex)?.sentence ?: return
        copyPrompt(sentence.text, "이 문장을 초등학생 영어 학습자에게 쉽게 설명해줘.")
    }

    private fun copyPrompt(target: String, context: String) {
        val prompt = """
            초등학생 영어 학습자에게 아래 표현을 쉽게 설명해줘.
            뜻, 중요한 단어, 문장 구조, 비슷한 예문 3개를 한국어로 알려줘.

            표현: "$target"
            참고: $context
        """.trimIndent()
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Seoin English GPT prompt", prompt))
        toast("GPT 프롬프트를 복사했어요.")
        val chrome = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")).apply {
            setPackage("com.android.chrome")
        }
        runCatching { startActivity(chrome) }.getOrElse {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"))) }
        }
    }

    private fun scrollToSentence(sentenceIndex: Int) {
        val binding = paragraphBindings.firstOrNull { it.ranges.any { range -> range.sentenceIndex == sentenceIndex } } ?: return
        readerScroll?.post {
            readerScroll?.smoothScrollTo(0, max(0, binding.textView.top - dp(28)))
        }
    }

    private fun findSentenceIndex(position: Int): Int {
        val exact = flatSentences.indexOfFirst { position in it.sentence.startMs..it.sentence.endMs }
        if (exact >= 0) return exact
        return flatSentences.indexOfLast { it.sentence.startMs <= position }.coerceIn(0, max(0, flatSentences.lastIndex))
    }

    private fun findChunk(sentenceIndex: Int, position: Int): Chunk? {
        if (activeMode == "natural") return null
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return null
        val chunks = sentence.chunkSets[activeMode].orEmpty()
        return chunks.firstOrNull { position in it.startMs..it.endMs }
            ?: chunks.lastOrNull { it.startMs <= position }
    }

    private fun offsetFor(textView: TextView, event: MotionEvent): Int? {
        val layout = textView.layout ?: return null
        val x = event.x - textView.totalPaddingLeft + textView.scrollX
        val y = event.y - textView.totalPaddingTop + textView.scrollY
        val line = layout.getLineForVertical(y.toInt().coerceAtLeast(0))
        return layout.getOffsetForHorizontal(line, x).coerceIn(0, textView.text.length)
    }

    private fun playerPositionMs(): Int {
        return player?.currentPosition
            ?.coerceIn(0L, Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 0
    }

    private fun playerDurationMs(): Int {
        val duration = player?.duration ?: 0L
        if (duration <= 0L || duration > Int.MAX_VALUE) return 0
        return duration.toInt()
    }

    private fun updatePlayIcon() {
        playButton?.setImageResource(if (player?.isPlaying == true || isTtsSpeaking) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun releasePlayer() {
        flowAutoToken += 1L
        handler.removeCallbacks(tick)
        stopTts()
        firstListenMode = false
        player?.release()
        player = null
        audioProfile = null
        playButton = null
        ttsModeButton = null
        unknownButton = null
        firstListenNextButton = null
        unknownDraft = null
        seekBar = null
        timeLabel = null
        modeLabel = null
        adjustPanel = null
        adjustTitle = null
        adjustText = null
        adjustRange = null
        waveformOffsetLabel = null
        startProfileView = null
        endProfileView = null
        manualChunkView = null
        readerScroll = null
        paragraphBindings = emptyList()
    }

    private fun loadManifest(): List<LessonMeta> {
        return runCatching {
            val json = JSONObject(readAsset("lessons/manifest.json"))
            val arr = json.getJSONArray("lessons")
            List(arr.length()) { index ->
                val item = arr.getJSONObject(index)
                LessonMeta(
                    id = item.getString("id"),
                    title = item.optString("title", item.getString("id")),
                    subtitle = item.optString("subtitle", ""),
                    level = item.optString("level", ""),
                    path = item.optString("path", item.optString("lessonFile", ""))
                )
            }
        }.getOrElse {
            toast("레슨 목록을 읽을 수 없습니다: ${it.message}")
            emptyList()
        }
    }

    private fun loadLesson(meta: LessonMeta): Lesson {
        val json = JSONObject(readAsset(meta.path))
        val basePath = meta.path.substringBeforeLast("/")
        val imageAssets = mutableMapOf<String, ImageAsset>()
        json.optJSONArray("imageAssets").forEachObject { item ->
            val asset = ImageAsset(
                id = item.getString("id"),
                file = item.getString("file"),
                role = item.optString("role", ""),
                label = item.optString("label", ""),
                page = item.optInt("page", 0)
            )
            imageAssets[asset.id] = asset
        }
        val audioAssets = mutableMapOf<String, AudioAsset>()
        json.optJSONArray("audioAssets").forEachObject { item ->
            val asset = AudioAsset(
                id = item.getString("id"),
                file = item.getString("file"),
                role = item.optString("role", ""),
                language = item.optString("language", "")
            )
            if (assetExists("$basePath/${asset.file}")) audioAssets[asset.id] = asset
        }
        val profiles = mutableMapOf<String, ChunkProfile>()
        json.optJSONArray("chunkProfiles").forEachObject { item ->
            val profile = ChunkProfile(
                id = item.getString("id"),
                label = item.optString("label", item.getString("id")),
                pauseBehavior = item.optString("pauseBehavior", "none")
            )
            profiles[profile.id] = profile
        }
        val vocabularySources = mutableMapOf<String, VocabularySource>()
        json.optJSONArray("vocabularySources").forEachObject { item ->
            val sourceItems = mutableListOf<VocabularySourceItem>()
            item.optJSONArray("items").forEachObject { sourceItem ->
                sourceItems.add(
                    VocabularySourceItem(
                        vocabId = sourceItem.optString("vocabId", ""),
                        sourceText = sourceItem.optString("sourceText", ""),
                        meaningText = sourceItem.optString("meaningText", ""),
                        rowIndex = sourceItem.optInt("rowIndex", 0),
                        box = sourceItem.optSourceBox("box")
                    )
                )
            }
            val source = VocabularySource(
                id = item.getString("id"),
                type = item.optString("type", ""),
                label = item.optString("label", ""),
                imageId = item.optString("imageId", ""),
                items = sourceItems
            )
            vocabularySources[source.id] = source
        }
        val vocabulary = mutableMapOf<String, Vocab>()
        json.optJSONArray("vocabulary").forEachObject { item ->
            val directExamples = (
                item.optJSONArray("examples").toStringList() +
                    listOf(item.optString("example", "")).filter { it.isNotBlank() }
                ).distinct()
            val vocab = Vocab(
                id = item.getString("id"),
                word = item.optString("word", item.getString("id")),
                lemma = item.optString("lemma", ""),
                forms = item.optJSONArray("forms").toStringList(),
                partOfSpeech = item.optString("partOfSpeech", ""),
                meaningKo = item.optString("meaningKo", ""),
                simpleKo = item.optString("simpleKo", ""),
                easyEnglish = item.optString("easyEnglish", ""),
                examples = directExamples,
                highlight = item.optBoolean("highlight", false),
                highlightStyle = item.optString("highlightStyle", ""),
                sourceRefs = item.optJSONArray("sourceRefs").toVocabSourceRefs(),
                explanationIds = item.optJSONArray("explanationIds").toStringList()
            )
            vocabulary[vocab.id] = vocab
        }
        val explanations = mutableMapOf<String, Explanation>()
        json.optJSONArray("explanations").forEachObject { item ->
            val explanation = Explanation(
                id = item.getString("id"),
                targetType = item.optString("targetType", ""),
                targetId = item.optString("targetId", ""),
                title = item.optString("title", ""),
                textKo = item.optString("textKo", ""),
                easyEnglish = item.optString("easyEnglish", ""),
                examples = item.optJSONArray("examples").toStringList()
            )
            explanations[explanation.id] = explanation
        }
        val paragraphs = mutableListOf<LessonParagraph>()
        json.getJSONArray("paragraphs").forEachObject { p ->
            val sentences = mutableListOf<LessonSentence>()
            p.getJSONArray("sentences").forEachObject { s ->
                val chunkSets = mutableMapOf<String, List<Chunk>>()
                s.optJSONArray("chunkSets").forEachObject { set ->
                    val chunks = mutableListOf<Chunk>()
                    set.optJSONArray("chunks").forEachObject { c ->
                        chunks.add(
                            Chunk(
                                id = c.getString("id"),
                                text = c.optString("text", ""),
                                startChar = c.optInt("startChar", 0),
                                endChar = c.optInt("endChar", 0),
                                startMs = c.optInt("startMs", s.optInt("startMs", 0)),
                                endMs = c.optInt("endMs", s.optInt("endMs", 0))
                            )
                        )
                    }
                    chunkSets[set.getString("id")] = chunks
                }
                val annotations = mutableListOf<Annotation>()
                s.optJSONArray("annotations").forEachObject { a ->
                    annotations.add(
                        Annotation(
                            id = a.optString("id", ""),
                            type = a.optString("type", ""),
                            wordId = a.optString("wordId", ""),
                            text = a.optString("text", ""),
                            startChar = a.optInt("startChar", 0),
                            endChar = a.optInt("endChar", 0),
                            explanationIds = a.optJSONArray("explanationIds").toStringList()
                        )
                    )
                }
                sentences.add(
                    LessonSentence(
                        id = s.getString("id"),
                        text = s.getString("text"),
                        audioId = s.optString("audioId", json.optString("defaultAudioId", "main_en")),
                        startMs = s.optInt("startMs", chunkSets.values.flatten().minOfOrNull { it.startMs } ?: 0),
                        endMs = s.optInt("endMs", chunkSets.values.flatten().maxOfOrNull { it.endMs } ?: 0),
                        chunkSets = chunkSets,
                        annotations = annotations
                    )
                )
            }
            paragraphs.add(LessonParagraph(p.getString("id"), p.optString("type", "story"), sentences))
        }
        val lesson = Lesson(
            id = json.optString("id", meta.id),
            title = json.optString("title", meta.title),
            subtitle = json.optString("subtitle", meta.subtitle.orEmpty()),
            level = json.optString("level", meta.level.orEmpty()),
            basePath = basePath,
            defaultAudioId = json.optString("defaultAudioId", audioAssets.keys.firstOrNull() ?: "main_en"),
            defaultChunkSetId = json.optString("defaultChunkSetId", "short"),
            imageAssets = imageAssets,
            audioAssets = audioAssets,
            vocabularySources = vocabularySources,
            profiles = profiles,
            paragraphs = paragraphs,
            vocabulary = vocabulary,
            explanations = explanations
        )
        applySavedAdjustments(lesson)
        return lesson
    }

    private fun Lesson.allSentences(): List<SentenceRef> {
        return paragraphs.flatMapIndexed { paragraphIndex, paragraph ->
            paragraph.sentences.map { SentenceRef(paragraphIndex, it) }
        }
    }

    private fun readAsset(path: String): String {
        return assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun assetExists(path: String): Boolean {
        return runCatching {
            assets.open(path).close()
            true
        }.getOrDefault(false)
    }

    private fun text(value: String, sizeSp: Float, textColor: Int, style: Int = Typeface.NORMAL): TextView {
        return TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(textColor)
            typeface = Typeface.create(Typeface.DEFAULT, style)
            includeFontPadding = true
        }
    }

    private fun chip(value: String): TextView {
        return text(value, 13f, color(R.color.skin_primary), Typeface.BOLD).apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(color(R.color.skin_surface_alt), dp(14), color(R.color.skin_line), dp(1))
        }
    }

    private fun pill(value: String): TextView {
        return text(value, 14f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(color(R.color.skin_surface), dp(16), color(R.color.skin_line), dp(1))
            isClickable = true
            isFocusable = true
        }
    }

    private fun iconButton(icon: Int, description: String): ImageButton {
        return ImageButton(this).apply {
            setImageResource(icon)
            contentDescription = description
            background = rounded(color(R.color.skin_surface_alt), dp(16))
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
        }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null, strokeWidth: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (stroke != null && strokeWidth > 0) setStroke(strokeWidth, stroke)
        }
    }

    private fun addMargin(view: View, left: Int = 0, right: Int = 0) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = left
        lp.rightMargin = right
        view.layoutParams = lp
    }

    private fun color(id: Int): Int = getColor(id)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun fixed(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun wrapWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun weightWrap() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    private fun matchFrame() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun LinearLayout.LayoutParams.withBottom(value: Int): LinearLayout.LayoutParams {
        bottomMargin = value
        return this
    }

    private fun LinearLayout.LayoutParams.withTop(value: Int): LinearLayout.LayoutParams {
        topMargin = value
        return this
    }

    private fun LinearLayout.LayoutParams.withRightMargin(value: Int): LinearLayout.LayoutParams {
        rightMargin = value
        return this
    }

    private fun JSONArray?.forEachObject(block: (JSONObject) -> Unit) {
        if (this == null) return
        for (i in 0 until length()) block(getJSONObject(i))
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { getString(it) }
    }

    private fun JSONArray?.toVocabSourceRefs(): List<VocabSourceRef> {
        if (this == null) return emptyList()
        return List(length()) { index ->
            val item = getJSONObject(index)
            VocabSourceRef(
                sourceId = item.optString("sourceId", ""),
                rowId = item.optString("rowId", ""),
                rowIndex = item.optInt("rowIndex", 0),
                box = item.optSourceBox("box")
            )
        }
    }

    private fun JSONObject.optSourceBox(name: String): SourceBox? {
        val box = optJSONObject(name) ?: return null
        return SourceBox(
            x = box.optDouble("x", 0.0).toFloat(),
            y = box.optDouble("y", 0.0).toFloat(),
            w = box.optDouble("w", 0.0).toFloat(),
            h = box.optDouble("h", 0.0).toFloat()
        )
    }

    private fun formatTime(ms: Int): String {
        val seconds = max(0, ms / 1000)
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun formatTimeMs(ms: Int): String {
        val safe = max(0, ms)
        val seconds = safe / 1000
        val millis = safe % 1000
        return String.format(Locale.US, "%02d:%02d.%03d", seconds / 60, seconds % 60, millis)
    }

    private fun decimalSeconds(ms: Int): String {
        return String.format(Locale.US, "%.1f", ms / 1000f)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private inner class WavyTextView(context: Context) : TextView(context) {
        private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(R.color.skin_accent)
            strokeWidth = dp(2).toFloat()
            style = Paint.Style.STROKE
        }
        private val wavePath = Path()
        private var wavyRanges: List<IntRange> = emptyList()

        fun setWavyRanges(ranges: List<IntRange>) {
            wavyRanges = ranges
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val textLayout = layout ?: return
            if (wavyRanges.isEmpty()) return
            val leftPad = totalPaddingLeft.toFloat()
            val topPad = totalPaddingTop.toFloat()
            wavyRanges.forEach { range ->
                val safeStart = range.first.coerceIn(0, text.length)
                val safeEnd = (range.last + 1).coerceIn(safeStart, text.length)
                if (safeEnd <= safeStart) return@forEach
                val startLine = textLayout.getLineForOffset(safeStart)
                val endLine = textLayout.getLineForOffset(safeEnd)
                for (line in startLine..endLine) {
                    val lineStart = textLayout.getLineStart(line)
                    val lineEnd = textLayout.getLineEnd(line)
                    val partStart = max(safeStart, lineStart)
                    val partEnd = min(safeEnd, lineEnd)
                    if (partEnd <= partStart) continue
                    val x1 = leftPad + textLayout.getPrimaryHorizontal(partStart)
                    val x2 = leftPad + textLayout.getPrimaryHorizontal(partEnd)
                    val y = topPad + textLayout.getLineBaseline(line) + dp(4)
                    drawWave(canvas, min(x1, x2), max(x1, x2), y.toFloat())
                }
            }
        }

        private fun drawWave(canvas: Canvas, startX: Float, endX: Float, y: Float) {
            if (endX - startX < dp(3)) return
            val step = dp(7).toFloat()
            val amp = dp(3).toFloat()
            wavePath.reset()
            wavePath.moveTo(startX, y)
            var x = startX
            var up = true
            while (x < endX) {
                val nextX = min(endX, x + step)
                val midX = (x + nextX) / 2f
                wavePath.quadTo(midX, y + if (up) -amp else amp, nextX, y)
                x = nextX
                up = !up
            }
            canvas.drawPath(wavePath, wavePaint)
        }
    }

    private inner class ManualChunkView(context: Context) : View(context) {
        var onChanged: ((List<ManualChunk>) -> Unit)? = null
        var onCompleted: ((List<ManualChunk>) -> Unit)? = null

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(25).toFloat()
            color = color(R.color.skin_ink)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(13).toFloat()
            color = color(R.color.skin_muted)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var sentenceText: String = ""
        private var tokens: List<WordToken> = emptyList()
        private var chunks: MutableList<ManualChunk> = mutableListOf()
        private val wordRects = mutableListOf<RectF>()
        private var draftStart = -1
        private var draftEnd = -1
        private var editingIndex: Int? = null

        fun setSentence(text: String, tokens: List<WordToken>, savedChunks: List<ManualChunk>) {
            sentenceText = text
            this.tokens = tokens
            chunks = savedChunks
                .filter { it.startWord in tokens.indices && it.endWord in tokens.indices && it.startWord <= it.endWord }
                .sortedBy { it.startWord }
                .toMutableList()
            draftStart = nextStartWord()
            draftEnd = draftStart
            editingIndex = null
            invalidate()
        }

        fun clearChunks() {
            chunks.clear()
            draftStart = nextStartWord()
            draftEnd = draftStart
            editingIndex = null
            onChanged?.invoke(currentChunks())
            invalidate()
        }

        fun currentChunks(): List<ManualChunk> = chunks.map { it.copy() }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (tokens.isEmpty()) return true
            parent?.requestDisallowInterceptTouchEvent(true)
            val word = wordAt(event.x, event.y)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val edit = word?.let { touched -> chunks.indexOfFirst { touched in it.startWord..it.endWord } } ?: -1
                    if (edit >= 0) {
                        editingIndex = edit
                        draftStart = chunks[edit].startWord
                        draftEnd = max(draftStart, word ?: draftStart)
                    } else {
                        editingIndex = null
                        draftStart = nextStartWord()
                        draftEnd = max(draftStart, word ?: draftStart)
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (draftStart >= 0) {
                        draftEnd = max(draftStart, word ?: draftEnd).coerceAtMost(tokens.lastIndex)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    commitDraft()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            layoutWords(width)
            bubblePaint.color = color(R.color.skin_surface)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(18).toFloat(), dp(18).toFloat(), bubblePaint)
            chunks.forEachIndexed { index, chunk ->
                drawBubble(canvas, chunk.startWord, chunk.endWord, if (index % 2 == 0) color(R.color.skin_mark) else color(R.color.skin_highlight), false)
            }
            if (draftStart in tokens.indices && draftEnd in tokens.indices) {
                drawBubble(canvas, draftStart, draftEnd, color(R.color.skin_accent), true)
            }
            tokens.forEachIndexed { index, token ->
                val rect = wordRects.getOrNull(index) ?: return@forEachIndexed
                canvas.drawText(token.text, rect.left + dp(10), rect.bottom - dp(10), textPaint)
            }
            drawHandle(canvas)
            smallPaint.color = color(R.color.skin_muted)
            canvas.drawText("${chunks.size} chunks", dp(16).toFloat(), height - dp(18).toFloat(), smallPaint)
        }

        private fun layoutWords(viewWidth: Int) {
            wordRects.clear()
            if (viewWidth <= 0) return
            val leftPad = dp(18).toFloat()
            val rightPad = dp(18).toFloat()
            var x = leftPad
            var y = dp(28).toFloat()
            val rowHeight = dp(50).toFloat()
            tokens.forEach { token ->
                val wordWidth = textPaint.measureText(token.text) + dp(22)
                if (x + wordWidth > viewWidth - rightPad && x > leftPad) {
                    x = leftPad
                    y += rowHeight
                }
                wordRects.add(RectF(x, y, x + wordWidth, y + dp(40)))
                x += wordWidth + dp(6)
            }
        }

        private fun drawBubble(canvas: Canvas, start: Int, end: Int, fill: Int, active: Boolean) {
            val safeStart = start.coerceIn(0, max(0, wordRects.lastIndex))
            val safeEnd = end.coerceIn(safeStart, max(safeStart, wordRects.lastIndex))
            bubblePaint.color = fill
            bubblePaint.alpha = if (active) 220 else 185
            for (index in safeStart..safeEnd) {
                val rect = RectF(wordRects[index]).apply { inset(-dp(2).toFloat(), -dp(2).toFloat()) }
                canvas.drawRoundRect(rect, dp(18).toFloat(), dp(18).toFloat(), bubblePaint)
                val next = wordRects.getOrNull(index + 1)
                if (next != null && index < safeEnd && abs(next.top - rect.top) < 4f) {
                    canvas.drawRect(rect.right - dp(12), rect.top, next.left + dp(12), rect.bottom, bubblePaint)
                }
            }
            bubblePaint.alpha = 255
        }

        private fun drawHandle(canvas: Canvas) {
            val next = nextStartWord()
            val rect = wordRects.getOrNull(next) ?: return
            bubblePaint.color = color(R.color.skin_primary)
            canvas.drawRoundRect(rect.left, rect.top - dp(18), rect.left + dp(34), rect.top + dp(10), dp(14).toFloat(), dp(14).toFloat(), bubblePaint)
            smallPaint.color = color(R.color.skin_surface)
            canvas.drawText("+", rect.left + dp(11), rect.top + dp(2), smallPaint)
        }

        private fun wordAt(x: Float, y: Float): Int? {
            return wordRects.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }
                ?: wordRects.withIndex().minByOrNull { (_, rect) ->
                    val cx = (rect.left + rect.right) / 2f
                    val cy = (rect.top + rect.bottom) / 2f
                    abs(cx - x) + abs(cy - y)
                }?.index
        }

        private fun nextStartWord(): Int {
            return (chunks.maxOfOrNull { it.endWord } ?: -1) + 1
        }

        private fun commitDraft() {
            if (draftStart !in tokens.indices || draftEnd !in tokens.indices) return
            val edit = editingIndex
            if (edit != null && edit in chunks.indices) {
                chunks[edit] = ManualChunk(chunks[edit].startWord, draftEnd)
                fixFollowingChunks(edit)
            } else if (draftStart == nextStartWord()) {
                chunks.add(ManualChunk(draftStart, draftEnd))
            }
            chunks = chunks.filter { it.startWord <= it.endWord && it.startWord in tokens.indices }.toMutableList()
            draftStart = nextStartWord()
            draftEnd = draftStart
            editingIndex = null
            onChanged?.invoke(currentChunks())
            invalidate()
            if (chunks.isNotEmpty() && nextStartWord() > tokens.lastIndex) onCompleted?.invoke(currentChunks())
        }

        private fun fixFollowingChunks(fromIndex: Int) {
            var previousEnd = chunks[fromIndex].endWord
            val fixed = chunks.take(fromIndex + 1).toMutableList()
            for (index in fromIndex + 1 until chunks.size) {
                val old = chunks[index]
                val start = previousEnd + 1
                if (start > tokens.lastIndex) break
                val end = max(start, old.endWord).coerceAtMost(tokens.lastIndex)
                fixed.add(ManualChunk(start, end))
                previousEnd = end
            }
            chunks = fixed
        }
    }

    private inner class BoundaryProfileView(context: Context, private val editsStart: Boolean) : View(context) {
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(11).toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private var profile: AudioProfile? = null
        private var label: String = ""
        private var centerMs: Int = 0
        private var candidateMs: Int? = null
        private var lowerMs: Int = 0
        private var upperMs: Int = 0
        private var displayStartMs: Int = 0
        private var displayEndMs: Int = 0

        fun setBoundary(profile: AudioProfile?, label: String, centerMs: Int, candidateMs: Int?, lowerMs: Int, upperMs: Int) {
            this.profile = profile
            this.label = label
            this.centerMs = centerMs
            this.candidateMs = candidateMs
            this.lowerMs = lowerMs
            this.upperMs = upperMs
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (profile == null || displayEndMs <= displayStartMs) return true
            parent?.requestDisallowInterceptTouchEvent(true)
            val clampedX = event.x.coerceIn(0f, width.toFloat())
            val touchedProfileMs = (displayStartMs + (clampedX / max(1f, width.toFloat()) * (displayEndMs - displayStartMs))).roundToInt()
            val touchedMs = touchedProfileMs - waveformOffsetMs
            val selection = activeAdjustSelection() ?: return true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    setBoundaryToTime(selection, editsStart, touchedMs, preview = false)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setBoundaryToTime(selection, editsStart, touchedMs, preview = true)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val profile = profile
            val width = width.toFloat()
            val height = height.toFloat()
            barPaint.color = color(R.color.skin_surface_alt)
            canvas.drawRoundRect(0f, 0f, width, height, dp(8).toFloat(), dp(8).toFloat(), barPaint)
            textPaint.color = color(R.color.skin_muted)
            canvas.drawText(label, dp(6).toFloat(), dp(14).toFloat(), textPaint)
            if (profile == null || profile.samples.isEmpty()) {
                canvas.drawText("프로파일 준비 중", dp(64).toFloat(), dp(14).toFloat(), textPaint)
                return
            }

            val centerProfileMs = centerMs + waveformOffsetMs
            val displayStart = max(0, centerProfileMs - 650)
            val displayEnd = min(profile.durationMs, centerProfileMs + 650)
            if (displayEnd <= displayStart) return
            displayStartMs = displayStart
            displayEndMs = displayEnd

            val startIndex = (displayStart / profile.windowMs).coerceIn(0, profile.samples.lastIndex)
            val endIndex = (displayEnd / profile.windowMs).coerceIn(startIndex, profile.samples.lastIndex)
            var localMax = 0.001f
            for (index in startIndex..endIndex) localMax = max(localMax, profile.samples[index])

            val top = dp(20).toFloat()
            val bottom = height - dp(8)
            val centerY = (top + bottom) / 2f
            val halfHeight = (bottom - top) / 2f
            barPaint.strokeWidth = dp(1).toFloat()
            barPaint.color = color(R.color.skin_line)
            canvas.drawLine(0f, centerY, width, centerY, barPaint)

            val allowedStart = max(lowerMs + waveformOffsetMs, displayStart)
            val allowedEnd = min(upperMs + waveformOffsetMs, displayEnd)
            if (allowedEnd > allowedStart) {
                val left = ((allowedStart - displayStart).toFloat() / (displayEnd - displayStart).toFloat()) * width
                val right = ((allowedEnd - displayStart).toFloat() / (displayEnd - displayStart).toFloat()) * width
                barPaint.color = color(R.color.skin_highlight)
                barPaint.alpha = 70
                canvas.drawRect(left, top, right, bottom, barPaint)
                barPaint.alpha = 255
            }

            barPaint.color = color(R.color.skin_primary_dark)
            barPaint.strokeWidth = dp(2).toFloat()
            for (index in startIndex..endIndex) {
                val time = index * profile.windowMs
                val x = ((time - displayStart).toFloat() / (displayEnd - displayStart).toFloat()) * width
                val normalized = (profile.samples[index] / localMax).coerceIn(0f, 1f)
                val barHeight = max(1f, normalized * halfHeight)
                canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, barPaint)
            }

            markerPaint.strokeWidth = dp(2).toFloat()
            markerPaint.color = color(R.color.skin_accent)
            drawMarker(canvas, centerMs + waveformOffsetMs, displayStart, displayEnd, height, markerPaint)
            candidateMs?.let {
                markerPaint.color = color(R.color.skin_primary_dark)
                drawMarker(canvas, it + waveformOffsetMs, displayStart, displayEnd, height, markerPaint)
            }

            textPaint.color = color(R.color.skin_ink)
            val candidateText = candidateMs?.let { "  후보 ${formatTimeMs(it)}" } ?: ""
            canvas.drawText("${formatTimeMs(centerMs)}$candidateText", dp(64).toFloat(), dp(14).toFloat(), textPaint)
        }

        private fun drawMarker(canvas: Canvas, timeMs: Int, displayStart: Int, displayEnd: Int, height: Float, paint: Paint) {
            if (timeMs !in displayStart..displayEnd) return
            val x = ((timeMs - displayStart).toFloat() / (displayEnd - displayStart).toFloat()) * width.toFloat()
            canvas.drawLine(x, dp(18).toFloat(), x, height - dp(4), paint)
        }
    }

    private data class LessonMeta(val id: String, val title: String, val subtitle: String?, val level: String?, val path: String)
    private data class ImageAsset(val id: String, val file: String, val role: String, val label: String, val page: Int)
    private data class AudioAsset(val id: String, val file: String, val role: String, val language: String)
    private data class ChunkProfile(val id: String, val label: String, val pauseBehavior: String)
    private data class SourceBox(val x: Float, val y: Float, val w: Float, val h: Float)
    private data class VocabSourceRef(val sourceId: String, val rowId: String, val rowIndex: Int, val box: SourceBox?)
    private data class VocabularySourceItem(val vocabId: String, val sourceText: String, val meaningText: String, val rowIndex: Int, val box: SourceBox?)
    private data class VocabularySource(val id: String, val type: String, val label: String, val imageId: String, val items: List<VocabularySourceItem>)
    private data class Vocab(
        val id: String,
        val word: String,
        val lemma: String,
        val forms: List<String>,
        val partOfSpeech: String,
        val meaningKo: String,
        val simpleKo: String,
        val easyEnglish: String,
        val examples: List<String>,
        val highlight: Boolean,
        val highlightStyle: String,
        val sourceRefs: List<VocabSourceRef>,
        val explanationIds: List<String>
    )
    private data class Explanation(val id: String, val targetType: String, val targetId: String, val title: String, val textKo: String, val easyEnglish: String, val examples: List<String>)
    private data class VocabStudyItem(val id: String, val word: String, val meaning: String, val note: String, val examples: List<String>)
    private data class Annotation(val id: String, val type: String, val wordId: String, val text: String, val startChar: Int, val endChar: Int, val explanationIds: List<String>)
    private data class Chunk(
        val id: String,
        val text: String,
        val startChar: Int,
        val endChar: Int,
        var startMs: Int,
        var endMs: Int,
        val originalStartMs: Int = startMs,
        val originalEndMs: Int = endMs
    )
    private data class LessonSentence(val id: String, val text: String, val audioId: String, val startMs: Int, val endMs: Int, val chunkSets: Map<String, List<Chunk>>, val annotations: List<Annotation>)
    private data class LessonParagraph(val id: String, val type: String, val sentences: List<LessonSentence>)
    private data class Lesson(
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
        val explanations: Map<String, Explanation>
    )
    private data class SentenceRef(val paragraphIndex: Int, val sentence: LessonSentence)
    private data class SentenceRange(val sentenceIndex: Int, val start: Int, val end: Int)
    private data class ParagraphBinding(val textView: TextView, val paragraphIndex: Int, val text: String, val ranges: List<SentenceRange>) {
        fun sentenceAt(offset: Int): Int? = ranges.firstOrNull { offset in it.start until it.end }?.sentenceIndex
        fun localOffset(sentenceIndex: Int, offset: Int): Int? = ranges.firstOrNull { it.sentenceIndex == sentenceIndex }?.let { offset - it.start }
    }
    private data class TtsSegment(
        val sentenceIndex: Int,
        val chunkId: String?,
        val text: String,
        val startMs: Int,
        val endMs: Int
    )
    private data class MasterSettings(
        val vocabEnabled: Boolean = true,
        val quizEnabled: Boolean = true,
        val quizMode: String = "choice",
        val manualChunkEnabled: Boolean = true,
        val firstListenPauseMs: Int = 1500,
        val firstListenRate: Float = 0.9f,
        val speakChunkPauseMs: Int = 700,
        val firstListenNextAlwaysEnabled: Boolean = false
    )
    private data class QuizItem(val word: String, val answer: String, val options: List<String>)
    private data class WordToken(val text: String, val startChar: Int, val endChar: Int)
    private data class ManualChunk(val startWord: Int, val endWord: Int)
    private data class UnknownUnderline(val startChar: Int, val endChar: Int)
    private data class UnknownDraft(val binding: ParagraphBinding, val startOffset: Int, var currentOffset: Int)
    private data class PlaybackBoundary(val endMs: Int, val nextStartMs: Int, val nextSentenceIndex: Int?)
    private data class AudioProfile(val samples: FloatArray, val windowMs: Int, val durationMs: Int)
    private data class AdjustSelection(
        val sentenceIndex: Int,
        val chunkIndex: Int,
        val sentence: LessonSentence,
        val chunk: Chunk,
        val chunks: List<Chunk>
    )
}

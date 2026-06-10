package com.seoin.englishstudy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.ToneGenerator
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
import android.text.style.ScaleXSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.util.Log
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
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.seoin.englishstudy.audio.AudioProfileDecoder
import com.seoin.englishstudy.chunk.*
import com.seoin.englishstudy.data.*
import com.seoin.englishstudy.model.Annotation
import com.seoin.englishstudy.model.*
import com.seoin.englishstudy.ui.dsl.*
import com.seoin.englishstudy.ui.views.BoundaryProfileView
import com.seoin.englishstudy.ui.views.ComicPanelView
import com.seoin.englishstudy.ui.views.DataComicPanelView
import com.seoin.englishstudy.ui.views.WavyTextView
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : Activity() {
    private enum class ReadingCoachState {
        IDLE,
        VOICE_SHELL_READY,
        CONVERSATION_READY,
        COACHING,
        CHILD_TURN,
        FEEDBACK,
        FALLBACK_TTS,
        DONE,
        RESET
    }

    private data class CoachInjectionStepResult(
        val ok: Boolean,
        val draftWritten: Boolean,
        val buttonReady: Boolean,
        val sendClicked: Boolean,
        val composerLength: Int,
        val reason: String,
    )

    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settingsStore: SettingsStore
    private lateinit var audioProfileDecoder: AudioProfileDecoder
    private val comicBgNames = setOf("snow", "sky", "forest", "desert", "lava", "ocean", "night", "room", "sunset", "plain")
    private val comicAnimNames = setOf("none", "bounce", "shiver", "float", "dash", "roll", "jump", "sway", "spin")

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
    private var manualBodyDraftSentenceIndex: Int? = null
    private var manualBodyDraftStartWord = -1
    private var manualBodyDraftEndWord = -1
    private var manualBodyEditingChunkIndex: Int? = null
    private var unknownUnderlineMode = false
    private var unknownDraft: UnknownDraft? = null
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
    private var seekBar: SeekBar? = null
    private var timeLabel: TextView? = null
    private var modeLabel: TextView? = null
    private var modeButtons: MutableMap<String, TextView> = mutableMapOf()
    private var readerScroll: ScrollView? = null
    private var readerActionHost: LinearLayout? = null
    private var adjustPanel: LinearLayout? = null
    private var adjustTitle: TextView? = null
    private var adjustText: TextView? = null
    private var adjustRange: TextView? = null
    private var waveformOffsetLabel: TextView? = null
    private var startProfileView: BoundaryProfileView? = null
    private var endProfileView: BoundaryProfileView? = null
    private var chatOverlay: FrameLayout? = null
    private var chatDialogBox: LinearLayout? = null
    private var chatWebView: WebView? = null
    private var chatStatusLabel: TextView? = null
    private var chatLoginSetupMode = false
    private var pendingChatPermissionRequest: PermissionRequest? = null
    private var pendingPrimeMicAfterPermission = false
    private var pendingChatPrompt: String? = null
    private var pendingChatAutoSend = false
    private var pendingChatVoiceBeforePrompt = false
    private var pendingChatVoiceBeforePromptStarted = false
    private var pendingChatCompactAfterSend = false
    private val blockReadingCoachTtsFallbackForDebug = true
    private var readingCoachActive = false
    private var readingCoachState = ReadingCoachState.IDLE
    private var readingCoachFallbackTts = false
    private var readingCoachPreviousActiveMode = "natural"
    private var readingCoachPreviousUseTts = true
    private var readingCoachPreferredChunkSetId = "short"
    private var readingCoachChunkSetId = "short"
    private var readingCoachSentenceIndex = 0
    private var readingCoachChunkIndex = 0
    private var readingCoachFlowToken = 0L
    private var readingCoachChunkToken = 0L
    private var readingCoachPrimed = false
    private var readingCoachVoiceShellPreparing = false
    private var readingCoachVoiceRequestInFlight = false
    private var readingCoachFirstVoiceChunkStarted = false
    private var readingCoachStatusLabel: TextView? = null
    private var readingCoachStateButton: TextView? = null
    private val questionPromptPrimedLessons = mutableSetOf<String>()
    private val answeredComprehensionChecks = mutableSetOf<String>()
    private var activeComprehensionPass = 0
    private var activeComprehensionCheck: ComprehensionCheck? = null
    private var activeComprehensionOptions: List<ComprehensionOption> = emptyList()
    private var comprehensionOverlay: View? = null
    private var comprehensionFeedbackLabel: TextView? = null
    private val ttsUtterances = ConcurrentHashMap<String, TtsSegment>()
    private var ttsDoneCallback: (() -> Unit)? = null
    private var flowAutoToken = 0L
    private val chatMicPermissionRequestCode = 14510

    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 25L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(this)
        audioProfileDecoder = AudioProfileDecoder(assets)
        root = FrameLayout(this)
        setContentView(root)
        lessonMetas = loadManifest()
        showLessonList()
        handler.postDelayed({ maybeShowChatGptLoginSetup() }, 450L)
    }

    override fun onDestroy() {
        destroyChatWebView()
        releasePlayer()
        releaseTts()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != chatMicPermissionRequestCode) return
        val request = pendingChatPermissionRequest
        pendingChatPermissionRequest = null
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val audioResources = request?.resources
                ?.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                ?.toTypedArray()
            if (audioResources != null && audioResources.isNotEmpty()) request.grant(audioResources)
            if (pendingPrimeMicAfterPermission) {
                pendingPrimeMicAfterPermission = false
                primeChatMicrophone()
            } else {
                chatStatusLabel?.text = "마이크 권한이 허용됐어요. 음성 버튼을 다시 눌러보세요."
            }
        } else {
            request?.deny()
            pendingPrimeMicAfterPermission = false
            toast("마이크 권한이 필요해요.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentLesson != null) showLessonList() else super.onBackPressed()
    }

    private fun showLessonList() {
        releasePlayer()
        clearComprehensionLayer()
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

    private fun showAfterComicQuizStage(lesson: Lesson) {
        lesson.vocabReflexGame?.takeIf { it.allCards().isNotEmpty() }?.let { game ->
            showVocabReflexStage(lesson, game)
            return
        }
        showFirstListenStage(lesson)
    }

    private fun showAfterBodyStage(lesson: Lesson) {
        showAfterFirstListenStage(lesson)
    }

    private fun showAfterFirstListenStage(lesson: Lesson) {
        if (masterSettings.manualChunkEnabled && lesson.allSentences().isNotEmpty()) {
            startManualChunkActivity(lesson)
        } else {
            showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
        }
    }

    private fun showFirstListenStage(lesson: Lesson) {
        showReader(lesson)
    }

    private fun showSpeakListenStage(lesson: Lesson, pass: Int, sentenceIndex: Int) {
        releasePlayer()
        clearComprehensionLayer()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)
        answeredComprehensionChecks.removeAll { it.startsWith("${lesson.id}|$pass|") }
        manualBodyMode = false
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
        readerActionHost = null

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
                if (activeComprehensionCheck != null) return@setOnTouchListener false
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
        if (activeComprehensionCheck != null) {
            comprehensionFeedbackLabel?.text = "질문에 답한 뒤 다음으로 넘어갈게요."
            return
        }
        if (pass == 2 && showNextComprehensionCheckIfNeeded(lesson, pass, selectedSentenceIndex)) return
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
            speechRate = masterSettings.firstListenRate,
            onDone = {
                if (pass == 2 && activeComprehensionCheck == null) {
                    showNextComprehensionCheckIfNeeded(currentLesson ?: return@speakTtsSegments, pass, sentenceIndex)
                }
            }
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

    private fun showNextComprehensionCheckIfNeeded(lesson: Lesson, pass: Int, sentenceIndex: Int): Boolean {
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return false
        val check = lesson.comprehensionChecks
            .filter { it.afterSentenceId == sentence.id }
            .firstOrNull { comprehensionCheckKey(lesson, pass, it) !in answeredComprehensionChecks }
            ?: return false
        showComprehensionCheckLayer(lesson, pass, check)
        return true
    }

    private fun comprehensionCheckKey(lesson: Lesson, pass: Int, check: ComprehensionCheck): String {
        return "${lesson.id}|$pass|${check.id}"
    }

    private fun showComprehensionCheckLayer(lesson: Lesson, pass: Int, check: ComprehensionCheck) {
        val options = comprehensionCheckOptions(lesson, check)
        if (options.isEmpty()) {
            answeredComprehensionChecks.add(comprehensionCheckKey(lesson, pass, check))
            handler.post { advanceSpeakListenSentence(lesson, pass) }
            return
        }

        activeComprehensionPass = pass
        activeComprehensionCheck = check
        activeComprehensionOptions = options
        comprehensionFeedbackLabel = null
        refreshAllParagraphs()
        scrollToSentence(flatSentences.indexOfFirst { it.sentence.id == check.sentenceId }.coerceAtLeast(0))

        val layer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_primary), dp(1))
            elevation = dp(10).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("질문 확인", 13f, color(R.color.skin_primary), Typeface.BOLD))
            addView(text(check.question, 23f, color(R.color.skin_ink), Typeface.BOLD).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(pill("GPT 보이스").apply {
            textSize = 13f
            setOnClickListener { openComprehensionHelp(lesson, check, options) }
        }, fixed(dp(112), dp(42)).withRightMargin(dp(8)))
        header.addView(pill("넘기기").apply {
            textSize = 13f
            setOnClickListener {
                answeredComprehensionChecks.add(comprehensionCheckKey(lesson, pass, check))
                clearComprehensionLayer()
                advanceSpeakListenSentence(lesson, pass)
            }
        }, fixed(dp(76), dp(42)))
        layer.addView(header, matchWrap())

        layer.addView(text("본문에서 알맞은 chunk를 눌러 골라요.", 14f, color(R.color.skin_muted)).apply {
            setPadding(0, dp(8), 0, 0)
        }, matchWrap())

        comprehensionFeedbackLabel = text("", 13f, color(R.color.skin_muted), Typeface.BOLD).apply {
            setPadding(0, dp(6), 0, 0)
        }
        layer.addView(comprehensionFeedbackLabel, matchWrap())

        comprehensionOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        comprehensionOverlay = layer
        root.addView(
            layer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
                leftMargin = dp(14)
                rightMargin = dp(14)
                topMargin = dp(14)
            }
        )
        speakPopupText(check.question)
    }

    private fun clearComprehensionLayer() {
        comprehensionOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        comprehensionOverlay = null
        comprehensionFeedbackLabel = null
        activeComprehensionPass = 0
        activeComprehensionCheck = null
        activeComprehensionOptions = emptyList()
        refreshAllParagraphs()
    }

    private fun comprehensionCheckOptions(lesson: Lesson, check: ComprehensionCheck): List<ComprehensionOption> {
        val chunkSetId = check.chunkSetId.ifBlank { lesson.defaultChunkSetId.ifBlank { "short" } }
        val sentenceIds = check.scopeSentenceIds.ifEmpty { listOf(check.sentenceId) }.toSet()
        val options = flatSentences
            .filter { it.sentence.id in sentenceIds }
            .flatMap { ref ->
                ref.sentence.chunkSets[chunkSetId].orEmpty().map { chunk ->
                    ComprehensionOption(ref.sentence.id, chunk.id, chunk.text)
                }
            }
            .filter { it.text.isNotBlank() }
        if (options.any { isCorrectComprehensionOption(check, it) }) return options
        return if (check.answerText.isNotBlank()) options + ComprehensionOption(check.sentenceId, "", check.answerText) else options
    }

    private fun isCorrectComprehensionOption(check: ComprehensionCheck, option: ComprehensionOption): Boolean {
        if (check.answerChunkId.isNotBlank() && option.chunkId == check.answerChunkId) return true
        return normalizeAnswerText(option.text) == normalizeAnswerText(check.answerText)
    }

    private fun handleComprehensionChunkTap(binding: ParagraphBinding, sentenceIndex: Int, paragraphOffset: Int) {
        val lesson = currentLesson ?: return
        val check = activeComprehensionCheck ?: return
        val localOffset = binding.localOffset(sentenceIndex, paragraphOffset) ?: return
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return
        val chunkSetId = check.chunkSetId.ifBlank { lesson.defaultChunkSetId.ifBlank { "short" } }
        val selection = comprehensionSelectionAt(sentence, chunkSetId, localOffset) ?: run {
            comprehensionFeedbackLabel?.text = "chunk 위를 눌러 골라주세요."
            return
        }
        val (chunk, option) = selection

        selectedSentenceIndex = sentenceIndex
        currentSentenceIndex = sentenceIndex
        currentChunkId = chunk.id
        refreshAllParagraphs()

        if (isCorrectComprehensionOption(check, option)) {
            answeredComprehensionChecks.add(comprehensionCheckKey(lesson, activeComprehensionPass, check))
            val feedback = correctComprehensionFeedback(check, option)
            comprehensionFeedbackLabel?.text = feedback
            speakPopupText(feedback, onDone = {
                val pass = activeComprehensionPass
                clearComprehensionLayer()
                advanceSpeakListenSentence(lesson, pass)
            })
        } else {
            comprehensionFeedbackLabel?.text = "아직 아니에요. 내용이 들어 있는 chunk를 다시 골라봐요."
        }
    }

    private fun correctComprehensionFeedback(check: ComprehensionCheck, option: ComprehensionOption): String {
        val custom = check.correctFeedback
            .replace("{answer}", option.text, ignoreCase = true)
            .ifBlank { check.answerText }
        return when {
            custom.isNotBlank() && custom.startsWith("correct", ignoreCase = true) -> custom
            custom.isNotBlank() -> "Correct! $custom"
            option.text.isNotBlank() -> "Correct! ${option.text}"
            else -> "Correct!"
        }
    }

    private fun normalizeAnswerText(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun openComprehensionHelp(lesson: Lesson, check: ComprehensionCheck, options: List<ComprehensionOption>) {
        val includeFullContext = questionPromptPrimedLessons.add(lesson.id)
        val prompt = buildComprehensionHelpPrompt(lesson, check, options, includeFullContext)
        openChatGptWithPrompt(prompt, autoSend = true, voiceBeforePrompt = true, compactAfterSend = true)
    }

    private fun buildComprehensionHelpPrompt(
        lesson: Lesson,
        check: ComprehensionCheck,
        options: List<ComprehensionOption>,
        includeFullContext: Boolean
    ): String {
        val sourceSentence = flatSentences.firstOrNull { it.sentence.id == check.sentenceId }?.sentence?.text.orEmpty()
        val optionText = options.mapIndexed { index, option -> "${index + 1}. ${option.text}" }.joinToString("\n")
        return buildString {
            if (includeFullContext) {
                appendLine("You are a friendly English reading coach for a young learner.")
                appendLine("Instructions:")
                appendLine("- Speak in simple English only.")
                appendLine("- Do not say the exact answer chunk first.")
                appendLine("- Give a short hint so the student can choose the right chunk from the passage.")
                appendLine("- Ask the student to answer out loud.")
                appendLine("- Keep each turn short and warm.")
                appendLine()
                appendLine("Reading passage:")
                appendLine(lessonStoryText(lesson))
                appendLine()
            }
            appendLine("Current question:")
            appendLine(check.question)
            if (sourceSentence.isNotBlank()) appendLine("Sentence: $sourceSentence")
            appendLine("Chunks the student can choose from:")
            appendLine(optionText)
            check.promptNote.takeIf { it.isNotBlank() }?.let { appendLine("Teacher note: $it") }
            appendLine()
            append("Start a voice conversation now. Help the student choose the right chunk, but do not reveal it immediately.")
        }
    }

    private fun lessonStoryText(lesson: Lesson): String {
        return lesson.paragraphs
            .filter { it.type != "title" }
            .flatMap { it.sentences }
            .joinToString(" ") { it.text }
    }

    private fun showVocabStage(lesson: Lesson, index: Int) {
        showVocabComicStudyStage(lesson, index.coerceAtLeast(0))
    }

    private fun showVocabComicStudyStage(lesson: Lesson, index: Int) {
        stopAllPlayback()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)

        val items = vocabStudyItems(lesson)
        val lessonComic = lesson.cinematicComic.takeIf { index == 0 }
        if (items.isEmpty() && lessonComic == null) {
            showAfterVocabStage(lesson)
            return
        }
        if (lessonComic == null && index >= items.size) {
            showAfterVocabStage(lesson)
            return
        }

        val isLessonComic = lessonComic != null
        val item = if (isLessonComic) {
            VocabStudyItem(
                id = "__lesson_comic",
                word = lesson.title,
                meaning = lessonComic?.meaning?.ifBlank { lesson.subtitle } ?: lesson.subtitle,
                longMeaning = "",
                note = "",
                examples = emptyList(),
                comic = lessonComic
            )
        } else {
            items[index]
        }
        val comic = lessonComic ?: item.comic
        val panels = comic?.panels.orEmpty()
        var active = true
        var panelIndex = 0
        val backgroundSheet = comic?.let { loadComicBackgroundSheet(lesson, it) }

        root.removeAllViews()
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFDF6EC.toInt(), 0xFFFCE8D8.toInt())
        )

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(16), dp(18), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()

        page.addView(flowHeader(lesson, "Word comic", if (isLessonComic) "story" else "${index + 1}/${items.size}"), matchWrap().withBottom(dp(10)))
        page.addView(text(item.word, if (isLessonComic) 28f else 36f, 0xFFEA6A22.toInt(), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
        }, matchWrap())
        page.addView(text(comic?.meaning?.ifBlank { item.meaning } ?: item.meaning, 17f, color(R.color.skin_muted), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, matchWrap().withBottom(dp(12)))
        val studyWords = comic?.words.orEmpty().ifEmpty { listOf(item.word) }
        if (studyWords.isNotEmpty()) {
            page.addView(vocabWordChips(lesson, studyWords, comic), matchWrap().withBottom(dp(10)))
        }

        val panelView = DataComicPanelView(this).apply {
            setShowCaption(false)
            setShowPanelNumber(false)
            setBackgroundSheet(backgroundSheet, comic?.backgroundColumns ?: 2, comic?.backgroundRows ?: 2)
        }
        val panelCaption = text("", 18f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(color(R.color.skin_surface), dp(18), 0xFFFFE0BE.toInt(), dp(1))
            setOnTouchListener { view, event ->
                if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true
                val textView = view as? TextView ?: return@setOnTouchListener true
                val offset = offsetFor(textView, event) ?: return@setOnTouchListener true
                speakTodayWordAt(textView.text.toString(), offset, lesson, comic)
                true
            }
        }
        if (panels.isNotEmpty()) {
            val panelSize = min(
                resources.displayMetrics.widthPixels - dp(36),
                max(dp(260), resources.displayMetrics.heightPixels - dp(360))
            )
            page.addView(panelView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelSize).withBottom(dp(10)))
            page.addView(panelCaption, matchWrap().withBottom(dp(10)))
        } else {
            page.addView(text(comicExplanationText(item, comic), 21f, color(R.color.skin_ink), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(28), dp(18), dp(28))
                background = rounded(color(R.color.skin_surface), dp(22), color(R.color.skin_line), dp(1))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(12)))
        }

        val explanation = text(comicExplanationText(item, comic), 15f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(color(R.color.skin_surface), dp(18), 0xFFFFE0BE.toInt(), dp(1))
        }
        if (panels.isEmpty()) page.addView(explanation, matchWrap().withBottom(dp(10)))

        fun speakPanel(targetPanelIndex: Int) {
            if (!active) return
            val caption = panels.getOrNull(targetPanelIndex)?.caption?.ifBlank { item.meaning } ?: item.meaning
            speakComicText(caption)
        }

        lateinit var next: TextView
        fun showPanel(targetPanelIndex: Int) {
            if (!active) return
            val panel = panels.getOrNull(targetPanelIndex) ?: return
            panelView.setPanel(panel, targetPanelIndex, item.word)
            val caption = panel.caption.ifBlank { item.meaning }
            panelCaption.text = styledTodayWords(caption, lesson, comic)
            next.text = if (targetPanelIndex < panels.lastIndex) {
                "Next"
            } else if (!isLessonComic && index < items.lastIndex) {
                "Next word"
            } else if (isLessonComic) {
                "Word"
            } else {
                "Start game"
            }
            speakPanel(targetPanelIndex)
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val replay = pill("Replay").apply {
            textSize = 16f
            setOnClickListener {
                if (panels.isNotEmpty()) {
                    speakPanel(panelIndex)
                } else {
                    active = false
                    showVocabComicStudyStage(lesson, index)
                }
            }
        }
        val nextButton = pill("Next").apply {
            textSize = 16f
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                stopTts()
                if (panels.isNotEmpty() && panelIndex < panels.lastIndex) {
                    panelIndex += 1
                    showPanel(panelIndex)
                } else {
                    active = false
                    if (isLessonComic) {
                        showVocabSpotlightStage(lesson)
                    } else if (index < items.lastIndex) {
                        showVocabComicStudyStage(lesson, index + 1)
                    } else {
                        showAfterVocabStage(lesson)
                    }
                }
            }
        }
        next = nextButton
        actions.addView(replay, LinearLayout.LayoutParams(0, dp(48), 1f).withRightMargin(dp(10)))
        actions.addView(nextButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        page.addView(actions, matchWrap())

        if (panels.isNotEmpty()) handler.postDelayed({ showPanel(0) }, 250L) else speakComicText(comicExplanationText(item, comic))
    }

    private fun showVocabSpotlightStage(lesson: Lesson) {
        stopAllPlayback()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)

        val vocab = lesson.vocabulary.values.firstOrNull { it.quizPanel != null } ?: run {
            showAfterVocabStage(lesson)
            return
        }
        val detail = vocabSpotlightDetail(vocab)
        var active = true

        root.removeAllViews()
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFDF6EC.toInt(), 0xFFFCE8D8.toInt())
        )

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(16), dp(18), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()

        page.addView(flowHeader(lesson, "Word picture", "1 word"), matchWrap().withBottom(dp(10)))
        page.addView(text(vocab.word, 42f, 0xFFEA6A22.toInt(), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, matchWrap().withBottom(dp(10)))

        val panel = DataComicPanelView(this).apply {
            setShowCaption(false)
            setShowPanelNumber(false)
            setPanel(vocab.quizPanel!!, 0, vocab.word)
        }
        val panelSize = min(
            resources.displayMetrics.widthPixels - dp(36),
            max(dp(280), resources.displayMetrics.heightPixels - dp(360))
        )
        page.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelSize).withBottom(dp(12)))

        page.addView(text(vocab.easyEnglish.ifBlank { vocab.meaningKo }.ifBlank { "Look at the picture and learn the word." }, 20f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(color(R.color.skin_surface), dp(18), 0xFFFFE0BE.toInt(), dp(1))
        }, matchWrap().withBottom(dp(12)))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lateinit var replay: TextView
        lateinit var next: TextView

        fun setReady(ready: Boolean) {
            replay.isEnabled = ready
            replay.alpha = if (ready) 1f else 0.42f
            next.isEnabled = ready
            next.alpha = if (ready) 1f else 0.42f
        }

        fun playDetail() {
            setReady(false)
            speakComicText(detail, onDone = {
                if (!active) return@speakComicText
                setReady(true)
            })
        }

        replay = pill("Replay").apply {
            textSize = 16f
            setOnClickListener { if (isEnabled) playDetail() }
        }
        next = pill("Next").apply {
            textSize = 16f
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                active = false
                stopTts()
                showAfterVocabStage(lesson)
            }
        }
        actions.addView(replay, LinearLayout.LayoutParams(0, dp(48), 1f).withRightMargin(dp(10)))
        actions.addView(next, LinearLayout.LayoutParams(0, dp(48), 1f))
        page.addView(actions, matchWrap())

        handler.postDelayed({ playDetail() }, 250L)
    }

    private fun vocabSpotlightDetail(vocab: Vocab): String {
        val short = vocab.easyEnglish.ifBlank { "to put something up high" }
        return "Look at the picture with me. The word is ${vocab.word}. It means $short. In the picture, imagine Kibu looking at something that is up high, not sitting on the floor. When we hang something, we put it on a hook, a wall, a line, or another high place. We might hang a coat, hang a bag, or hang a sign so people can see it. So when you hear ${vocab.word}, think about putting something up and letting it stay there. Try saying it with me: ${vocab.word}."
    }

    private fun vocabWordChips(lesson: Lesson, words: List<String>, comic: VocabComic?): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(2), 0)
        }
        words.distinctBy { normalizeKittyKey(it) }.forEach { token ->
            val vocab = findVocabByToken(lesson, token)
            val label = vocab?.word ?: token
            row.addView(chip(label).apply {
                textSize = 14f
                setOnClickListener { speakVocabToken(lesson, token, comic) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).withRightMargin(dp(8)))
        }
        scroll.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    private fun speakVocabToken(lesson: Lesson, token: String, comic: VocabComic?) {
        val info = todayWordInfo(lesson, token, comic)
        val vocab = findVocabByToken(lesson, token)
        val word = info?.first ?: vocab?.word ?: token
        val meaning = info?.second
            ?: vocab?.comic?.meaning
            ?: vocab?.easyEnglish
            ?: vocab?.meaningKo
            ?: vocab?.simpleKo
            ?: "Tap this word again while studying."
        speakPopupText("$word. $meaning")
    }

    private fun findVocabByToken(lesson: Lesson, token: String): Vocab? {
        val key = normalizeKittyKey(token)
        return lesson.vocabulary.values.firstOrNull {
            it.id == token || normalizeKittyKey(it.word) == key || normalizeKittyKey(it.lemma) == key || it.forms.any { form -> normalizeKittyKey(form) == key }
        }
    }

    private fun speakTodayWordAt(textValue: String, offset: Int, lesson: Lesson, comic: VocabComic?): Boolean {
        val token = wordTokens(textValue).firstOrNull { offset in it.startChar until it.endChar } ?: return false
        val info = todayWordInfo(lesson, token.text, comic) ?: return false
        player?.pause()
        stopTts()
        updatePlayIcon()
        speakPopupText("${info.first}. ${info.second}")
        return true
    }

    private fun todayWordInfo(lesson: Lesson, token: String, preferredComic: VocabComic?): Pair<String, String>? {
        val key = normalizeKittyKey(token)
        if (key.isBlank()) return null
        val sources = listOfNotNull(preferredComic, lesson.cinematicComic).distinct()
        sources.forEach { comic ->
            val matchedWord = comic.words.firstOrNull { normalizeKittyKey(it) == key }
            val meaning = comic.wordExplanations[key]
                ?: comic.wordExplanations[token.lowercase(Locale.US)]
            if (matchedWord != null || meaning != null) {
                val vocab = findVocabByToken(lesson, matchedWord ?: token)
                val resolvedWord = matchedWord ?: vocab?.word ?: token.trim { !it.isLetterOrDigit() }
                val resolvedMeaning = meaning
                    ?: vocab?.comic?.meaning
                    ?: vocab?.easyEnglish
                    ?: vocab?.meaningKo
                    ?: vocab?.simpleKo
                    ?: return null
                return resolvedWord to resolvedMeaning
            }
        }
        return null
    }

    private fun styledTodayWords(value: String, lesson: Lesson, comic: VocabComic?): SpannableString {
        val span = SpannableString(value)
        applyTodayWordSpans(span, value, 0, value.length, lesson, comic)
        return span
    }

    private fun applyTodayWordSpans(
        span: SpannableString,
        textValue: String,
        baseStart: Int,
        baseEnd: Int,
        lesson: Lesson,
        comic: VocabComic?
    ) {
        wordTokens(textValue).forEach { token ->
            if (todayWordInfo(lesson, token.text, comic) == null) return@forEach
            val start = baseStart + token.startChar
            val end = min(baseStart + token.endChar, baseEnd)
            if (start in 0 until end && end <= span.length) {
                span.setSpan(ForegroundColorSpan(color(R.color.skin_primary_dark)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(BackgroundColorSpan(colorWithAlpha(color(R.color.skin_mark), 120)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun loadComicBackgroundSheet(lesson: Lesson, comic: VocabComic): Bitmap? {
        val assetId = comic.backgroundImageAssetId.ifBlank { return null }
        val asset = lesson.imageAssets[assetId] ?: return null
        return loadBitmapAsset("${lesson.basePath}/${asset.file}")
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
            if (item.longMeaning.isNotBlank()) {
                meaningBox.addView(text(item.longMeaning, 13f, color(R.color.skin_ink)).apply {
                    setPadding(0, dp(4), 0, 0)
                })
            }
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
            page.addView(pill(
                when {
                    masterSettings.vocabGameMode2Enabled -> "Find the Kitty"
                    testTargets == null -> "테스트 보기"
                    else -> "틀린 단어 다시 테스트 (${testTargets.size})"
                }
            ).apply {
                textSize = 16f
                background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
                setOnClickListener {
                    if (masterSettings.vocabGameMode2Enabled) {
                        showFindKittyStage(lesson)
                        return@setOnClickListener
                    }
                    val reflexGame = lesson.vocabReflexGame?.takeIf { it.allCards().isNotEmpty() }
                    if (reflexGame != null) {
                        showVocabReflexStage(lesson, reflexGame, testTargets)
                    } else {
                        showVocabTestStage(lesson, testTargets)
                    }
                }
            }, matchWrap())
        } else {
            handler.post {
                focusedRow?.let { row ->
                    scroller.smoothScrollTo(0, max(0, row.top - dp(12)))
                }
            }
            val button = nextButton
            playVocabStudyIntro(lesson, items[safeIndex]) {
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
            val explanationEnglishLong = explanations.mapNotNull { it.easyEnglishLong.takeIf { value -> value.isNotBlank() } }.firstOrNull().orEmpty()
            val meaning = vocab.easyEnglish.ifBlank { explanationEnglish }.ifBlank { vocab.meaningKo }.ifBlank { "No definition yet." }
            val longMeaning = vocab.easyEnglishLong.ifBlank { explanationEnglishLong }
            val note = vocab.meaningKo
                .takeIf { it.isNotBlank() && it != meaning }
                ?: vocab.simpleKo.takeIf { it.isNotBlank() }
                ?: explanations.mapNotNull { it.textKo.takeIf { value -> value.isNotBlank() && value != meaning } }.firstOrNull().orEmpty()
            VocabStudyItem(
                id = vocab.id,
                word = vocab.word,
                meaning = meaning,
                longMeaning = longMeaning,
                note = note,
                examples = (vocab.examples + explanations.flatMap { it.examples }).distinct(),
                comic = vocab.comic
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
            if (item.longMeaning.isNotBlank()) {
                append(". ")
                append(item.longMeaning)
            }
            if (!example.isNullOrBlank()) {
                append(". Example. ")
                append(example)
            }
        }
        speakPopupText(spoken, onDone)
    }

    private fun comicExplanationText(item: VocabStudyItem, comic: VocabComic?): String {
        val meaning = comic?.meaning?.ifBlank { item.meaning } ?: item.meaning
        return buildString {
            append(item.word)
            append(". ")
            append(meaning)
            if (item.longMeaning.isNotBlank()) {
                append(". ")
                append(item.longMeaning)
            }
            item.examples.firstOrNull()?.takeIf { it.isNotBlank() }?.let { example ->
                append(". Example. ")
                append(example)
            }
        }
    }

    private fun playVocabStudyIntro(lesson: Lesson, item: VocabStudyItem, onDone: (() -> Unit)? = null) {
        if (showVocabComicSequence(lesson, item) {
                if (item.comic?.readDefinitionAfter == true) {
                    speakVocabStudyItem(item, onDone)
                } else {
                    onDone?.invoke()
                }
            }
        ) return
        speakVocabStudyItem(item, onDone)
    }

    private fun showDataComicSequence(lesson: Lesson, item: VocabStudyItem, comic: VocabComic, onDone: () -> Unit): Boolean {
        val panels = comic.panels
        if (panels.isEmpty()) return false
        val backgroundSheet = loadComicBackgroundSheet(lesson, comic)
        var active = true
        var step = 0

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
            isFocusable = true
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            background = rounded(color(R.color.skin_surface), dp(20), color(R.color.skin_primary), dp(1))
            elevation = dp(14).toFloat()
        }
        val title = text(item.word, 28f, color(R.color.skin_primary_dark), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }
        val meaning = text(comic.meaning.ifBlank { item.meaning }, 15f, color(R.color.skin_muted), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val panelViews = panels.mapIndexed { index, panel ->
            DataComicPanelView(this).apply {
                setPanel(panel, index, item.word)
                setBackgroundSheet(backgroundSheet, comic.backgroundColumns, comic.backgroundRows)
            }
        }
        panelViews.chunked(2).forEach { rowPanels ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowPanels.forEachIndexed { indexInRow, view ->
                row.addView(view, LinearLayout.LayoutParams(0, dp(250), 1f).withRightMargin(if (indexInRow == 0) dp(10) else 0))
            }
            grid.addView(row, matchWrap().withBottom(dp(10)))
        }
        val skip = pill("Skip").apply {
            textSize = 13f
        }

        fun finish() {
            if (!active) return
            active = false
            stopTts()
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            onDone()
        }

        fun showStep(index: Int) {
            if (!active) return
            if (index >= panels.size) {
                handler.postDelayed({ finish() }, 650L)
                return
            }
            step = index
            panelViews.forEachIndexed { panelIndex, view ->
                view.setCurrent(panelIndex == step)
            }
            val caption = panels[index].caption.ifBlank { item.meaning }
            speakComicText(caption, onDone = {
                handler.postDelayed({ showStep(index + 1) }, 700L)
            })
        }

        skip.setOnClickListener { finish() }
        overlay.setOnClickListener { }
        card.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        card.addView(meaning, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)).withBottom(dp(8)))
        card.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(4)))
        card.addView(skip, fixed(dp(104), dp(40)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        overlay.addView(
            card,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, min(dp(660), resources.displayMetrics.heightPixels - dp(100)), Gravity.CENTER).apply {
                leftMargin = dp(14)
                rightMargin = dp(14)
            }
        )
        root.addView(overlay, matchFrame())
        showStep(0)
        return true
    }

    private fun showVocabComicSequence(lesson: Lesson, item: VocabStudyItem, onDone: () -> Unit): Boolean {
        val comic = item.comic ?: return false
        if (comic.panels.isNotEmpty()) {
            return showDataComicSequence(lesson, item, comic, onDone)
        }
        val asset = lesson.imageAssets[comic.imageAssetId] ?: return false
        val bitmap = loadBitmapAsset("${lesson.basePath}/${asset.file}") ?: return false
        val panelCount = comic.panelCount.coerceAtLeast(1)
        val narrations = comic.narrations.ifEmpty { List(panelCount) { item.meaning } }
        val focusSteps = comic.focusSteps
        var index = 0
        var active = true
        var waitToken = 0
        var canTapAdvance = false
        var pendingAdvance: (() -> Unit)? = null

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
            isFocusable = true
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = rounded(color(R.color.skin_surface), dp(20), color(R.color.skin_primary), dp(1))
            elevation = dp(14).toFloat()
        }
        val panelView = ComicPanelView(this).apply {
            setComic(bitmap, panelCount, comic.layout)
        }
        val caption = text("", 18f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }
        val progress = text("", 13f, color(R.color.skin_muted), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }
        val feedback = text("", 15f, color(R.color.skin_primary_dark), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }
        val choiceBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(28), 0, dp(28), 0)
        }
        val panelContainer = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
        }
        val choiceScrim = View(this).apply {
            visibility = View.GONE
            background = rounded(colorWithAlpha(color(R.color.skin_surface), 172), dp(18))
        }
        val skip = pill("건너뛰기").apply {
            textSize = 13f
        }

        fun finish() {
            if (!active) return
            active = false
            stopTts()
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            bitmap.recycle()
            onDone()
        }

        var showFocusStepRef: ((Int) -> Unit)? = null

        fun tryTapAdvance() {
            if (!active || !canTapAdvance) return
            val action = pendingAdvance ?: return
            waitToken++
            canTapAdvance = false
            pendingAdvance = null
            feedback.text = ""
            action()
        }

        fun scheduleAdvance(action: () -> Unit) {
            waitToken++
            val token = waitToken
            canTapAdvance = false
            pendingAdvance = action
            handler.postDelayed({
                if (active && token == waitToken) {
                    canTapAdvance = false
                    pendingAdvance = null
                    feedback.text = ""
                    action()
                }
            }, 700L)
        }

        fun setChoices(step: VocabComicFocusStep) {
            choiceBox.removeAllViews()
            feedback.text = ""
            if (step.choices.isEmpty()) {
                choiceBox.visibility = View.GONE
                choiceScrim.visibility = View.GONE
                return
            }
            choiceBox.visibility = View.VISIBLE
            choiceScrim.visibility = View.VISIBLE
            step.choices.forEachIndexed { optionIndex, option ->
                val button = pill(option.text).apply {
                    textSize = 16f
                    background = rounded(colorWithAlpha(color(R.color.skin_surface), 232), dp(18), colorWithAlpha(color(R.color.skin_primary_dark), 95), dp(1))
                    elevation = dp(8).toFloat()
                    setOnClickListener {
                        if (option.correct) {
                            waitToken++
                            val choiceToken = waitToken
                            for (i in 0 until choiceBox.childCount) choiceBox.getChildAt(i).isEnabled = false
                            val message = option.feedback.ifBlank { "That is a good idea!" }
                            feedback.text = message
                            panelView.setChoiceMark(true)
                            val voiceDelay = playComicChoiceTone(correct = true)
                            handler.postDelayed({
                                if (!active || choiceToken != waitToken) return@postDelayed
                                speakComicText(option.voiceText.ifBlank { message }, onDone = {
                                    scheduleAdvance { showFocusStepRef?.invoke(index + 1) }
                                })
                            }, voiceDelay)
                        } else {
                            waitToken++
                            val choiceToken = waitToken
                            val message = option.feedback.ifBlank { "Uh-uh, think again." }
                            feedback.text = message
                            panelView.setChoiceMark(false)
                            val voiceDelay = playComicChoiceTone(correct = false)
                            handler.postDelayed({
                                if (!active || choiceToken != waitToken) return@postDelayed
                                speakComicText(option.voiceText.ifBlank { message })
                            }, voiceDelay)
                        }
                    }
                }
                choiceBox.addView(button, LinearLayout.LayoutParams(0, dp(54), 1f).withRightMargin(if (optionIndex < step.choices.lastIndex) dp(12) else 0))
            }
        }

        fun showFocusStep(nextIndex: Int) {
            if (!active) return
            if (nextIndex >= focusSteps.size) {
                finish()
                return
            }
            waitToken++
            val token = waitToken
            canTapAdvance = false
            pendingAdvance = null
            index = nextIndex
            val step = focusSteps[index]
            val narration = step.narration.ifBlank { item.meaning }
            panelView.setPanel(step.panelIndex.coerceIn(0, panelCount - 1))
            panelView.setFocusStep(step)
            panelView.setChoiceMark(null)
            caption.text = narration
            progress.text = "${index + 1}/${focusSteps.size}"
            setChoices(step)
            handler.postDelayed({
                if (!active || token != waitToken) return@postDelayed
                speakComicText(narration, onDone = {
                    if (step.choices.isEmpty()) {
                        scheduleAdvance { showFocusStep(index + 1) }
                    }
                })
            }, step.transitionMs.coerceAtLeast(850).toLong())
        }

        showFocusStepRef = { showFocusStep(it) }

        fun showPanel(nextIndex: Int) {
            if (!active) return
            if (nextIndex >= panelCount) {
                finish()
                return
            }
            index = nextIndex
            val narration = narrations.getOrNull(index).orEmpty().ifBlank { item.meaning }
            panelView.setPanel(index)
            panelView.setFocusStep(null)
            panelView.setChoiceMark(null)
            caption.text = narration
            progress.text = "${index + 1}/$panelCount"
            setChoices(
                VocabComicFocusStep(
                    id = "",
                    panelIndex = index,
                    narration = "",
                    sourceBox = null,
                    zoomScale = 1f,
                    focusMode = "sparkle",
                    dimAlpha = 0,
                    transitionMs = 0,
                    choices = emptyList()
                )
            )
            speakComicText(narration, onDone = {
                scheduleAdvance { showPanel(index + 1) }
            })
        }

        overlay.setOnClickListener { tryTapAdvance() }
        card.setOnClickListener { tryTapAdvance() }
        panelView.setOnClickListener { tryTapAdvance() }
        panelContainer.setOnClickListener { tryTapAdvance() }
        skip.setOnClickListener { finish() }
        panelContainer.addView(panelView, matchFrame())
        panelContainer.addView(
            choiceScrim,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92), Gravity.BOTTOM).apply {
                leftMargin = dp(18)
                rightMargin = dp(18)
                bottomMargin = dp(18)
            }
        )
        panelContainer.addView(
            choiceBox,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70), Gravity.BOTTOM).apply {
                leftMargin = dp(18)
                rightMargin = dp(18)
                bottomMargin = dp(28)
            }
        )
        card.addView(text(item.word, 28f, color(R.color.skin_primary_dark), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).withBottom(dp(8)))
        card.addView(panelContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)).withBottom(dp(8)))
        card.addView(caption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        card.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)).withBottom(dp(4)))
        card.addView(feedback, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).withBottom(dp(8)))
        card.addView(skip, fixed(dp(128), dp(42)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        overlay.addView(
            card,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, min(dp(650), resources.displayMetrics.heightPixels - dp(120)), Gravity.CENTER).apply {
                leftMargin = dp(22)
                rightMargin = dp(22)
            }
        )
        root.addView(overlay, matchFrame())
        if (focusSteps.isNotEmpty()) showFocusStep(0) else showPanel(0)
        return true
    }

    private fun showQuizStage(lesson: Lesson) {
        stopAllPlayback()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)
        if (masterSettings.vocabGameMode2Enabled) {
            showFindKittyStage(lesson)
            return
        }
        lesson.vocabReflexGame?.takeIf { it.allCards().isNotEmpty() }?.let { game ->
            showVocabReflexStage(lesson, game)
            return
        }
        quizItems = buildQuizItems(lesson)
        quizIndex = 0
        if (quizItems.isEmpty()) {
            showFirstListenStage(lesson)
            return
        }
        showQuizItem(lesson)
    }

    private fun showVocabReflexStage(
        lesson: Lesson,
        game: VocabReflexGame,
        targetIds: Set<String>? = null
    ) {
        stopAllPlayback()
        val activeTargets = targetIds ?: game.targetWords
            .map { it.wordId }
            .filterNot { isVocabReflexWordPassed(lesson.id, it) }
            .toSet()
        if (activeTargets.isEmpty()) {
            showFirstListenStage(lesson)
            return
        }
        val cards = game.playCardsFor(activeTargets)
        if (cards.isEmpty()) {
            showFirstListenStage(lesson)
            return
        }
        showVocabReflexCard(lesson, game, cards, index = 0, results = mutableListOf())
    }

    private fun showVocabReflexCard(
        lesson: Lesson,
        game: VocabReflexGame,
        cards: List<VocabReflexPlayCard>,
        index: Int,
        results: MutableList<VocabReflexResult>
    ) {
        val playCard = cards.getOrNull(index) ?: run {
            finishVocabReflexRound(lesson, game, results)
            return
        }
        val card = playCard.card
        val options = card.options.take(2)
        if (options.size != 2) {
            handler.post { showVocabReflexCard(lesson, game, cards, index + 1, results) }
            return
        }
        val startedAt = System.currentTimeMillis()
        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()

        val roundLabel = if (playCard.roundType == "echo") "Echo" else "Seed"
        page.addView(flowHeader(lesson, "단어 카드", "$roundLabel ${index + 1}/${cards.size}"), matchWrap().withBottom(dp(12)))
        page.addView(text("맞는 cue card를 빠르게 고르세요.", 15f, color(R.color.skin_muted), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, matchWrap().withBottom(dp(8)))
        page.addView(text(card.targetWord, 42f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(20))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }, matchWrap().withBottom(dp(16)))

        val feedback = text("", 16f, color(R.color.skin_primary_dark), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            minHeight = dp(62)
        }
        val optionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val buttons = mutableListOf<TextView>()
        options.forEach { option ->
            val button = text(option.text, 24f, color(R.color.skin_ink), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(22), dp(12), dp(22))
                background = rounded(color(R.color.skin_surface), dp(20), color(R.color.skin_line), dp(1))
                isClickable = true
                isFocusable = true
            }
            buttons.add(button)
            optionRow.addView(button, LinearLayout.LayoutParams(0, dp(178), 1f).withRightMargin(if (buttons.size == 1) dp(12) else 0))
        }
        page.addView(optionRow, matchWrap().withBottom(dp(14)))
        page.addView(feedback, matchWrap().withBottom(dp(10)))
        val bridge = text(card.readingBridge, 14f, color(R.color.skin_muted)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(color(R.color.skin_surface_alt), dp(14))
        }
        page.addView(bridge, matchWrap())

        buttons.forEachIndexed { optionIndex, button ->
            val option = options[optionIndex]
            button.setOnClickListener {
                buttons.forEach { it.isEnabled = false; it.isClickable = false }
                val reactionMs = (System.currentTimeMillis() - startedAt).toInt()
                val correctOption = card.correctOption()
                val correct = option.isCorrect
                val status = vocabReflexStatus(correct, reactionMs, game.timing)
                results.add(
                    VocabReflexResult(
                        cardId = card.id,
                        targetWordId = card.targetWordId,
                        targetWord = card.targetWord,
                        chosenCue = option.text,
                        correctCue = correctOption?.text.orEmpty(),
                        reactionMs = reactionMs,
                        status = status,
                        cardType = card.cardType,
                        errorTag = if (correct) "" else option.errorTag,
                        feedbackWrong = card.feedbackWrong,
                        readingBridge = card.readingBridge,
                        correct = correct
                    )
                )
                button.background = rounded(
                    if (correct) 0xFFDFF1E7.toInt() else 0xFFF5DCDC.toInt(),
                    dp(20),
                    if (correct) 0xFF4A9E68.toInt() else 0xFFB75A5A.toInt(),
                    dp(2)
                )
                buttons.forEachIndexed { idx, other ->
                    if (options[idx].isCorrect) {
                        other.background = rounded(0xFFDFF1E7.toInt(), dp(20), 0xFF4A9E68.toInt(), dp(2))
                    }
                }
                feedback.text = buildString {
                    append(if (correct) card.feedbackCorrect else card.feedbackWrong)
                    append("\n")
                    append("${reactionMs}ms · $status")
                }
                handler.postDelayed({
                    showVocabReflexCard(lesson, game, cards, index + 1, results)
                }, game.rules.moveNextDelayMs.toLong())
            }
        }
        speakPopupText(card.targetWord)
    }

    private fun finishVocabReflexRound(
        lesson: Lesson,
        game: VocabReflexGame,
        results: List<VocabReflexResult>
    ) {
        val grouped = results.groupBy { it.targetWordId }
        val failedWordIds = mutableSetOf<String>()
        game.targetWords.forEach { target ->
            val wordResults = grouped[target.wordId].orEmpty()
            if (wordResults.isEmpty()) return@forEach
            val wrong = wordResults.count { !it.correct }
            val averageMs = wordResults.map { it.reactionMs }.average().roundToInt()
            val passed = wrong == 0 && averageMs <= game.timing.okayMsMax
            saveVocabReflexWordStats(lesson.id, target.wordId, wordResults, passed)
            if (passed) saveVocabKnown(lesson.id, target.wordId, true) else failedWordIds.add(target.wordId)
        }
        showVocabReflexSummary(lesson, game, results, failedWordIds)
    }

    private fun showVocabReflexSummary(
        lesson: Lesson,
        game: VocabReflexGame,
        results: List<VocabReflexResult>,
        failedWordIds: Set<String>
    ) {
        root.removeAllViews()
        root.setBackgroundColor(color(R.color.skin_background))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()
        val reviewItems = vocabReflexReviewItems(results, game)
        page.addView(flowHeader(lesson, "단어 카드 결과", if (failedWordIds.isEmpty()) "통과" else "재도전 ${failedWordIds.size}개"), matchWrap().withBottom(dp(14)))
        val average = results.map { it.reactionMs }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
        val wrong = results.count { !it.correct }
        val slow = results.count { it.correct && it.reactionMs >= game.timing.slowMsMin }
        page.addView(text("평균 ${average}ms · 틀림 ${wrong} · 느림 ${slow}", 22f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(24), dp(18), dp(24))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }, matchWrap().withBottom(dp(14)))
        val logText = game.targetWords.joinToString("\n") { target ->
            val stats = loadVocabReflexWordStats(lesson.id, target.wordId)
            "${target.word}: avg ${stats.averageMs}ms · wrong ${stats.wrong} · slow ${stats.slow} · ${if (stats.passed) "PASS" else "again"}"
        }
        page.addView(text(logText, 14f, color(R.color.skin_muted)).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.skin_surface_alt), dp(14))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(12)))
        if (reviewItems.isNotEmpty()) {
            page.addView(pill("느린/틀린 카드만 GPT 보이스 리뷰").apply {
                textSize = 16f
                background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
                setOnClickListener { sendVocabReflexVoiceReview(lesson, reviewItems) }
            }, matchWrap().withBottom(dp(10)))
        }
        if (failedWordIds.isEmpty()) {
            page.addView(pill("본문으로").apply {
                textSize = 16f
                setOnClickListener { showFirstListenStage(lesson) }
            }, matchWrap())
        } else {
            page.addView(pill("통과 못한 단어 다시").apply {
                textSize = 16f
                setOnClickListener { showVocabReflexStage(lesson, game, failedWordIds) }
            }, matchWrap())
        }
    }

    private fun vocabReflexStatus(correct: Boolean, reactionMs: Int, timing: VocabReflexTiming): String {
        return when {
            !correct -> "wrong"
            reactionMs <= timing.fastMsMax -> "fast"
            reactionMs <= timing.okayMsMax -> "okay"
            else -> "slow"
        }
    }

    private fun vocabReflexReviewItems(results: List<VocabReflexResult>, game: VocabReflexGame): List<VocabReflexResult> {
        return results.filter { !it.correct || it.reactionMs >= game.timing.slowMsMin }
    }

    private fun sendVocabReflexVoiceReview(lesson: Lesson, reviewItems: List<VocabReflexResult>) {
        if (reviewItems.isEmpty()) return
        val items = JSONArray()
        reviewItems.forEach { result ->
            items.put(
                JSONObject()
                    .put("targetWordId", result.targetWordId)
                    .put("targetWord", result.targetWord)
                    .put("chosenCue", result.chosenCue)
                    .put("correctCue", result.correctCue)
                    .put("reactionMs", result.reactionMs)
                    .put("status", if (result.correct) "slow" else "wrong")
                    .put("cardType", result.cardType)
                    .put("errorTag", result.errorTag)
                    .put("feedbackWrong", result.feedbackWrong)
                    .put("readingBridge", result.readingBridge)
            )
        }
        val payload = JSONObject()
            .put("mode", "vocab_reflex_voice_review")
            .put("lessonId", lesson.id)
            .put("items", items)
        val prompt = """
            You are a short voice review coach for a vocabulary reflex game.
            Review only the wrong or slow items in this JSON.
            Use Korean for explanation, but keep English words and cue words in English.
            For each confusion, say the target word, the correct cue, why the chosen cue is not right, and one short bridge to the story.
            After a few explanations, ask one quick A/B check.
            Keep the review short.

            ${payload.toString(2)}
        """.trimIndent()
        openChatGptWithPrompt(prompt, autoSend = true, voiceBeforePrompt = true, compactAfterSend = true)
    }

    private fun isVocabReflexWordPassed(lessonId: String, wordId: String): Boolean {
        return getSharedPreferences("vocab_reflex_logs", MODE_PRIVATE)
            .getBoolean("$lessonId|$wordId|passed", false)
    }

    private fun saveVocabReflexWordStats(
        lessonId: String,
        wordId: String,
        results: List<VocabReflexResult>,
        passed: Boolean
    ) {
        if (results.isEmpty()) return
        val prefs = getSharedPreferences("vocab_reflex_logs", MODE_PRIVATE)
        val prefix = "$lessonId|$wordId"
        val attempts = prefs.getInt("$prefix|attempts", 0) + results.size
        val totalMs = prefs.getLong("$prefix|totalMs", 0L) + results.sumOf { it.reactionMs.toLong() }
        val wrong = prefs.getInt("$prefix|wrong", 0) + results.count { !it.correct }
        val slow = prefs.getInt("$prefix|slow", 0) + results.count { it.correct && it.status == "slow" }
        val lastAverage = results.map { it.reactionMs }.average().roundToInt()
        val errorTags = results.filter { !it.correct && it.errorTag.isNotBlank() }
            .groupingBy { it.errorTag }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key}:${it.value}" }
        prefs.edit()
            .putInt("$prefix|attempts", attempts)
            .putLong("$prefix|totalMs", totalMs)
            .putInt("$prefix|wrong", wrong)
            .putInt("$prefix|slow", slow)
            .putInt("$prefix|lastAverageMs", lastAverage)
            .putBoolean("$prefix|passed", passed)
            .putString("$prefix|lastErrorTags", errorTags)
            .apply()
    }

    private fun loadVocabReflexWordStats(lessonId: String, wordId: String): VocabReflexWordStats {
        val prefs = getSharedPreferences("vocab_reflex_logs", MODE_PRIVATE)
        val prefix = "$lessonId|$wordId"
        val attempts = prefs.getInt("$prefix|attempts", 0)
        val totalMs = prefs.getLong("$prefix|totalMs", 0L)
        return VocabReflexWordStats(
            attempts = attempts,
            averageMs = if (attempts > 0) (totalMs / attempts).toInt() else 0,
            lastAverageMs = prefs.getInt("$prefix|lastAverageMs", 0),
            wrong = prefs.getInt("$prefix|wrong", 0),
            slow = prefs.getInt("$prefix|slow", 0),
            passed = prefs.getBoolean("$prefix|passed", false),
            lastErrorTags = prefs.getString("$prefix|lastErrorTags", "") ?: ""
        )
    }

    private fun VocabReflexGame.allCards(): List<VocabReflexCard> {
        return sets.flatMap { it.cards }
    }

    private fun VocabReflexGame.playCardsFor(targetIds: Set<String>): List<VocabReflexPlayCard> {
        return sets.flatMap { set ->
            set.cards
                .filter { it.targetWordId in targetIds }
                .map { card -> VocabReflexPlayCard(set.id, set.roundType, card) }
        }
    }

    private fun VocabReflexCard.correctOption(): VocabReflexOption? {
        return options.firstOrNull { it.id == answerOptionId } ?: options.firstOrNull { it.isCorrect }
    }

    private fun showFindKittyStage(
        lesson: Lesson,
        deck: List<KittyWord> = findKittyWords(lesson).shuffled(),
        round: Int = 0,
        collected: List<KittyWord> = emptyList()
    ) {
        stopAllPlayback()
        currentLesson = lesson
        flatSentences = lesson.allSentences()
        masterSettings = activeMasterSettings(lesson.id)

        if (deck.isEmpty()) {
            showFirstListenStage(lesson)
            return
        }
        if (round >= deck.size) {
            showFindKittyFinish(lesson, deck, collected)
            return
        }

        val target = deck[round]
        val options = (listOf(target) + deck.filter { it.id != target.id }.shuffled().take(3)).shuffled()
        val cardButtons = mutableMapOf<String, View>()
        var solved = false

        root.removeAllViews()
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFDF6EC.toInt(), 0xFFFCE8D8.toInt())
        )

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(16), dp(18), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        header.addView(text("Find the Kitty 🐱", 28f, 0xFFEA6A22.toInt(), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(text("June 10th", 13f, 0xFFD98942.toInt(), Typeface.BOLD).apply {
            gravity = Gravity.RIGHT or Gravity.BOTTOM
        }, fixed(dp(90), dp(42)))
        page.addView(header, matchWrap().withBottom(dp(6)))

        page.addView(progressStrip(round, deck.size), matchWrap().withBottom(dp(14)))
        page.addView(text(target.prompt, 21f, color(R.color.skin_ink), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(color(R.color.skin_surface), dp(24), 0xFFFFE0BE.toInt(), dp(1))
        }, matchWrap().withBottom(dp(14)))

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val sceneHeight = min(dp(190), max(dp(142), (resources.displayMetrics.widthPixels - dp(58)) / 2))
        options.chunked(2).forEach { rowWords ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowWords.forEachIndexed { indexInRow, word ->
                val card = kittySceneCard(word).apply {
                    setOnClickListener {
                        if (solved) return@setOnClickListener
                        val correct = word.id == target.id
                        animate().scaleX(0.96f).scaleY(0.96f).setDuration(70L).withEndAction {
                            animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
                        }.start()
                        if (correct) {
                            solved = true
                            cardButtons.values.forEach { it.isEnabled = false; it.isClickable = false }
                            cardButtons[target.id]?.let { setKittySceneCardState(it, target, "correct") }
                            saveVocabKnown(lesson.id, target.id, true)
                            showFindKittyFeedback(
                                page = page,
                                correct = true,
                                target = target,
                                picked = word,
                                onNext = {
                                    val nextCollected = if (collected.any { it.id == target.id }) collected else collected + target
                                    showFindKittyStage(lesson, deck, round + 1, nextCollected)
                                }
                            )
                            speakPopupText("Meow! You found it. ${target.word}. ${target.def}.")
                        } else {
                            setKittySceneCardState(this, word, "wrong")
                            showFindKittyFeedback(page, correct = false, target = target, picked = word, onNext = {})
                            speakPopupText("That one is ${word.word}. ${word.def}. Look again.")
                        }
                    }
                }
                cardButtons[word.id] = card
                row.addView(card, LinearLayout.LayoutParams(0, sceneHeight, 1f).withRightMargin(if (indexInRow == 0) dp(10) else 0))
            }
            grid.addView(row, matchWrap().withBottom(dp(10)))
        }
        page.addView(grid, matchWrap())

        val feedbackSlot = FrameLayout(this).apply {
            tag = "kittyFeedback"
            minimumHeight = dp(116)
        }
        page.addView(feedbackSlot, matchWrap().withBottom(dp(8)))

        if (collected.isNotEmpty()) {
            page.addView(findKittyCollectedShelf(collected), matchWrap())
        }

        speakPopupText(target.prompt)
    }

    private fun showFindKittyFeedback(
        page: LinearLayout,
        correct: Boolean,
        target: KittyWord,
        picked: KittyWord,
        onNext: () -> Unit
    ) {
        val slot = findTaggedChild<FrameLayout>(page, "kittyFeedback") ?: return
        slot.removeAllViews()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(
                if (correct) 0xFFEAF8F0.toInt() else 0xFFFFF4DF.toInt(),
                dp(24),
                if (correct) 0xFFAADDC0.toInt() else 0xFFFFCF8A.toInt(),
                dp(1)
            )
        }
        if (correct) {
            box.addView(text("Meow! You found it!", 19f, 0xFF28965A.toInt(), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            }, matchWrap())
            box.addView(text("${target.word} (${target.pos}) = ${target.def}", 15f, color(R.color.skin_ink)).apply {
                gravity = Gravity.CENTER
            }, matchWrap().withTop(dp(2)))
            box.addView(pill("Next kitty →").apply {
                textSize = 16f
                background = rounded(0xFF35A864.toInt(), dp(20))
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { onNext() }
            }, fixed(dp(168), dp(44)).withTop(dp(8)))
        } else {
            box.addView(text("That one is ${picked.word}.", 17f, 0xFF9A6A16.toInt(), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            }, matchWrap())
            box.addView(text(picked.def, 15f, color(R.color.skin_muted)).apply {
                gravity = Gravity.CENTER
            }, matchWrap().withTop(dp(2)))
            box.addView(text("Look again.", 16f, 0xFFB57918.toInt(), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            }, matchWrap().withTop(dp(4)))
        }
        slot.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun showFindKittyFinish(lesson: Lesson, deck: List<KittyWord>, collected: List<KittyWord>) {
        stopAllPlayback()
        root.removeAllViews()
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFDF6EC.toInt(), 0xFFFCE8D8.toInt())
        )

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(22), dp(20), dp(96))
        }
        root.addView(page, matchFrame())
        addMasterButton()
        page.addView(text("🎉🐱🎉", 54f, 0xFFEA6A22.toInt(), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, matchWrap().withBottom(dp(4)))
        page.addView(text("You found all the kitties!", 25f, 0xFFEA6A22.toInt(), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, matchWrap().withBottom(dp(4)))
        page.addView(text("${deck.size} word cards collected", 15f, 0xFFD98942.toInt(), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }, matchWrap().withBottom(dp(16)))

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        deck.forEach { word ->
            list.addView(text("${word.word}\n${word.def}", 16f, color(R.color.skin_ink), Typeface.BOLD).apply {
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = rounded(color(R.color.skin_surface), dp(16), 0xFFFFE0BE.toInt(), dp(1))
            }, matchWrap().withBottom(dp(8)))
        }
        scroll.addView(list, matchWrap())
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).withBottom(dp(12)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(pill("Play again").apply {
            textSize = 16f
            setOnClickListener { showFindKittyStage(lesson) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).withRightMargin(dp(10)))
        actions.addView(pill(if (lesson.vocabReflexGame?.allCards()?.isNotEmpty() == true) "2카드로" else "본문으로").apply {
            textSize = 16f
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener { showAfterComicQuizStage(lesson) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        page.addView(actions, matchWrap())
        speakPopupText("You found all the kitties.")
    }

    private fun progressStrip(done: Int, total: Int): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(0xFFFFE0BE.toInt(), dp(8))
            clipToOutline = false
        }
        val completed = done.coerceIn(0, total)
        if (completed > 0) {
            bar.addView(View(this).apply {
                background = rounded(0xFFF59A32.toInt(), dp(8))
            }, LinearLayout.LayoutParams(0, dp(10), completed.toFloat()))
        }
        val remaining = (total - completed).coerceAtLeast(1)
        bar.addView(View(this), LinearLayout.LayoutParams(0, dp(10), remaining.toFloat()))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(bar, LinearLayout.LayoutParams(0, dp(10), 1f).withRightMargin(dp(8)))
        row.addView(text("$completed/$total", 12f, 0xFFD98942.toInt(), Typeface.BOLD), fixed(dp(48), dp(24)))
        return row
    }

    private fun kittySceneCard(word: KittyWord): FrameLayout {
        return FrameLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isClickable = true
            isFocusable = true
            word.cardPanel?.let { panel ->
                addView(DataComicPanelView(this@MainActivity).apply {
                    setPanel(panel, 0, word.word)
                    setCurrent(false)
                    setShowPanelNumber(false)
                }, matchFrame())
            } ?: addView(text(kittySceneText(word.sceneId, "idle"), 34f, color(R.color.skin_ink), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                includeFontPadding = false
            }, matchFrame())
            setKittySceneCardState(this, word, "idle")
        }
    }

    private fun setKittySceneCardState(card: View, word: KittyWord, state: String) {
        val stroke = when (state) {
            "correct" -> 0xFF3DA868.toInt()
            "wrong" -> 0xFFE07A7A.toInt()
            else -> 0xFFFFE0BE.toInt()
        }
        val strokeWidth = if (state == "idle") dp(2) else dp(4)
        val fill = if (word.cardPanel != null) 0xFFFFFBF4.toInt() else kittySceneColor(word.sceneId)
        card.background = rounded(fill, dp(24), stroke, strokeWidth)
        if (card is ViewGroup) {
            val mood = when (state) {
                "correct" -> "happy"
                "wrong" -> "sad"
                else -> "idle"
            }
            for (index in 0 until card.childCount) {
                when (val child = card.getChildAt(index)) {
                    is TextView -> child.text = kittySceneText(word.sceneId, mood)
                    is DataComicPanelView -> child.setCurrent(state == "correct")
                }
            }
        }
    }

    private fun kittySceneText(sceneId: String, mood: String): String {
        val cat = when (mood) {
            "happy" -> "😺"
            "sad" -> "🙀"
            else -> "🐱"
        }
        return when (sceneId) {
            "freezing" -> "❄️     ❄️\n$cat\n🧊"
            "enclosed" -> "📦\n$cat"
            "active" -> "💨  $cat  🧶"
            "volcano" -> "🌋  $cat"
            "identity" -> "🎭\n$cat   ❓"
            "destination" -> "$cat  · · ·  🚩🏠"
            "wildlife" -> "🦉    🌳\n🌲  $cat  🦊"
            "approach" -> "$cat  ➡️  🐦"
            else -> cat
        }
    }

    private fun kittySceneColor(sceneId: String): Int {
        return when (sceneId) {
            "freezing" -> 0xFFE8F2FF.toInt()
            "enclosed" -> 0xFFFEF2D6.toInt()
            "active" -> 0xFFFFF0DB.toInt()
            "volcano" -> 0xFFFEE3E3.toInt()
            "identity" -> 0xFFF1ECFF.toInt()
            "destination" -> 0xFFE6FAEF.toInt()
            "wildlife" -> 0xFFE8F8E8.toInt()
            "approach" -> 0xFFFFF6D8.toInt()
            else -> color(R.color.skin_surface)
        }
    }

    private fun findKittyCollectedShelf(collected: List<KittyWord>): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(text("My word cards", 12f, 0xFFD98942.toInt(), Typeface.BOLD), matchWrap().withBottom(dp(4)))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        collected.take(4).forEach { word ->
            row.addView(text(word.word, 13f, 0xFFB85F18.toInt(), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
                background = rounded(0xFFFFE7C6.toInt(), dp(14))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).withRightMargin(dp(6)))
        }
        if (collected.size > 4) {
            row.addView(text("+${collected.size - 4}", 13f, 0xFFB85F18.toInt(), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            }, fixed(dp(42), dp(30)))
        }
        box.addView(row, matchWrap())
        return box
    }

    private fun findKittyWords(lesson: Lesson): List<KittyWord> {
        val studyItems = vocabStudyItems(lesson)
        if (studyItems.size >= 4) {
            return studyItems.mapIndexed { index, item ->
                val vocab = lesson.vocabulary[item.id]
                val definition = vocab?.comic?.meaning
                    ?.ifBlank { item.meaning }
                    ?: item.meaning
                KittyWord(
                    id = item.id,
                    word = item.word,
                    pos = vocab?.partOfSpeech?.ifBlank { "word" } ?: "word",
                    def = definition.ifBlank { item.longMeaning }.ifBlank { "No definition yet." },
                    prompt = "Find the ${item.word.uppercase(Locale.US)} kitty!",
                    sceneId = kittySceneForWord(item.word, index),
                    cardPanel = vocab?.quizPanel
                        ?: vocab?.comic?.panels?.firstOrNull()?.copy(caption = "", bubble = null)
                )
            }
        }
        val vocabByWord = lesson.vocabulary.values.associateBy { normalizeKittyKey(it.word) }
        return defaultKittyWords().map { base ->
            val vocab = vocabByWord[base.id] ?: return@map base
            val definition = vocab.easyEnglish
                .ifBlank { lesson.explanations[vocab.explanationIds.firstOrNull().orEmpty()]?.easyEnglish.orEmpty() }
                .ifBlank { base.def }
            base.copy(
                pos = vocab.partOfSpeech.ifBlank { base.pos },
                def = definition
            )
        }
    }

    private fun kittySceneForWord(word: String, index: Int): String {
        val key = normalizeKittyKey(word)
        val known = defaultKittyWords().firstOrNull { it.id == key }?.sceneId
        if (known != null) return known
        val scenes = listOf("freezing", "enclosed", "active", "volcano", "identity", "destination", "wildlife", "approach")
        return scenes[index % scenes.size]
    }

    private fun defaultKittyWords(): List<KittyWord> {
        return listOf(
            KittyWord("freezing", "freezing", "adjective", "being very cold", "Find the FREEZING kitty!", "freezing", null),
            KittyWord("enclosed", "enclosed", "adjective", "being inside something", "Find the ENCLOSED kitty!", "enclosed", null),
            KittyWord("active", "active", "adjective", "being full of action", "Find the ACTIVE kitty!", "active", null),
            KittyWord("volcano", "volcano", "noun", "a mountain that shoots out lava", "Find the kitty by the VOLCANO!", "volcano", null),
            KittyWord("identity", "identity", "noun", "who someone really is", "Find the kitty hiding its IDENTITY!", "identity", null),
            KittyWord("destination", "destination", "noun", "the place you are going to", "Find the kitty's DESTINATION!", "destination", null),
            KittyWord("wildlife", "wildlife", "noun", "animals that live in nature", "Find the WILDLIFE!", "wildlife", null),
            KittyWord("approach", "approach", "verb", "to get closer to something", "Which kitty will APPROACH the bird?", "approach", null)
        )
    }

    private fun normalizeKittyKey(value: String): String {
        return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }

    private inline fun <reified T : View> findTaggedChild(parent: ViewGroup, tagValue: String): T? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.tag == tagValue && child is T) return child
        }
        return null
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
        settingsStore.saveVocabKnown(lessonId, vocabId, known)
    }

    private fun showManualChunkMode(lesson: Lesson, sentenceIndex: Int) {
        flatSentences = lesson.allSentences()
        val practiceIndices = manualPracticeSentenceIndices(lesson)
        if (practiceIndices.isEmpty()) {
            showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
            return
        }
        val practicePosition = sentenceIndex.coerceIn(0, practiceIndices.lastIndex)
        startManualChunkActivity(lesson, practiceIndices[practicePosition])
    }

    private fun startManualChunkActivity(lesson: Lesson, startSentenceIndex: Int? = null) {
        if (startManualBodyModeInCurrentReader(lesson, startSentenceIndex, scrollToTarget = startSentenceIndex != null)) return
        showReader(lesson)
        readerScroll?.post { startManualBodyModeInCurrentReader(lesson, startSentenceIndex, scrollToTarget = startSentenceIndex != null) }
    }

    private fun startManualBodyModeInCurrentReader(lesson: Lesson, startSentenceIndex: Int? = null, scrollToTarget: Boolean = false): Boolean {
        if (currentLesson?.id != lesson.id || paragraphBindings.isEmpty() || readerScroll == null) return false
        if (flatSentences.isEmpty()) flatSentences = lesson.allSentences()
        val practiceIndices = manualPracticeSentenceIndices(lesson)
        if (practiceIndices.isEmpty()) {
            showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
            return true
        }
        val requested = startSentenceIndex?.takeIf { it in practiceIndices }
        val targetSentenceIndex = requested ?: practiceIndices.first()

        stopAllPlayback()
        manualBodyMode = true
        clearManualBodyDraft()
        activeMode = "sentence"
        useTts = true
        manualSentenceIndex = targetSentenceIndex
        selectedSentenceIndex = targetSentenceIndex
        currentSentenceIndex = targetSentenceIndex
        currentChunkId = null
        explicitSegmentEndMs = null
        explicitSegmentNextStartMs = null
        pendingStartMs = flatSentences.getOrNull(targetSentenceIndex)?.sentence?.startMs

        readerScroll?.setOnTouchListener(null)
        paragraphBindings.forEach { binding ->
            binding.textView.setOnTouchListener(paragraphGesture(binding))
        }
        installManualBodyControls(lesson)
        refreshAllParagraphs()
        if (scrollToTarget) scrollToSentence(targetSentenceIndex)
        return true
    }

    private fun installManualBodyControls(lesson: Lesson) {
        val host = readerActionHost ?: return
        val practiceIndices = manualPracticeSentenceIndices(lesson)
        val practicePosition = practiceIndices.indexOf(manualSentenceIndex).takeIf { it >= 0 } ?: 0
        host.removeAllViews()
        host.addView(text("청크 활동 ${practicePosition + 1}/${practiceIndices.size}. 진한 문장에서 바로 드래그해 청크를 나눠요.", 14f, color(R.color.skin_muted)).apply {
            setPadding(dp(2), 0, dp(2), dp(8))
        }, matchWrap())
        host.addView(manualBodyControls(lesson), matchWrap())
    }

    private fun installReaderControls(lesson: Lesson) {
        val host = readerActionHost ?: return
        host.removeAllViews()
        host.addView(controls(), matchWrap().withBottom(dp(8)))
        host.addView(readingCoachPanel(lesson), matchWrap().withBottom(dp(8)))
        if (masterSettings.manualChunkEnabled) {
            host.addView(flowNextPanel(lesson), matchWrap())
        }
    }

    private fun readingCoachPanel(lesson: Lesson): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface_alt), dp(18), color(R.color.skin_line), dp(1))
        }
        row.addView(text("ChatGPT voice coach reads each chunk, then Seoin shadows it.", 14f, color(R.color.skin_muted)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pill("낭독 코치").apply {
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener { startReadingCoachMode(lesson) }
        }, fixed(dp(132), dp(42)))
        return row
    }

    private fun startReadingCoachMode(lesson: Lesson) {
        if (flatSentences.isEmpty()) flatSentences = lesson.allSentences()
        clearPendingChatPromptState()
        readingCoachPreviousActiveMode = activeMode
        readingCoachPreviousUseTts = useTts
        readingCoachPreferredChunkSetId = coachChunkSetId(lesson)
        readingCoachChunkSetId = readingCoachPreferredChunkSetId
        val firstPosition = firstCoachPosition(lesson, selectedSentenceIndex)
            ?: firstCoachPosition(lesson, 0)
            ?: run {
                toast("No chunks for coach.")
                return
            }
        stopAllPlayback()
        readingCoachActive = true
        readingCoachState = ReadingCoachState.IDLE
        readingCoachFallbackTts = false
        readingCoachPrimed = false
        readingCoachVoiceRequestInFlight = false
        readingCoachFirstVoiceChunkStarted = false
        readingCoachSentenceIndex = firstPosition.first
        readingCoachChunkIndex = firstPosition.second
        readingCoachFlowToken += 1L
        readingCoachChunkToken += 1L
        useTts = true
        updateReadingCoachSelection(lesson, readingCoachSentenceIndex, readingCoachChunkIndex)
        refreshAllParagraphs()
        scrollToSentence(readingCoachSentenceIndex)
        installReadingCoachControls(lesson)
        showChatGptAssistantDialog()
    }

    private fun installReadingCoachControls(lesson: Lesson) {
        val host = readerActionHost ?: return
        host.removeAllViews()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(color(R.color.skin_surface), dp(18), color(R.color.skin_line), dp(1))
        }
        readingCoachStatusLabel = text("", 14f, color(R.color.skin_muted), Typeface.BOLD)
        box.addView(readingCoachStatusLabel, matchWrap().withBottom(dp(8)))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(pill("다시 듣기").apply {
            setOnClickListener { readCurrentCoachChunk(lesson, forceTts = readingCoachFallbackTts) }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).withRightMargin(dp(6)))
        row.addView(pill("따라하기").apply {
            setOnClickListener { openCoachTalkTurn(lesson, readingCoachChunkToken) }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).withRightMargin(dp(6)))
        row.addView(pill("다음 청크").apply {
            setOnClickListener { moveReadingCoachChunk(lesson, 1, autoRead = true) }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).withRightMargin(dp(6)))
        row.addView(pill("문장 반복").apply {
            setOnClickListener {
                readingCoachChunkIndex = 0
                readCurrentCoachChunk(lesson, forceTts = readingCoachFallbackTts)
            }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).withRightMargin(dp(6)))
        row.addView(pill("끝내기").apply {
            background = rounded(color(R.color.skin_surface_alt), dp(18), color(R.color.skin_line), dp(1))
            setOnClickListener { endReadingCoachMode(lesson) }
        }, LinearLayout.LayoutParams(0, dp(46), 0.8f))
        box.addView(row, matchWrap())
        readingCoachStateButton = pill("").apply {
            setOnClickListener { retryReadingCoachVoice(lesson) }
        }
        box.addView(readingCoachStateButton, matchWrap().withTop(dp(8)))
        host.addView(box, matchWrap())
        updateReadingCoachStatus()
    }

    private fun prepareReadingCoachSession() {
        if (!readingCoachActive || readingCoachPrimed || readingCoachVoiceShellPreparing || readingCoachFallbackTts) return
        val lesson = currentLesson ?: return
        val token = readingCoachFlowToken
        expandChatGptAssistantDialogForVoice()
        bringChatGptDialogToForegroundForVoice("prepare-start")
        readingCoachVoiceShellPreparing = true
        readingCoachState = ReadingCoachState.IDLE
        clearPendingChatPromptState()
        updateReadingCoachStatus("Opening ChatGPT voice mode...")
        requestCoachVoiceMode(token, retries = 35) { clicked ->
            if (!readingCoachActive || token != readingCoachFlowToken) return@requestCoachVoiceMode
            if (!clicked) {
                readingCoachVoiceShellPreparing = false
                switchReadingCoachToTtsFallback(lesson, "voice mode button not ready")
                return@requestCoachVoiceMode
            }
            bringChatGptDialogToForegroundForVoice("voice-button-clicked")
            readingCoachFallbackTts = false
            readingCoachState = ReadingCoachState.VOICE_SHELL_READY
            Log.d("SeoinCoach", "voice mode requested")
            setChatAudioDucked(true)
            val marker = coachMarker("prime")
            val prime = """
                You are Seoin's kind English reading teacher.
                Speak in very easy English. Be warm, short, and calm.
                In coaching mode: read one chunk slowly, ask Seoin to repeat, listen, then give one tiny pronunciation or chunking tip.
                Do not answer this setup message out loud if possible. If you must respond, keep it silent and short.
            """.trimIndent()
            updateReadingCoachStatus("Voice clicked. Waiting for voice screen...")
            waitForCoachVoiceUiReady(token, maxWaitMs = 25_000L) { ready ->
                if (!readingCoachActive || token != readingCoachFlowToken || readingCoachFallbackTts) return@waitForCoachVoiceUiReady
                if (!ready) {
                    setChatAudioDucked(false)
                    readingCoachVoiceShellPreparing = false
                    switchReadingCoachToTtsFallback(lesson, "voice microphone UI not ready")
                    return@waitForCoachVoiceUiReady
                }
                bringChatGptDialogToForegroundForVoice("voice-ready-before-prime")
                updateReadingCoachStatus("Microphone is ready. Priming coach...")
                setCoachMicOpen(false) { micReady ->
                    Log.d("SeoinCoach", "initial mic closed before prime ready=$micReady")
                    updateReadingCoachStatus("Voice is ready. Waiting one second before priming...")
                    handler.postDelayed({
                        if (!readingCoachActive || token != readingCoachFlowToken || readingCoachFallbackTts) return@postDelayed
                        Log.d("SeoinCoach", "voice settle before prime finished delayMs=1000")
                        bringChatGptDialogToForegroundForVoice("prime-inject")
                        readLastAssistantText { baseline ->
                            if (!readingCoachActive || token != readingCoachFlowToken || readingCoachFallbackTts) return@readLastAssistantText
                            injectCoachMessage(prime, marker, attempt = 0, maxAttempts = 0, visibleTimeoutMs = 20_000L) { visible ->
                                if (!readingCoachActive || token != readingCoachFlowToken) return@injectCoachMessage
                                if (!visible) {
                                    setChatAudioDucked(false)
                                    readingCoachVoiceShellPreparing = false
                                    switchReadingCoachToTtsFallback(lesson, "priming user bubble not visible after extended wait")
                                    return@injectCoachMessage
                                }
                                val primeToken = readingCoachChunkToken
                                waitForCoachTextCompleteAfter(primeToken, baseline, timeoutMs = 4200L) { _ ->
                                    if (!readingCoachActive || token != readingCoachFlowToken) return@waitForCoachTextCompleteAfter
                                    readingCoachPrimed = true
                                    readingCoachFallbackTts = false
                                    readingCoachVoiceShellPreparing = false
                                    readingCoachState = ReadingCoachState.CONVERSATION_READY
                                    Log.d("SeoinCoach", "conversation ready marker=$marker")
                                    setChatAudioDucked(false)
                                    readCurrentCoachChunk(lesson, forceTts = false)
                                }
                            }
                        }
                    }, 1000L)
                }
            }
        }
    }

    private fun readCurrentCoachChunk(lesson: Lesson, forceTts: Boolean = false) {
        val token = ++readingCoachChunkToken
        val position = currentCoachPosition(lesson) ?: run {
            finishReadingCoach(lesson)
            return
        }
        val (sentenceIndex, chunkIndex) = position
        val chunk = updateReadingCoachSelection(lesson, sentenceIndex, chunkIndex) ?: run {
            finishReadingCoach(lesson)
            return
        }
        refreshAllParagraphs()
        scrollToSentence(sentenceIndex)
        updateReadingCoachStatus("Coach is reading: ${chunk.text}")

        if (forceTts || readingCoachFallbackTts || !readingCoachPrimed || chatWebView == null) {
            if (blockReadingCoachTtsFallbackForDebug) {
                readingCoachFallbackTts = false
                readingCoachState = if (readingCoachPrimed) ReadingCoachState.CONVERSATION_READY else ReadingCoachState.VOICE_SHELL_READY
                Log.d(
                    "SeoinCoach",
                    "fallback blocked in readCurrent reason=forceTts:$forceTts fallback:$readingCoachFallbackTts primed:$readingCoachPrimed web:${chatWebView != null}"
                )
                updateReadingCoachStatus("DEBUG: TTS fallback is blocked. Staying in ChatGPT voice flow.")
                return
            }
            readingCoachFallbackTts = true
            readingCoachState = ReadingCoachState.FALLBACK_TTS
            updateReadingCoachStatus("TTS fallback reading. Repeat after it, then press Next.")
            speakTtsSegments(
                listOf(chunkTtsSegment(sentenceIndex, chunk)),
                pauseAfterMs = 0,
                speechRate = masterSettings.firstListenRate,
                onDone = {
                    if (!readingCoachActive || token != readingCoachChunkToken) return@speakTtsSegments
                    readingCoachState = ReadingCoachState.CHILD_TURN
                    updateReadingCoachStatus("Your turn. Read it out loud, then press Next Chunk.")
                }
            )
            return
        }

        readingCoachState = ReadingCoachState.COACHING
        updateReadingCoachStatus("Coach is speaking. Wait for your turn.")
        val marker = coachMarker("chunk_${sentenceIndex}_${readingCoachChunkIndex}")
        val prompt = """
            Read this part slowly one time, then say: "Now your turn, Seoin."
            After Seoin repeats it, give one very short friendly tip.
            Chunk: "${chunk.text}"
        """.trimIndent()
        readLastAssistantText { baseline ->
            if (!readingCoachActive || token != readingCoachChunkToken) return@readLastAssistantText
            injectCoachMessage(prompt, marker, attempt = 0) { visible ->
                if (!readingCoachActive || token != readingCoachChunkToken) return@injectCoachMessage
                if (!visible) {
                    switchReadingCoachToTtsFallback(lesson, "chunk inject not visible")
                    return@injectCoachMessage
                }
                if (!readingCoachFirstVoiceChunkStarted) {
                    readingCoachFirstVoiceChunkStarted = true
                    Log.d("SeoinCoach", "first voice chunk started marker=$marker")
                }
                waitForCoachTextCompleteAfter(token, baseline, timeoutMs = 9000L) { spokenText ->
                    if (!readingCoachActive || token != readingCoachChunkToken) return@waitForCoachTextCompleteAfter
                    waitForCoachMediaQuiet(token, spokenText) {
                        if (!readingCoachActive || token != readingCoachChunkToken) return@waitForCoachMediaQuiet
                        openCoachTalkTurn(lesson, token)
                    }
                }
            }
        }
    }

    private fun openCoachTalkTurn(lesson: Lesson, token: Long) {
        if (!readingCoachActive || token != readingCoachChunkToken) return
        readingCoachState = ReadingCoachState.CHILD_TURN
        updateReadingCoachStatus("Your turn. Speak now.")
        setCoachMicOpen(open = true) { clicked ->
            Log.d("SeoinCoach", "talk open clicked=$clicked")
            val talkToken = readingCoachChunkToken
            handler.postDelayed({
                if (!readingCoachActive || talkToken != readingCoachChunkToken) return@postDelayed
                setCoachMicOpen(open = false) { muteClicked ->
                    Log.d("SeoinCoach", "talk window ended; mic close clicked=$muteClicked")
                    if (!readingCoachActive || talkToken != readingCoachChunkToken) return@setCoachMicOpen
                    readingCoachState = ReadingCoachState.FEEDBACK
                    updateReadingCoachStatus("Coach feedback...")
                    readLastAssistantText { baseline ->
                        if (!readingCoachActive || talkToken != readingCoachChunkToken) return@readLastAssistantText
                        waitForCoachTextCompleteAfter(talkToken, baseline, timeoutMs = 8500L) { feedbackText ->
                            if (!readingCoachActive || talkToken != readingCoachChunkToken) return@waitForCoachTextCompleteAfter
                            waitForCoachMediaQuiet(talkToken, feedbackText) {
                                if (!readingCoachActive || talkToken != readingCoachChunkToken) return@waitForCoachMediaQuiet
                                Log.d("SeoinCoach", "coach handoff next-chunk")
                                moveReadingCoachChunk(lesson, 1, autoRead = true)
                            }
                        }
                    }
                }
            }, 8000L)
        }
    }

    private fun moveReadingCoachChunk(lesson: Lesson, delta: Int, autoRead: Boolean) {
        val currentChunks = coachChunksFor(lesson, readingCoachSentenceIndex)
        var nextSentence = readingCoachSentenceIndex
        var nextChunk = readingCoachChunkIndex + delta
        if (nextChunk !in currentChunks.indices) {
            val nextPosition = firstCoachPosition(lesson, readingCoachSentenceIndex + 1)
            if (nextPosition == null) {
                finishReadingCoach(lesson)
                return
            }
            nextSentence = nextPosition.first
            nextChunk = nextPosition.second
        }
        readingCoachSentenceIndex = nextSentence
        readingCoachChunkIndex = nextChunk.coerceAtLeast(0)
        updateReadingCoachSelection(lesson, readingCoachSentenceIndex, readingCoachChunkIndex)
        refreshAllParagraphs()
        scrollToSentence(readingCoachSentenceIndex)
        updateReadingCoachStatus()
        if (autoRead) readCurrentCoachChunk(lesson, forceTts = readingCoachFallbackTts)
    }

    private fun finishReadingCoach(lesson: Lesson) {
        readingCoachState = ReadingCoachState.DONE
        currentChunkId = null
        refreshAllParagraphs()
        updateReadingCoachStatus("Coach session done. Great reading.")
        speakPopupText("Great reading, Seoin. You finished the coaching practice.", onDone = {
            if (readingCoachActive) endReadingCoachMode(lesson)
        })
    }

    private fun endReadingCoachMode(lesson: Lesson) {
        if (chatOverlay != null) {
            dismissChatGptAssistantOverlay()
        } else {
            resetReadingCoachStateOnly()
            restoreReaderAfterCoach(lesson)
        }
    }

    private fun restoreReaderAfterCoach(lesson: Lesson?) {
        val targetLesson = lesson ?: currentLesson ?: return
        stopAllPlayback()
        activeMode = readingCoachPreviousActiveMode.ifBlank { "natural" }
        useTts = readingCoachPreviousUseTts
        currentChunkId = null
        explicitSegmentEndMs = null
        explicitSegmentNextStartMs = null
        pendingStartMs = flatSentences.getOrNull(selectedSentenceIndex)?.sentence?.startMs
        refreshAllParagraphs()
        installReaderControls(targetLesson)
        refreshMode()
        updatePlayIcon()
    }

    private fun resetReadingCoachStateOnly() {
        readingCoachFlowToken += 1L
        readingCoachChunkToken += 1L
        readingCoachActive = false
        readingCoachState = ReadingCoachState.RESET
        readingCoachFallbackTts = false
        readingCoachPrimed = false
        readingCoachVoiceShellPreparing = false
        readingCoachVoiceRequestInFlight = false
        readingCoachFirstVoiceChunkStarted = false
        readingCoachStatusLabel = null
        readingCoachStateButton = null
        setChatAudioDucked(false)
    }

    private fun switchReadingCoachToTtsFallback(lesson: Lesson, reason: String) {
        if (!readingCoachActive) return
        Log.d("SeoinCoach", "fallback reason=$reason")
        if (blockReadingCoachTtsFallbackForDebug) {
            readingCoachVoiceShellPreparing = false
            readingCoachFallbackTts = false
            readingCoachState = if (readingCoachPrimed) ReadingCoachState.CONVERSATION_READY else ReadingCoachState.VOICE_SHELL_READY
            Log.d("SeoinCoach", "fallback blocked for debug reason=$reason")
            updateReadingCoachStatus("DEBUG: TTS fallback blocked. Voice error: $reason")
            return
        }
        val shouldCloseVoice = readingCoachState == ReadingCoachState.VOICE_SHELL_READY || readingCoachVoiceShellPreparing
        readingCoachVoiceShellPreparing = false
        readingCoachFallbackTts = true
        readingCoachState = ReadingCoachState.FALLBACK_TTS
        updateReadingCoachStatus("Voice coach not ready. Using app TTS fallback.")
        compactChatGptAssistantDialog()
        if (shouldCloseVoice) {
            closeChatVoiceSession {
                handler.postDelayed({
                    if (readingCoachActive && readingCoachFallbackTts) readCurrentCoachChunk(lesson, forceTts = true)
                }, 900L)
            }
        } else {
            readCurrentCoachChunk(lesson, forceTts = true)
        }
    }

    private fun retryReadingCoachVoice(lesson: Lesson) {
        if (!readingCoachActive || readingCoachVoiceShellPreparing) return
        stopAllPlayback()
        readingCoachFlowToken += 1L
        readingCoachChunkToken += 1L
        readingCoachFallbackTts = false
        readingCoachPrimed = false
        readingCoachVoiceRequestInFlight = false
        readingCoachState = ReadingCoachState.IDLE
        updateReadingCoachStatus("Retrying voice coach...")
        expandChatGptAssistantDialogForVoice()
        val token = readingCoachFlowToken
        handler.postDelayed({
            if (!readingCoachActive || token != readingCoachFlowToken) return@postDelayed
            if (chatWebView == null || chatOverlay == null) {
                showChatGptAssistantDialog()
            } else {
                prepareReadingCoachSession()
            }
        }, 850L)
    }

    private fun updateReadingCoachStatus(extra: String? = null) {
        val chunk = coachChunksFor(currentLesson ?: return, readingCoachSentenceIndex).getOrNull(readingCoachChunkIndex)
        val total = coachChunksFor(currentLesson ?: return, readingCoachSentenceIndex).size
        val stateText = when (readingCoachState) {
            ReadingCoachState.IDLE -> "준비중"
            ReadingCoachState.VOICE_SHELL_READY -> "voice ready"
            ReadingCoachState.CONVERSATION_READY -> "conversation ready"
            ReadingCoachState.COACHING -> "말하는중"
            ReadingCoachState.CHILD_TURN -> "네 차례"
            ReadingCoachState.FEEDBACK -> "피드백중"
            ReadingCoachState.FALLBACK_TTS -> "TTS fallback"
            ReadingCoachState.DONE -> "완료"
            ReadingCoachState.RESET -> "종료"
        }
        readingCoachStatusLabel?.text = extra ?: "낭독 코치 ${readingCoachSentenceIndex + 1}/${flatSentences.size}, chunk ${readingCoachChunkIndex + 1}/$total: ${chunk?.text.orEmpty()}"
        readingCoachStateButton?.text = "현재 상태: $stateText · Voice 다시 시도"
        chatStatusLabel?.text = "Reading coach: $stateText"
    }

    private fun coachChunkSetId(lesson: Lesson): String {
        val sentence = flatSentences.getOrNull(selectedSentenceIndex)?.sentence
        val preferred = listOf(activeMode, lesson.defaultChunkSetId, "short", "long", "sentence")
            .filter { it.isNotBlank() && it != "natural" }
        return preferred.firstOrNull { id -> sentence?.chunkSets?.get(id).orEmpty().isNotEmpty() }
            ?: preferred.firstOrNull()
            ?: "short"
    }

    private fun coachChunksFor(lesson: Lesson, sentenceIndex: Int): List<Chunk> {
        return coachChunkSource(lesson, sentenceIndex).second
    }

    private fun coachChunkSource(lesson: Lesson, sentenceIndex: Int): Pair<String, List<Chunk>> {
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return readingCoachPreferredChunkSetId to emptyList()
        val candidates = listOf(readingCoachPreferredChunkSetId, lesson.defaultChunkSetId, "short", "sentence")
            .filter { it.isNotBlank() && it != "natural" }
            .distinct()
        candidates.forEach { id ->
            val chunks = sentence.chunkSets[id].orEmpty()
            if (chunks.isNotEmpty()) return id to chunks
        }
        return readingCoachPreferredChunkSetId to emptyList()
    }

    private fun updateReadingCoachSelection(lesson: Lesson, sentenceIndex: Int, chunkIndex: Int): Chunk? {
        val (setId, chunks) = coachChunkSource(lesson, sentenceIndex)
        val chunk = chunks.getOrNull(chunkIndex) ?: return null
        readingCoachChunkSetId = setId
        activeMode = setId
        readingCoachSentenceIndex = sentenceIndex
        readingCoachChunkIndex = chunkIndex
        currentSentenceIndex = sentenceIndex
        selectedSentenceIndex = sentenceIndex
        currentChunkId = chunk.id
        pendingStartMs = chunk.startMs
        return chunk
    }

    private fun currentCoachPosition(lesson: Lesson): Pair<Int, Int>? {
        val chunks = coachChunksFor(lesson, readingCoachSentenceIndex)
        if (readingCoachChunkIndex in chunks.indices) return readingCoachSentenceIndex to readingCoachChunkIndex
        return firstCoachPosition(lesson, readingCoachSentenceIndex)
    }

    private fun firstCoachPosition(lesson: Lesson, startSentenceIndex: Int): Pair<Int, Int>? {
        if (flatSentences.isEmpty()) return null
        val start = startSentenceIndex.coerceIn(0, flatSentences.lastIndex)
        for (index in start..flatSentences.lastIndex) {
            val chunks = coachChunksFor(lesson, index)
            if (chunks.isNotEmpty()) return index to 0
        }
        return null
    }

    private fun coachMarker(label: String): String {
        return "SEOIN_COACH_${System.currentTimeMillis()}_${label}_${(1000..9999).random()}"
    }

    private fun exitManualBodyModeInCurrentReader(lesson: Lesson) {
        stopAllPlayback()
        manualBodyMode = false
        clearManualBodyDraft()
        activeMode = "natural"
        useTts = shouldUseTtsForMode(activeMode, lesson)
        currentChunkId = null
        readerScroll?.setOnTouchListener(null)
        paragraphBindings.forEach { binding ->
            binding.textView.setOnTouchListener(paragraphGesture(binding))
        }
        installReaderControls(lesson)
        refreshAllParagraphs()
        refreshMode()
        updatePlayIcon()
    }

    private fun manualPracticeSentenceIndices(lesson: Lesson): List<Int> {
        if (flatSentences.isEmpty()) return emptyList()
        val titleIndex = flatSentences.indexOfFirst {
            it.sentence.text.trim().equals(lesson.title.trim(), ignoreCase = true)
        }
        val startIndex = if (titleIndex >= 0) titleIndex + 1 else 0
        val explicitOn = flatSentences.mapIndexedNotNull { index, ref ->
            if (index < startIndex) return@mapIndexedNotNull null
            if (ref.sentence.chunkActivity == "on") index else null
        }
        val autoCandidates = flatSentences.mapIndexedNotNull { index, ref ->
            if (index < startIndex) return@mapIndexedNotNull null
            if (ref.sentence.chunkActivity != "auto") return@mapIndexedNotNull null
            val value = ref.sentence.text.trim()
            val wordCount = wordTokens(value).size
            val isHeading = value.isNotBlank() && value.all { !it.isLetter() || it.isUpperCase() }
            if (wordCount < 5 || isHeading) null else index to wordCount
        }
        if (explicitOn.isEmpty() && autoCandidates.isEmpty()) return emptyList()
        val fillCount = max(0, 5 - explicitOn.size)
        val fill = autoCandidates
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
            .filterNot { it.first in explicitOn }
            .take(fillCount)
            .map { it.first }
        return (explicitOn + fill).distinct().sorted()
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
        val manualPracticeIndices = if (manualMode) manualPracticeSentenceIndices(lesson) else emptyList()
        if (manualMode && manualPracticeIndices.isEmpty()) {
            showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
            return
        }
        manualBodyMode = manualMode
        clearManualBodyDraft()
        activeMode = if (manualBodyMode) "sentence" else "natural"
        useTts = manualBodyMode || shouldUseTtsForMode(activeMode, lesson)
        val requestedSentenceIndex = startSentenceIndex.coerceIn(0, max(0, flatSentences.lastIndex))
        val initialSentenceIndex = when {
            manualBodyMode -> if (requestedSentenceIndex in manualPracticeIndices) requestedSentenceIndex else manualPracticeIndices.first()
            else -> requestedSentenceIndex
        }
        selectedSentenceIndex = initialSentenceIndex
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
        page.addView(modeBar(lesson), matchWrap().withBottom(dp(10)))
        page.addView(progressBar(), matchWrap().withBottom(dp(10)))
        if (manualBodyMode) {
            val practicePosition = manualPracticeIndices.indexOf(manualSentenceIndex).takeIf { it >= 0 } ?: 0
            page.addView(text("청크 활동 ${practicePosition + 1}/${manualPracticeIndices.size}. 진한 문장에서 청크의 마지막 단어를 탭하세요.", 15f, color(R.color.skin_muted)).apply {
                setPadding(0, 0, 0, dp(8))
            }, matchWrap())
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
        val actionHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        readerActionHost = actionHost
        page.addView(actionHost, matchWrap())
        when {
            manualBodyMode -> installManualBodyControls(lesson)
            else -> installReaderControls(lesson)
        }
        seekBar?.max = max(1, editableDurationMs())

        if (!manualBodyMode && lessonHasAudio(lesson)) preparePlayer(lesson) else handler.post(tick)
        refreshAllParagraphs()
        refreshMode()
        if (manualBodyMode) scrollToSentence(manualSentenceIndex)
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
                    paragraph.type == "quiz" -> 17f
                    else -> masterSettings.readerTextSizeSp
                }
                setTextColor(color(R.color.skin_ink))
                includeFontPadding = true
                letterSpacing = masterSettings.readerLetterSpacing
                setLineSpacing(masterSettings.readerLineSpacingDp * resources.displayMetrics.density, 1.08f)
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
        if (manualBodyMode) {
            return View.OnTouchListener { _, event ->
                if (unknownUnderlineMode) {
                    handleUnknownUnderlineTouch(binding, event)
                    return@OnTouchListener true
                }
                handleManualBodyChunkTouch(binding, event)
            }
        }
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val offset = offsetFor(binding.textView, e) ?: return true
                val sentenceIndex = binding.sentenceAt(offset) ?: return true
                if (activeComprehensionCheck != null) {
                    handleComprehensionChunkTap(binding, sentenceIndex, offset)
                    return true
                }
                val lesson = currentLesson
                val localOffset = binding.localOffset(sentenceIndex, offset)
                val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence
                if (lesson != null && localOffset != null && sentence != null && speakTodayWordAt(sentence.text, localOffset, lesson, lesson.cinematicComic)) {
                    return true
                }
                if (activeMode != "natural") {
                    playChunkAtOffset(sentenceIndex, binding.localOffset(sentenceIndex, offset))
                } else {
                    playSentenceOnce(sentenceIndex)
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (activeComprehensionCheck != null) return true
                val offset = offsetFor(binding.textView, e) ?: return true
                val sentenceIndex = binding.sentenceAt(offset) ?: return true
                selectedSentenceIndex = sentenceIndex
                val sentence = flatSentences[sentenceIndex].sentence
                showSentencePopup(sentence)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (activeComprehensionCheck != null) return
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
            if (activeComprehensionCheck != null) updateComprehensionPress(binding, event)
            detector.onTouchEvent(event)
            true
        }
    }

    private fun updateComprehensionPress(binding: ParagraphBinding, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val offset = offsetFor(binding.textView, event)
                val sentenceIndex = offset?.let { binding.sentenceAt(it) }
                val option = if (offset != null && sentenceIndex != null) comprehensionOptionAt(binding, sentenceIndex, offset) else null
                if (option != null && sentenceIndex != null) {
                    if (currentSentenceIndex != sentenceIndex || currentChunkId != option.chunkId) {
                        currentSentenceIndex = sentenceIndex
                        currentChunkId = option.chunkId
                        refreshAllParagraphs()
                    }
                } else if (currentChunkId != null) {
                    currentChunkId = null
                    refreshAllParagraphs()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (currentChunkId != null) {
                    currentChunkId = null
                    refreshAllParagraphs()
                }
            }
        }
    }

    private fun comprehensionOptionAt(binding: ParagraphBinding, sentenceIndex: Int, paragraphOffset: Int): ComprehensionOption? {
        val lesson = currentLesson ?: return null
        val check = activeComprehensionCheck ?: return null
        val localOffset = binding.localOffset(sentenceIndex, paragraphOffset) ?: return null
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return null
        val chunkSetId = check.chunkSetId.ifBlank { lesson.defaultChunkSetId.ifBlank { "short" } }
        return comprehensionSelectionAt(sentence, chunkSetId, localOffset)?.second
    }

    private fun comprehensionSelectionAt(
        sentence: LessonSentence,
        chunkSetId: String,
        localOffset: Int
    ): Pair<Chunk, ComprehensionOption>? {
        val check = activeComprehensionCheck ?: return null
        val optionByChunk = activeComprehensionOptions
            .filter { it.sentenceId == sentence.id }
            .associateBy { it.chunkId }
        val matches = sentence.chunkSets[chunkSetId].orEmpty()
            .filter { localOffset in it.startChar until it.endChar }
            .mapNotNull { chunk -> optionByChunk[chunk.id]?.let { option -> chunk to option } }
        if (matches.isEmpty()) return null
        return matches.firstOrNull { (chunk, _) -> chunk.id == check.answerChunkId }
            ?: matches.minByOrNull { (chunk, _) -> chunk.endChar - chunk.startChar }
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

    private fun handleManualBodyChunkTouch(binding: ParagraphBinding, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val offset = offsetFor(binding.textView, event) ?: return false
                if (!beginManualBodyChunkDraft(binding, offset)) return false
                binding.textView.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (manualBodyDraftSentenceIndex == null) return false
                offsetFor(binding.textView, event)?.let { updateManualBodyChunkDraft(binding, it) }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (manualBodyDraftSentenceIndex == null) return false
                offsetFor(binding.textView, event)?.let { updateManualBodyChunkDraft(binding, it) }
                commitManualBodyChunkDraft()
                binding.textView.parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (manualBodyDraftSentenceIndex != null) {
                    clearManualBodyDraft()
                    refreshAllParagraphs()
                }
                binding.textView.parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun beginManualBodyChunkDraft(binding: ParagraphBinding, offset: Int): Boolean {
        val lesson = currentLesson ?: return false
        val sentenceIndex = binding.sentenceAt(offset) ?: return false
        if (sentenceIndex != manualSentenceIndex) return false
        val localOffset = binding.localOffset(sentenceIndex, offset) ?: return false
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return false
        val tokens = wordTokens(sentence.text)
        if (tokens.isEmpty()) return false
        val wordIndex = wordIndexAt(tokens, localOffset) ?: return false
        val chunks = loadManualChunks(lesson.id, sentence.id)
        val editingIndex = chunks.indexOfFirst { wordIndex in it.startWord..it.endWord }.takeIf { it >= 0 }
        val draftStart = editingIndex?.let { chunks[it].startWord } ?: ((chunks.maxOfOrNull { it.endWord } ?: -1) + 1)
        if (draftStart !in tokens.indices) return false
        val initialEnd = editingIndex?.let { chunks[it].endWord } ?: wordIndex

        manualBodyDraftSentenceIndex = sentenceIndex
        manualBodyDraftStartWord = draftStart
        manualBodyDraftEndWord = max(draftStart, initialEnd).coerceAtMost(tokens.lastIndex)
        manualBodyEditingChunkIndex = editingIndex
        selectedSentenceIndex = sentenceIndex
        currentSentenceIndex = sentenceIndex
        pendingStartMs = sentence.startMs
        refreshAllParagraphs()
        return true
    }

    private fun updateManualBodyChunkDraft(binding: ParagraphBinding, offset: Int) {
        val sentenceIndex = manualBodyDraftSentenceIndex ?: return
        if (binding.sentenceAt(offset) != sentenceIndex) return
        val localOffset = binding.localOffset(sentenceIndex, offset) ?: return
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: return
        val tokens = wordTokens(sentence.text)
        val wordIndex = wordIndexAt(tokens, localOffset) ?: return
        val nextEnd = max(manualBodyDraftStartWord, wordIndex).coerceAtMost(tokens.lastIndex)
        if (nextEnd != manualBodyDraftEndWord) {
            manualBodyDraftEndWord = nextEnd
            refreshAllParagraphs()
        }
    }

    private fun commitManualBodyChunkDraft() {
        val lesson = currentLesson ?: run {
            clearManualBodyDraft()
            return
        }
        val sentenceIndex = manualBodyDraftSentenceIndex ?: return
        val sentence = flatSentences.getOrNull(sentenceIndex)?.sentence ?: run {
            clearManualBodyDraft()
            return
        }
        val tokens = wordTokens(sentence.text)
        if (tokens.isEmpty() || manualBodyDraftStartWord !in tokens.indices || manualBodyDraftEndWord !in tokens.indices) {
            clearManualBodyDraft()
            refreshAllParagraphs()
            return
        }

        val chunks = loadManualChunks(lesson.id, sentence.id).toMutableList()
        val edit = manualBodyEditingChunkIndex
        if (edit != null && edit in chunks.indices) {
            chunks[edit] = ManualChunk(chunks[edit].startWord, manualBodyDraftEndWord)
            fixFollowingManualBodyChunks(chunks, edit, tokens.lastIndex)
        } else {
            val nextStart = (chunks.maxOfOrNull { it.endWord } ?: -1) + 1
            if (manualBodyDraftStartWord == nextStart) {
                chunks.add(ManualChunk(manualBodyDraftStartWord, manualBodyDraftEndWord))
            }
        }

        val normalized = normalizeManualChunks(tokens, chunks)
        saveManualChunks(lesson.id, sentence.id, sentence.text, tokens, normalized)
        clearManualBodyDraft()
        refreshAllParagraphs()
        scrollToSentence(sentenceIndex)

        if ((normalized.maxOfOrNull { it.endWord } ?: -1) >= tokens.lastIndex) {
            toast("문장 청크 저장 완료")
            speakManualChunks(sentence.text, tokens, normalized)
        }
    }

    private fun fixFollowingManualBodyChunks(chunks: MutableList<ManualChunk>, fromIndex: Int, lastWordIndex: Int) {
        var previousEnd = chunks[fromIndex].endWord
        var index = fromIndex + 1
        while (index < chunks.size) {
            val start = previousEnd + 1
            if (start > lastWordIndex) {
                while (chunks.size > index) chunks.removeAt(chunks.lastIndex)
                break
            }
            val old = chunks[index]
            if (old.endWord < start) {
                chunks.removeAt(index)
                continue
            }
            val end = old.endWord.coerceAtMost(lastWordIndex)
            chunks[index] = ManualChunk(start, end)
            previousEnd = end
            index += 1
        }
    }

    private fun wordIndexAt(tokens: List<WordToken>, localOffset: Int): Int? {
        return tokens.indexOfFirst { localOffset in it.startChar until it.endChar }.takeIf { it >= 0 }
            ?: tokens.indexOfLast { it.startChar <= localOffset }.takeIf { it >= 0 }
    }

    private fun clearManualBodyDraft() {
        manualBodyDraftSentenceIndex = null
        manualBodyDraftStartWord = -1
        manualBodyDraftEndWord = -1
        manualBodyEditingChunkIndex = null
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
        val practiceIndices = manualPracticeSentenceIndices(lesson)
        val practicePosition = practiceIndices.indexOf(manualSentenceIndex).takeIf { it >= 0 } ?: 0
        val isLastPractice = practicePosition >= practiceIndices.lastIndex
        row.addView(pill(if (isLastPractice) "2차로" else "다음 문장").apply {
            setOnClickListener { moveManualBodySentence(lesson, 1) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).withRightMargin(dp(8)))
        row.addView(pill("본문으로").apply {
            background = rounded(color(R.color.skin_mark), dp(18), color(R.color.skin_primary), dp(1))
            setOnClickListener { exitManualBodyModeInCurrentReader(lesson) }
        }, LinearLayout.LayoutParams(0, dp(48), 0.9f))
        return row
    }

    private fun moveManualBodySentence(lesson: Lesson, delta: Int) {
        val practiceIndices = manualPracticeSentenceIndices(lesson)
        if (practiceIndices.isEmpty()) {
            showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
            return
        }
        val currentPosition = practiceIndices.indexOf(manualSentenceIndex).takeIf { it >= 0 } ?: 0
        val nextPosition = currentPosition + delta
        if (nextPosition > practiceIndices.lastIndex) {
            showSpeakListenStage(lesson, pass = 2, sentenceIndex = 0)
            return
        }
        manualSentenceIndex = practiceIndices[nextPosition.coerceAtLeast(0)]
        selectedSentenceIndex = manualSentenceIndex
        currentSentenceIndex = manualSentenceIndex
        pendingStartMs = flatSentences.getOrNull(manualSentenceIndex)?.sentence?.startMs
        clearManualBodyDraft()
        installManualBodyControls(lesson)
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

        startProfileView = boundaryProfileView(editsStart = true)
        endProfileView = boundaryProfileView(editsStart = false)
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

    private fun boundaryProfileView(editsStart: Boolean): BoundaryProfileView {
        return BoundaryProfileView(
            context = this,
            editsStart = editsStart,
            waveformOffsetMs = { waveformOffsetMs },
            activeAdjustSelection = { activeAdjustSelection() },
            setBoundaryToTime = { selection, start, requestedMs, preview ->
                setBoundaryToTime(selection, start, requestedMs, preview)
            },
            formatTimeMs = ::formatTimeMs
        )
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
        row.addView(text("단어 수가 긴 5문장을 원래 본문 화면에서 차례대로 청크로 나눠 볼 수 있어요.", 14f, color(R.color.skin_muted)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pill("청크 활동").apply {
            setOnClickListener { startManualChunkActivity(lesson) }
        }, fixed(dp(150), dp(42)))
        return row
    }

    private fun addMasterButton() {
        addChatGptAssistButton()

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

    private fun addChatGptAssistButton() {
        val button = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat)
            contentDescription = "ChatGPT 보조"
            background = rounded(color(R.color.skin_surface), dp(24), color(R.color.skin_primary), dp(1))
            elevation = dp(6).toFloat()
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { showChatGptAssistantDialog() }
        }
        val lp = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(18)
            bottomMargin = dp(90)
        }
        root.addView(button, lp)
    }

    private fun maybeShowChatGptLoginSetup() {
        if (settingsStore.isChatLoginChecked()) return
        if (isFinishing || isDestroyed) return
        showChatGptLoginSetupDialog()
    }

    private fun showChatGptLoginSetupDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
        }
        box.addView(text("ChatGPT 로그인 확인", 20f, color(R.color.skin_ink), Typeface.BOLD), matchWrap())
        box.addView(text("GPT 보이스를 쓰기 전에 ChatGPT 로그인과 마이크 권한을 먼저 확인해 둘게요.", 14f, color(R.color.skin_muted)).apply {
            setPadding(0, dp(8), 0, dp(14))
        }, matchWrap())
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dialog = AlertDialog.Builder(this)
            .setView(box)
            .create()
        actions.addView(pill("나중에").apply {
            textSize = 13f
            setOnClickListener {
                settingsStore.setChatLoginChecked(true)
                dialog.dismiss()
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).withRightMargin(dp(8)))
        actions.addView(pill("로그인 확인하기").apply {
            textSize = 13f
            setOnClickListener {
                settingsStore.setChatLoginChecked(true)
                dialog.dismiss()
                showChatGptAssistantDialog(loginSetup = true)
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1.35f))
        box.addView(actions, matchWrap())
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((resources.displayMetrics.widthPixels * 0.86f).roundToInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
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
        val kittyGame = CheckBox(this).apply { text = "단어게임모드2 Find the Kitty"; isChecked = active.vocabGameMode2Enabled }
        val choice = CheckBox(this).apply { text = "퀴즈는 사지선다"; isChecked = active.quizMode == "choice" }
        val manual = CheckBox(this).apply { text = "본문에서 직접 청크 모드"; isChecked = active.manualChunkEnabled }
        val alwaysNext = CheckBox(this).apply { text = "1차 다음 단계 항상 활성화"; isChecked = active.firstListenNextAlwaysEnabled }
        listOf(vocab, quiz, kittyGame, choice, manual, alwaysNext).forEach { box.addView(it, matchWrap()) }

        val firstPause = numberSettingInput("1차 문장 pause(초)", decimalSeconds(active.firstListenPauseMs), box)
        val firstRate = numberSettingInput("1차/말하기 TTS 속도", String.format(Locale.US, "%.2f", active.firstListenRate), box)
        val speakPause = numberSettingInput("2차 청크 pause(초)", decimalSeconds(active.speakChunkPauseMs), box)
        val readerTextSize = numberSettingInput("본문 글씨 크기(sp)", String.format(Locale.US, "%.1f", active.readerTextSizeSp), box)
        val readerLetterSpacing = numberSettingInput("본문 자간", String.format(Locale.US, "%.3f", active.readerLetterSpacing), box)
        val readerSpaceScale = numberSettingInput("띄어쓰기 폭 배율", String.format(Locale.US, "%.2f", active.readerSpaceScale), box)
        val readerLineSpacing = numberSettingInput("줄간격(dp)", String.format(Locale.US, "%.1f", active.readerLineSpacingDp), box)
        if (lesson?.vocabReflexGame != null) {
            box.addView(pill("단어 카드 로그 보기").apply {
                textSize = 14f
                setOnClickListener { showVocabReflexLogDialog(lesson) }
            }, matchWrap().withTop(dp(12)))
        }

        fun dialogSettings(): MasterSettings {
            return MasterSettings(
                vocabEnabled = vocab.isChecked,
                quizEnabled = quiz.isChecked,
                vocabGameMode2Enabled = kittyGame.isChecked,
                quizMode = if (choice.isChecked) "choice" else "speak",
                manualChunkEnabled = manual.isChecked,
                firstListenPauseMs = secondsInputMs(firstPause, 1500),
                firstListenRate = firstRate.text.toString().toFloatOrNull()?.coerceIn(0.5f, 1.5f) ?: 0.9f,
                speakChunkPauseMs = secondsInputMs(speakPause, 700),
                firstListenNextAlwaysEnabled = alwaysNext.isChecked,
                readerTextSizeSp = readerTextSize.text.toString().toFloatOrNull()?.coerceIn(14f, 32f) ?: 19f,
                readerLetterSpacing = readerLetterSpacing.text.toString().toFloatOrNull()?.coerceIn(0f, 0.18f) ?: 0f,
                readerSpaceScale = readerSpaceScale.text.toString().toFloatOrNull()?.coerceIn(0.7f, 2.4f) ?: 1f,
                readerLineSpacingDp = readerLineSpacing.text.toString().toFloatOrNull()?.coerceIn(0f, 24f) ?: 6f
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("진행 스킴")
            .setView(box)
            .setPositiveButton("전체 저장") { _, _ ->
                val settings = dialogSettings()
                saveMasterSettings("universal", settings)
                masterSettings = lesson?.let { activeMasterSettings(it.id) } ?: settings
                applyReaderTextSettings()
                toast("전체 설정 저장")
            }
            .setNegativeButton("닫기", null)
            .create()
        if (lesson != null) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "이 콘텐츠 저장") { _, _ ->
                val settings = dialogSettings()
                settingsStore.setMasterOverride(lesson.id, true)
                saveMasterSettings(lesson.id, settings)
                masterSettings = settings
                applyReaderTextSettings()
                toast("콘텐츠별 설정 저장")
            }
        }
        dialog.show()
    }

    private fun applyReaderTextSettings() {
        val lesson = currentLesson ?: return
        paragraphBindings.forEach { binding ->
            val paragraphType = lesson.paragraphs.getOrNull(binding.paragraphIndex)?.type
            binding.textView.textSize = if (paragraphType == "quiz") 17f else masterSettings.readerTextSizeSp
            binding.textView.letterSpacing = masterSettings.readerLetterSpacing
            binding.textView.setLineSpacing(masterSettings.readerLineSpacingDp * resources.displayMetrics.density, 1.08f)
        }
        refreshAllParagraphs()
    }

    private fun showVocabReflexLogDialog(lesson: Lesson) {
        val game = lesson.vocabReflexGame ?: return
        val body = game.targetWords.joinToString("\n\n") { target ->
            val stats = loadVocabReflexWordStats(lesson.id, target.wordId)
            buildString {
                append(target.word)
                append(" · ")
                append(if (stats.passed) "PASS" else "not yet")
                append("\n")
                append("avg ${stats.averageMs}ms, last ${stats.lastAverageMs}ms, attempts ${stats.attempts}")
                append("\n")
                append("wrong ${stats.wrong}, slow ${stats.slow}")
                if (stats.lastErrorTags.isNotBlank()) {
                    append("\nerrors: ")
                    append(stats.lastErrorTags)
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("단어 카드 로그")
            .setMessage(body.ifBlank { "아직 기록이 없어요." })
            .setPositiveButton("닫기", null)
            .setNegativeButton("초기화") { _, _ ->
                getSharedPreferences("vocab_reflex_logs", MODE_PRIVATE).edit().clear().apply()
                toast("단어 카드 로그 초기화")
            }
            .show()
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
        return if (settingsStore.hasMasterOverride(lessonId)) {
            loadMasterSettings(lessonId)
        } else {
            loadMasterSettings("universal")
        }
    }

    private fun loadMasterSettings(scope: String): MasterSettings {
        return settingsStore.loadMasterSettings(scope)
    }

    private fun saveMasterSettings(scope: String, settings: MasterSettings) {
        settingsStore.saveMasterSettings(scope, settings)
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

    private fun speakComicText(value: String, onDone: (() -> Unit)? = null) {
        ensureTts()
        tts?.setSpeechRate(0.68f)
        speakPopupText(value, onDone = {
            tts?.setSpeechRate(0.9f)
            onDone?.invoke()
        })
    }

    private fun stopTts() {
        tts?.stop()
        tts?.setSpeechRate(0.9f)
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
            val profile = runCatching { audioProfileDecoder.decode(lesson) }.getOrNull()
            runOnUiThread {
                if (currentLesson?.id == lesson.id) {
                    audioProfile = profile
                    updateAdjustPanel()
                }
            }
        }.start()
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
            (binding.textView as? WavyTextView)?.apply {
                setWavyRanges(wavyRangesFor(binding))
                setComprehensionBubbles(comprehensionBubblesFor(binding))
                val manualOverlay = manualChunkOverlaysFor(binding)
                setManualChunks(manualOverlay.first, manualOverlay.second)
            }
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

    private fun comprehensionBubblesFor(binding: ParagraphBinding): List<ComprehensionBubble> {
        val lesson = currentLesson ?: return emptyList()
        val check = activeComprehensionCheck ?: return emptyList()
        val chunkSetId = check.chunkSetId.ifBlank { lesson.defaultChunkSetId.ifBlank { "short" } }
        val bubbles = mutableListOf<ComprehensionBubble>()
        activeComprehensionOptions.forEachIndexed { optionIndex, option ->
            val range = binding.ranges.firstOrNull {
                flatSentences.getOrNull(it.sentenceIndex)?.sentence?.id == option.sentenceId
            } ?: return@forEachIndexed
            val sentence = flatSentences.getOrNull(range.sentenceIndex)?.sentence ?: return@forEachIndexed
            val chunk = sentence.chunkSets[chunkSetId].orEmpty().firstOrNull { it.id == option.chunkId } ?: return@forEachIndexed
            val start = range.start + chunk.startChar
            val end = min(range.start + chunk.endChar, range.end)
            if (start in 0 until end && end <= binding.text.length) {
                bubbles.add(
                    ComprehensionBubble(
                        range = start until end,
                        color = comprehensionBubbleColor(optionIndex),
                        pressed = range.sentenceIndex == currentSentenceIndex && chunk.id == currentChunkId
                    )
                )
            }
        }
        return bubbles
    }

    private fun comprehensionBubbleColor(index: Int): Int {
        return when (index % 5) {
            0 -> colorWithAlpha(color(R.color.skin_surface_alt), 235)
            1 -> colorWithAlpha(color(R.color.skin_mark), 220)
            2 -> colorWithAlpha(color(R.color.skin_highlight), 220)
            3 -> colorWithAlpha(color(R.color.skin_accent), 135)
            else -> colorWithAlpha(color(R.color.skin_primary), 55)
        }
    }

    private fun manualChunkOverlaysFor(binding: ParagraphBinding): Pair<List<ManualChunkBubble>, ManualChunkHint?> {
        val lesson = currentLesson ?: return emptyList<ManualChunkBubble>() to null
        if (!manualBodyMode) return emptyList<ManualChunkBubble>() to null
        val chunks = mutableListOf<ManualChunkBubble>()
        var hint: ManualChunkHint? = null
        binding.ranges.forEach { range ->
            if (range.sentenceIndex != manualSentenceIndex) return@forEach
            val sentence = flatSentences.getOrNull(range.sentenceIndex)?.sentence ?: return@forEach
            val tokens = wordTokens(sentence.text)
            if (tokens.isEmpty()) return@forEach
            val storedChunks = loadManualChunks(lesson.id, sentence.id)
            val draftActive = manualBodyDraftSentenceIndex == range.sentenceIndex &&
                manualBodyDraftStartWord in tokens.indices &&
                manualBodyDraftEndWord in tokens.indices
            val manualChunks = if (draftActive) {
                val preview = storedChunks.toMutableList()
                val edit = manualBodyEditingChunkIndex
                if (edit != null && edit in preview.indices) {
                    preview[edit] = ManualChunk(preview[edit].startWord, manualBodyDraftEndWord)
                    fixFollowingManualBodyChunks(preview, edit, tokens.lastIndex)
                } else {
                    val nextStart = (preview.maxOfOrNull { it.endWord } ?: -1) + 1
                    if (manualBodyDraftStartWord == nextStart) preview.add(ManualChunk(manualBodyDraftStartWord, manualBodyDraftEndWord))
                }
                normalizeManualChunks(tokens, preview)
            } else {
                storedChunks
            }
            val draftPreviewIndex = if (draftActive) {
                manualChunks.indexOfFirst { it.startWord == manualBodyDraftStartWord && it.endWord == manualBodyDraftEndWord }
                    .takeIf { it >= 0 }
                    ?: manualChunks.indexOfFirst { manualBodyDraftStartWord in it.startWord..it.endWord }
            } else {
                -1
            }
            manualChunks.forEachIndexed { index, chunk ->
                val startChar = tokens.getOrNull(chunk.startWord)?.startChar ?: return@forEachIndexed
                val endChar = tokens.getOrNull(chunk.endWord)?.endChar ?: return@forEachIndexed
                val start = (range.start + startChar).coerceIn(range.start, range.end)
                val end = (range.start + endChar).coerceIn(start, range.end)
                if (end > start && end <= binding.text.length) {
                    chunks.add(
                        ManualChunkBubble(
                            range = start until end,
                            color = if (index == draftPreviewIndex) colorWithAlpha(color(R.color.skin_accent), 180) else manualChunkColor(index),
                            boundary = !draftActive && chunk.endWord < tokens.lastIndex
                        )
                    )
                }
            }
            val nextStartWord = ((manualChunks.maxOfOrNull { it.endWord } ?: -1) + 1)
            if (!draftActive && nextStartWord in tokens.indices) {
                val nextToken = tokens[nextStartWord]
                val start = (range.start + nextToken.startChar).coerceIn(range.start, range.end)
                val end = (range.start + nextToken.endChar).coerceIn(start, range.end)
                if (end > start && end <= binding.text.length) hint = ManualChunkHint(start until end)
            }
        }
        return chunks to hint
    }

    private fun manualChunkColor(index: Int): Int {
        return when (index % 3) {
            0 -> colorWithAlpha(0xFF2563EB.toInt(), 96)
            1 -> colorWithAlpha(0xFF059669.toInt(), 100)
            else -> colorWithAlpha(0xFFDB2777.toInt(), 96)
        }
    }

    private fun styledParagraph(binding: ParagraphBinding): SpannableString {
        val span = SpannableString(binding.text)
        val lesson = currentLesson ?: return span
        binding.ranges.forEach { range ->
            val sentence = flatSentences[range.sentenceIndex].sentence
            if (activeComprehensionCheck == null && !manualBodyMode) {
                if (range.sentenceIndex == currentSentenceIndex) {
                    span.setSpan(BackgroundColorSpan(color(R.color.skin_highlight)), range.start, range.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (range.sentenceIndex == selectedSentenceIndex) {
                    span.setSpan(BackgroundColorSpan(color(R.color.skin_surface_alt)), range.start, range.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            if (manualBodyMode) {
                // Manual chunk visuals are drawn by WavyTextView overlays so text metrics never change.
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
            applyTodayWordSpans(span, sentence.text, range.start, range.end, lesson, lesson.cinematicComic)

            val currentChunk = sentence.chunkSets[activeMode].orEmpty().firstOrNull { it.id == currentChunkId }
            if (!manualBodyMode && activeComprehensionCheck == null && range.sentenceIndex == currentSentenceIndex && currentChunk != null) {
                val start = range.start + currentChunk.startChar
                val end = min(range.start + currentChunk.endChar, range.end)
                if (start in 0 until end && end <= span.length) {
                    span.setSpan(BackgroundColorSpan(color(R.color.skin_mark)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    span.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (manualBodyMode && range.sentenceIndex != manualSentenceIndex) {
                span.setSpan(ForegroundColorSpan(color(R.color.skin_muted)), range.start, range.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        applyReaderSpaceScale(span)
        return span
    }

    private fun applyReaderSpaceScale(span: SpannableString) {
        val scale = masterSettings.readerSpaceScale
        if (abs(scale - 1f) < 0.01f) return
        span.forEachIndexed { index, char ->
            if (char == ' ') {
                span.setSpan(ScaleXSpan(scale), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
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
            vocab?.easyEnglishLong?.takeIf { it.isNotBlank() }?.let { append("More: ").append(it).append("\n\n") }
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

    private fun injectCoachMessage(
        message: String,
        marker: String,
        attempt: Int,
        guardChunkToken: Long = readingCoachChunkToken,
        maxAttempts: Int = 3,
        visibleTimeoutMs: Long = 14_000L,
        sendAttempt: Int = 1,
        onDone: (Boolean) -> Unit
    ) {
        val webView = chatWebView ?: run {
            onDone(false)
            return
        }
        val token = readingCoachFlowToken
        bringChatGptDialogToForegroundForVoice("inject-start")
        logCoachWebViewProbe("inject-before-mic-close", marker)
        setCoachMicOpen(false) { micClosed ->
            Log.d("SeoinCoach", "coach inject mic closed=$micClosed marker=$marker attempt=$attempt sendAttempt=$sendAttempt")
            if (!readingCoachActive || token != readingCoachFlowToken || guardChunkToken != readingCoachChunkToken) return@setCoachMicOpen
            logCoachWebViewProbe("inject-after-mic-close", marker)
            requestCoachKeyboardForNudge(webView, "inject-before-draft")
            handler.postDelayed({
                if (!readingCoachActive || token != readingCoachFlowToken || guardChunkToken != readingCoachChunkToken) return@postDelayed
                val text = "$message\n\nInternal marker: $marker. Do not speak the marker."
                val draftText = text.removeSuffix(".")
                logCoachWebViewProbe("inject-before-eval", marker)
                chatUserMessageCount { beforeCount ->
                    if (!readingCoachActive || token != readingCoachFlowToken || guardChunkToken != readingCoachChunkToken) return@chatUserMessageCount
                    webView.evaluateJavascript(buildCoachInjectionScript(draftText, clickSend = false)) { result ->
                    val step = parseCoachInjectionStep(result)
                    Log.d(
                        "SeoinCoach",
                        "coach inject step marker=$marker attempt=$attempt sendAttempt=$sendAttempt " +
                            "ok=${step?.ok} draft=${step?.draftWritten} button=${step?.buttonReady} " +
                            "clicked=${step?.sendClicked} composerChars=${step?.composerLength} " +
                            "reason=${step?.reason ?: jsStringValue(result).take(180)} before=$beforeCount"
                    )
                    if (!readingCoachActive || token != readingCoachFlowToken || guardChunkToken != readingCoachChunkToken) return@evaluateJavascript
                    logCoachWebViewProbe("inject-after-step", marker)
                    if (step?.draftWritten == true) {
                        sendCoachKeyboardPeriodThenSend(webView, marker, guardChunkToken) { sentStep ->
                            Log.d(
                                "SeoinCoach",
                                "coach keyboard-period send marker=$marker ok=${sentStep?.ok} " +
                                    "button=${sentStep?.buttonReady} clicked=${sentStep?.sendClicked} " +
                                    "composerChars=${sentStep?.composerLength} reason=${sentStep?.reason}"
                            )
                            if (!readingCoachActive || token != readingCoachFlowToken || guardChunkToken != readingCoachChunkToken) return@sendCoachKeyboardPeriodThenSend
                            if (sentStep?.sendClicked != true && sentStep?.ok != true) {
                                if (sendAttempt < 12) {
                                    handler.postDelayed({
                                        if (readingCoachActive && token == readingCoachFlowToken && guardChunkToken == readingCoachChunkToken) {
                                            injectCoachMessage(
                                                message = message,
                                                marker = marker,
                                                attempt = attempt,
                                                guardChunkToken = guardChunkToken,
                                                maxAttempts = maxAttempts,
                                                visibleTimeoutMs = visibleTimeoutMs,
                                                sendAttempt = sendAttempt + 1,
                                                onDone = onDone
                                            )
                                        }
                                    }, 120L)
                                } else {
                                    onDone(false)
                                }
                                return@sendCoachKeyboardPeriodThenSend
                            }
                            logCoachWebViewProbe("inject-after-send-click", marker)
                        confirmCoachUserBubble(marker, beforeCount, startedAtMs = System.currentTimeMillis(), pollCount = 0, guardChunkToken = guardChunkToken, timeoutMs = visibleTimeoutMs, allowSpaceNudge = true) { visible ->
                            Log.d("SeoinCoach", "coach inject visible marker=$marker visible=$visible")
                            if (!visible && attempt < maxAttempts) {
                                handler.postDelayed({
                                    if (readingCoachActive && token == readingCoachFlowToken && guardChunkToken == readingCoachChunkToken) {
                                        injectCoachMessage(
                                            message = message,
                                            marker = marker,
                                            attempt = attempt + 1,
                                            guardChunkToken = guardChunkToken,
                                            maxAttempts = maxAttempts,
                                            visibleTimeoutMs = visibleTimeoutMs,
                                            sendAttempt = 1,
                                            onDone = onDone
                                        )
                                    }
                                }, 1500L)
                            } else {
                                onDone(visible)
                            }
                        }
                        }
                    } else if (sendAttempt < 12) {
                        handler.postDelayed({
                            if (readingCoachActive && token == readingCoachFlowToken && guardChunkToken == readingCoachChunkToken) {
                                injectCoachMessage(
                                    message = message,
                                    marker = marker,
                                    attempt = attempt,
                                    guardChunkToken = guardChunkToken,
                                    maxAttempts = maxAttempts,
                                    visibleTimeoutMs = visibleTimeoutMs,
                                    sendAttempt = sendAttempt + 1,
                                    onDone = onDone
                                )
                            }
                        }, 75L)
                    } else if (attempt < maxAttempts) {
                        handler.postDelayed({
                            if (readingCoachActive && token == readingCoachFlowToken && guardChunkToken == readingCoachChunkToken) {
                                injectCoachMessage(
                                    message = message,
                                    marker = marker,
                                    attempt = attempt + 1,
                                    guardChunkToken = guardChunkToken,
                                    maxAttempts = maxAttempts,
                                    visibleTimeoutMs = visibleTimeoutMs,
                                    sendAttempt = 1,
                                    onDone = onDone
                                )
                            }
                        }, 1500L)
                    } else {
                        onDone(false)
                    }
                }
            }
            }, 450L)
        }
    }

    private fun parseCoachInjectionStep(rawResult: String?): CoachInjectionStepResult? {
        val clean = jsStringValue(rawResult).ifBlank { rawResult.orEmpty() }.trim()
        return runCatching {
            val json = JSONObject(clean)
            CoachInjectionStepResult(
                ok = json.optBoolean("ok"),
                draftWritten = json.optBoolean("draftWritten"),
                buttonReady = json.optBoolean("buttonReady"),
                sendClicked = json.optBoolean("sendClicked"),
                composerLength = json.optInt("composerLength", 0),
                reason = json.optString("reason"),
            )
        }.getOrNull()
    }

    private fun sendCoachKeyboardPeriodThenSend(
        webView: WebView,
        marker: String,
        guardChunkToken: Long,
        onDone: (CoachInjectionStepResult?) -> Unit
    ) {
        if (!readingCoachActive || guardChunkToken != readingCoachChunkToken) {
            onDone(null)
            return
        }
        requestCoachKeyboardForNudge(webView, "before-key-period")
        logCoachWebViewProbe("before-key-period", marker)
        webView.evaluateJavascript(buildCoachComposerNudgeScript()) { focusResult ->
            Log.d("SeoinCoach", "coach period focus result=${jsStringValue(focusResult)}")
            handler.postDelayed({
                if (!readingCoachActive || guardChunkToken != readingCoachChunkToken) return@postDelayed
                requestCoachKeyboardForNudge(webView, "key-period")
                val downPeriod = webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PERIOD))
                val upPeriod = webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PERIOD))
                Log.d("SeoinCoach", "coach keyboard period down=$downPeriod up=$upPeriod marker=$marker")
                logCoachWebViewProbe("after-key-period", marker)
                handler.postDelayed({
                    if (!readingCoachActive || guardChunkToken != readingCoachChunkToken) return@postDelayed
                    webView.evaluateJavascript(buildCoachClickSendScript(requireTrailingPeriod = true)) { sendResult ->
                        val step = parseCoachInjectionStep(sendResult)
                        Log.d("SeoinCoach", "coach click send after period result=${jsStringValue(sendResult)}")
                        onDone(step)
                    }
                }, 320L)
            }, 450L)
        }
    }

    private fun scheduleCoachComposerNudge(marker: String, guardChunkToken: Long) {
        val token = readingCoachFlowToken
        val nudgeDelayMs = 3500L
        Log.d("SeoinCoach", "coach composer nudge scheduled marker=$marker delayMs=$nudgeDelayMs")
        handler.postDelayed({
            if (!readingCoachActive || token != readingCoachFlowToken || guardChunkToken != readingCoachChunkToken) return@postDelayed
            val webView = chatWebView ?: return@postDelayed
            Log.d("SeoinCoach", "coach composer nudge start marker=$marker")
            requestCoachKeyboardForNudge(webView, "before-nudge")
            logCoachWebViewProbe("nudge-before", marker)
            webView.evaluateJavascript(buildCoachComposerNudgeScript()) { result ->
                Log.d("SeoinCoach", "coach composer focus nudge result=${jsStringValue(result)}")
                handler.postDelayed({
                    if (!readingCoachActive || token != readingCoachFlowToken || guardChunkToken != readingCoachChunkToken) return@postDelayed
                    sendCoachAndroidKeyNudge(webView, marker, guardChunkToken)
                }, 700L)
            }
        }, nudgeDelayMs)
    }

    private fun sendCoachAndroidKeyNudge(webView: WebView, marker: String, guardChunkToken: Long, onDone: ((Boolean) -> Unit)? = null) {
        if (!readingCoachActive || guardChunkToken != readingCoachChunkToken) {
            onDone?.invoke(false)
            return
        }
        requestCoachKeyboardForNudge(webView, "android-key-nudge")
        val downSpace = webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
        val upSpace = webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))
        val success = downSpace || upSpace
        Log.d("SeoinCoach", "coach android key nudge SPACE down=$downSpace up=$upSpace marker=$marker")
        logCoachWebViewProbe("nudge-after-key-space", marker)
        Log.d("SeoinCoach", "coach android key nudge kept space input marker=$marker success=$success")
        onDone?.invoke(success)
    }

    private fun requestCoachKeyboardForNudge(webView: WebView, reason: String) {
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        webView.post {
            val shown = (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT) ?: false
            Log.d("SeoinCoach", "coach keyboard nudge reason=$reason shown=$shown")
        }
    }

    private fun buildCoachComposerNudgeScript(): String = """
        (function() {
          const result = {
            ok: false,
            reason: "",
            beforeLength: 0
          };
          const isVisible = function(el) {
            if (!el) return false;
            const rect = el.getBoundingClientRect();
            const style = window.getComputedStyle(el);
            return rect.width > 0 && rect.height > 0 &&
              style.visibility !== "hidden" &&
              style.display !== "none";
          };
          const isEditable = function(input) {
            return input.isContentEditable || input.getAttribute("contenteditable") === "true";
          };
          const readInput = function(input) {
            if (!input) return "";
            if (isEditable(input)) return input.textContent || "";
            return input.value || "";
          };
          const inputs = Array.from(document.querySelectorAll("#prompt-textarea, [data-testid='prompt-textarea'], textarea, div[contenteditable='true'], [contenteditable='true'], .ProseMirror"))
            .filter(function(el) { return isVisible(el) && !el.closest("[aria-hidden='true']"); });
          const input = inputs.find(function(el) {
            return el.id === "prompt-textarea" || el.getAttribute("data-testid") === "prompt-textarea";
          }) || inputs[0];
          if (!input) {
            result.reason = "no-input";
            return JSON.stringify(result);
          }
          const before = readInput(input);
          result.beforeLength = before.length;
          input.focus();
          if (isEditable(input)) {
            try {
              const selection = window.getSelection();
              const range = document.createRange();
              range.selectNodeContents(input);
              range.collapse(false);
              if (selection) {
                selection.removeAllRanges();
                selection.addRange(range);
              }
            } catch (e) {
              result.reason = "focus-selection-fallback";
            }
          }
          result.ok = true;
          if (!result.reason) result.reason = "focused";
          return JSON.stringify(result);
        })();
    """.trimIndent()

    private fun logCoachWebViewProbe(label: String, marker: String? = null) {
        val webView = chatWebView ?: return
        val labelLiteral = JSONObject.quote(label)
        val markerLiteral = marker?.let { JSONObject.quote(it) } ?: "null"
        val script = """
            (function() {
              const label = $labelLiteral;
              const marker = $markerLiteral;
              const isVisible = function(el) {
                if (!el) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 &&
                  style.visibility !== "hidden" &&
                  style.display !== "none";
              };
              const labelOf = function(el) {
                if (!el) return "";
                return [
                  el.getAttribute("aria-label"),
                  el.getAttribute("data-testid"),
                  el.getAttribute("title"),
                  el.textContent
                ].filter(Boolean).join(" ").replace(/\s+/g, " ").trim().slice(0, 120);
              };
              const readInput = function(input) {
                if (!input) return "";
                if (input.isContentEditable || input.getAttribute("contenteditable") === "true") {
                  return input.textContent || "";
                }
                return input.value || "";
              };
              const active = document.activeElement;
              const composers = Array.from(document.querySelectorAll("#prompt-textarea, [data-testid='prompt-textarea'], textarea, div[contenteditable='true'], [contenteditable='true'], .ProseMirror"));
              const visibleComposers = composers.filter(function(el) { return isVisible(el) && !el.closest("[aria-hidden='true']"); });
              const composerText = visibleComposers.map(readInput).join("\n");
              const userNodes = Array.from(document.querySelectorAll("[data-message-author-role='user']"));
              const assistantNodes = Array.from(document.querySelectorAll("[data-message-author-role='assistant']"));
              const lastUser = userNodes.length ? (userNodes[userNodes.length - 1].innerText || userNodes[userNodes.length - 1].textContent || "") : "";
              const lastAssistant = assistantNodes.length ? (assistantNodes[assistantNodes.length - 1].innerText || assistantNodes[assistantNodes.length - 1].textContent || "") : "";
              const buttons = Array.from(document.querySelectorAll("button, [role='button']")).filter(isVisible).map(labelOf).filter(Boolean).slice(0, 12);
              return JSON.stringify({
                label: label,
                hasFocus: document.hasFocus(),
                visibilityState: document.visibilityState,
                hidden: document.hidden,
                readyState: document.readyState,
                url: String(location.href).slice(0, 120),
                activeTag: active ? active.tagName : "",
                activeId: active ? (active.id || "") : "",
                activeTestId: active ? (active.getAttribute("data-testid") || "") : "",
                activeRole: active ? (active.getAttribute("role") || "") : "",
                activeLabel: labelOf(active),
                composerCount: composers.length,
                visibleComposerCount: visibleComposers.length,
                composerLength: composerText.length,
                composerHasMarker: !!(marker && composerText.indexOf(marker) >= 0),
                userMessages: userNodes.length,
                lastUserHasMarker: !!(marker && lastUser.indexOf(marker) >= 0),
                lastUserPreview: lastUser.replace(/\s+/g, " ").slice(-140),
                assistantMessages: assistantNodes.length,
                lastAssistantPreview: lastAssistant.replace(/\s+/g, " ").slice(-140),
                buttons: buttons.join(" | ")
              });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            Log.d("SeoinCoach", "web probe ${jsStringValue(result)}")
        }
    }

    private fun buildCoachInjectionScript(message: String, clickSend: Boolean = true): String {
        val text = JSONObject.quote(message)
        val shouldClickSend = if (clickSend) "true" else "false"
        val inputSelector = JSONObject.quote(
            "#prompt-textarea, [data-testid='prompt-textarea'], textarea, div[contenteditable='true'], [contenteditable='true'], .ProseMirror"
        )
        val sendSelector = JSONObject.quote(
            "button[data-testid='send-button'], button[data-testid='composer-submit-button'], button[data-testid='composer-send-button'], button[aria-label='Send prompt'], button[aria-label='Send message'], button[aria-label='Send']"
        )
        return """
            (function() {
              const text = $text;
              const clickSend = $shouldClickSend;
              const selector = $inputSelector;
              const sendSelector = $sendSelector;
              const result = {
                ok: false,
                draftWritten: false,
                buttonReady: false,
                sendClicked: false,
                composerLength: 0,
                reason: ""
              };
              const isEditable = function(input) {
                return input.isContentEditable || input.getAttribute("contenteditable") === "true";
              };
              const setNativeValue = function(input, value) {
                const proto = input.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                const descriptor = Object.getOwnPropertyDescriptor(proto, "value");
                if (descriptor && descriptor.set) {
                  descriptor.set.call(input, value);
                } else {
                  input.value = value;
                }
              };
              const clearInput = function(input) {
                if (isEditable(input)) {
                  input.textContent = "";
                } else {
                  setNativeValue(input, "");
                }
                input.dispatchEvent(new InputEvent("input", { bubbles: true, inputType: "deleteContentBackward", data: null }));
                input.dispatchEvent(new Event("change", { bubbles: true }));
              };
              const readInput = function(input) {
                if (isEditable(input)) {
                  return input.textContent || "";
                }
                return input.value || "";
              };
              const isVisible = function(el) {
                if (!el) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 && style.visibility !== "hidden" && style.display !== "none";
              };
              const labelOf = function(el) {
                return [
                  el.getAttribute("aria-label"),
                  el.getAttribute("data-testid"),
                  el.getAttribute("title"),
                  el.textContent
                ].filter(Boolean).join(" ").toLowerCase();
              };
              const inputs = Array.from(document.querySelectorAll(selector))
                .filter(function(el) { return isVisible(el) && !el.closest("[aria-hidden='true']"); });
              const input = inputs.find(function(el) {
                return el.id === "prompt-textarea" || el.getAttribute("data-testid") === "prompt-textarea";
              }) || inputs[0];
              if (!input) {
                result.reason = "no-input";
                return JSON.stringify(result);
              }
              input.focus();

              const firstLine = text.split("\n").find(function(line) { return line.trim().length > 0; }) || text;
              const current = readInput(input);
              if (!current.includes(firstLine) || current.length < text.length) {
                clearInput(input);
                if (isEditable(input)) {
                  const selection = window.getSelection();
                  const range = document.createRange();
                  let inserted = false;
                  try {
                    range.selectNodeContents(input);
                    if (selection) {
                      selection.removeAllRanges();
                      selection.addRange(range);
                    }
                    inserted = document.execCommand("insertText", false, text);
                  } catch (e) {
                    inserted = false;
                  }
                  if (!inserted || !readInput(input).includes(firstLine)) {
                    input.textContent = text;
                  }
                } else {
                  setNativeValue(input, text);
                }
                input.dispatchEvent(new InputEvent("input", { bubbles: true, inputType: "insertText", data: text }));
                input.dispatchEvent(new Event("change", { bubbles: true }));
              }
              const written = readInput(input);
              result.composerLength = written.length;
              result.draftWritten = written.includes(firstLine);
              const findSendButton = function() {
                const direct = Array.from(document.querySelectorAll(sendSelector));
                const broad = Array.from(document.querySelectorAll("button, [role='button']")).filter(function(button) {
                  const label = labelOf(button);
                  return label.includes("send") ||
                    label.includes("submit") ||
                    label.includes("\uBCF4\uB0B4\uAE30") ||
                    label.includes("\uC804\uC1A1");
                });
                return direct.concat(broad).find(function(button) {
                  return isVisible(button) && !button.disabled && button.getAttribute("aria-disabled") !== "true";
                });
              };
              if (window.__trpgSendTimer) {
                clearTimeout(window.__trpgSendTimer);
                window.__trpgSendTimer = null;
              }
              if (!result.draftWritten) {
                result.reason = "draft-missing";
                return JSON.stringify(result);
              }
              if (!clickSend) {
                const readyButton = findSendButton();
                result.buttonReady = !!readyButton;
                result.ok = result.draftWritten;
                result.reason = readyButton ? "draft-ready" : "draft-ready-send-not-ready";
                return JSON.stringify(result);
              }
              const button = findSendButton();
              result.buttonReady = !!button;
              if (!button) {
                result.reason = "send-not-ready";
                return JSON.stringify(result);
              }
              button.click();
              result.ok = true;
              result.sendClicked = true;
              result.reason = "clicked";
              return JSON.stringify(result);
            })();
        """.trimIndent()
    }

    private fun buildCoachClickSendScript(requireTrailingPeriod: Boolean): String {
        val requirePeriod = if (requireTrailingPeriod) "true" else "false"
        val inputSelector = JSONObject.quote(
            "#prompt-textarea, [data-testid='prompt-textarea'], textarea, div[contenteditable='true'], [contenteditable='true'], .ProseMirror"
        )
        val sendSelector = JSONObject.quote(
            "button[data-testid='send-button'], button[data-testid='composer-submit-button'], button[data-testid='composer-send-button'], button[aria-label='Send prompt'], button[aria-label='Send message'], button[aria-label='Send']"
        )
        return """
            (function() {
              const requirePeriod = $requirePeriod;
              const selector = $inputSelector;
              const sendSelector = $sendSelector;
              const result = {
                ok: false,
                draftWritten: false,
                buttonReady: false,
                sendClicked: false,
                composerLength: 0,
                reason: ""
              };
              const isEditable = function(input) {
                return input && (input.isContentEditable || input.getAttribute("contenteditable") === "true");
              };
              const readInput = function(input) {
                if (!input) return "";
                if (isEditable(input)) return input.textContent || "";
                return input.value || "";
              };
              const isVisible = function(el) {
                if (!el) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 && style.visibility !== "hidden" && style.display !== "none";
              };
              const labelOf = function(el) {
                return [
                  el.getAttribute("aria-label"),
                  el.getAttribute("data-testid"),
                  el.getAttribute("title"),
                  el.textContent
                ].filter(Boolean).join(" ").toLowerCase();
              };
              const inputs = Array.from(document.querySelectorAll(selector))
                .filter(function(el) { return isVisible(el) && !el.closest("[aria-hidden='true']"); });
              const input = inputs.find(function(el) {
                return el.id === "prompt-textarea" || el.getAttribute("data-testid") === "prompt-textarea";
              }) || inputs[0];
              if (!input) {
                result.reason = "no-input";
                return JSON.stringify(result);
              }
              input.focus();
              const written = readInput(input);
              result.composerLength = written.length;
              result.draftWritten = written.trim().length > 0;
              if (requirePeriod && !written.trimEnd().endsWith(".")) {
                result.reason = "missing-keyboard-period";
                return JSON.stringify(result);
              }
              const direct = Array.from(document.querySelectorAll(sendSelector));
              const broad = Array.from(document.querySelectorAll("button, [role='button']")).filter(function(button) {
                const label = labelOf(button);
                return label.includes("send") ||
                  label.includes("submit") ||
                  label.includes("\uBCF4\uB0B4\uAE30") ||
                  label.includes("\uC804\uC1A1");
              });
              const button = direct.concat(broad).find(function(item) {
                return isVisible(item) && !item.disabled && item.getAttribute("aria-disabled") !== "true";
              });
              result.buttonReady = !!button;
              if (!button) {
                result.reason = "send-not-ready";
                return JSON.stringify(result);
              }
              button.click();
              result.ok = true;
              result.sendClicked = true;
              result.reason = "clicked";
              return JSON.stringify(result);
            })();
        """.trimIndent()
    }

    private fun chatUserMessageCount(onDone: (Int) -> Unit) {
        val webView = chatWebView ?: run {
            onDone(0)
            return
        }
        val script = """
            (function() {
              return String(document.querySelectorAll('[data-message-author-role="user"]').length);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            onDone(result.filter { it.isDigit() }.toIntOrNull() ?: 0)
        }
    }

    private fun confirmCoachUserBubble(
        marker: String,
        previousCount: Int,
        startedAtMs: Long,
        pollCount: Int,
        guardChunkToken: Long,
        timeoutMs: Long,
        allowSpaceNudge: Boolean = false,
        spaceNudgeSent: Boolean = false,
        onDone: (Boolean) -> Unit
    ) {
        val webView = chatWebView ?: run {
            onDone(false)
            return
        }
        val markerLiteral = JSONObject.quote(marker)
        val script = """
            (function() {
              const marker = $markerLiteral;
              const nodes = Array.from(document.querySelectorAll('[data-message-author-role="user"]'));
              const last = nodes.length ? (nodes[nodes.length - 1].innerText || '') : '';
              const visible = nodes.length >= ${previousCount + 1} && last.indexOf(marker) >= 0;
              return "count=" + nodes.length + ";visible=" + visible + ";last=" + encodeURIComponent(last.slice(-160));
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            if (!readingCoachActive || guardChunkToken != readingCoachChunkToken) return@evaluateJavascript
            val visible = result.contains("visible=true")
            val count = Regex("count=(\\d+)").find(result)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
            Log.d("SeoinCoach", "coach user-visible poll=$pollCount marker=$marker visible=$visible userMessages=$count")
            if (pollCount < 4 || pollCount % 10 == 0) {
                logCoachWebViewProbe("user-visible-poll-$pollCount", marker)
            }
            if (visible || System.currentTimeMillis() - startedAtMs >= timeoutMs) {
                onDone(visible)
            } else if (allowSpaceNudge && !spaceNudgeSent && pollCount >= 18) {
                Log.d("SeoinCoach", "coach user-visible space nudge trigger poll=$pollCount marker=$marker")
                sendCoachAndroidKeyNudge(webView, marker, guardChunkToken) { nudged ->
                    Log.d("SeoinCoach", "coach user-visible space nudge result=$nudged marker=$marker")
                    handler.postDelayed({
                        if (readingCoachActive && guardChunkToken == readingCoachChunkToken) {
                            confirmCoachUserBubble(
                                marker = marker,
                                previousCount = previousCount,
                                startedAtMs = startedAtMs,
                                pollCount = pollCount + 1,
                                guardChunkToken = guardChunkToken,
                                timeoutMs = timeoutMs,
                                allowSpaceNudge = allowSpaceNudge,
                                spaceNudgeSent = true,
                                onDone = onDone
                            )
                        }
                    }, 350L)
                }
            } else {
                handler.postDelayed({
                    if (readingCoachActive && guardChunkToken == readingCoachChunkToken) {
                        confirmCoachUserBubble(
                            marker = marker,
                            previousCount = previousCount,
                            startedAtMs = startedAtMs,
                            pollCount = pollCount + 1,
                            guardChunkToken = guardChunkToken,
                            timeoutMs = timeoutMs,
                            allowSpaceNudge = allowSpaceNudge,
                            spaceNudgeSent = spaceNudgeSent,
                            onDone = onDone
                        )
                    }
                }, 100L)
            }
        }
    }

    private fun waitForCoachTextCompleteAfter(token: Long, baseline: String, timeoutMs: Long, onDone: (String) -> Unit) {
        val start = System.currentTimeMillis()
        val baselineText = baseline.trim()
        fun poll(lastText: String, stableCount: Int) {
            if (!readingCoachActive || token != readingCoachChunkToken) return
            if (System.currentTimeMillis() - start > timeoutMs) {
                Log.d("SeoinCoach", "coach text complete reason=timeout-or-no-new-text")
                onDone(lastText.ifBlank { baselineText })
                return
            }
            readLastAssistantText { text ->
                if (!readingCoachActive || token != readingCoachChunkToken) return@readLastAssistantText
                val current = text.trim()
                val hasNewText = current.isNotBlank() && current != baselineText
                if (!hasNewText) {
                    handler.postDelayed({ poll(lastText, 0) }, 500L)
                    return@readLastAssistantText
                }
                val stable = current == lastText
                val nextStable = if (stable) stableCount + 1 else 0
                val punctuationDone = current.lastOrNull()?.let { it == '.' || it == '!' || it == '?' } == true
                if (nextStable >= 2 && (punctuationDone || nextStable >= 4)) {
                    Log.d("SeoinCoach", "coach text complete reason=${if (punctuationDone) "punctuation" else "stable"}")
                    onDone(current)
                } else {
                    handler.postDelayed({ poll(current, nextStable) }, 500L)
                }
            }
        }
        poll("", 0)
    }

    private fun waitForCoachTextComplete(token: Long, timeoutMs: Long, onDone: () -> Unit) {
        val start = System.currentTimeMillis()
        fun poll(lastText: String, stableCount: Int) {
            if (!readingCoachActive || token != readingCoachChunkToken) return
            if (System.currentTimeMillis() - start > timeoutMs) {
                Log.d("SeoinCoach", "coach text complete reason=timeout")
                onDone()
                return
            }
            readLastAssistantText { text ->
                if (!readingCoachActive || token != readingCoachChunkToken) return@readLastAssistantText
                val stable = text.isNotBlank() && text == lastText
                val nextStable = if (stable) stableCount + 1 else 0
                val punctuationDone = text.trim().lastOrNull()?.let { it == '.' || it == '!' || it == '?' } == true
                if (text.isNotBlank() && nextStable >= 2 && (punctuationDone || nextStable >= 4)) {
                    Log.d("SeoinCoach", "coach text complete reason=${if (punctuationDone) "punctuation" else "stable"}")
                    onDone()
                } else {
                    handler.postDelayed({ poll(text, nextStable) }, 500L)
                }
            }
        }
        poll("", 0)
    }

    private fun readLastAssistantText(onDone: (String) -> Unit) {
        val webView = chatWebView ?: run {
            onDone("")
            return
        }
        val script = """
            (function() {
              const nodes = Array.from(document.querySelectorAll('[data-message-author-role="assistant"]'));
              const last = nodes.length ? (nodes[nodes.length - 1].innerText || '') : '';
              return last.slice(-1000);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            onDone(jsStringValue(result))
        }
    }

    private fun waitForCoachMediaQuiet(token: Long, text: String, onDone: () -> Unit) {
        val start = System.currentTimeMillis()
        val fallbackFinishAtMs = start + estimatePostTextVoiceDelayMs(text)
        val stuckActiveFinishAtMs = fallbackFinishAtMs + 2_000L
        var sawMediaActivity = false
        var quietSinceMs = 0L
        var finished = false

        fun finish(reason: String) {
            if (finished || !readingCoachActive || token != readingCoachChunkToken) return
            finished = true
            cleanupChatMediaProbe()
            Log.d("SeoinCoach", "media gate wait finish reason=$reason")
            onDone()
        }

        fun poll() {
            if (!readingCoachActive || token != readingCoachChunkToken) return
            if (finished) return
            probeChatMediaPlayback { hasProbe, active ->
                if (!readingCoachActive || token != readingCoachChunkToken || finished) return@probeChatMediaPlayback
                val now = System.currentTimeMillis()
                val elapsedMs = now - start
                if (active) {
                    sawMediaActivity = true
                    quietSinceMs = 0L
                } else if (sawMediaActivity && quietSinceMs == 0L) {
                    quietSinceMs = now
                }
                val quietMs = if (quietSinceMs > 0L) now - quietSinceMs else 0L
                when {
                    sawMediaActivity && !active && quietMs >= 0L -> finish("media-quiet")
                    !hasProbe && elapsedMs >= 1_500L && now >= fallbackFinishAtMs -> finish("fallback-no-probe")
                    hasProbe && !sawMediaActivity && elapsedMs >= 1_500L && now >= fallbackFinishAtMs -> finish("fallback-delay")
                    hasProbe && active && elapsedMs >= 1_500L && now >= stuckActiveFinishAtMs -> finish("fallback-stuck-active")
                    elapsedMs >= 45_000L -> finish("max-wait")
                    else -> handler.postDelayed({ poll() }, 100L)
                }
            }
        }
        Log.d("SeoinCoach", "media gate wait start textWords=${text.split(Regex("\\s+")).filter { it.isNotBlank() }.size} fallbackMs=${fallbackFinishAtMs - start}")
        poll()
    }

    private fun estimatePostTextVoiceDelayMs(text: String): Long {
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return (1_500L + words * 460L).coerceIn(1_500L, 9_000L)
    }

    private fun probeChatMediaPlayback(onDone: (hasProbe: Boolean, active: Boolean) -> Unit) {
        val webView = chatWebView ?: run {
            onDone(false, false)
            return
        }
        webView.evaluateJavascript(buildMediaPlaybackProbeScript()) { result ->
            val clean = jsStringValue(result)
            val json = runCatching { JSONObject(clean) }.getOrNull()
            val hasProbe = json?.optBoolean("hasProbe", false) ?: false
            val recentActive = json?.optBoolean("recentActive", false) ?: false
            Log.d("SeoinCoach", "media probe hasProbe=$hasProbe active=$recentActive result=$clean")
            onDone(hasProbe, recentActive)
        }
    }

    private fun buildMediaPlaybackProbeScript(): String = """
            (function() {
              const now = Date.now();
              const state = window.__seoinMediaProbe || {
                hasProbe: false,
                sawActivity: false,
                lastActiveAt: 0,
                lastTimes: {},
                nextId: 1,
                observer: null
              };
              window.__seoinMediaProbe = state;

              const markActive = function() {
                state.sawActivity = true;
                state.lastActiveAt = Date.now();
              };

              const attach = function(item) {
                if (!item) return;
                state.hasProbe = true;
                if (!item.__seoinMediaProbeId) item.__seoinMediaProbeId = 'm' + (state.nextId++);
                if (item.__seoinMediaProbeAttached) return;
                item.__seoinMediaProbeAttached = true;
                ['play', 'playing', 'timeupdate', 'volumechange'].forEach(function(name) {
                  item.addEventListener(name, markActive, true);
                });
                ['pause', 'ended', 'stalled', 'suspend'].forEach(function(name) {
                  item.addEventListener(name, function() {}, true);
                });
              };

              const attachAll = function(root) {
                const media = Array.from((root || document).querySelectorAll ? (root || document).querySelectorAll('audio,video') : []);
                media.forEach(attach);
                return media;
              };

              const media = attachAll(document);
              const activeNow = media.some(function(item) {
                return !item.paused && !item.ended && item.readyState > 1;
              });
              media.forEach(function(item) {
                const id = item.__seoinMediaProbeId || 'unknown';
                const current = Number.isFinite(item.currentTime) ? item.currentTime : 0;
                const previous = state.lastTimes[id];
                if (typeof previous === 'number' && Math.abs(current - previous) > 0.015) {
                  markActive();
                }
                state.lastTimes[id] = current;
              });

              if (!state.observer && document.documentElement) {
                state.observer = new MutationObserver(function(mutations) {
                  mutations.forEach(function(mutation) {
                    Array.from(mutation.addedNodes || []).forEach(function(node) {
                      if (!node) return;
                      if (node.matches && node.matches('audio,video')) attach(node);
                      attachAll(node);
                    });
                  });
                });
                state.observer.observe(document.documentElement, { childList: true, subtree: true });
              }

              const ageMs = state.lastActiveAt ? now - state.lastActiveAt : 999999;
              return JSON.stringify({
                hasProbe: state.hasProbe,
                sawActivity: state.sawActivity,
                recentActive: ageMs <= 180,
                ageMs: ageMs,
                mediaCount: media.length,
                activeNow: activeNow
              });
            })();
    """.trimIndent()

    private fun cleanupChatMediaProbe() {
        val webView = chatWebView ?: return
        val script = """
            (function() {
              const state = window.__seoinMediaProbe;
              if (state && state.observer) {
                try { state.observer.disconnect(); } catch (e) {}
              }
              Array.from(document.querySelectorAll('audio,video')).forEach(function(item) {
                try {
                  delete item.__seoinMediaProbeAttached;
                  delete item.__seoinMediaProbeId;
                } catch (e) {
                  item.__seoinMediaProbeAttached = false;
                  item.__seoinMediaProbeId = null;
                }
              });
              window.__seoinMediaProbe = null;
              return 'CLEANED';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun setCoachMicOpen(open: Boolean, onDone: (Boolean) -> Unit) {
        val webView = chatWebView ?: run {
            onDone(false)
            return
        }
        val desiredLiteral = if (open) "true" else "false"
        val script = """
            (function() {
              const desiredOpen = $desiredLiteral;
              const isVisible = function(el) {
                if (!el) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 &&
                  style.visibility !== 'hidden' &&
                  style.display !== 'none';
              };
              const labelOf = function(el) {
                return [
                  el.getAttribute('aria-label'),
                  el.getAttribute('data-testid'),
                  el.getAttribute('title'),
                  el.textContent
                ].filter(Boolean).join(' ').trim().toLowerCase();
              };
              const classify = function(label) {
                const closeAction =
                  /mute|turn off microphone|disable microphone|\uB9C8\uC774\uD06C \uB044\uAE30|\uC74C\uC18C\uAC70/.test(label) &&
                  !/unmute|\uCF1C\uAE30|\uD574\uC81C/.test(label);
                const openAction =
                  /unmute|turn on microphone|enable microphone|\uB9C8\uC774\uD06C \uCF1C\uAE30|\uC74C\uC18C\uAC70 \uD574\uC81C/.test(label);
                if (closeAction) return true;
                if (openAction) return false;
                return null;
              };
              const candidates = Array.from(document.querySelectorAll('button, [role="button"]'))
                .filter(function(button) {
                  if (!isVisible(button) || button.disabled || button.getAttribute('aria-disabled') === 'true') return false;
                  const label = labelOf(button);
                  if (/end|close|hang up|leave|disconnect|\uC885\uB8CC|\uB05D\uB0B4\uAE30|\uB2EB\uAE30|\uB098\uAC00\uAE30/.test(label)) return false;
                  return /mic|microphone|mute|unmute|\uB9C8\uC774\uD06C|\uC74C\uC18C\uAC70/.test(label);
                })
                .map(function(button) {
                  const label = labelOf(button);
                  return { button: button, label: label, currentOpen: classify(label) };
                });
              const known = candidates.find(function(item) { return item.currentOpen !== null; });
              if (!known) {
                return JSON.stringify({ ok: false, clicked: false, reason: 'no-known-mic', desiredOpen: desiredOpen, labels: candidates.map(function(item) { return item.label; }).join(' | ').slice(0, 260) });
              }
              if (known.currentOpen === desiredOpen) {
                return JSON.stringify({ ok: true, clicked: false, already: true, currentOpen: known.currentOpen, desiredOpen: desiredOpen, label: known.label });
              }
              known.button.click();
              return JSON.stringify({ ok: true, clicked: true, already: false, currentOpen: known.currentOpen, desiredOpen: desiredOpen, label: known.label });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            val clean = jsStringValue(result)
            val ok = clean.contains("\"ok\":true")
            Log.d("SeoinCoach", "mic gate desiredOpen=$open ok=$ok result=$clean")
            onDone(ok)
        }
    }

    private fun closeChatVoiceSession(onDone: () -> Unit) {
        val webView = chatWebView ?: run {
            onDone()
            return
        }
        val script = """
            (function() {
              const buttons = Array.from(document.querySelectorAll('button')).reverse();
              const endButton = buttons.find(function(button) {
                const label = ((button.getAttribute('aria-label') || '') + ' ' + (button.getAttribute('title') || '') + ' ' + button.innerText).toLowerCase();
                return label.includes('end') ||
                  label.includes('close') ||
                  label.includes('hang up') ||
                  label.includes('leave') ||
                  label.includes('stop') ||
                  label.includes('\uC885\uB8CC') ||
                  label.includes('\uB2EB\uAE30') ||
                  label.includes('\uC911\uC9C0');
              });
              if (endButton && !endButton.disabled && endButton.getAttribute('aria-disabled') !== 'true') {
                endButton.click();
                return "VOICE_CLOSE_CLICKED";
              }
              return "NO_VOICE_CLOSE";
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            Log.d("SeoinCoach", "voice close before fallback result=$result")
            onDone()
        }
    }

    private fun setChatAudioDucked(ducked: Boolean) {
        val webView = chatWebView ?: return
        val targetVolume = if (ducked) 0.0 else 1.0
        val script = """
            (function() {
              window.__seoinDuckOriginals = window.__seoinDuckOriginals || new WeakMap();
              window.__seoinDuckActive = ${if (ducked) "true" else "false"};
              function applyDuck() {
                Array.from(document.querySelectorAll('audio,video')).forEach(function(media) {
                  if (window.__seoinDuckActive) {
                    if (!window.__seoinDuckOriginals.has(media)) {
                      window.__seoinDuckOriginals.set(media, { volume: media.volume, muted: media.muted });
                    }
                    media.volume = $targetVolume;
                  } else {
                    const original = window.__seoinDuckOriginals.get(media);
                    if (original) {
                      media.volume = original.volume;
                      media.muted = original.muted;
                    }
                  }
                });
              }
              if (!window.__seoinDuckObserver) {
                window.__seoinDuckObserver = new MutationObserver(applyDuck);
                window.__seoinDuckObserver.observe(document.documentElement, { childList: true, subtree: true });
              }
              applyDuck();
              return window.__seoinDuckActive ? "DUCKED" : "RESTORED";
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun jsStringValue(result: String?): String {
        if (result == null || result == "null") return ""
        return runCatching {
            JSONObject("{\"value\":$result}").optString("value", "")
        }.getOrDefault(result.trim('"'))
    }

    private fun showChatGptAssistantDialog(loginSetup: Boolean = false, startCompact: Boolean = false) {
        stopAllPlayback()
        destroyChatWebView()
        chatLoginSetupMode = loginSetup

        val status = text(
            if (loginSetup) "ChatGPT 로그인 화면을 불러오는 중..." else "ChatGPT를 불러오는 중...",
            12f,
            color(R.color.skin_muted)
        ).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        chatStatusLabel = status
        val compactHeight = dp(64)
        val popupHeight = when {
            startCompact -> compactHeight
            readingCoachActive -> (resources.displayMetrics.heightPixels * 0.50f).roundToInt()
            else -> (resources.displayMetrics.heightPixels * 0.88f).roundToInt()
        }
        val webHeight = if (startCompact) dp(12) else max(dp(240), popupHeight - dp(128))
        val webView = chatGptWebView(status)
        chatWebView = webView

        val overlay = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            elevation = dp(28).toFloat()
            setPadding(0, 0, 0, 0)
        }
        chatOverlay = overlay

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            background = rounded(color(R.color.skin_surface), if (startCompact) dp(10) else dp(18))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            minimumHeight = if (startCompact) 0 else popupHeight
        }
        chatDialogBox = box
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            background = rounded(color(R.color.skin_surface_alt), dp(18))
        }
        top.addView(text(if (loginSetup) "ChatGPT 로그인" else "ChatGPT 보조", 17f, color(R.color.skin_ink), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(pill("마이크").apply {
            textSize = 12f
            setOnClickListener { primeChatMicrophone() }
        }, fixed(dp(78), dp(38)).withRightMargin(dp(6)))
        top.addView(pill("문장 복사").apply {
            textSize = 12f
            setOnClickListener { copySelectedPromptForChat() }
        }, fixed(dp(92), dp(38)).withRightMargin(dp(6)))
        top.addView(pill("Chrome").apply {
            textSize = 12f
            setOnClickListener { openChatGptInChrome() }
        }, fixed(dp(78), dp(38)).withRightMargin(dp(6)))
        if (readingCoachActive) {
            top.addView(pill("접기").apply {
                textSize = 12f
                setOnClickListener { compactChatGptAssistantDialog() }
            }, fixed(dp(64), dp(38)).withRightMargin(dp(6)))
        }
        top.addView(pill("닫기").apply {
            textSize = 12f
            setOnClickListener { dismissChatGptAssistantOverlay() }
        }, fixed(dp(64), dp(38)))

        box.addView(top, matchWrap())
        box.addView(status, matchWrap())
        box.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, webHeight))
        overlay.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (startCompact) {
            chatStatusLabel?.text = "Voice ChatGPT"
            compactChatDialogContent()
        }

        root.addView(
            overlay,
            if (startCompact) compactChatOverlayLayoutParams(compactHeight) else expandedChatOverlayLayoutParams(popupHeight)
        )
        if (startCompact) scheduleCompactChatDialogReposition(compactHeight)
        overlay.post {
            overlay.bringToFront()
            overlay.requestFocus()
            webView.requestFocus()
        }
        webView.loadUrl("https://chatgpt.com/")
    }

    private fun dismissChatGptAssistantOverlay(restoreReader: Boolean = true) {
        val wasReadingCoach = readingCoachActive
        val restoreLesson = currentLesson
        pendingChatPermissionRequest?.deny()
        pendingChatPermissionRequest = null
        pendingPrimeMicAfterPermission = false
        pendingChatCompactAfterSend = false
        chatLoginSetupMode = false
        if (wasReadingCoach) resetReadingCoachStateOnly()
        destroyChatWebView()
        if (wasReadingCoach && restoreReader) restoreReaderAfterCoach(restoreLesson)
    }

    private fun expandedChatOverlayLayoutParams(height: Int): FrameLayout.LayoutParams {
        val width = (resources.displayMetrics.widthPixels * 0.96f).roundToInt()
        val gravity = if (readingCoachActive) {
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        } else {
            Gravity.CENTER
        }
        return FrameLayout.LayoutParams(width, height, gravity).apply {
            if (readingCoachActive) topMargin = dp(8)
        }
    }

    private fun compactChatOverlayLayoutParams(height: Int): FrameLayout.LayoutParams {
        val geometry = compactChatDialogGeometry(height)
        return FrameLayout.LayoutParams(geometry.width, height, Gravity.TOP or Gravity.START).apply {
            leftMargin = geometry.x
            topMargin = geometry.y
        }
    }

    private fun applyExpandedChatOverlay(height: Int) {
        val overlay = chatOverlay ?: return
        overlay.layoutParams = expandedChatOverlayLayoutParams(height)
        overlay.visibility = View.VISIBLE
        overlay.isClickable = true
        overlay.isFocusable = true
        overlay.isFocusableInTouchMode = true
        overlay.bringToFront()
        overlay.requestFocus()
    }

    private fun applyCompactChatOverlay(height: Int) {
        val overlay = chatOverlay ?: return
        overlay.layoutParams = compactChatOverlayLayoutParams(height)
        overlay.visibility = View.VISIBLE
        overlay.isClickable = true
        overlay.isFocusable = false
        overlay.isFocusableInTouchMode = false
        overlay.bringToFront()
        overlay.requestLayout()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun chatGptWebView(status: TextView): WebView {
        return WebView(this).apply {
            setBackgroundColor(color(R.color.skin_surface))
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.userAgentString = WebSettings.getDefaultUserAgent(this@MainActivity)
                .replace("; wv", "")
                .replace("Version/4.0 ", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    status.text = "마이크를 준비한 뒤 ChatGPT 화면 안의 음성 버튼을 눌러주세요."
                    val prepareDelay = if (readingCoachActive) 2200L else 700L
                    handler.postDelayed({
                        if (chatWebView === view) {
                            if (readingCoachActive) {
                                prepareReadingCoachSession()
                            } else if (chatLoginSetupMode && pendingChatPrompt == null) {
                                status.text = "로그인 후 닫기를 누르면 준비가 끝나요."
                            } else if (pendingChatVoiceBeforePrompt) {
                                startChatVoiceBeforePromptFlow()
                            } else {
                                injectPendingChatPrompt()
                            }
                        }
                    }, prepareDelay)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    handler.post { handleChatWebPermission(request) }
                }
            }
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                    view.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
                false
            }
        }
    }

    private fun openChatGptWithPrompt(
        prompt: String,
        autoSend: Boolean,
        voiceBeforePrompt: Boolean,
        compactAfterSend: Boolean
    ) {
        pendingChatPrompt = prompt
        pendingChatAutoSend = autoSend
        pendingChatVoiceBeforePrompt = voiceBeforePrompt
        pendingChatVoiceBeforePromptStarted = false
        pendingChatCompactAfterSend = compactAfterSend
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Seoin English GPT prompt", prompt))
        showChatGptAssistantDialog()
    }

    private fun startChatVoiceBeforePromptFlow() {
        if (pendingChatPrompt == null || pendingChatVoiceBeforePromptStarted) return
        pendingChatVoiceBeforePromptStarted = true
        chatStatusLabel?.text = "Voice 버튼을 기다린 뒤 프롬프트를 보낼게요."
        handler.postDelayed({
            if (pendingChatPrompt == null) return@postDelayed
            triggerChatVoiceBeforePrompt(attempt = 0)
        }, 900L)
    }

    private fun triggerChatVoiceBeforePrompt(attempt: Int) {
        if (pendingChatPrompt == null) return
        triggerChatVoiceButton { clicked ->
            when {
                clicked -> {
                    chatStatusLabel?.text = "Voice를 열었어요. 잠깐 기다렸다가 프롬프트를 보낼게요."
                    handler.postDelayed({
                        if (pendingChatPrompt != null) injectPendingChatPrompt()
                    }, 1800L)
                }
                attempt < 5 -> {
                    chatStatusLabel?.text = "Voice 버튼을 찾는 중이에요."
                    handler.postDelayed({
                        triggerChatVoiceBeforePrompt(attempt + 1)
                    }, 700L)
                }
                else -> {
                    chatStatusLabel?.text = "Voice 버튼을 못 찾았어요. 프롬프트를 먼저 넣어둘게요."
                    handler.postDelayed({
                        if (pendingChatPrompt != null) injectPendingChatPrompt()
                    }, 450L)
                }
            }
        }
    }

    private fun injectPendingChatPrompt() {
        val prompt = pendingChatPrompt ?: return
        val webView = chatWebView ?: return
        val autoSend = pendingChatAutoSend
        val compactAfterSend = pendingChatCompactAfterSend
        val promptLiteral = JSONObject.quote(prompt)
        val script = """
            (function() {
              const text = $promptLiteral;
              const editor =
                document.querySelector('#prompt-textarea') ||
                document.querySelector('[contenteditable="true"]') ||
                document.querySelector('textarea');
              if (!editor) return "NO_EDITOR";
              editor.focus();
              if (editor.isContentEditable) {
                document.execCommand('selectAll', false, null);
                document.execCommand('insertText', false, text);
              } else {
                editor.value = text;
              }
              editor.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: text }));
              if (!$autoSend) return "OK";
              function findSendButton() {
                const buttons = Array.from(document.querySelectorAll('button'));
                return document.querySelector('[data-testid="send-button"]') ||
                  buttons.find(function(button) {
                  const label = ((button.getAttribute('aria-label') || '') + ' ' + (button.getAttribute('title') || '') + ' ' + button.innerText).toLowerCase();
                  return label.includes('send') || label.includes('\uBCF4\uB0B4\uAE30') || label.includes('\uC804\uC1A1');
                });
              }
              function clickSendWhenReady(remaining) {
                const send = findSendButton();
                if (send && !send.disabled && send.getAttribute('aria-disabled') !== 'true') {
                  send.click();
                  return;
                }
                if (remaining > 0) {
                  setTimeout(function() { clickSendWhenReady(remaining - 1); }, 420);
                }
              }
              setTimeout(function() { clickSendWhenReady(6); }, 1050);
              return "OK_SEND_SCHEDULED";
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            when {
                result.contains("OK_SEND_SCHEDULED") -> {
                    clearPendingChatPromptState()
                    chatStatusLabel?.text = "프롬프트를 넣었어요. 보내기 버튼을 기다렸다가 누를게요."
                    if (compactAfterSend) {
                        handler.postDelayed({ compactChatGptAssistantDialog() }, 3200L)
                    }
                }
                result.contains("OK") -> {
                    clearPendingChatPromptState()
                    chatStatusLabel?.text = if (autoSend) "프롬프트를 넣었어요." else "프롬프트를 입력창에 넣었어요."
                }
                result.contains("NO_EDITOR") -> {
                    chatStatusLabel?.text = "로그인이 필요하면 로그인 후 다시 열어주세요. 프롬프트는 클립보드에도 복사됐어요."
                }
                else -> {
                    chatStatusLabel?.text = "프롬프트 입력을 기다리는 중이에요."
                }
            }
        }
    }

    private fun clearPendingChatPromptState() {
        pendingChatPrompt = null
        pendingChatAutoSend = false
        pendingChatVoiceBeforePrompt = false
        pendingChatVoiceBeforePromptStarted = false
        pendingChatCompactAfterSend = false
    }

    private fun compactChatGptAssistantDialog() {
        val height = dp(64)
        chatStatusLabel?.text = "Voice ChatGPT"
        compactChatDialogContent()
        chatDialogBox?.apply {
            minimumHeight = 0
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            background = rounded(color(R.color.skin_surface), dp(10))
            requestLayout()
        }
        applyCompactChatOverlay(height)
        scheduleCompactChatDialogReposition(height)
    }

    private fun expandChatGptAssistantDialogForVoice() {
        val popupHeight = (resources.displayMetrics.heightPixels * 0.50f).roundToInt()
        val webHeight = max(dp(240), popupHeight - dp(128))
        chatStatusLabel?.apply {
            visibility = View.VISIBLE
            text = "Voice 버튼을 준비하는 중이에요."
        }
        chatDialogBox?.apply {
            minimumHeight = popupHeight
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, popupHeight)
            background = rounded(color(R.color.skin_surface), dp(18))
            requestLayout()
        }
        (chatDialogBox?.getChildAt(0) as? LinearLayout)?.apply {
            setPadding(dp(12), dp(10), dp(12), dp(8))
            background = rounded(color(R.color.skin_surface_alt), dp(18))
            for (index in 0 until childCount) getChildAt(index)?.visibility = View.VISIBLE
            (getChildAt(0) as? TextView)?.apply {
                text = "ChatGPT 보조"
                textSize = 17f
            }
            if (childCount > 0) getChildAt(childCount - 1)?.layoutParams = fixed(dp(64), dp(38))
        }
        chatWebView?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, webHeight)
        applyExpandedChatOverlay(popupHeight)
    }

    private fun bringChatGptDialogToForegroundForVoice(reason: String) {
        val overlay = chatOverlay
        val box = chatDialogBox
        val webView = chatWebView
        val popupHeight = (resources.displayMetrics.heightPixels * 0.50f).roundToInt()
        val webHeight = max(dp(240), popupHeight - dp(128))

        box?.apply {
            visibility = View.VISIBLE
            minimumHeight = popupHeight
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, popupHeight)
            elevation = dp(20).toFloat()
            bringToFront()
            requestLayout()
            invalidate()
        }
        webView?.apply {
            visibility = View.VISIBLE
            alpha = 1f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, webHeight)
            elevation = dp(24).toFloat()
            isFocusable = true
            isFocusableInTouchMode = true
            bringToFront()
            requestFocus()
            requestLayout()
            invalidate()
        }
        applyExpandedChatOverlay(popupHeight)
        Log.d(
            "SeoinCoach",
            "coach foreground prep reason=$reason overlayAttached=${overlay?.parent != null} web=${webView != null} box=${box != null}"
        )
    }

    private fun scheduleCompactChatDialogReposition(height: Int = dp(64)) {
        readerScroll?.post {
            applyCompactChatOverlay(height)
        }
        handler.postDelayed({
            applyCompactChatOverlay(height)
        }, 240L)
    }

    private fun compactChatDialogContent() {
        val box = chatDialogBox ?: return
        val top = box.getChildAt(0) as? LinearLayout
        val status = box.getChildAt(1)
        val webView = box.getChildAt(2)
        top?.apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(color(R.color.skin_surface_alt), dp(10))
            for (index in 0 until childCount) {
                getChildAt(index)?.visibility = if (index == 0 || index == childCount - 1) View.VISIBLE else View.GONE
            }
            (getChildAt(0) as? TextView)?.apply {
                text = "Voice ChatGPT"
                textSize = 13f
            }
            getChildAt(childCount - 1)?.layoutParams = fixed(dp(52), dp(28))
        }
        status?.visibility = View.GONE
        webView?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12))
    }

    private fun compactChatDialogGeometry(dialogHeight: Int): CompactDialogGeometry {
        val metrics = resources.displayMetrics
        val defaultMargin = dp(14)
        var x = defaultMargin
        var y = dp(116)
        var width = metrics.widthPixels - defaultMargin * 2
        if (readingCoachActive) {
            val scrollLocation = IntArray(2)
            readerScroll?.takeIf { it.width > 0 && it.height > 0 }?.getLocationOnScreen(scrollLocation)
            y = if (scrollLocation[1] > 0) {
                scrollLocation[1] - dialogHeight - dp(6)
            } else {
                dp(72)
            }
            y = y.coerceIn(dp(8), max(dp(8), metrics.heightPixels - dialogHeight - dp(8)))
            return CompactDialogGeometry(x = x, y = y, width = max(dp(280), width))
        }
        val overlay = comprehensionOverlay ?: return CompactDialogGeometry(x = x, y = y, width = width)
        val overlayLocation = IntArray(2)
        if (overlay.width > 0 && overlay.height > 0) {
            overlay.getLocationOnScreen(overlayLocation)
            x = max(defaultMargin, overlayLocation[0])
            width = min(overlay.width, metrics.widthPixels - x - defaultMargin)
            y = overlayLocation[1] + overlay.height + dp(4)
        }
        y = y.coerceIn(dp(10), max(dp(10), metrics.heightPixels - dialogHeight - dp(10)))
        return CompactDialogGeometry(x = x, y = y, width = max(dp(280), width))
    }

    private fun triggerChatVoiceButton(onDone: ((Boolean) -> Unit)? = null) {
        val webView = chatWebView ?: run {
            onDone?.invoke(false)
            return
        }
        val script = """
            (function() {
              const buttons = Array.from(document.querySelectorAll('button')).reverse();
              const explicit =
                document.querySelector('[data-testid="voice-mode-button"]') ||
                document.querySelector('[data-testid="composer-speech-button"]') ||
                document.querySelector('[data-testid="voice-button"]');
              const voice = explicit || buttons.find(function(button) {
                const label = ((button.getAttribute('aria-label') || '') + ' ' + (button.getAttribute('title') || '') + ' ' + button.innerText).toLowerCase();
                return label.includes('voice') ||
                  label.includes('dictate') ||
                  label.includes('speak') ||
                  label.includes('microphone') ||
                  label.includes('audio') ||
                  label.includes('\uC74C\uC131') ||
                  label.includes('\uB300\uD654') ||
                  label.includes('\uB9C8\uC774\uD06C') ||
                  label.includes('\uBCF4\uC774\uC2A4');
              });
              if (voice && !voice.disabled && voice.getAttribute('aria-disabled') !== 'true') {
                voice.click();
                return "VOICE_CLICKED";
              }
              return "NO_VOICE_BUTTON";
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            val clicked = result.contains("VOICE_CLICKED")
            chatStatusLabel?.text = if (clicked) {
                "보이스 채팅을 열었어요."
            } else {
                "마이크 준비 완료. 화면 안의 음성 버튼을 눌러주세요."
            }
            onDone?.invoke(clicked)
        }
    }

    private fun clearCoachComposerDraft(onDone: () -> Unit) {
        val webView = chatWebView ?: run {
            onDone()
            return
        }
        val script = """
            (function() {
              const isVisible = function(el) {
                if (!el) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
              };
              const readInput = function(input) {
                if (!input) return '';
                if (input.isContentEditable || input.getAttribute('contenteditable') === 'true') return input.textContent || '';
                return input.value || '';
              };
              const clearInput = function(input) {
                if (!input) return false;
                input.focus();
                if (input.isContentEditable || input.getAttribute('contenteditable') === 'true') {
                  input.textContent = '';
                } else {
                  const proto = input.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                  const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                  setter.call(input, '');
                }
                input.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'deleteContentBackward', data: null }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
              };
              const inputs = Array.from(document.querySelectorAll('#prompt-textarea, [data-testid="prompt-textarea"], textarea, [contenteditable="true"]'))
                .filter(function(el) { return isVisible(el) && !el.closest('[aria-hidden="true"]'); });
              const input = inputs.find(function(el) {
                return el.id === 'prompt-textarea' || el.getAttribute('data-testid') === 'prompt-textarea';
              }) || inputs[0];
              const before = readInput(input);
              const cleared = clearInput(input);
              return JSON.stringify({ cleared: cleared, beforeLength: before.length, before: before.slice(0, 48) });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            Log.d("SeoinCoach", "coach composer clear result=${jsStringValue(result)}")
            onDone()
        }
    }

    private fun requestCoachVoiceMode(
        flowToken: Long,
        retries: Int,
        onDone: (Boolean) -> Unit
    ) {
        val webView = chatWebView ?: run {
            if (retries > 0) {
                handler.postDelayed({
                    if (readingCoachActive && flowToken == readingCoachFlowToken) {
                        requestCoachVoiceMode(flowToken, retries - 1, onDone)
                    }
                }, 1000L)
            } else {
                onDone(false)
            }
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPrimeMicAfterPermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), chatMicPermissionRequestCode)
            onDone(false)
            return
        }
        if (readingCoachVoiceRequestInFlight) {
            updateReadingCoachStatus("Voice request already running...")
            handler.postDelayed({
                if (readingCoachActive && flowToken == readingCoachFlowToken && !readingCoachFallbackTts) {
                    requestCoachVoiceMode(flowToken, retries, onDone)
                }
            }, 700L)
            return
        }
        bringChatGptDialogToForegroundForVoice("voice-button-request")
        readingCoachVoiceRequestInFlight = true
        clearPendingChatPromptState()
        clearCoachComposerDraft {
            handler.postDelayed({
                if (!readingCoachActive || flowToken != readingCoachFlowToken || readingCoachFallbackTts) {
                    readingCoachVoiceRequestInFlight = false
                    return@postDelayed
                }
                webView.evaluateJavascript(buildCoachVoiceButtonScript()) { result ->
                    if (!readingCoachActive || flowToken != readingCoachFlowToken || readingCoachFallbackTts) {
                        readingCoachVoiceRequestInFlight = false
                        return@evaluateJavascript
                    }
                    val clean = jsStringValue(result)
                    val success = clean.contains("\"success\":true")
                    readingCoachVoiceRequestInFlight = false
                    Log.d("SeoinCoach", "voice mode request retries=$retries success=$success result=$clean")
                    if (success) {
                        onDone(true)
                        return@evaluateJavascript
                    }
                    if (retries > 0) {
                        updateReadingCoachStatus("Waiting for ChatGPT voice button... $retries")
                        handler.postDelayed({
                            if (readingCoachActive && flowToken == readingCoachFlowToken && !readingCoachFallbackTts) {
                                requestCoachVoiceMode(flowToken, retries - 1, onDone)
                            }
                        }, 1000L)
                    } else {
                        onDone(false)
                    }
                }
            }, 350L)
        }
    }

    private fun buildCoachVoiceButtonScript(): String = """
        (function() {
          const isVisible = function(el) {
            if (!el) return false;
            const rect = el.getBoundingClientRect();
            const style = window.getComputedStyle(el);
            return rect.width > 0 && rect.height > 0 &&
              style.visibility !== 'hidden' &&
              style.display !== 'none';
          };

          const labelOf = function(el) {
            return [
              el.getAttribute('aria-label'),
              el.getAttribute('data-testid'),
              el.getAttribute('title'),
              el.textContent
            ].filter(Boolean).join(' ').toLowerCase();
          };

          const score = function(el) {
            if (!isVisible(el) || el.disabled || el.getAttribute('aria-disabled') === 'true') return 0;
            const label = labelOf(el);
            const isVoiceLike = /voice|voice mode|voice chat|voice conversation|\uBCF4\uC774\uC2A4|\uC74C\uC131 \uBAA8\uB4DC|\uC74C\uC131 \uB300\uD654|\uC74C\uC131\uC73C\uB85C \uB300\uD654|\uB300\uD654 \uC2DC\uC791/.test(label);

            if (/send|submit|attach|upload|\uC804\uC1A1|\uBCF4\uB0B4\uAE30|\uCCA8\uBD80/.test(label)) return 0;
            if (/options|menu|close|stop|end|leave|hang up|\uC635\uC158|\uBA54\uB274|\uB2EB\uAE30|\uC911\uC9C0|\uC885\uB8CC/.test(label)) return 0;
            if (/dictate|dictation|\uBC1B\uC544\uC4F0\uAE30|\uC74C\uC131 \uC785\uB825/.test(label) && !isVoiceLike) return 0;
            if (/composer-speech-button|speech/.test(label) && !isVoiceLike) return 0;

            let points = 0;
            if (/voice-mode-button|voice-button/.test(label)) points += 150;
            if (/voice mode|start voice|voice chat|voice conversation/.test(label)) points += 120;
            if (/\uC74C\uC131 \uBAA8\uB4DC|\uC74C\uC131 \uB300\uD654|\uC74C\uC131\uC73C\uB85C \uB300\uD654|\uB300\uD654 \uC2DC\uC791|\uBCF4\uC774\uC2A4/.test(label)) points += 120;
            if (/\bvoice\b|\uC74C\uC131/.test(label)) points += 80;
            if (/speech/.test(label)) points += 60;
            if (/microphone|\bmic\b|\uB9C8\uC774\uD06C/.test(label)) points += 65;
            return points;
          };

          const candidates = Array.from(document.querySelectorAll('button, [role="button"]'))
            .map(function(el) { return { el: el, points: score(el), label: labelOf(el).slice(0, 100) }; })
            .filter(function(item) { return item.points >= 60; })
            .sort(function(a, b) { return b.points - a.points; });

          const allLabels = Array.from(document.querySelectorAll('button, [role="button"]'))
            .filter(isVisible)
            .map(function(el) { return labelOf(el).slice(0, 80); })
            .filter(Boolean)
            .join(' | ')
            .slice(0, 420);

          const target = candidates.length ? candidates[0] : null;
          if (!target) {
            return JSON.stringify({ success: false, reason: 'no-voice-button', labels: allLabels });
          }

          target.el.click();
          return JSON.stringify({
            success: true,
            points: target.points,
            label: target.label,
            candidates: candidates.slice(0, 4).map(function(item) {
              return { points: item.points, label: item.label };
            })
          });
        })();
    """.trimIndent()

    private fun waitForCoachVoiceUiReady(
        flowToken: Long,
        maxWaitMs: Long,
        startedAtMs: Long = System.currentTimeMillis(),
        attempt: Int = 0,
        onDone: (Boolean) -> Unit
    ) {
        val webView = chatWebView ?: run {
            onDone(false)
            return
        }
        val script = """
            (function() {
              const isVisible = function(el) {
                if (!el) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
              };
              const labelOf = function(el) {
                return [
                  el.getAttribute('aria-label'),
                  el.getAttribute('data-testid'),
                  el.getAttribute('title'),
                  el.textContent
                ].filter(Boolean).join(' ').trim().toLowerCase();
              };
              const text = (document.body && document.body.innerText || '').toLowerCase();
              const visibleComposer = Array.from(document.querySelectorAll('#prompt-textarea, [data-testid="prompt-textarea"], textarea, [contenteditable="true"]'))
                .some(function(el) { return isVisible(el) && !el.closest('[aria-hidden="true"]'); });
              const labels = Array.from(document.querySelectorAll('button, [role="button"]'))
                .filter(isVisible)
                .map(labelOf)
                .join('\n');
              const hasReadyText =
                text.includes('start speaking') ||
                text.includes('speak now') ||
                text.includes('listening') ||
                text.includes('connected') ||
                text.includes('\uB300\uD654\uB97C \uC2DC\uC791') ||
                text.includes('\uB4E4\uC744 \uC900\uBE44') ||
                text.includes('\uB4E3\uACE0');
              const hasEndButton = /end conversation|end voice|leave voice|disconnect|hang up|\uC885\uB8CC|\uB05D\uB0B4\uAE30|\uB098\uAC00\uAE30/.test(labels);
              const hasVoiceMicControl = (/mute|unmute|microphone|\bmic\b|\uB9C8\uC774\uD06C|\uC74C\uC18C\uAC70/.test(labels) && hasEndButton);
              const hasMicError =
                text.includes('microphone access required') ||
                text.includes('enable microphone access') ||
                text.includes('\uB9C8\uC774\uD06C \uC561\uC138\uC2A4') ||
                text.includes('\uB9C8\uC774\uD06C \uAD8C\uD55C');
              const voiceReady = !hasMicError && (hasEndButton || hasVoiceMicControl || (hasReadyText && !visibleComposer));
              return JSON.stringify({
                voiceReady: voiceReady,
                composerReady: visibleComposer,
                hasEndButton: hasEndButton,
                hasVoiceMicControl: hasVoiceMicControl,
                hasReadyText: hasReadyText,
                hasMicError: hasMicError,
                labels: labels.slice(0, 220)
              });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            if (!readingCoachActive || flowToken != readingCoachFlowToken || readingCoachFallbackTts) return@evaluateJavascript
            val clean = jsStringValue(result)
            val voiceReady = clean.contains("\"voiceReady\":true")
            val composerReady = clean.contains("\"composerReady\":true")
            Log.d("SeoinCoach", "voice ui wait attempt=$attempt ready=${voiceReady && composerReady} result=$clean")
            if (voiceReady && composerReady) {
                onDone(true)
                return@evaluateJavascript
            }
            if (System.currentTimeMillis() - startedAtMs >= maxWaitMs) {
                onDone(false)
                return@evaluateJavascript
            }
            updateReadingCoachStatus(
                "Waiting for voice conversation and input..."
            )
            handler.postDelayed({
                if (readingCoachActive && flowToken == readingCoachFlowToken && !readingCoachFallbackTts) {
                    waitForCoachVoiceUiReady(flowToken, maxWaitMs, startedAtMs, attempt + 1, onDone)
                }
            }, 850L)
        }
    }

    private fun primeChatMicrophone(auto: Boolean = false) {
        val webView = chatWebView ?: return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPrimeMicAfterPermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), chatMicPermissionRequestCode)
            return
        }
        chatStatusLabel?.text = if (auto) {
            "마이크 권한을 확인하는 중..."
        } else {
            "마이크를 준비하는 중..."
        }
        webView.requestFocus()
        val script = """
            (async function() {
              try {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                  return "NO_MEDIA_DEVICES";
                }
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                stream.getTracks().forEach(function(track) { track.stop(); });
                return "OK";
              } catch (error) {
                return "ERR:" + error.name + ":" + (error.message || "");
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            when {
                result.contains("OK") -> chatStatusLabel?.text = "마이크 준비 완료. ChatGPT 화면 안의 음성 버튼을 눌러주세요."
                result.contains("NO_MEDIA_DEVICES") -> chatStatusLabel?.text = "이 WebView에서 마이크 API를 찾지 못했어요. Chrome 버튼을 사용해주세요."
                result.contains("NotAllowedError") || result.contains("Permission") -> chatStatusLabel?.text = "마이크가 차단됐어요. 위의 마이크 버튼을 다시 누르거나 Chrome으로 열어주세요."
                else -> chatStatusLabel?.text = "마이크 확인 결과: $result"
            }
        }
    }

    private fun handleChatWebPermission(request: PermissionRequest) {
        val needsMic = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        if (needsMic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingChatPermissionRequest?.deny()
            pendingChatPermissionRequest = request
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), chatMicPermissionRequestCode)
        } else {
            val audioResources = request.resources
                .filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                .toTypedArray()
            if (audioResources.isNotEmpty()) {
                request.grant(audioResources)
                chatStatusLabel?.text = "WebView 마이크 권한을 승인했어요."
            } else {
                request.deny()
            }
        }
    }

    private fun destroyChatWebView() {
        chatOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        chatOverlay = null
        chatDialogBox = null
        chatWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        chatWebView = null
        chatStatusLabel = null
    }

    private fun copySelectedPromptForChat() {
        val (target, context) = selectedSentencePrompt() ?: run {
            toast("선택된 문장이 없어요.")
            return
        }
        copyPrompt(target, context, openChat = false)
    }

    private fun selectedSentencePrompt(): Pair<String, String>? {
        val sentence = flatSentences.getOrNull(selectedSentenceIndex)?.sentence ?: return null
        return sentence.text to "이 문장을 초등학생 영어 학습자에게 쉽게 설명해줘."
    }

    private fun copyPrompt(target: String, context: String, openChat: Boolean = true) {
        val prompt = """
            초등학생 영어 학습자에게 아래 표현을 쉽게 설명해줘.
            뜻, 중요한 단어, 문장 구조, 비슷한 예문 3개를 한국어로 알려줘.

            표현: "$target"
            참고: $context
        """.trimIndent()
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Seoin English GPT prompt", prompt))
        toast("GPT 프롬프트를 복사했어요.")
        if (openChat) openChatGptWithPrompt(prompt, autoSend = false, voiceBeforePrompt = false, compactAfterSend = false)
    }

    private fun openChatGptInChrome() {
        val chrome = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")).apply {
            setPackage("com.android.chrome")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        runCatching { startActivity(chrome) }.getOrElse {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                })
            }.getOrElse {
                toast("Chrome 또는 브라우저를 열 수 없어요.")
            }
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
        player?.release()
        player = null
        audioProfile = null
        playButton = null
        ttsModeButton = null
        unknownButton = null
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
        readerScroll = null
        readerActionHost = null
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
                easyEnglishLong = item.optString("easyEnglishLong", ""),
                examples = directExamples,
                comic = parseVocabComic(item.optJSONObject("comic")),
                quizPanel = parseVocabQuizPanel(item.optJSONObject("quiz")),
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
                easyEnglishLong = item.optString("easyEnglishLong", ""),
                examples = item.optJSONArray("examples").toStringList()
            )
            explanations[explanation.id] = explanation
        }
        val vocabReflexGame = parseVocabReflexGame(json.optJSONObject("vocabReflexGame"))
        val rootComicSource = json.optJSONObject("cinematicComic")
            ?: json.optJSONObject("lessonComic")
            ?: json.optJSONObject("comic")
            ?: json.takeIf { it.has("panels") && it.has("words") }
        val cinematicComic = parseVocabComic(rootComicSource)
        val comprehensionChecks = mutableListOf<ComprehensionCheck>()
        json.optJSONArray("comprehensionChecks").forEachObject { item ->
            val sentenceId = item.optString("sentenceId", "")
            comprehensionChecks.add(
                ComprehensionCheck(
                    id = item.optString("id", "check_${comprehensionChecks.size + 1}"),
                    question = item.optString("question", ""),
                    sentenceId = sentenceId,
                    afterSentenceId = item.optString("afterSentenceId", sentenceId),
                    chunkSetId = item.optString("chunkSetId", json.optString("defaultChunkSetId", "short")),
                    answerChunkId = item.optString("answerChunkId", ""),
                    answerText = item.optString("answerText", ""),
                    correctFeedback = item.optString(
                        "correctFeedback",
                        item.optString(
                            "feedbackCorrect",
                            item.optString("correctAnswer", item.optString("answerFeedback", ""))
                        )
                    ),
                    scopeSentenceIds = item.optJSONArray("scopeSentenceIds").toStringList(),
                    promptNote = item.optString("promptNote", "")
                )
            )
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
                        annotations = annotations,
                        chunkActivity = s.optChunkActivityMode()
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
            explanations = explanations,
            vocabReflexGame = vocabReflexGame,
            cinematicComic = cinematicComic,
            comprehensionChecks = comprehensionChecks
        )
        applySavedAdjustments(lesson)
        return lesson
    }

    private fun parseVocabReflexGame(source: JSONObject?): VocabReflexGame? {
        if (source == null) return null
        val rulesJson = source.optJSONObject("rules")
        val timingJson = source.optJSONObject("timingRules")
        val rules = VocabReflexRules(
            moveNextDelayMs = rulesJson?.optInt("moveNextDelayMs", 650) ?: 650
        )
        val timing = VocabReflexTiming(
            fastMsMax = timingJson?.optInt("fastMsMax", 1500) ?: 1500,
            okayMsMax = timingJson?.optInt("okayMsMax", 3000) ?: 3000,
            slowMsMin = timingJson?.optInt("slowMsMin", 3001) ?: 3001
        )
        val targets = mutableListOf<VocabReflexTargetWord>()
        source.optJSONArray("targetWords").forEachObject { item ->
            targets.add(
                VocabReflexTargetWord(
                    wordId = item.optString("wordId", ""),
                    word = item.optString("word", ""),
                    coreCue = item.optString("coreCue", ""),
                    readingBridge = item.optString("readingBridge", "")
                )
            )
        }
        val sets = mutableListOf<VocabReflexSet>()
        source.optJSONArray("sets").forEachObject { setJson ->
            val cards = mutableListOf<VocabReflexCard>()
            setJson.optJSONArray("cards").forEachObject { cardJson ->
                val options = mutableListOf<VocabReflexOption>()
                cardJson.optJSONArray("options").forEachObject { optionJson ->
                    options.add(
                        VocabReflexOption(
                            id = optionJson.optString("id", ""),
                            text = optionJson.optString("text", ""),
                            isCorrect = optionJson.optBoolean("isCorrect", false),
                            cueType = optionJson.optString("cueType", ""),
                            distractorStrategy = optionJson.optString("distractorStrategy", ""),
                            linkedWordId = optionJson.optString("linkedWordId", ""),
                            errorTag = optionJson.optString("errorTag", "")
                        )
                    )
                }
                cards.add(
                    VocabReflexCard(
                        id = cardJson.optString("id", ""),
                        targetWordId = cardJson.optString("targetWordId", ""),
                        targetWord = cardJson.optString("targetWord", ""),
                        options = options,
                        answerOptionId = cardJson.optString("answerOptionId", ""),
                        cardType = cardJson.optString("cardType", ""),
                        feedbackCorrect = cardJson.optString("feedbackCorrect", ""),
                        feedbackWrong = cardJson.optString("feedbackWrong", ""),
                        readingBridge = cardJson.optString("readingBridge", ""),
                        echoCardId = cardJson.optString("echoCardId", ""),
                        echoOfCardId = cardJson.optString("echoOfCardId", "")
                    )
                )
            }
            sets.add(
                VocabReflexSet(
                    id = setJson.optString("id", ""),
                    roundType = setJson.optString("roundType", "seed"),
                    sourceSetId = setJson.optString("sourceSetId", ""),
                    cards = cards
                )
            )
        }
        return VocabReflexGame(
            version = source.optString("version", "1.0"),
            lessonId = source.optString("lessonId", ""),
            title = source.optString("title", ""),
            rules = rules,
            timing = timing,
            targetWords = targets,
            sets = sets
        )
    }

    private fun parseVocabComic(source: JSONObject?): VocabComic? {
        if (source == null) return null
        val imageAssetId = source.optString("imageAssetId", "")
        val backgroundSheet = source.optJSONObject("backgroundSheet")
        val backgroundImageAssetId = source.optString("backgroundImageAssetId", "")
            .ifBlank { source.optString("backgroundAssetId", "") }
            .ifBlank { source.optString("backgroundSheetAssetId", "") }
        val panels = source.optJSONArray("panels").toComicPanels()
        if (imageAssetId.isBlank() && backgroundImageAssetId.isBlank() && panels.isEmpty()) return null
        val narrations = source.optJSONArray("narration").toStringList()
            .ifEmpty { source.optJSONArray("panelNarrations").toStringList() }
            .ifEmpty { source.optJSONArray("panels").toPanelNarrations() }
        return VocabComic(
            word = source.optString("word", ""),
            meaning = source.optString("meaning", ""),
            imageAssetId = imageAssetId,
            backgroundImageAssetId = backgroundImageAssetId,
            backgroundColumns = source.optInt("backgroundColumns", backgroundSheet?.optInt("columns", 2) ?: 2).coerceAtLeast(1),
            backgroundRows = source.optInt("backgroundRows", backgroundSheet?.optInt("rows", 2) ?: 2).coerceAtLeast(1),
            panelCount = source.optInt("panelCount", narrations.size.takeIf { it > 0 } ?: 3),
            layout = source.optString("layout", "horizontal"),
            words = source.optJSONArray("words").toStringList(),
            wordExplanations = source.optJSONObject("wordExplanations").toNormalizedStringMap(),
            narrations = narrations,
            panels = panels,
            focusSteps = source.optJSONArray("focusSteps").toVocabComicFocusSteps(),
            readDefinitionAfter = source.optBoolean("readDefinitionAfter", false)
        )
    }

    private fun parseVocabQuizPanel(source: JSONObject?): ComicPanel? {
        if (source == null) return null
        return source.toComicPanel(includeText = false).takeIf { it.sprites.isNotEmpty() }
    }

    private fun JSONArray?.toComicPanels(): List<ComicPanel> {
        if (this == null) return emptyList()
        val result = mutableListOf<ComicPanel>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(item.toComicPanel(includeText = true, fallbackFrame = i % 4))
        }
        return result
    }

    private fun JSONObject.toComicPanel(includeText: Boolean, fallbackFrame: Int = -1): ComicPanel {
        val sprites = mutableListOf<ComicSprite>()
        optJSONArray("sprites").forEachObject { sprite ->
            sprites.add(
                ComicSprite(
                    char = sprite.optString("char", ""),
                    src = sprite.optString("src", ""),
                    x = sprite.optDouble("x", 50.0).toFloat(),
                    y = sprite.optDouble("y", 60.0).toFloat(),
                    scale = sprite.optDouble("scale", 1.0).toFloat(),
                    rotate = sprite.optDouble("rotate", 0.0).toFloat(),
                    flip = sprite.optBoolean("flip", false),
                    anim = sprite.optString("anim", "none").takeIf { it in comicAnimNames } ?: "none"
                )
            )
        }
        val bubble = if (includeText) {
            optJSONObject("bubble")?.let { bubbleSource ->
                ComicBubble(
                    anchor = bubbleSource.optInt("anchor", 0),
                    x = bubbleSource.optDouble("x", -1.0).toFloat(),
                    y = bubbleSource.optDouble("y", -1.0).toFloat(),
                    text = bubbleSource.optString("text", "")
                )
            }
        } else {
            null
        }
        return ComicPanel(
            sceneId = optString("sceneId", ""),
            bg = optString("bg", "plain").takeIf { it in comicBgNames } ?: "plain",
            bgFrame = optFrameIndex(fallbackFrame),
            mood = optString("mood", ""),
            shot = optString("shot", ""),
            climax = optBoolean("climax", false),
            fx = optString("fx", ""),
            zoom = optJSONObject("zoom")?.toComicZoom(),
            sfx = optJSONArray("sfx").toComicSfxList(),
            caption = if (includeText) optString("caption", "") else "",
            sprites = sprites,
            bubble = bubble?.takeIf { it.text.isNotBlank() }
        )
    }

    private fun JSONObject.toComicZoom(): ComicZoom {
        return ComicZoom(
            type = optString("type", ""),
            scale = optDouble("scale", 1.35).toFloat(),
            originX = optDouble("originX", 50.0).toFloat(),
            originY = optDouble("originY", 55.0).toFloat()
        )
    }

    private fun JSONArray?.toComicSfxList(): List<ComicSfx> {
        if (this == null) return emptyList()
        val result = mutableListOf<ComicSfx>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val text = item.optString("text", "")
            if (text.isBlank()) continue
            result.add(
                ComicSfx(
                    text = text,
                    x = item.optDouble("x", 50.0).toFloat(),
                    y = item.optDouble("y", 50.0).toFloat(),
                    size = item.optDouble("size", 9.0).toFloat(),
                    rotate = item.optDouble("rotate", -8.0).toFloat(),
                    color = parseColorSafe(item.optString("color", "#fde047"), 0xFFFDE047.toInt())
                )
            )
        }
        return result
    }

    private fun JSONObject.optFrameIndex(defaultValue: Int): Int {
        val names = listOf("bgFrame", "backgroundFrame", "sceneFrame", "frameIndex", "sceneIndex")
        names.forEach { name ->
            if (has(name)) return optInt(name, defaultValue)
        }
        return defaultValue
    }

    private fun JSONObject?.toNormalizedStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        keys().forEach { key ->
            val value = optString(key, "")
            if (key.isNotBlank() && value.isNotBlank()) {
                result[normalizeKittyKey(key)] = value
                result[key.lowercase(Locale.US)] = value
            }
        }
        return result
    }

    private fun JSONArray?.toVocabComicFocusSteps(): List<VocabComicFocusStep> {
        if (this == null) return emptyList()
        val result = mutableListOf<VocabComicFocusStep>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val panelIndex = item.optInt("panelIndex", panelIdToIndex(item.optString("panelId", "")))
            val effect = item.optJSONObject("effect")
            val style = effect?.optString("highlightStyle", "") ?: ""
            val focusMode = item.optString(
                "focusMode",
                effect?.optString(
                    "focusMode",
                    if (style.contains("camera", ignoreCase = true)) "camera" else "sparkle"
                ) ?: "sparkle"
            )
            val dimAlpha = ((effect?.optDouble("dimOpacity", 0.55) ?: 0.55) * 255.0).roundToInt()
            val choices = mutableListOf<VocabComicChoice>()
            item.optJSONObject("choice")?.optJSONArray("options").forEachObject { option ->
                choices.add(
                    VocabComicChoice(
                        text = option.optString("text", ""),
                        correct = option.optBoolean("isCorrect", false),
                        feedback = option.optString("feedback", ""),
                        voiceText = option.optString("voiceText", "")
                    )
                )
            }
            result.add(
                VocabComicFocusStep(
                    id = item.optString("id", "step_${i + 1}"),
                    panelIndex = panelIndex,
                    narration = item.optJSONObject("narration")?.optString("text", "")
                        ?: item.optString("narration", ""),
                    sourceBox = item.optPixelBox("sourceBboxPx"),
                    zoomScale = (effect?.optDouble("zoomScale", 1.45) ?: 1.45).toFloat(),
                    focusMode = focusMode,
                    dimAlpha = dimAlpha,
                    transitionMs = effect?.optInt("transitionMs", 900) ?: 900,
                    glow = style.contains("glow", ignoreCase = true),
                    choices = choices
                )
            )
        }
        return result
    }

    private fun panelIdToIndex(panelId: String): Int {
        val number = Regex("(\\d+)").find(panelId)?.value?.toIntOrNull() ?: 1
        return (number - 1).coerceAtLeast(0)
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

    private fun loadBitmapAsset(path: String): Bitmap? {
        return runCatching {
            assets.open(path).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun playTinyTone(toneType: Int, durationMs: Int) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull() ?: return
        tone.startTone(toneType, durationMs)
        handler.postDelayed({ tone.release() }, (durationMs + 80).toLong())
    }

    private fun playToneSequence(tones: List<Int>, durationMs: Int = 150, gapMs: Int = 70) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull() ?: return
        tones.forEachIndexed { index, toneType ->
            handler.postDelayed({ tone.startTone(toneType, durationMs) }, (index * (durationMs + gapMs)).toLong())
        }
        handler.postDelayed({ tone.release() }, (tones.size * (durationMs + gapMs) + 90).toLong())
    }

    private fun playComicChoiceTone(correct: Boolean): Long {
        return if (correct) {
            playTinyTone(ToneGenerator.TONE_PROP_ACK, 220)
            handler.postDelayed({ playToneSequence(listOf(ToneGenerator.TONE_DTMF_3, ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_DTMF_9), durationMs = 185, gapMs = 38) }, 120L)
            820L
        } else {
            playTinyTone(ToneGenerator.TONE_PROP_NACK, 260)
            handler.postDelayed({ playToneSequence(listOf(ToneGenerator.TONE_PROP_BEEP, ToneGenerator.TONE_DTMF_1), durationMs = 190, gapMs = 45) }, 90L)
            520L
        }
    }

    private fun addMargin(view: View, left: Int = 0, right: Int = 0) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = left
        lp.rightMargin = right
        view.layoutParams = lp
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
}

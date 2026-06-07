package com.seoin.englishstudy.data

import android.content.Context
import com.seoin.englishstudy.model.MasterSettings

internal class SettingsStore(context: Context) {
    private val appContext = context.applicationContext

    private fun prefs(name: String) = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun isChatLoginChecked(): Boolean {
        return prefs("chat_gpt_setup").getBoolean("login_checked", false)
    }

    fun setChatLoginChecked(value: Boolean) {
        prefs("chat_gpt_setup").edit().putBoolean("login_checked", value).apply()
    }

    fun saveVocabKnown(lessonId: String, vocabId: String, known: Boolean) {
        prefs("vocab_known").edit()
            .putBoolean("$lessonId|$vocabId", known)
            .apply()
    }

    fun hasMasterOverride(lessonId: String): Boolean {
        return prefs("master_settings").getBoolean("$lessonId|override", false)
    }

    fun setMasterOverride(lessonId: String, value: Boolean) {
        prefs("master_settings").edit()
            .putBoolean("$lessonId|override", value)
            .apply()
    }

    fun loadMasterSettings(scope: String): MasterSettings {
        val prefs = prefs("master_settings")
        return MasterSettings(
            vocabEnabled = prefs.getBoolean("$scope|vocab", true),
            quizEnabled = prefs.getBoolean("$scope|quiz", true),
            vocabGameMode2Enabled = prefs.getBoolean("$scope|vocabGameMode2", true),
            quizMode = prefs.getString("$scope|quizMode", "choice") ?: "choice",
            manualChunkEnabled = prefs.getBoolean("$scope|manual", true),
            firstListenPauseMs = prefs.getInt("$scope|firstPauseMs", 1500),
            firstListenRate = prefs.getFloat("$scope|firstRate", 0.9f),
            speakChunkPauseMs = prefs.getInt("$scope|speakChunkPauseMs", 700),
            firstListenNextAlwaysEnabled = prefs.getBoolean("$scope|firstNextAlways", false),
            readerTextSizeSp = prefs.getFloat("$scope|readerTextSizeSp", 19f),
            readerLetterSpacing = prefs.getFloat("$scope|readerLetterSpacing", 0f),
            readerSpaceScale = prefs.getFloat("$scope|readerSpaceScale", 1f),
            readerLineSpacingDp = prefs.getFloat("$scope|readerLineSpacingDp", 6f)
        )
    }

    fun saveMasterSettings(scope: String, settings: MasterSettings) {
        prefs("master_settings").edit()
            .putBoolean("$scope|vocab", settings.vocabEnabled)
            .putBoolean("$scope|quiz", settings.quizEnabled)
            .putBoolean("$scope|vocabGameMode2", settings.vocabGameMode2Enabled)
            .putString("$scope|quizMode", settings.quizMode)
            .putBoolean("$scope|manual", settings.manualChunkEnabled)
            .putInt("$scope|firstPauseMs", settings.firstListenPauseMs)
            .putFloat("$scope|firstRate", settings.firstListenRate)
            .putInt("$scope|speakChunkPauseMs", settings.speakChunkPauseMs)
            .putBoolean("$scope|firstNextAlways", settings.firstListenNextAlwaysEnabled)
            .putFloat("$scope|readerTextSizeSp", settings.readerTextSizeSp)
            .putFloat("$scope|readerLetterSpacing", settings.readerLetterSpacing)
            .putFloat("$scope|readerSpaceScale", settings.readerSpaceScale)
            .putFloat("$scope|readerLineSpacingDp", settings.readerLineSpacingDp)
            .apply()
    }
}

package com.zyren.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Türkçe konuşma tanıma (SpeechRecognizer) + sesli yanıt (TextToSpeech). Ana iş parçacığından kullanılmalı. */
class VoiceController(
    private val ctx: Context,
    private val onText: (String) -> Unit,
    private val onState: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    var onSpeakDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("tr", "TR"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) onState("Türkçe ses verisi yok (Ayarlar > Metin okuma'dan indirin)")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { main.post { onSpeakDone?.invoke() } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { main.post { onSpeakDone?.invoke() } }
                })
            } else onState("Metin okuma başlatılamadı")
        }
    }

    fun speak(text: String) {
        if (!ttsReady) { onSpeakDone?.invoke(); return }
        val clean = text.replace(Regex("[*#`_>]"), "").take(1500)
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "zyren")
    }

    fun stopSpeaking() { tts?.stop() }

    fun startListening() {
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onState("Bu cihazda konuşma tanıma servisi yok"); return }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onState("Dinliyorum…") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onState("") }
            override fun onError(error: Int) { onState("Anlaşılamadı (kod $error)") }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                onState("")
                if (!t.isNullOrBlank()) onText(t)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer = r
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        })
    }

    fun stopListening() { recognizer?.stopListening() }

    fun shutdown() {
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
    }
}

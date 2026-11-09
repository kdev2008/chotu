package com.devkumar.jarvis

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

/** बोलकर सुनना (हिंदी/English) और जवाब बोलना। सब कुछ मुख्य थ्रेड से बुलाएँ। */
class VoiceIO(
    private val ctx: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onState: (OrbView.State) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onProblem: (String) -> Unit,
    private val onErrorCode: ((Int) -> Unit)? = null
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var doneCallback: (() -> Unit)? = null

    /** "hi-IN" या "en-IN" */
    var language: String = "hi-IN"

    var listening: Boolean = false
        private set

    /** बातचीत में: बोलते हुए थोड़ा रुकें तो भी वाक्य पूरा होने का इंतज़ार करो। */
    var patient: Boolean = false

    init {
        tts = TextToSpeech(ctx) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        main.post { onState(OrbView.State.SPEAKING) }
                    }

                    override fun onDone(utteranceId: String?) {
                        main.post {
                            onState(OrbView.State.IDLE)
                            runDone()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post {
                            onState(OrbView.State.IDLE)
                            runDone()
                        }
                    }
                })
            }
        }
    }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onProblem("इस फ़ोन में speech recognition उपलब्ध नहीं है (Google app चाहिए)।")
            return
        }
        stopSpeaking()
        destroyRecognizer()

        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                onState(OrbView.State.LISTENING)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onLevel(0f)
            }

            override fun onError(error: Int) {
                listening = false
                onLevel(0f)
                onState(OrbView.State.IDLE)
                val handler = onErrorCode
                if (handler != null) {
                    handler(error)
                } else if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onProblem("सुनाई नहीं दिया, फिर से बोलिए।")
                } else {
                    onProblem("आवाज़ पहचानने में दिक्कत (कोड $error)।")
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                onLevel(0f)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) {
                    onState(OrbView.State.IDLE)
                } else {
                    onFinal(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { onPartial(it) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (patient) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
        }
        recognizer = r
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val t = tts
        val clean = text.replace(Regex("[*_#`>]"), "").trim()
        if (t == null || !ttsReady || clean.isEmpty()) {
            if (onDone != null) main.post { onDone() }
            return
        }
        doneCallback = onDone
        val hindi = clean.any { it in 'ऀ'..'ॿ' }
        val locale = if (hindi) Locale("hi", "IN") else Locale("en", "IN")
        val res = t.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            onProblem("इस भाषा की आवाज़ फ़ोन में नहीं है (Settings → Text-to-speech से Hindi voice डाउनलोड करें)।")
            onState(OrbView.State.IDLE)
            main.post { runDone() }
            return
        }
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    /** सुनना और बोलना दोनों रोक दो, बाकी कॉलबैक भी रद्द। */
    fun cancel() {
        doneCallback = null
        destroyRecognizer()
        tts?.stop()
    }

    private fun runDone() {
        val cb = doneCallback
        doneCallback = null
        cb?.invoke()
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    fun shutdown() {
        destroyRecognizer()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

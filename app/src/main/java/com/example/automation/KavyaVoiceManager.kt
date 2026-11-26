package com.example.automation

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
import android.speech.tts.Voice
import android.util.Log
import com.example.model.KavyaLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class KavyaVoiceManager(private val context: Context) {
  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())

  private var tts: TextToSpeech? = null
  private var ttsReady = false
  private var currentSpeechRate = 1.0f
  private var currentLanguage = KavyaLanguage.Hinglish

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  private val _partialTranscript = MutableStateFlow("")
  val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

  private val _voiceError = MutableStateFlow<String?>(null)
  val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

  private val _selectedVoiceLabel = MutableStateFlow("Warm Female Voice")
  val selectedVoiceLabel: StateFlow<String> = _selectedVoiceLabel.asStateFlow()

  private var speechRecognizer: SpeechRecognizer? = null
  private var onFinalTranscript: ((String) -> Unit)? = null

  init {
    tts = TextToSpeech(appContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        ttsReady = true
        configurePreferredFemaleVoice(currentLanguage)
        tts?.setOnUtteranceProgressListener(
          object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
              _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
              _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
              _isSpeaking.value = false
            }
          }
        )
      } else {
        Log.w(TAG, "TextToSpeech init failed with status $status")
      }
    }
  }

  fun updateVoiceSettings(language: KavyaLanguage, speechSpeed: Float) {
    currentLanguage = language
    currentSpeechRate = speechSpeed.coerceIn(0.6f, 1.8f)
    if (ttsReady) {
      tts?.setSpeechRate(currentSpeechRate)
      tts?.setPitch(1.06f)
      configurePreferredFemaleVoice(language)
    }
  }

  private fun configurePreferredFemaleVoice(language: KavyaLanguage) {
    val engine = tts ?: return
    val preferredLocale = when (language) {
      KavyaLanguage.Hindi -> Locale("hi", "IN")
      KavyaLanguage.Hinglish -> Locale("en", "IN")
      KavyaLanguage.English -> Locale("en", "IN")
    }
    engine.language = preferredLocale

    val availableVoices = try {
      engine.voices?.toList().orEmpty()
    } catch (_: Exception) {
      emptyList()
    }

    if (availableVoices.isEmpty()) return

    val femaleHints = listOf(
      "female", "woman", "-f-", "hie", "hia", "ena", "enc", "tpf", "sfg",
      "lekha", "veena", "samantha", "karen", "moira", "neerja", "swara", "aditi"
    )
    val maleHints = listOf("male", "-m-", "hid", "hib", "enb", "end", "rishi", "prabhat")

    fun scoreVoice(voice: Voice): Int {
      var score = 0
      val name = voice.name.lowercase(Locale.ROOT)
      val features = voice.features?.joinToString(" ")?.lowercase(Locale.ROOT).orEmpty()
      val combined = "$name $features"

      if (voice.locale.language == preferredLocale.language) score += 50
      if (voice.locale.country == preferredLocale.country) score += 25
      if (voice.locale.language == "en" && preferredLocale == Locale("en", "IN")) score += 20

      if (femaleHints.any { combined.contains(it) }) score += 80
      if (maleHints.any { combined.contains(it) && !combined.contains("female") }) score -= 60

      score += when (voice.quality) {
        Voice.QUALITY_VERY_HIGH -> 30
        Voice.QUALITY_HIGH -> 20
        Voice.QUALITY_NORMAL -> 10
        else -> 0
      }
      if (!voice.isNetworkConnectionRequired) score += 5
      return score
    }

    val bestVoice = availableVoices.maxByOrNull { scoreVoice(it) }
    if (bestVoice != null) {
      engine.voice = bestVoice
      _selectedVoiceLabel.value = "${bestVoice.locale.displayLanguage} (${bestVoice.name.take(18)})"
    }
  }

  fun speak(rawText: String) {
    if (!ttsReady) return
    val cleaned = cleanTextForSpeech(rawText)
    if (cleaned.isBlank()) return

    // Automatically select Hindi locale if Devanagari characters are dominant
    val devanagariCount = cleaned.count { it in '\u0900'..'\u097F' }
    if (devanagariCount > 4) {
      configurePreferredFemaleVoice(KavyaLanguage.Hindi)
    } else {
      configurePreferredFemaleVoice(currentLanguage)
    }

    tts?.setSpeechRate(currentSpeechRate)
    tts?.setPitch(1.06f)
    val params = Bundle()
    tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, "kavya_${System.currentTimeMillis()}")
  }

  fun stopSpeaking() {
    tts?.stop()
    _isSpeaking.value = false
  }

  fun clearError() {
    _voiceError.value = null
  }

  fun startListening(language: KavyaLanguage, onResult: (String) -> Unit) {
    _voiceError.value = null
    if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
      _voiceError.value = "Speech recognition service is not available on this device."
      return
    }
    stopSpeaking()
    onFinalTranscript = onResult

    mainHandler.post {
      try {
        speechRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(
          object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
              _isListening.value = true
              _partialTranscript.value = ""
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
              _isListening.value = false
            }

            override fun onError(error: Int) {
              _isListening.value = false
              _partialTranscript.value = ""
              val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Kuch sunai nahi diya. Phir se bolo!"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Voice input timeout ho gaya. Phir try karein."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mujhe microphone permission chahiye."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                  "Speech recognition ke liye network issue aaya."
                else -> "Voice recognition abhi unavailable hai (code $error)."
              }
              _voiceError.value = msg
            }

            override fun onResults(results: Bundle?) {
              _isListening.value = false
              _partialTranscript.value = ""
              val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              val spoken = matches?.firstOrNull()?.trim().orEmpty()
              if (spoken.isNotBlank()) {
                onFinalTranscript?.invoke(spoken)
              }
            }

            override fun onPartialResults(partialResults: Bundle?) {
              val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
              _partialTranscript.value = partial
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
          }
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechLocaleTag)
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
      } catch (e: Exception) {
        _isListening.value = false
        _voiceError.value = "Could not start microphone: ${e.localizedMessage ?: "Error"}"
      }
    }
  }

  fun stopListening() {
    mainHandler.post {
      try {
        speechRecognizer?.stopListening()
      } catch (_: Exception) {
      }
      _isListening.value = false
    }
  }

  fun release() {
    mainHandler.post {
      try {
        speechRecognizer?.destroy()
        speechRecognizer = null
      } catch (_: Exception) {
      }
    }
    tts?.stop()
    tts?.shutdown()
    tts = null
  }

  private fun cleanTextForSpeech(input: String): String {
    return input
      .replace(Regex("[*`#_~>]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private companion object {
    const val TAG = "KavyaVoiceManager"
  }
}

package dev.agam.learn.ui.experience

import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.agam.learn.data.AppSettings
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** Feedback hooks that learning screens can invoke without depending on Android services. */
interface LearningFeedback {
    fun onAnswer(correct: Boolean)
    fun onMilestone()
}

private object NoLearningFeedback : LearningFeedback {
    override fun onAnswer(correct: Boolean) = Unit
    override fun onMilestone() = Unit
}

val LocalLearningFeedback = compositionLocalOf<LearningFeedback> { NoLearningFeedback }

/** True when the learner or the system has asked the app to avoid decorative motion. */
val LocalReducedMotion = compositionLocalOf { false }

internal fun Flow<AppSettings>.withSettingsFailureFallback(): Flow<AppSettings> = catch { cause ->
    if (cause is CancellationException) throw cause
    emit(
        AppSettings(
            soundEffectsEnabled = false,
            hapticsEnabled = false,
            reducedMotionEnabled = true
        )
    )
}

internal enum class LearningSound { CORRECT, INCORRECT, MILESTONE }
internal enum class LearningHaptic { SUCCESS, ERROR, MILESTONE }

internal interface LearningFeedbackEffects {
    fun play(sound: LearningSound)
    fun vibrate(haptic: LearningHaptic)
}

/** Keeps preference decisions separate from Android output so each feedback event is easy to verify. */
internal class ConfigurableLearningFeedback(
    private val effects: LearningFeedbackEffects
) : LearningFeedback {
    private var settings: AppSettings? = null

    fun updateSettings(value: AppSettings) {
        settings = value
    }

    override fun onAnswer(correct: Boolean) {
        val current = settings ?: return
        if (current.soundEffectsEnabled) {
            effects.play(if (correct) LearningSound.CORRECT else LearningSound.INCORRECT)
        }
        if (current.hapticsEnabled) {
            effects.vibrate(if (correct) LearningHaptic.SUCCESS else LearningHaptic.ERROR)
        }
    }

    override fun onMilestone() {
        val current = settings ?: return
        if (current.soundEffectsEnabled) effects.play(LearningSound.MILESTONE)
        if (current.hapticsEnabled) effects.vibrate(LearningHaptic.MILESTONE)
    }
}

/** Applies saved learning preferences and owns the short-lived Android media resources. */
@Composable
fun LearningExperienceProvider(
    settingsRepository: SettingsRepository,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val settingsFlow = remember(settingsRepository) {
        settingsRepository.observeSettings().withSettingsFailureFallback()
    }
    val settings by settingsFlow
        .collectAsState(
            initial = AppSettings(soundEffectsEnabled = false, hapticsEnabled = false)
        )
    val systemReducedMotion by rememberSystemReducedMotion()
    val effects = remember(context) { AndroidFeedbackEffects(context) }
    val feedback = remember(effects) { ConfigurableLearningFeedback(effects) }

    SideEffect { feedback.updateSettings(settings) }
    DisposableEffect(effects) {
        onDispose { effects.close() }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLearningFeedback provides feedback,
        LocalReducedMotion provides (settings.reducedMotionEnabled || systemReducedMotion)
    ) {
        content()
    }
}

@Composable
private fun rememberSystemReducedMotion(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val state = remember(context) { mutableStateOf(readSystemReducedMotion(context)) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = readSystemReducedMotion(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        state.value = readSystemReducedMotion(context)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return state
}

private fun readSystemReducedMotion(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)

/** Non-blocking SoundPool playback with gentle platform vibration patterns. */
private class AndroidFeedbackEffects(context: Context) : LearningFeedbackEffects {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val vibrator = appContext.getSystemService(Vibrator::class.java)
    private val readySamples = mutableSetOf<Int>()
    private var closed = false
    private var activeStreamId = 0
    private var soundPool: SoundPool? = null
    private var correctSound = 0
    private var incorrectSound = 0
    private var milestoneSound = 0

    init {
        if (audioManager != null) {
            val pool = runCatching {
                SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
            }.getOrNull()
            soundPool = pool
            if (pool != null) {
                try {
                    pool.setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0 && !closed && soundPool != null) readySamples += sampleId
                    }
                    correctSound = pool.load(appContext, R.raw.answer_correct, 1)
                    incorrectSound = pool.load(appContext, R.raw.answer_incorrect, 1)
                    milestoneSound = pool.load(appContext, R.raw.learning_milestone, 1)
                } catch (_: RuntimeException) {
                    soundPool = null
                    readySamples.clear()
                    runCatching { pool.release() }
                }
            }
        }
    }

    override fun play(sound: LearningSound) {
        val sampleId = when (sound) {
            LearningSound.CORRECT -> correctSound
            LearningSound.INCORRECT -> incorrectSound
            LearningSound.MILESTONE -> milestoneSound
        }
        val pool = soundPool ?: return
        if (closed || sampleId == 0 || sampleId !in readySamples) return
        val audio = audioManager ?: return
        // SoundPool follows media volume; honor silent and vibrate ringer modes as well.
        if (runCatching { audio.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_SILENT) != AudioManager.RINGER_MODE_NORMAL) return
        if (runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0) <= 0) return

        // A milestone replaces an answer cue that was just played, so the two never overlap.
        if (activeStreamId != 0) runCatching { pool.stop(activeStreamId) }
        activeStreamId = runCatching { pool.play(sampleId, 1f, 1f, 1, 0, 1f) }.getOrDefault(0)
    }

    override fun vibrate(haptic: LearningHaptic) {
        val pattern = when (haptic) {
            LearningHaptic.SUCCESS -> SUCCESS_PATTERN
            LearningHaptic.ERROR -> ERROR_PATTERN
            LearningHaptic.MILESTONE -> MILESTONE_PATTERN
        }
        if (closed) return
        val deviceVibrator = vibrator ?: return
        if (!runCatching { deviceVibrator.hasVibrator() }.getOrDefault(false)) return
        val systemHapticsEnabled = runCatching {
            Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            ) != 0
        }.getOrDefault(true)
        if (!systemHapticsEnabled) return

        runCatching {
            deviceVibrator.vibrate(
                VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
            )
        }
    }

    fun close() {
        if (closed) return
        closed = true
        readySamples.clear()
        activeStreamId = 0
        soundPool?.let { runCatching { it.release() } }
        soundPool = null
        runCatching { vibrator?.cancel() }
    }

    private companion object {
        val SUCCESS_PATTERN = VibrationPattern(
            timings = longArrayOf(0, 25),
            amplitudes = intArrayOf(0, 75)
        )
        val ERROR_PATTERN = VibrationPattern(
            timings = longArrayOf(0, 35, 45, 35),
            amplitudes = intArrayOf(0, 150, 0, 150)
        )
        val MILESTONE_PATTERN = VibrationPattern(
            timings = longArrayOf(0, 30, 30, 45, 35, 65),
            amplitudes = intArrayOf(0, 80, 0, 115, 0, 165)
        )
    }
}

private data class VibrationPattern(
    val timings: LongArray,
    val amplitudes: IntArray
)

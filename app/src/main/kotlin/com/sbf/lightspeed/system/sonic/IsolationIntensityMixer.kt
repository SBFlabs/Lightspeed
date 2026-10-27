package com.sbf.lightspeed.system.sonic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Real-time Speech / Background Acoustic Matrix Mixer with Speech Activity Detection.
 *
 * Adapted from android-realtime-voice-isolation (MIT License, Copyright (c) 2026 André Naumann).
 * Implements:
 * 1. vocalBoostLevel (0.0f..1.0f): Amplifies extracted speech stem up to +8dB with 1.3x base compensation
 *    and hyperbolic tangent soft-limiting.
 * 2. backgroundMuffleLevel (0.0f..1.0f): Progressively attenuates the non-speech background residual,
 *    smoothed by a Speech Activity Detector so non-speech segments don't pump unnaturally.
 */
internal class IsolationIntensityMixer {
    @Volatile var vocalBoostLevel: Float = 0.0f
    @Volatile var backgroundMuffleLevel: Float = 0.0f

    private var smoothedSpeechPresence = 0f

    companion object {
        private const val ISOLATED_VOICE_COMPENSATION_GAIN = 1.3f
        private const val MIN_NO_SPEECH_REDUCTION_SCALE = 0.35f
        private const val SPEECH_RATIO_FLOOR = 0.22f
        private const val SPEECH_RATIO_CEIL = 0.58f
        private const val ATTACK_ALPHA = 0.35f
        private const val RELEASE_ALPHA = 0.08f
        private const val EPSILON = 1e-6f
    }

    fun reset() {
        smoothedSpeechPresence = 0f
    }

    fun mixHop(
        dryHop: FloatArray,
        speechHop: FloatArray,
        outputHop: FloatArray,
        hopSize: Int = StftProcessor.HOP_SIZE
    ) {
        val boost = vocalBoostLevel.coerceIn(0.0f, 1.0f)
        val muffle = backgroundMuffleLevel.coerceIn(0.0f, 1.0f)

        val speechPresence = updateSpeechPresence(dryHop, speechHop, hopSize)
        val effectiveReduction = computeEffectiveBackgroundReduction(muffle, speechPresence)
        val backgroundGain = (1.0f - effectiveReduction).coerceIn(0.0f, 1.0f)

        val baseVoiceGain = 1.0f + ((ISOLATED_VOICE_COMPENSATION_GAIN - 1.0f) * effectiveReduction * speechPresence)
        val userBoostGain = 1.0f + (boost * 1.512f)
        val speechGain = baseVoiceGain * userBoostGain

        for (i in 0 until hopSize) {
            val dry = dryHop[i]
            val speech = speechHop[i]
            val background = dry - speech
            val mixed = (background * backgroundGain) + (speech * speechGain)
            outputHop[i] = softLimit(mixed)
        }
    }

    private fun updateSpeechPresence(dryHop: FloatArray, speechHop: FloatArray, hopSize: Int): Float {
        if (hopSize <= 0) return smoothedSpeechPresence
        var dryEnergy = 0f
        var speechEnergy = 0f
        for (i in 0 until hopSize) {
            val d = dryHop[i]
            val s = speechHop[i]
            dryEnergy += d * d
            speechEnergy += s * s
        }
        val dryRms = sqrt(dryEnergy / hopSize)
        val speechRms = sqrt(speechEnergy / hopSize)
        val ratio = speechRms / max(dryRms, EPSILON)
        val rawPresence = ((ratio - SPEECH_RATIO_FLOOR) / (SPEECH_RATIO_CEIL - SPEECH_RATIO_FLOOR)).coerceIn(0f, 1f)
        val alpha = if (rawPresence > smoothedSpeechPresence) ATTACK_ALPHA else RELEASE_ALPHA
        smoothedSpeechPresence += alpha * (rawPresence - smoothedSpeechPresence)
        return smoothedSpeechPresence.coerceIn(0f, 1f)
    }

    private fun computeEffectiveBackgroundReduction(requestedReduction: Float, speechPresence: Float): Float {
        val noSpeechScale = MIN_NO_SPEECH_REDUCTION_SCALE +
            ((1f - MIN_NO_SPEECH_REDUCTION_SCALE) * speechPresence.coerceIn(0f, 1f))
        return (requestedReduction * noSpeechScale).coerceIn(0f, 1f)
    }

    private fun softLimit(sample: Float): Float {
        val mag = abs(sample)
        if (mag <= 0.90f) return sample
        val limited = 0.90f + 0.099f * tanh(((mag - 0.90f) / 0.099f).toDouble()).toFloat()
        return if (sample < 0f) -limited else limited
    }
}

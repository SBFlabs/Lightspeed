package com.sbf.lightspeed.system.sonic

/**
 * JNI bridge to `libsonicdsp.so` (C++/NDK): STFT/iSTFT, polyphase 48k<->16k resampling,
 * high-band residual bypass, RMS VAD gate and post-mixer soft limiter for Neural Voice Isolation 2.0.
 * 100% offline; the library performs no I/O. Falls back (isAvailable == false) to the legacy
 * 16 kHz mono Kotlin path when the native library cannot be loaded.
 */
internal class NativeSonicDsp : AutoCloseable {
    @Volatile
    private var handle: Long = nativeCreate()

    /** [stereo48] = 768 interleaved stereo frames; fills [dry16] (256) and [spectrum] (514). */
    fun analyze(stereo48: ShortArray, dry16: FloatArray, spectrum: FloatArray) {
        val h = handle
        if (h == 0L) return
        nativeAnalyze(h, stereo48, dry16, spectrum)
    }

    fun synthesize(enhancedSpectrum: FloatArray, speech16: FloatArray) {
        val h = handle
        if (h == 0L) return
        nativeSynthesize(h, enhancedSpectrum, speech16)
    }

    /** Upsamples [mixed16] (256) and blends the delay-aligned residual into [outStereo48] (1536). */
    fun render(mixed16: FloatArray, airGain: Float, sideGain: Float, outStereo48: ShortArray) {
        val h = handle
        if (h == 0L) return
        nativeRender(h, mixed16, airGain, sideGain, outStereo48)
    }

    fun reset() {
        val h = handle
        if (h == 0L) return
        nativeReset(h)
    }

    @Synchronized
    override fun close() {
        val h = handle
        if (h != 0L) {
            handle = 0L
            nativeDestroy(h)
        }
    }

    companion object {
        const val HOP_SIZE_48K = StftProcessor.HOP_SIZE * 3
        const val SAMPLE_RATE_48K = 48_000

        val isAvailable: Boolean = try {
            System.loadLibrary("sonicdsp")
            true
        } catch (_: Throwable) {
            false
        }

        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeAnalyze(handle: Long, pcm: ShortArray, dry16: FloatArray, spectrum: FloatArray)
        @JvmStatic private external fun nativeSynthesize(handle: Long, spectrum: FloatArray, speech16: FloatArray)
        @JvmStatic private external fun nativeRender(handle: Long, mixed16: FloatArray, airGain: Float, sideGain: Float, out: ShortArray)
    }
}

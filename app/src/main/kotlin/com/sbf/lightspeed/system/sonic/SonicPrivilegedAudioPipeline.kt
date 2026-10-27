package com.sbf.lightspeed.system.sonic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import com.sbf.lightspeed.system.logSwallowed
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Privileged Real-Time Loopback Audio Capture & Neural Inference Pipeline.
 *
 * Adapted from android-realtime-voice-isolation (MIT License, Copyright (c) 2026 André Naumann).
 * Runs inside Shizuku UserService (UID 2000) with CAPTURE_AUDIO_OUTPUT & MODIFY_AUDIO_ROUTING.
 * Creates an AudioPolicy loopback capture sink excluding UID 2000 to eliminate feedback,
 * captures via REMOTE_SUBMIX_SOURCE (8) with fixedVolume so device volume never breaks capture,
 * runs 16kHz PCM streaming hops through GtcrnProcessor and IsolationIntensityMixer,
 * and renders clean speech/background audio via low-latency AudioTrack.
 */
internal class SonicPrivilegedAudioPipeline(
    private val shellContext: Context,
    private val appContext: Context,
    private val mixer: IsolationIntensityMixer,
    private val excludedUidsProvider: (() -> Set<Int>)? = null
) {
    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    private var audioPolicy: Any? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var gtcrnProcessor: GtcrnProcessor? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val REMOTE_SUBMIX_SOURCE = 8
        private const val FIXED_VOLUME_TAG = "fixedVolume"
    }

    @Synchronized
    fun start(): Boolean {
        if (isRunning.get()) return true
        return try {
            val audioManager = shellContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val myUid = Process.myUid()

            // 1. Configure AudioMixingRule excluding our output UID
            val mixingRuleClass = Class.forName("android.media.audiopolicy.AudioMixingRule")
            val ruleBuilderClass = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
            val ruleBuilder = ruleBuilderClass.getDeclaredConstructor().newInstance()

            val matchUsage = mixingRuleClass.getDeclaredField("RULE_MATCH_ATTRIBUTE_USAGE").getInt(null)
            val matchUid = mixingRuleClass.getDeclaredField("RULE_MATCH_UID").getInt(null)

            val addRuleMethod = ruleBuilderClass.methods.firstOrNull {
                (it.name == "addRule" || it.name == "addRuleInternal") && it.parameterTypes.size == 2
            } ?: ruleBuilderClass.declaredMethods.firstOrNull {
                (it.name == "addRule" || it.name == "addRuleInternal") && it.parameterTypes.size == 2
            }?.apply { isAccessible = true } ?: return false

            val excludeMixRuleMethod = ruleBuilderClass.methods.firstOrNull {
                (it.name == "excludeMixRule" || it.name == "excludeMixRuleInternal") && it.parameterTypes.size == 2
            } ?: ruleBuilderClass.declaredMethods.firstOrNull {
                (it.name == "excludeMixRule" || it.name == "excludeMixRuleInternal") && it.parameterTypes.size == 2
            }?.apply { isAccessible = true }

            for (usage in intArrayOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_GAME, AudioAttributes.USAGE_UNKNOWN)) {
                val attr = AudioAttributes.Builder().setUsage(usage).build()
                addRuleMethod.invoke(ruleBuilder, attr, matchUsage)
            }
            excludeMixRuleMethod?.invoke(ruleBuilder, matchUid, Integer.valueOf(myUid))
            excludedUidsProvider?.invoke()?.forEach { uid ->
                if (uid > 0 && uid != myUid) {
                    excludeMixRuleMethod?.invoke(ruleBuilder, matchUid, Integer.valueOf(uid))
                }
            }

            val buildRuleMethod = ruleBuilderClass.methods.firstOrNull { it.name == "build" }
                ?: ruleBuilderClass.declaredMethods.firstOrNull { it.name == "build" }?.apply { isAccessible = true }
                ?: return false
            val mixingRule = buildRuleMethod.invoke(ruleBuilder)

            // 2. Build 16kHz Mono Loopback AudioMix (AudioMix requires CHANNEL_OUT_* mask)
            val mixFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val audioMixClass = Class.forName("android.media.audiopolicy.AudioMix")
            val mixBuilderClass = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
            val routeLoopback = audioMixClass.getDeclaredField("ROUTE_FLAG_LOOP_BACK").getInt(null)

            val mixBuilder = mixBuilderClass.getConstructor(mixingRuleClass).newInstance(mixingRule)
            val setFormatMethod = mixBuilderClass.methods.firstOrNull { it.name == "setFormat" && it.parameterTypes.size == 1 }
                ?: mixBuilderClass.declaredMethods.firstOrNull { it.name == "setFormat" }?.apply { isAccessible = true }
            setFormatMethod?.invoke(mixBuilder, mixFormat)

            val setRouteFlagsMethod = mixBuilderClass.methods.firstOrNull { it.name == "setRouteFlags" && it.parameterTypes.size == 1 }
                ?: mixBuilderClass.declaredMethods.firstOrNull { it.name == "setRouteFlags" }?.apply { isAccessible = true }
            setRouteFlagsMethod?.invoke(mixBuilder, routeLoopback)

            val buildMixMethod = mixBuilderClass.methods.firstOrNull { it.name == "build" }
                ?: mixBuilderClass.declaredMethods.firstOrNull { it.name == "build" }?.apply { isAccessible = true }
                ?: return false
            val audioMix = buildMixMethod.invoke(mixBuilder)

            // 3. Register AudioPolicy
            val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
            val policyBuilderClass = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")
            val policyBuilder = try {
                policyBuilderClass.getConstructor(Context::class.java).newInstance(shellContext)
            } catch (_: Exception) {
                policyBuilderClass.getConstructor(Context::class.java).newInstance(null as Context?)
            }
            val addMixMethod = policyBuilderClass.methods.firstOrNull { it.name == "addMix" && it.parameterTypes.size == 1 }
                ?: policyBuilderClass.declaredMethods.firstOrNull { it.name == "addMix" }?.apply { isAccessible = true }
            addMixMethod?.invoke(policyBuilder, audioMix)

            val buildPolicyMethod = policyBuilderClass.methods.firstOrNull { it.name == "build" }
                ?: policyBuilderClass.declaredMethods.firstOrNull { it.name == "build" }?.apply { isAccessible = true }
                ?: return false
            val policy = buildPolicyMethod.invoke(policyBuilder)

            val status = try {
                val regStatic = AudioManager::class.java.getDeclaredMethod("registerAudioPolicyStatic", policyClass)
                regStatic.isAccessible = true
                regStatic.invoke(null, policy) as? Int ?: -1
            } catch (_: Exception) {
                val regMethod = AudioManager::class.java.getDeclaredMethod("registerAudioPolicy", policyClass)
                regMethod.isAccessible = true
                regMethod.invoke(audioManager, policy) as? Int ?: -1
            }
            if (status != 0 /* AudioManager.SUCCESS */) {
                return false
            }
            audioPolicy = policy

            // 4. Create AudioRecord via fixedVolume remote submix or createAudioRecordSink fallback
            val record = createFixedVolumeAudioRecord(policy, policyClass, audioMix, audioMixClass)
            audioRecord = record

            // 5. Create Low-Latency AudioTrack Output with ALLOW_CAPTURE_BY_NONE
            val outFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val outAttrsBuilder = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                outAttrsBuilder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            }

            val minTrackBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(outAttrsBuilder.build())
                .setAudioFormat(outFormat)
                .setBufferSizeInBytes(minTrackBuffer.coerceAtLeast(StftProcessor.HOP_SIZE * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            val track = trackBuilder.build()
            audioTrack = track

            // 6. Initialize Neural Inference Engine
            mixer.reset()
            gtcrnProcessor = GtcrnProcessor(appContext)

            // 7. Launch DSP Streaming Loop
            isRunning.set(true)
            record.startRecording()
            track.play()

            val worker = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                runProcessingLoop(record, track)
            }, "SonicPrivilegedAudioWorker")
            workerThread = worker
            worker.start()
            true
        } catch (e: Exception) {
            logSwallowed("SonicPrivilegedAudioPipeline", "start:pipeline", e)
            stop()
            false
        }
    }

    private fun createFixedVolumeAudioRecord(
        policy: Any,
        policyClass: Class<*>,
        audioMix: Any,
        audioMixClass: Class<*>
    ): AudioRecord {
        try {
            val getRegMethod = audioMixClass.methods.firstOrNull { it.name == "getRegistration" }
                ?: audioMixClass.declaredMethods.firstOrNull { it.name == "getRegistration" }?.apply { isAccessible = true }
            val registration = getRegMethod?.invoke(audioMix) as? String
            if (!registration.isNullOrEmpty()) {
                val attrBuilder = AudioAttributes.Builder()
                val setCapturePreset = AudioAttributes.Builder::class.java.methods.firstOrNull {
                    it.name == "setInternalCapturePreset" && it.parameterTypes.size == 1
                } ?: AudioAttributes.Builder::class.java.declaredMethods.firstOrNull {
                    it.name == "setInternalCapturePreset" && it.parameterTypes.size == 1
                }?.apply { isAccessible = true }
                setCapturePreset?.invoke(attrBuilder, REMOTE_SUBMIX_SOURCE)

                val addTag = AudioAttributes.Builder::class.java.methods.firstOrNull {
                    it.name == "addTag" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                } ?: AudioAttributes.Builder::class.java.declaredMethods.firstOrNull {
                    it.name == "addTag" && it.parameterTypes.size == 1
                }?.apply { isAccessible = true }
                addTag?.invoke(attrBuilder, "addr=$registration")
                addTag?.invoke(attrBuilder, FIXED_VOLUME_TAG)

                val minRecBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(StftProcessor.HOP_SIZE * 4)

                val recBuilder = AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minRecBuf)

                val setAttrs = AudioRecord.Builder::class.java.methods.firstOrNull {
                    it.name == "setAudioAttributes" && it.parameterTypes.size == 1
                } ?: AudioRecord.Builder::class.java.declaredMethods.firstOrNull {
                    it.name == "setAudioAttributes" && it.parameterTypes.size == 1
                }?.apply { isAccessible = true }
                setAttrs?.invoke(recBuilder, attrBuilder.build())

                val rec = recBuilder.build()
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    return rec
                }
                rec.release()
            }
        } catch (e: Exception) {
            logSwallowed("SonicPrivilegedAudioPipeline", "createFixedVolumeAudioRecord", e)
        }

        val createSinkMethod = policyClass.methods.firstOrNull { it.name == "createAudioRecordSink" }
            ?: policyClass.declaredMethods.firstOrNull { it.name == "createAudioRecordSink" }?.apply { isAccessible = true }
        return createSinkMethod?.invoke(policy, audioMix) as AudioRecord
    }

    private fun runProcessingLoop(record: AudioRecord, track: AudioTrack) {
        val hopSize = StftProcessor.HOP_SIZE
        val inputPcm = ShortArray(hopSize)
        val inputFloat = FloatArray(hopSize)
        val dryDelayFloat = FloatArray(hopSize)
        val outputFloat = FloatArray(hopSize)
        val outputPcm = ShortArray(hopSize)

        val proc = gtcrnProcessor ?: return

        while (isRunning.get()) {
            var readOffset = 0
            while (readOffset < hopSize && isRunning.get()) {
                val read = record.read(inputPcm, readOffset, hopSize - readOffset)
                if (read <= 0) break
                readOffset += read
            }
            if (!isRunning.get() || readOffset < hopSize) break

            PcmConverter.toFloat(inputPcm, inputFloat, hopSize)
            val isolatedSpeech = proc.process(inputFloat)
            mixer.mixHop(dryDelayFloat, isolatedSpeech, outputFloat, hopSize)
            inputFloat.copyInto(dryDelayFloat)
            PcmConverter.toShort(outputFloat, outputPcm)

            track.write(outputPcm, 0, hopSize)
        }
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        try {
            workerThread?.interrupt()
            workerThread?.join(300)
        } catch (_: Exception) {}
        workerThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) { logSwallowed("SonicPrivilegedAudioPipeline", "stop:record", e) }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (e: Exception) { logSwallowed("SonicPrivilegedAudioPipeline", "stop:track", e) }
        audioTrack = null

        try {
            gtcrnProcessor?.close()
        } catch (e: Exception) { logSwallowed("SonicPrivilegedAudioPipeline", "stop:gtcrn", e) }
        gtcrnProcessor = null

        audioPolicy?.let { policy ->
            try {
                val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
                try {
                    val unregStatic = AudioManager::class.java.getDeclaredMethod("unregisterAudioPolicyAsyncStatic", policyClass)
                    unregStatic.isAccessible = true
                    unregStatic.invoke(null, policy)
                } catch (_: Exception) {
                    val audioManager = shellContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val unregMethod = AudioManager::class.java.getDeclaredMethod(
                        "unregisterAudioPolicyAsync",
                        policyClass
                    )
                    unregMethod.isAccessible = true
                    unregMethod.invoke(audioManager, policy)
                }
            } catch (e: Exception) { logSwallowed("SonicPrivilegedAudioPipeline", "stop:policy", e) }
        }
        audioPolicy = null

        try {
            gtcrnProcessor?.close()
        } catch (e: Exception) { logSwallowed("SonicPrivilegedAudioPipeline", "stop:gtcrn", e) }
        gtcrnProcessor = null
    }

    fun isRunning(): Boolean = isRunning.get()
}

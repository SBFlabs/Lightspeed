package com.sbf.lightspeed.system

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TacticalAudioEngine {
    private const val TAG = "TacticalAudioEngine"
    private var mediaRecorder: MediaRecorder? = null
    var isRecording = false
        private set
    private var currentFilePath: String? = null

    fun isRecordingActive(): Boolean = isRecording

    fun toggle(context: Context) {
        if (isRecording) {
            stopRecording(context)
        } else {
            startRecording(context)
        }
    }

    fun startRecording(context: Context) {
        if (isRecording) return

        try {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                launchSystemVoiceRecorder(context)
                return
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "TacticalAudio").apply {
                if (!exists()) mkdirs()
            }
            val outputFile = File(outputDir, "TAC_REC_$timestamp.m4a")
            currentFilePath = outputFile.absolutePath

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            LightspeedHapticEngine.heavyClick(context)
            Toast.makeText(context, "Tactical Audio: Recording Started", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Recording started -> ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed starting internal recorder, falling back to system app", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            launchSystemVoiceRecorder(context)
        }
    }

    fun stopRecording(context: Context) {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            LightspeedHapticEngine.click(context)
            Toast.makeText(context, "Tactical Audio: Recording Saved", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Recording stopped and saved -> $currentFilePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopping recorder", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        }
    }

    fun launchSystemVoiceRecorder(context: Context) {
        val recorderIntents = listOf(
            Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
            Intent("android.provider.MediaStore.RECORD_SOUND"),
            context.packageManager.getLaunchIntentForPackage("com.google.android.apps.recorder"),
            context.packageManager.getLaunchIntentForPackage("com.sec.android.app.voicenote"),
            context.packageManager.getLaunchIntentForPackage("com.miui.soundrecorder"),
            context.packageManager.getLaunchIntentForPackage("com.oneplus.soundrecorder"),
            context.packageManager.getLaunchIntentForPackage("com.coloros.soundrecorder")
        )

        for (intent in recorderIntents) {
            if (intent != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    LightspeedHapticEngine.click(context)
                    return
                } catch (_: Exception) {}
            }
        }

        Toast.makeText(context, "Audio Recorder not found", Toast.LENGTH_SHORT).show()
    }
}

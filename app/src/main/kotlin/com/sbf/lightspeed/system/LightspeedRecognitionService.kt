package com.sbf.lightspeed.system

import android.content.Intent
import android.speech.RecognitionService

class LightspeedRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {}
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}

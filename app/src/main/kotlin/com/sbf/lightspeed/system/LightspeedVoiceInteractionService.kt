package com.sbf.lightspeed.system

import android.service.voice.VoiceInteractionService

/**
 * Service to register Lightspeed as the Android digital assistant.
 * Intercepts digital assistant triggers (long-press Power button, home hold, navigation swipe)
 * to dispatch custom mapped actions without root.
 */
class LightspeedVoiceInteractionService : VoiceInteractionService()

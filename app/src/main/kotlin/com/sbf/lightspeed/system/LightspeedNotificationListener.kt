package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService

/**
 * Lightweight NotificationListenerService for querying active MediaSession and MediaController.
 * Enabled optionally if the user grants Notification Access in Android Settings.
 */
class LightspeedNotificationListener : NotificationListenerService() {

    companion object {
        var instance: LightspeedNotificationListener? = null
            private set

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, LightspeedNotificationListener::class.java)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        LightspeedMediaManager.onNotificationListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) {
            instance = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }
}

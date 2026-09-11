package com.sbf.lightspeed.system

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedTheme
object TacticalFlyoutLauncher {

    fun launch(context: Context) {
        val intent = Intent(context, TacticalFlyoutActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        context.startActivity(intent)
    }

    fun launchChatGPT(context: Context) {
        val pm = context.packageManager
        val directIntent = pm.getLaunchIntentForPackage("com.openai.chatgpt")
        if (directIntent != null) {
            try {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(directIntent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            LightspeedHapticEngine.click(context)
        } catch (_: Exception) {
            Toast.makeText(context, "ChatGPT could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchClaude(context: Context) {
        val pm = context.packageManager
        val directIntent = pm.getLaunchIntentForPackage("com.anthropic.claude")
        if (directIntent != null) {
            try {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(directIntent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            LightspeedHapticEngine.click(context)
        } catch (_: Exception) {
            Toast.makeText(context, "Claude could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchGemini(context: Context) {
        val pm = context.packageManager
        val geminiPkgIntent = pm.getLaunchIntentForPackage("com.google.android.apps.bard")
        if (geminiPkgIntent != null) {
            try {
                geminiPkgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(geminiPkgIntent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        val assistIntents = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent(Intent.ACTION_ASSIST).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        for (intent in assistIntents) {
            try {
                context.startActivity(intent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            LightspeedHapticEngine.click(context)
        } catch (_: Exception) {
            Toast.makeText(context, "Gemini could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchLens(context: Context) {
        val lensIntents = listOf(
            context.packageManager.getLaunchIntentForPackage("com.google.ar.lens"),
            Intent().apply {
                component = ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.lens.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.intent.action.VIEW").apply {
                data = Uri.parse("googleapp://lens")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in lensIntents) {
            if (intent != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    LightspeedHapticEngine.click(context)
                    return
                } catch (_: Exception) {}
            }
        }

        Toast.makeText(context, "Google Lens not found", Toast.LENGTH_SHORT).show()
    }

    fun launchQrScanner(context: Context) {
        val qrIntents = listOf(
            Intent("com.google.android.gms.vision.barcode.SCAN").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent("com.google.zxing.client.android.SCAN").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("SCAN_MODE", "QR_CODE_MODE")
            },
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("googleapp://lens")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in qrIntents) {
            try {
                context.startActivity(intent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        Toast.makeText(context, "QR Scanner not found", Toast.LENGTH_SHORT).show()
    }
}

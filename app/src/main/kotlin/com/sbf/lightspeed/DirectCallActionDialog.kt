package com.sbf.lightspeed

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Dialog launched when the user picks the built-in Direct Call action from the Action Selection Menu.
 * Allows either picking a phone contact via Android's contact picker (zero READ_CONTACTS manifest
 * permission needed) or typing any phone number directly.
 */
@Composable
fun DirectCallActionDialog(
    activity: Activity,
    onDismiss: () -> Unit,
    onTokenCreated: (String) -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val primary = MaterialTheme.colorScheme.primary
    var phoneInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var contactBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingCreatedToken by remember { mutableStateOf<String?>(null) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val readOk = readPickedContact(activity, uri) { name, number, bmp ->
                    if (!number.isNullOrBlank()) {
                        phoneInput = number
                        if (!name.isNullOrBlank()) labelInput = name
                        contactBitmap = bmp
                        errorMessage = null
                    } else {
                        errorMessage = lsString(R.string.dcall_contact_read_error)
                    }
                }
                if (!readOk) {
                    errorMessage = lsString(R.string.dcall_contact_read_error)
                }
            }
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(activity, lsString(R.string.dcall_perm_denied_note), Toast.LENGTH_LONG).show()
        }
        val ready = pendingCreatedToken
        pendingCreatedToken = null
        if (!ready.isNullOrBlank()) {
            onTokenCreated(ready)
        }
    }

    DeckDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = lsString(R.string.dcall_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = lsString(R.string.dcall_dialog_sub),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = lsColors.textSecondary
                )

                DeckButton(
                    text = lsString(R.string.dcall_pick_contact),
                    icon = Icons.Default.Contacts,
                    isActive = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        LightspeedHapticEngine.tick(activity)
                        val pickIntent = Intent(
                            Intent.ACTION_PICK,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                        )
                        try {
                            contactPickerLauncher.launch(pickIntent)
                        } catch (e: Exception) {
                            logSwallowed("DirectCallActionDialog", "launchContactPicker", e)
                            errorMessage = lsString(R.string.dcall_contact_read_error)
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = lsColors.borderSubtle)
                    Text(
                        text = lsString(R.string.dcall_or_manual),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textSecondary
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = lsColors.borderSubtle)
                }

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = {
                        phoneInput = it
                        errorMessage = null
                    },
                    label = { Text(lsString(R.string.dcall_phone_hint), fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = lsColors.textPrimary,
                        textDirection = TextDirection.Ltr
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = lsColors.borderSubtle,
                        focusedTextColor = lsColors.textPrimary,
                        unfocusedTextColor = lsColors.textPrimary,
                        focusedLabelColor = primary,
                        unfocusedLabelColor = lsColors.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text(lsString(R.string.dcall_label_hint), fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.5.sp, color = lsColors.textPrimary),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = lsColors.borderSubtle,
                        focusedTextColor = lsColors.textPrimary,
                        unfocusedTextColor = lsColors.textPrimary,
                        focusedLabelColor = primary,
                        unfocusedLabelColor = lsColors.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val activeErr = errorMessage
                if (!activeErr.isNullOrBlank()) {
                    Text(
                        text = activeErr,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.dcall_save_btn),
                isActive = true,
                onClick = {
                    val cleanNumber = phoneInput.filter { it.isDigit() || it in "+*#,;" }
                    if (cleanNumber.none { it.isDigit() }) {
                        errorMessage = lsString(R.string.dcall_invalid_number)
                        return@DeckButton
                    }
                    val trimmedLabel = labelInput.trim()
                    val resolvedLabel = if (trimmedLabel.isNotEmpty()) {
                        trimmedLabel
                    } else {
                        lsString(R.string.dcall_default_label_prefix, cleanNumber)
                    }
                    val callIntent = Intent(
                        Intent.ACTION_CALL,
                        Uri.fromParts("tel", cleanNumber, null)
                    )
                    val token = LightspeedShortcutManager.createCustomShortcutToken(
                        context = activity,
                        pkg = "",
                        label = resolvedLabel,
                        intent = callIntent,
                        bitmap = contactBitmap
                    )
                    LightspeedActionRegistry.labelCache[token] = resolvedLabel
                    if (!LightspeedActionRegistry.allTokens.contains(token)) {
                        LightspeedActionRegistry.allTokens.add(token)
                    }
                    LightspeedHapticEngine.click(activity)
                    val hasPerm = ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        onTokenCreated(token)
                    } else {
                        pendingCreatedToken = token
                        callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    }
                }
            )
        },
        dismissButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_cancel),
                onClick = onDismiss
            )
        }
    )
}

private fun readPickedContact(
    activity: Activity,
    contactUri: Uri,
    onResult: (name: String?, number: String?, bitmap: Bitmap?) -> Unit
): Boolean {
    return try {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )
        activity.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return false
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
            val number = if (numIdx >= 0) cursor.getString(numIdx) else null
            val photoStr = if (photoIdx >= 0) cursor.getString(photoIdx) else null
            val bmp = decodeContactThumbnail(activity, photoStr)
            onResult(name, number, bmp)
            true
        } ?: false
    } catch (e: Exception) {
        logSwallowed("DirectCallActionDialog", "readPickedContact", e)
        false
    }
}

@Suppress("DEPRECATION")
private fun decodeContactThumbnail(activity: Activity, photoUriStr: String?): Bitmap? {
    if (photoUriStr.isNullOrBlank()) return null
    return try {
        val uri = Uri.parse(photoUriStr)
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(activity.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            MediaStore.Images.Media.getBitmap(activity.contentResolver, uri)
        }
        if (raw != null && (raw.width > 144 || raw.height > 144)) {
            val scaled = Bitmap.createScaledBitmap(raw, 144, 144, true)
            if (scaled !== raw) raw.recycle()
            scaled
        } else {
            raw
        }
    } catch (_: Exception) {
        null
    }
}

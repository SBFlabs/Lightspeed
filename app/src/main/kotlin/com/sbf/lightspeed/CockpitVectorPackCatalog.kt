package com.sbf.lightspeed

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Built-in searchable vector pack ("Lightspeed Vectors").
 *
 * Previews render zero-Bitmap directly via Compose [ImageVector] or Canvas path drawing so scrolling
 * or searching allocates zero idle Bitmap memory. A single 128x128 Bitmap (~64 KB) is rasterized
 * on-demand only when the user taps or long-presses a vector to assign or save it.
 */
object CockpitVectorPackCatalog {

    data class VectorPackEntry(
        val id: String,
        val label: String,
        val keywords: String,
        val imageVector: ImageVector? = null,
        val legacyCategoryId: String? = null
    )

    val ENTRIES: List<VectorPackEntry> = buildList {
        // Core Lightspeed geometric category glyphs
        val legacyKeywords = mapOf(
            "comm" to "communication chat bubble message talk",
            "internet" to "internet web browser globe world",
            "audio" to "audio headphones music sound podcast",
            "graphics" to "graphics video film play camera media",
            "games" to "games gaming controller gamepad play",
            "finance" to "finance wallet money cash bank pay",
            "learning" to "learning book read study education",
            "utilities" to "utilities wrench tool repair fix",
            "security" to "security shield privacy guard protect",
            "productivity" to "productivity briefcase work office",
            "stores" to "stores shopping bag market buy",
            "health" to "health fitness heart pulse cardio",
            "settings" to "settings gear system config styles",
            "other" to "other spark star custom"
        )
        LightspeedHudVectorIcons.BUILTIN_VECTOR_PACK.forEach { (id, label) ->
            add(
                VectorPackEntry(
                    id = id,
                    label = label.substringBefore(" ("),
                    keywords = "${label.lowercase()} ${legacyKeywords[id].orEmpty()}",
                    legacyCategoryId = id
                )
            )
        }

        // Expanded everyday, launcher, travel, security, media, and utility vectors
        fun addVec(id: String, label: String, keywords: String, vec: ImageVector) {
            add(VectorPackEntry(id = id, label = label, keywords = keywords, imageVector = vec))
        }

        // Travel, Drive & Vehicles
        addVec("vec_car", "Car", "car auto drive vehicle commute traffic", Icons.Rounded.DirectionsCar)
        addVec("vec_ev_car", "Electric Car", "ev electric car auto charge vehicle", Icons.Rounded.ElectricCar)
        addVec("vec_taxi", "Taxi", "taxi cab ride hail car", Icons.Rounded.LocalTaxi)
        addVec("vec_bus", "Bus", "bus transit public commute", Icons.Rounded.DirectionsBus)
        addVec("vec_train", "Train", "train rail metro subway commute", Icons.Rounded.Train)
        addVec("vec_bike", "Bicycle", "bike bicycle cycle sport ride", Icons.AutoMirrored.Rounded.DirectionsBike)
        addVec("vec_moto", "Motorcycle", "motorcycle scooter moped bike", Icons.Rounded.TwoWheeler)
        addVec("vec_flight", "Flight", "flight plane airplane airport travel", Icons.Rounded.Flight)
        addVec("vec_nav", "Navigation", "navigation gps route maps arrow", Icons.Rounded.Navigation)
        addVec("vec_map", "Map", "map maps places location earth", Icons.Rounded.Map)
        addVec("vec_rocket", "Rocket", "rocket lightspeed launch boost fast speed", Icons.Rounded.RocketLaunch)

        // Security & Privacy
        addVec("vec_shield", "Shield", "shield security protect guard defend", Icons.Rounded.Shield)
        addVec("vec_verified_shield", "Verified Shield", "shield verified safe check privacy", Icons.Rounded.VerifiedUser)
        addVec("vec_security_shield", "Security Badge", "security shield safety guard", Icons.Rounded.Security)
        addVec("vec_privacy_shield", "Privacy Guard", "privacy shield protect good", Icons.Rounded.GppGood)
        addVec("vec_lock", "Lock", "lock padlock vault password secure", Icons.Rounded.Lock)
        addVec("vec_key", "Key", "key passkey password unlock auth", Icons.Rounded.Key)
        addVec("vec_fingerprint", "Fingerprint", "fingerprint biometric auth unlock", Icons.Rounded.Fingerprint)
        addVec("vec_stealth", "Stealth Eye", "stealth hide incognito private mask", Icons.Rounded.VisibilityOff)

        // Home, Drawer & Actions
        addVec("vec_home", "Home", "home house launcher main", Icons.Rounded.Home)
        addVec("vec_apps", "Apps Grid", "apps grid drawer launcher menu", Icons.Rounded.Apps)
        addVec("vec_search", "Search", "search find lookup lens", Icons.Rounded.Search)
        addVec("vec_star", "Star", "star favorite rating bookmark", Icons.Rounded.Star)
        addVec("vec_bookmark", "Bookmark", "bookmark save read tag", Icons.Rounded.Bookmark)
        addVec("vec_folder", "Folder", "folder files directory container", Icons.Rounded.Folder)
        addVec("vec_bolt", "Bolt", "bolt lightning flash quick action energy", Icons.Rounded.Bolt)
        addVec("vec_flashlight", "Flashlight", "flashlight torch lamp light", Icons.Rounded.FlashlightOn)

        // Communication & Social
        addVec("vec_phone", "Phone", "phone call dialer voice", Icons.Rounded.Phone)
        addVec("vec_messages", "Messages", "messages chat sms talk", Icons.Rounded.Forum)
        addVec("vec_email", "Email", "email mail inbox message", Icons.Rounded.Email)
        addVec("vec_bell", "Alerts", "alerts bell notifications remind", Icons.Rounded.Notifications)
        addVec("vec_groups", "Contacts", "contacts people groups family team", Icons.Rounded.Groups)
        addVec("vec_share", "Share", "share send export link", Icons.Rounded.Share)

        // Media, Photo & Creativity
        addVec("vec_music", "Music", "music note audio song playlist", Icons.Rounded.MusicNote)
        addVec("vec_headphones", "Headphones", "headphones audio podcast listen", Icons.Rounded.Headphones)
        addVec("vec_play", "Play", "play video stream media", Icons.Rounded.PlayArrow)
        addVec("vec_camera", "Camera", "camera photo snap picture", Icons.Rounded.PhotoCamera)
        addVec("vec_gallery", "Gallery", "gallery photos images pictures", Icons.Rounded.Image)
        addVec("vec_palette", "Themes", "palette colors theme paint design", Icons.Rounded.Palette)
        addVec("vec_brush", "Brush", "brush draw studio edit art", Icons.Rounded.Brush)
        addVec("vec_mic", "Microphone", "mic microphone voice record audio", Icons.Rounded.Mic)

        // System, Hardware & Developer
        addVec("vec_terminal", "Terminal", "terminal shell console cli dev", Icons.Rounded.Terminal)
        addVec("vec_code", "Code", "code developer programming brackets", Icons.Rounded.Code)
        addVec("vec_bug", "Bug Hunter", "bug debug culprit report issue", Icons.Rounded.BugReport)
        addVec("vec_cpu", "Processor", "cpu chip ram memory hardware", Icons.Rounded.Memory)
        addVec("vec_storage", "Storage", "storage sd card disk space", Icons.Rounded.SdStorage)
        addVec("vec_battery", "Battery", "battery power charge energy", Icons.Rounded.BatteryChargingFull)
        addVec("vec_wifi", "Wi-Fi", "wifi wireless internet network", Icons.Rounded.Wifi)
        addVec("vec_bluetooth", "Bluetooth", "bluetooth wireless pair", Icons.Rounded.Bluetooth)
        addVec("vec_gear", "Settings", "settings gear config preferences", Icons.Rounded.Settings)
        addVec("vec_tune", "Sliders", "sliders tune controls adjust", Icons.Rounded.Tune)
        addVec("vec_screenshot", "Screenshot", "screenshot screen capture frame", Icons.Rounded.Screenshot)

        // Time, Display & Weather
        addVec("vec_moon", "Night Moon", "moon night dark mode sleep", Icons.Rounded.DarkMode)
        addVec("vec_sun", "Day Sun", "sun light brightness day mode", Icons.Rounded.LightMode)
        addVec("vec_clock", "Clock", "clock timer alarm schedule time", Icons.Rounded.Schedule)
        addVec("vec_calendar", "Calendar", "calendar date schedule events", Icons.Rounded.CalendarMonth)
        addVec("vec_weather", "Weather", "weather cloud sky forecast", Icons.Rounded.WbCloudy)

        // Everyday Life & Lifestyle
        addVec("vec_notes", "Notes", "notes memo write text pad", Icons.Rounded.EditNote)
        addVec("vec_tasks", "Tasks", "tasks check done todo checklist", Icons.Rounded.CheckCircle)
        addVec("vec_calc", "Calculator", "calculator math numbers", Icons.Rounded.Calculate)
        addVec("vec_wallet", "Wallet", "wallet money bank pay finance", Icons.Rounded.AccountBalanceWallet)
        addVec("vec_cart", "Shopping Cart", "cart shop store buy groceries", Icons.Rounded.ShoppingCart)
        addVec("vec_coffee", "Coffee", "coffee cafe cup drink tea", Icons.Rounded.LocalCafe)
        addVec("vec_food", "Food", "food dining restaurant meal", Icons.Rounded.Restaurant)
        addVec("vec_workout", "Workout", "workout gym fitness dumbbell sport", Icons.Rounded.FitnessCenter)
        addVec("vec_heart", "Heart", "heart love health favorite", Icons.Rounded.Favorite)
        addVec("vec_pets", "Pets", "pets paw dog cat animal", Icons.Rounded.Pets)
        addVec("vec_gamepad", "Controller", "gamepad console gaming play", Icons.Rounded.SportsEsports)
        addVec("vec_book", "Book", "book reading library guide", Icons.AutoMirrored.Rounded.MenuBook)
        addVec("vec_work", "Office", "work office building job", Icons.Rounded.HomeWork)
        addVec("vec_power", "Power", "power shutdown reboot standby", Icons.Rounded.PowerSettingsNew)
    }

    fun searchEntries(query: String): List<VectorPackEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ENTRIES
        val tokens = q.split(' ').filter { it.isNotBlank() }
        return ENTRIES.filter { entry ->
            val haystack = "${entry.label} ${entry.keywords} ${entry.id}".lowercase()
            tokens.all { haystack.contains(it) }
        }
    }

    /** Rasterizes ONE vector entry into a 128x128 ARGB_8888 Bitmap strictly on user selection. */
    fun renderEntryToBitmap(
        entry: VectorPackEntry,
        sizePx: Int = 128,
        colorInt: Int = Color.WHITE
    ): Bitmap {
        val legacyId = entry.legacyCategoryId
        if (legacyId != null) {
            return LightspeedHudVectorIcons.renderVectorPackBitmap(legacyId, sizePx, colorInt)
        }
        val vec = entry.imageVector ?: return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val vw = vec.viewportWidth.coerceAtLeast(1f)
        val vh = vec.viewportHeight.coerceAtLeast(1f)
        val scale = minOf(sizePx / vw, sizePx / vh) * 0.86f
        val dx = (sizePx - vw * scale) * 0.5f
        val dy = (sizePx - vh * scale) * 0.5f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = colorInt
        }
        drawVectorNode(canvas, vec.root, paint, Color.alpha(colorInt))
        canvas.restore()
        return bmp
    }

    private fun drawVectorNode(
        canvas: Canvas,
        node: VectorNode,
        paint: Paint,
        baseAlpha: Int
    ) {
        when (node) {
            is VectorPath -> {
                val composePath = node.pathData.toPath().apply {
                    fillType = node.pathFillType
                }
                paint.alpha = (baseAlpha * node.fillAlpha).toInt().coerceIn(0, 255)
                canvas.drawPath(composePath.asAndroidPath(), paint)
            }
            is VectorGroup -> {
                canvas.save()
                canvas.translate(node.translationX, node.translationY)
                if (node.rotation != 0f || node.scaleX != 1f || node.scaleY != 1f) {
                    canvas.translate(node.pivotX, node.pivotY)
                    if (node.rotation != 0f) canvas.rotate(node.rotation)
                    if (node.scaleX != 1f || node.scaleY != 1f) canvas.scale(node.scaleX, node.scaleY)
                    canvas.translate(-node.pivotX, -node.pivotY)
                }
                if (node.clipPathData.isNotEmpty()) {
                    canvas.clipPath(node.clipPathData.toPath().asAndroidPath())
                }
                for (child in node) {
                    drawVectorNode(canvas, child, paint, baseAlpha)
                }
                canvas.restore()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CockpitVectorPackTile(
    entry: CockpitVectorPackCatalog.VectorPackEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val iconColor = MaterialTheme.colorScheme.onSurface
    val iconArgb = iconColor.toArgb()

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val vec = entry.imageVector
        val legacyId = entry.legacyCategoryId
        if (vec != null) {
            Icon(
                imageVector = vec,
                contentDescription = entry.label,
                tint = iconColor,
                modifier = Modifier.size(38.dp)
            )
        } else if (legacyId != null) {
            Canvas(modifier = Modifier.size(38.dp)) {
                drawIntoCanvas { canvas ->
                    LightspeedHudVectorIcons.drawCategoryVectorIcon(
                        canvas = canvas.nativeCanvas,
                        categoryId = legacyId,
                        cx = size.width * 0.5f,
                        cy = size.height * 0.5f,
                        r = size.minDimension * 0.36f,
                        color = iconArgb,
                        alpha = 255
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.label,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

import re

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'r') as f:
    content = f.read()

# We need to swap the priority.
# Right now, it does:
# var generatedToken = ""
# if (data.hasExtra("android.content.pm.extra.PIN_ITEM_REQUEST")) { ... }
# if (generatedToken.isBlank()) { ... EXTRA_SHORTCUT_INTENT ... }

# Let's change it so it checks EXTRA_SHORTCUT_INTENT first.
old_block = """                        if (data.hasExtra("android.content.pm.extra.PIN_ITEM_REQUEST")) {
                            val pinRequest = try {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST", LauncherApps.PinItemRequest::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST") as? LauncherApps.PinItemRequest
                                }
                            } catch (_: Exception) {
                                @Suppress("DEPRECATION")
                                data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST") as? LauncherApps.PinItemRequest
                            }

                            if (pinRequest != null && pinRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                                try { pinRequest.accept() } catch (_: Exception) {}
                                val info = pinRequest.shortcutInfo
                                if (info != null) {
                                    val label = info.shortLabel?.toString() ?: info.longLabel?.toString() ?: "Shortcut"
                                    generatedToken = LightspeedShortcutManager.createPinnedShortcutToken(info.`package`, info.id, label)
                                }
                            }
                        }

                        if (generatedToken.isBlank()) {"""

new_block = """                        // 1. Prioritize authentic Intent extraction (MacroDroid style)
                        val shortcutIntent = try {
                            if (Build.VERSION.SDK_INT >= 33) {
                                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent
                            }
                        } catch (_: Exception) {
                            @Suppress("DEPRECATION")
                            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent
                        }

                        if (shortcutIntent != null) {
                            val shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
                            val rawBmp: Bitmap? = try {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON) as? Bitmap
                                }
                            } catch (_: Exception) { null }

                            val shortcutBmp = rawBmp ?: run {
                                val iconRes = try {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE) as? Intent.ShortcutIconResource
                                    }
                                } catch (_: Exception) { null }
                                if (iconRes != null) {
                                    try {
                                        val foreignRes = packageManager.getResourcesForApplication(iconRes.packageName)
                                        val id = foreignRes.getIdentifier(iconRes.resourceName, null, null)
                                        if (id != 0) {
                                            val d = foreignRes.getDrawable(id, null)
                                            if (d != null) {
                                                val w = d.intrinsicWidth.coerceIn(48, 256)
                                                val h = d.intrinsicHeight.coerceIn(48, 256)
                                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                                val canvas = android.graphics.Canvas(bmp)
                                                d.setBounds(0, 0, w, h)
                                                d.draw(canvas)
                                                bmp
                                            } else null
                                        } else null
                                    } catch (_: Exception) { null }
                                } else null
                            }

                            val pkg = shortcutIntent.`package` ?: shortcutIntent.component?.packageName ?: ""
                            generatedToken = LightspeedShortcutManager.createCustomShortcutToken(
                                context = this@CockpitGearPickerActivity,
                                pkg = pkg,
                                label = shortcutName,
                                intent = shortcutIntent,
                                bitmap = shortcutBmp
                            )
                        }

                        // 2. Fallback to PIN_ITEM_REQUEST if EXTRA_SHORTCUT_INTENT was missing
                        if (generatedToken.isBlank() && data.hasExtra("android.content.pm.extra.PIN_ITEM_REQUEST")) {
                            val pinRequest = try {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST", LauncherApps.PinItemRequest::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST") as? LauncherApps.PinItemRequest
                                }
                            } catch (_: Exception) {
                                @Suppress("DEPRECATION")
                                data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST") as? LauncherApps.PinItemRequest
                            }

                            if (pinRequest != null && pinRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                                try { pinRequest.accept() } catch (_: Exception) {}
                                val info = pinRequest.shortcutInfo
                                if (info != null) {
                                    val label = info.shortLabel?.toString() ?: info.longLabel?.toString() ?: "Shortcut"
                                    generatedToken = LightspeedShortcutManager.createPinnedShortcutToken(info.`package`, info.id, label)
                                }
                            }
                        }

                        if (false) {"""

content = content.replace(old_block, new_block)

# Remove the old extraction logic which is now at the top
old_redundant = """                            val shortcutIntent = try {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent
                                }
                            } catch (_: Exception) {
                                @Suppress("DEPRECATION")
                                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent
                            }

                            val shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
                            if (shortcutIntent != null) {
                                val rawBmp: Bitmap? = try {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON) as? Bitmap
                                    }
                                } catch (_: Exception) { null }

                                val shortcutBmp = rawBmp ?: run {
                                    val iconRes = try {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE) as? Intent.ShortcutIconResource
                                        }
                                    } catch (_: Exception) { null }
                                    if (iconRes != null) {
                                        try {
                                            val foreignRes = packageManager.getResourcesForApplication(iconRes.packageName)
                                            val id = foreignRes.getIdentifier(iconRes.resourceName, null, null)
                                            if (id != 0) {
                                                val d = foreignRes.getDrawable(id, null)
                                                if (d != null) {
                                                    val w = d.intrinsicWidth.coerceIn(48, 256)
                                                    val h = d.intrinsicHeight.coerceIn(48, 256)
                                                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                                    val canvas = Canvas(bmp)
                                                    d.setBounds(0, 0, w, h)
                                                    d.draw(canvas)
                                                    bmp
                                                } else null
                                            } else null
                                        } catch (_: Exception) { null }
                                    } else null
                                }

                                val pkg = shortcutIntent.`package` ?: shortcutIntent.component?.packageName ?: ""
                                generatedToken = LightspeedShortcutManager.createCustomShortcutToken(
                                    context = this@CockpitGearPickerActivity,
                                    pkg = pkg,
                                    label = shortcutName,
                                    intent = shortcutIntent,
                                    bitmap = shortcutBmp
                                )
                            }"""

content = content.replace(old_redundant, "")

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'w') as f:
    f.write(content)

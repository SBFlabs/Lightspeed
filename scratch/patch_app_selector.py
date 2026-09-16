import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

# Add AppSelector state
old_vars = """        var activeAppsList by remember { mutableStateOf<List<ActiveAppInfo>>(emptyList()) }"""
new_vars = """        var activeAppsList by remember { mutableStateOf<List<ActiveAppInfo>>(emptyList()) }
        var showAppSelector by remember { mutableStateOf(false) }"""
content = content.replace(old_vars, new_vars)

old_add_app = """                            Text("+ ADD APP", color = dynamicPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.clickable {
                                // TODO: Open App Selector Dialog
                            }.padding(4.dp))"""
new_add_app = """                            Text("+ ADD APP", color = dynamicPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.clickable {
                                showAppSelector = true
                            }.padding(4.dp))"""
content = content.replace(old_add_app, new_add_app)

app_selector_composable = """
    @Composable
    private fun AppSelectorOverlay(pm: PackageManager, onAppSelected: (String) -> Unit, onDismiss: () -> Unit) {
        var searchQuery by remember { mutableStateOf("") }
        var installedApps by remember { mutableStateOf<List<ActiveAppInfo>>(emptyList()) }

        LaunchedEffect(Unit) {
            kotlinx.coroutines.Dispatchers.IO.invoke {
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val apps = packages.filter { pm.getLaunchIntentForPackage(it.packageName) != null }.map {
                    ActiveAppInfo(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it))
                }.sortedBy { it.name.lowercase() }
                installedApps = apps
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.6f)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.7f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E1E1E)).padding(16.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null){}) {
                Text("ADD PINNED APP", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.lazy.LazyColumn {
                    items(installedApps.size) { i ->
                        val app = installedApps[i]
                        Row(modifier = Modifier.fillMaxWidth().clickable { onAppSelected(app.pkg) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val bmp = remember(app.icon) {
                                val d = app.icon!!
                                if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) d.bitmap else {
                                    val b = android.graphics.Bitmap.createBitmap(if (d.intrinsicWidth>0) d.intrinsicWidth else 1, if (d.intrinsicHeight>0) d.intrinsicHeight else 1, android.graphics.Bitmap.Config.ARGB_8888)
                                    val c = android.graphics.Canvas(b)
                                    d.setBounds(0,0,c.width,c.height)
                                    d.draw(c)
                                    b
                                }
                            }
                            androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(app.name, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
"""

if 'fun AppSelectorOverlay' not in content:
    content = content + "\n" + app_selector_composable


# Render it in the dock
old_render = """                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DockActionButton(Modifier.weight(1f), "MUTE ALL", Icons.AutoMirrored.Filled.VolumeOff, Color(0xFFEF4444)) {}
                            DockActionButton(Modifier.weight(1f), "DND", Icons.Default.DoNotDisturbOn, dynamicSecondary) {}
                            DockActionButton(Modifier.weight(1f), "AutoEQ", Icons.Default.Tune, Color(0xFF10B981)) {}
                        }
                    }
                }
            }"""
new_render = """                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DockActionButton(Modifier.weight(1f), "MUTE ALL", Icons.AutoMirrored.Filled.VolumeOff, Color(0xFFEF4444)) {}
                            DockActionButton(Modifier.weight(1f), "DND", Icons.Default.DoNotDisturbOn, dynamicSecondary) {}
                            DockActionButton(Modifier.weight(1f), "AutoEQ", Icons.Default.Tune, Color(0xFF10B981)) {}
                        }
                    }
                }
            }
            
            AnimatedVisibility(visible = showAppSelector, enter = fadeIn(), exit = fadeOut()) {
                AppSelectorOverlay(pm = pm, onAppSelected = { pkg ->
                    addPinnedApp(service, pkg)
                    refreshTrigger++
                    showAppSelector = false
                }, onDismiss = { showAppSelector = false })
            }"""
content = content.replace(old_render, new_render)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)


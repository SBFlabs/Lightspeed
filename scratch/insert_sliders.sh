#!/bin/bash
sed -i '/HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)/a\
        // 2. Animation Speeds\
        OverrideSliderRow(\
            icon = Icons.Default.Speed,\
            title = "Animation Speeds",\
            subtitle = "Global master scale & Window/Transition/Animator subdomains",\
            value = animationScale,\
            valueRange = 0.0f..2.0f,\
            steps = 19,\
            isEnabled = isShizukuActive,\
            onValueChange = { animationScale = it },\
            onValueChangeFinished = {\
                if (!isShizukuActive) return@OverrideSliderRow\
                LightspeedHapticEngine.tick(context)\
                scope.launch(Dispatchers.IO) {\
                    ElevatedTaskCloser.execShizuku("settings put global window_animation_scale ${animationScale}")\
                    ElevatedTaskCloser.execShizuku("settings put global transition_animation_scale ${animationScale}")\
                    ElevatedTaskCloser.execShizuku("settings put global animator_duration_scale ${animationScale}")\
                }\
            }\
        )\
\
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)\
\
        // 3. Display Metrics\
        OverrideSliderRow(\
            icon = Icons.Default.ScreenshotMonitor,\
            title = "Display Metrics",\
            subtitle = "On-the-fly PPI / DPI adjustments (Default: ${context.resources.configuration.densityDpi})",\
            value = displayDpi,\
            valueRange = 200.0f..800.0f,\
            steps = 59,\
            valueFormatter = { "${it.toInt()} dpi" },\
            isEnabled = isShizukuActive,\
            onValueChange = { displayDpi = it },\
            onValueChangeFinished = {\
                if (!isShizukuActive) return@OverrideSliderRow\
                LightspeedHapticEngine.tick(context)\
                scope.launch(Dispatchers.IO) {\
                    ElevatedTaskCloser.execShizuku("wm density ${displayDpi.toInt()}")\
                }\
            }\
        )\
\
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)\
\
        // 4. Font Scale\
        OverrideSliderRow(\
            icon = Icons.Default.FontDownload,\
            title = "Font Scale",\
            subtitle = "Tactile slider for system FONT_SCALE override",\
            value = fontScale,\
            valueRange = 0.5f..2.0f,\
            steps = 14,\
            isEnabled = isShizukuActive,\
            onValueChange = { fontScale = it },\
            onValueChangeFinished = {\
                if (!isShizukuActive) return@OverrideSliderRow\
                LightspeedHapticEngine.tick(context)\
                scope.launch(Dispatchers.IO) {\
                    ElevatedTaskCloser.execShizuku("settings put system font_scale ${fontScale}")\
                }\
            }\
        )\
\
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

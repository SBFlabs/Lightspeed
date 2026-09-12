#!/bin/bash
sed -i '91,108c\
        OverrideSliderRow(\
            icon = Icons.Default.VerticalDistribute,\
            title = "Native Edge Gesture Sovereignty",\
            subtitle = "Force back-gesture insets to 0 to allow Lightspeed full edge control",\
            value = edgeGestureScale,\
            valueRange = 0.0f..2.0f,\
            steps = 19,\
            isEnabled = isShizukuActive,\
            onValueChange = { edgeGestureScale = it },\
            onValueChangeFinished = {\
                if (!isShizukuActive) return@OverrideSliderRow\
                LightspeedHapticEngine.tick(context)\
                scope.launch(Dispatchers.IO) {\
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left ${edgeGestureScale}")\
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_right ${edgeGestureScale}")\
                }\
            }\
        )' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

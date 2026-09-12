#!/bin/bash
sed -i '/\.pointerInput(enabled, valueRange, steps) {/,/contentAlignment = Alignment.CenterStart/c\
            .pointerInput(enabled, valueRange, steps) {\
                if (!enabled) return@pointerInput\
                var accumulatedY = 0f\
                androidx.compose.foundation.gestures.detectDragGestures(\
                    onDragStart = { _ ->\
                        isDragging = true\
                        accumulatedY = 0f\
                        dragProgress = if (range > 0f) ((currentVal - valueRange.start) / range).coerceIn(0f, 1f) else 0f\
                        lastHapticSteppedVal = currentVal\
                    },\
                    onDragEnd = {\
                        onValueChangeFinished?.invoke()\
                        isDragging = false\
                    },\
                    onDragCancel = {\
                        onValueChangeFinished?.invoke()\
                        isDragging = false\
                    },\
                    onDrag = { change, dragAmount ->\
                        change.consume()\
                        accumulatedY += dragAmount.y\
                        val totalW = size.width.toFloat()\
                        val thumbRadiusPx = 14.dp.toPx()\
                        val usableWidth = (totalW - thumbRadiusPx * 2f).coerceAtLeast(1f)\
                        \
                        // Precision Scrubbing: For every 75px moved vertically, slider speed halves\
                        val precisionScale = 1f / (1f + (kotlin.math.abs(accumulatedY) / 75f))\
                        val deltaProgress = (dragAmount.x / usableWidth) * precisionScale\
                        \
                        dragProgress = (dragProgress + deltaProgress).coerceIn(0f, 1f)\
                        val rawValue = valueRange.start + dragProgress * range\
                        val steppedValue = if (steps > 0) {\
                            val stepSize = range / (steps + 1)\
                            (kotlin.math.round((rawValue - valueRange.start) / stepSize) * stepSize + valueRange.start).coerceIn(valueRange.start, valueRange.endInclusive)\
                        } else {\
                            rawValue.coerceIn(valueRange.start, valueRange.endInclusive)\
                        }\
                        if (steppedValue != lastHapticSteppedVal) {\
                            LightspeedHapticEngine.scrubTick(context)\
                            lastHapticSteppedVal = steppedValue\
                        }\
                        onValueChange(steppedValue)\
                    }\
                )\
            },\
        contentAlignment = Alignment.CenterStart' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt


#!/bin/bash
sed -i '/val steppedValue = if (steps > 0) {/,/rawValue.coerceIn(valueRange.start, valueRange.endInclusive)\n                        }/c\
                        val steppedValue = if (steps > 0) {\
                            val normalStepSize = range / (steps + 1)\
                            val isPrecisionScrubbing = kotlin.math.abs(accumulatedY) > 40f\
                            val activeStepSize = if (isPrecisionScrubbing) normalStepSize / 10f else normalStepSize\
                            (kotlin.math.round((rawValue - valueRange.start) / activeStepSize) * activeStepSize + valueRange.start).coerceIn(valueRange.start, valueRange.endInclusive)\
                        } else {\
                            rawValue.coerceIn(valueRange.start, valueRange.endInclusive)\
                        }' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

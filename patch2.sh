#!/bin/bash
sed -i '/val target = lastIndex.coerceIn(0, widgetIds.size - 1)/a \
        }\n\n        LaunchedEffect(pagerState.currentPage) {\n            android.preference.PreferenceManager.getDefaultSharedPreferences(activity).edit().putInt("refueling_stack_memory", pagerState.currentPage).apply()' app/src/main/kotlin/com/sbf/lightspeed/RefuelingWidgets.kt

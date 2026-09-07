#!/bin/bash
set -e

FILE="app/src/main/kotlin/com/sbf/lightspeed/RefuelingWidgets.kt"

# Replace the initial scroll logic with one that reads from prefs
sed -i 's/pagerState.animateScrollToPage(widgetIds.size - 1)/val lastIndex = android.preference.PreferenceManager.getDefaultSharedPreferences(activity).getInt("refueling_stack_memory", widgetIds.size - 1)\n                val target = lastIndex.coerceIn(0, widgetIds.size - 1)\n                pagerState.scrollToPage(target)/g' $FILE

# Add a LaunchedEffect to save the index whenever it changes
sed -i '/Box(/i \        LaunchedEffect(pagerState.currentPage) {\n            android.preference.PreferenceManager.getDefaultSharedPreferences(activity).edit().putInt("refueling_stack_memory", pagerState.currentPage).apply()\n        }' $FILE

echo "Stack memory patched."

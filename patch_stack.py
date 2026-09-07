import sys

file_path = "app/src/main/kotlin/com/sbf/lightspeed/RefuelingWidgets.kt"
with open(file_path, 'r') as f:
    content = f.read()

old_block = """        LaunchedEffect(widgetIds.size) {
            if (widgetIds.isNotEmpty()) {
                pagerState.animateScrollToPage(widgetIds.size - 1)
            }
        }"""

new_block = """        LaunchedEffect(widgetIds.size) {
            if (widgetIds.isNotEmpty()) {
                val lastIndex = android.preference.PreferenceManager.getDefaultSharedPreferences(activity).getInt("refueling_stack_memory", widgetIds.size - 1)
                val target = lastIndex.coerceIn(0, widgetIds.size - 1)
                pagerState.scrollToPage(target)
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            android.preference.PreferenceManager.getDefaultSharedPreferences(activity).edit().putInt("refueling_stack_memory", pagerState.currentPage).apply()
        }"""

content = content.replace(old_block, new_block)

with open(file_path, 'w') as f:
    f.write(content)

print("Stack patched via Python.")

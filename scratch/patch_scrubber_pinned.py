import re

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'r') as f:
    content = f.read()

old_block = """                val letterIndices = remember(flatItemsList) {
                    val map = mutableMapOf<Char, Int>()
                    flatItemsList.forEachIndexed { index, item ->
                        if (item is PickerRowItem.AppHeader) {
                            val firstChar = item.appName.firstOrNull()?.uppercaseChar() ?: '#'
                            val targetKey = if (firstChar in 'A'..'Z') firstChar else '#'
                            if (!map.containsKey(targetKey)) map[targetKey] = index
                        }
                    }
                    map
                }"""

new_block = """                val letterIndices = remember(flatItemsList) {
                    val map = mutableMapOf<Char, Int>()
                    flatItemsList.forEachIndexed { index, item ->
                        if (item is PickerRowItem.AppHeader && !item.isPinned) {
                            val firstChar = item.appName.firstOrNull()?.uppercaseChar() ?: '#'
                            val targetKey = if (firstChar in 'A'..'Z') firstChar else '#'
                            if (!map.containsKey(targetKey)) map[targetKey] = index
                        }
                    }
                    map
                }"""

content = content.replace(old_block, new_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'w') as f:
    f.write(content)

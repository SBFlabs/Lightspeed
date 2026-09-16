import re

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'r') as f:
    content = f.read()

old_block = """                                                                dragChange.consume()
                                                            } else {
                                                                isDragging = false
                                                                break
                                                            }"""

new_block = """                                                                dragChange.consume()
                                                            } else {
                                                                if (!hasDragged) {
                                                                    // It was a simple tap, scroll to the exact letter tapped!
                                                                    if (flatItemsList.isNotEmpty()) {
                                                                        val letterArray = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
                                                                        val letterRatio = (initialY / sideBarHeight).coerceIn(0f, 0.999f)
                                                                        val letterIndex = (letterRatio * letterArray.size).toInt()
                                                                        
                                                                        var searchIndex = letterIndex
                                                                        var finalListIdx = -1
                                                                        while (searchIndex >= 0) {
                                                                            val l = letterArray[searchIndex]
                                                                            if (letterIndices.containsKey(l)) {
                                                                                finalListIdx = letterIndices[l]!!
                                                                                break
                                                                            }
                                                                            searchIndex--
                                                                        }
                                                                        if (finalListIdx == -1) finalListIdx = 0
                                                                        coroutineScope.launch { listState.scrollToItem(finalListIdx) }
                                                                    }
                                                                }
                                                                isDragging = false
                                                                break
                                                            }"""

content = content.replace(old_block, new_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'w') as f:
    f.write(content)

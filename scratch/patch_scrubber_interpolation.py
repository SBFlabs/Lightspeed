import re

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'r') as f:
    content = f.read()

# Add showHud state alongside isDragging
old_state = "var isDragging by remember { mutableStateOf(false) }"
new_state = """var isDragging by remember { mutableStateOf(false) }
var showHud by remember { mutableStateOf(false) }"""
content = content.replace(old_state, new_state)

# Update the HUD block to use showHud instead of isDragging
old_hud = "if (isDragging && hudLetter.isNotEmpty())"
new_hud = "if (showHud && hudLetter.isNotEmpty())"
content = content.replace(old_hud, new_hud)

# Extract pointer input block and replace it
pointer_block = """                                    var currentDragY by remember { mutableStateOf(0f) }
                                    Box(
                                        modifier = Modifier"""

new_pointer_block = """                                    var currentDragY by remember { mutableStateOf(0f) }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(32.dp)
                                            .padding(start = 6.dp)
                                            .onGloballyPositioned { sideBarHeight = it.size.height.toFloat().coerceAtLeast(1f) }
                                            .pointerInput(flatItemsList) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        var hasDragged = false
                                                        var initialY = down.position.y
                                                        showHud = true

                                                        fun calculateInterpolatedIndex(yPos: Float) {
                                                            if (flatItemsList.isEmpty()) return
                                                            val letterArray = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
                                                            val letterRatio = (yPos / sideBarHeight).coerceIn(0f, 0.999f)
                                                            val fractionalIndex = letterRatio * letterArray.size
                                                            val letterIndex = fractionalIndex.toInt()
                                                            val fractionWithinLetter = fractionalIndex - letterIndex

                                                            var startListIdx = -1
                                                            var searchStart = letterIndex
                                                            while (searchStart >= 0) {
                                                                val l = letterArray[searchStart]
                                                                if (letterIndices.containsKey(l)) {
                                                                    startListIdx = letterIndices[l]!!
                                                                    break
                                                                }
                                                                searchStart--
                                                            }
                                                            if (startListIdx == -1) startListIdx = 0

                                                            var endListIdx = -1
                                                            var searchEnd = letterIndex + 1
                                                            while (searchEnd < letterArray.size) {
                                                                val l = letterArray[searchEnd]
                                                                if (letterIndices.containsKey(l)) {
                                                                    endListIdx = letterIndices[l]!!
                                                                    break
                                                                }
                                                                searchEnd++
                                                            }
                                                            if (endListIdx == -1) endListIdx = flatItemsList.size - 1

                                                            val finalFloatIdx = startListIdx + (fractionWithinLetter * (endListIdx - startListIdx))
                                                            val finalListIdx = finalFloatIdx.toInt().coerceIn(0, flatItemsList.size - 1)

                                                            coroutineScope.launch { listState.scrollToItem(finalListIdx) }

                                                            val item = flatItemsList[finalListIdx]
                                                            val label = when (item) {
                                                                is PickerRowItem.SystemHeader -> "System Actions"
                                                                is PickerRowItem.SystemCategoryHeader -> item.title
                                                                is PickerRowItem.SystemAction -> item.label
                                                                is PickerRowItem.SystemCustomizationOption -> item.title
                                                                is PickerRowItem.SystemCustomizationSlider -> item.title
                                                                is PickerRowItem.AppHeader -> item.appName
                                                                is PickerRowItem.SubHeader -> item.label.substringBefore(" (")
                                                                is PickerRowItem.ShortcutAction -> item.label
                                                            }
                                                            hudLetter = if (label.length >= 2) label.take(1).uppercase() + label.substring(1, 2).lowercase() else label.uppercase()
                                                        }

                                                        // Handle immediate tap
                                                        calculateInterpolatedIndex(initialY.coerceIn(0f, sideBarHeight))

                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val dragChange = event.changes.firstOrNull()
                                                            if (dragChange != null && dragChange.pressed) {
                                                                val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                                                if (!hasDragged && kotlin.math.abs(dragY - initialY) > 8f) {
                                                                    hasDragged = true
                                                                    isDragging = true
                                                                }
                                                                
                                                                if (hasDragged) {
                                                                    currentDragY = dragY
                                                                    calculateInterpolatedIndex(dragY)
                                                                }
                                                                dragChange.consume()
                                                            } else {
                                                                isDragging = false
                                                                showHud = false
                                                                break
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                    ) {"""

# Do a regex replace between `var currentDragY by remember { mutableStateOf(0f) }` and `) {`
import re
content = re.sub(r'                                    var currentDragY by remember \{ mutableStateOf\(0f\) \}\n                                    Box\([\s\S]*?                                            \}\n                                    \) \{', new_pointer_block, content)

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'w') as f:
    f.write(content)

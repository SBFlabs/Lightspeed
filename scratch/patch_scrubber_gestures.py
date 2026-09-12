import re

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'r') as f:
    content = f.read()

old_scrubber_block = """                                            .pointerInput(flatItemsList) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        isDragging = true
                                                        val currentY = down.position.y.coerceIn(0f, sideBarHeight)
                                                        currentDragY = currentY

                                                        if (flatItemsList.isNotEmpty()) {
                                                            val letterArray = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
                                                            val letterRatio = (currentY / sideBarHeight).coerceIn(0f, 0.999f)
                                                            val letterIndex = (letterRatio * letterArray.size).toInt()
                                                            val targetLetter = letterArray[letterIndex]
                                                            
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
                                                            hudLetter = targetLetter.toString()
                                                        }
                                                        down.consume()

                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val dragChange = event.changes.firstOrNull()
                                                            if (dragChange != null && dragChange.pressed) {
                                                                val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                                                currentDragY = dragY
                                                                if (flatItemsList.isNotEmpty()) {
                                                                    val letterArray = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
                                                                    val letterRatio = (dragY / sideBarHeight).coerceIn(0f, 0.999f)
                                                                    val letterIndex = (letterRatio * letterArray.size).toInt()
                                                                    val targetLetter = letterArray[letterIndex]
                                                                    
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
                                                                    hudLetter = targetLetter.toString()
                                                                }
                                                                dragChange.consume()
                                                            } else {
                                                                isDragging = false
                                                                break
                                                            }
                                                        }
                                                    }
                                                }
                                            }"""

new_scrubber_block = """                                            .pointerInput(flatItemsList) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        var hasDragged = false
                                                        var initialY = down.position.y

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
                                                                    if (flatItemsList.isNotEmpty()) {
                                                                        val letterArray = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
                                                                        val letterRatio = (dragY / sideBarHeight).coerceIn(0f, 0.999f)
                                                                        val letterIndex = (letterRatio * letterArray.size).toInt()
                                                                        val targetLetter = letterArray[letterIndex]
                                                                        
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
                                                                        
                                                                        // Extract the 2-letter label from the target app for the HUD overlay
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
                                                                }
                                                                dragChange.consume()
                                                            } else {
                                                                isDragging = false
                                                                break
                                                            }
                                                        }
                                                    }
                                                }
                                            }"""

content = content.replace(old_scrubber_block, new_scrubber_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'w') as f:
    f.write(content)

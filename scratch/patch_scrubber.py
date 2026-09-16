import re

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'r') as f:
    content = f.read()

old_scrubber_block = """                                    // A-Z Scrubber Sidebar
                                    Column(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(32.dp)
                                            .padding(start = 6.dp)
                                            .onGloballyPositioned { sideBarHeight = it.size.height.toFloat().coerceAtLeast(1f) }
                                            .pointerInput(flatItemsList) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        isDragging = true
                                                        val currentY = down.position.y.coerceIn(0f, sideBarHeight)

                                                        if (flatItemsList.isNotEmpty()) {
                                                            val ratio = (currentY / sideBarHeight).coerceIn(0f, 1f)
                                                            val idx = (ratio * (flatItemsList.size - 1)).toInt().coerceIn(0, flatItemsList.size - 1)
                                                            coroutineScope.launch { listState.scrollToItem(idx) }

                                                            val item = flatItemsList[idx]
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
                                                        down.consume()

                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val dragChange = event.changes.firstOrNull()
                                                            if (dragChange != null && dragChange.pressed) {
                                                                val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                                                if (flatItemsList.isNotEmpty()) {
                                                                    val ratio = (dragY / sideBarHeight).coerceIn(0f, 1f)
                                                                    val idx = (ratio * (flatItemsList.size - 1)).toInt().coerceIn(0, flatItemsList.size - 1)
                                                                    coroutineScope.launch { listState.scrollToItem(idx) }

                                                                    val item = flatItemsList[idx]
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
                                                                dragChange.consume()
                                                            } else {
                                                                isDragging = false
                                                                break
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        verticalArrangement = Arrangement.SpaceEvenly,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                            val hasApps = letterIndices.containsKey(letter)
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = letter.toString(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hasApps) dynamicSecondary else Color.White.copy(alpha = 0.2f)
                                                )
                                            }
                                        }
                                    }"""

new_scrubber_block = """                                    // A-Z Scrubber Sidebar
                                    var currentDragY by remember { mutableStateOf(0f) }
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
                                            }
                                    ) {
                                        if (isDragging) {
                                            // Morph into a physical scrollbar track and thumb
                                            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(Color.White.copy(alpha=0.1f), RoundedCornerShape(2.dp)).align(Alignment.Center))
                                            Box(
                                                modifier = Modifier
                                                    .offset(y = with(androidx.compose.ui.platform.LocalDensity.current) { (currentDragY - 16.dp.toPx()).toDp() })
                                                    .height(32.dp).width(6.dp)
                                                    .background(dynamicPrimary, RoundedCornerShape(3.dp))
                                                    .align(Alignment.TopCenter)
                                            )
                                        } else {
                                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                                                "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                                    val hasApps = letterIndices.containsKey(letter)
                                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = letter.toString(),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (hasApps) dynamicSecondary else Color.White.copy(alpha = 0.2f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }"""

content = content.replace(old_scrubber_block, new_scrubber_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/CockpitGearPickerActivity.kt', 'w') as f:
    f.write(content)

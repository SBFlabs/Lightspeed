#!/bin/bash
sed -i '306,307c\
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)\
@Composable' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

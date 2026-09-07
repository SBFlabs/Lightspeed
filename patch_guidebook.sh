#!/bin/bash
FILE="app/src/main/kotlin/com/sbf/lightspeed/settings/GuidebookBottomSheet.kt"

# Remove the old enum
sed -i '/enum class GuidebookViewMode {/,/}/d' $FILE

# Replace state initialization
sed -i 's/var viewMode by remember { mutableStateOf(GuidebookViewMode.BILINGUAL) }/val context = androidx.compose.ui.platform.LocalContext.current\n    val viewMode by com.sbf.lightspeed.system.LightspeedLanguageEngine.modeFlow.collectAsState()/g' $FILE

# Replace the listOf
sed -i 's/GuidebookViewMode.BILINGUAL to "Bilingual"/com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT to "Co-Pilot"/g' $FILE
sed -i 's/GuidebookViewMode.VESSEL_LORE_ONLY to "Vessel Lore"/com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE to "Vessel Lore"/g' $FILE
sed -i 's/GuidebookViewMode.TACTICAL_ANDROID_ONLY to "Tactical Android"/com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS to "Clear Comms"/g' $FILE

# Replace the clickable action
sed -i 's/clickable { viewMode = mode }/clickable { com.sbf.lightspeed.system.LightspeedLanguageEngine.setMode(context, mode) }/g' $FILE

# Replace the when block
sed -i 's/GuidebookViewMode.VESSEL_LORE_ONLY/com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE/g' $FILE
sed -i 's/GuidebookViewMode.TACTICAL_ANDROID_ONLY/com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS/g' $FILE
sed -i 's/GuidebookViewMode.BILINGUAL/com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT/g' $FILE


#!/bin/bash
set -e

FILE="app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt"

# 1. Inject currentLanguageMode
sed -i '/val isImportSuccess by viewModel.isImportSuccess.collectAsState()/a \    val currentLanguageMode by com.sbf.lightspeed.system.LightspeedLanguageEngine.modeFlow.collectAsState()' $FILE

# 2. Update sectionTitles0
sed -i 's/val sectionTitles0 = remember(isLeftFlankUnified) {/val sectionTitles0 = remember(isLeftFlankUnified, currentLanguageMode) {/g' $FILE
sed -i 's/"Left Deflector — Flank Vector Zones (Upper & Lower)"/"${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LEFT_DEFLECTOR)} — Flank Vector Zones (Upper & Lower)"/g' $FILE
sed -i 's/"Left Deflector — Astrogation Core Zone"/"${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LEFT_DEFLECTOR)} — Astrogation Core Zone"/g' $FILE
sed -i 's/"Left Deflector — Upper Vector Zone"/"${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LEFT_DEFLECTOR)} — Upper Vector Zone"/g' $FILE
sed -i 's/"Left Deflector — Lower Vector Zone"/"${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LEFT_DEFLECTOR)} — Lower Vector Zone"/g' $FILE

# 3. Update sectionTitles1
sed -i 's/val sectionTitles1 = remember {/val sectionTitles1 = remember(currentLanguageMode) {/g' $FILE
sed -i 's/"Sensor Area"/com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.SENSOR_AREA)/g' $FILE
sed -i 's/"Telemetry & Indicators"/com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.TELEMETRY_AND_INDICATORS)/g' $FILE
sed -i 's/"Tactical Hardware Deck"/com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.TACTICAL_HARDWARE)/g' $FILE
sed -i 's/"Refueling Bay"/com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.REFUELING_BAY)/g' $FILE
sed -i 's/"Configuration Vault"/com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.SHIP_DATA_VAULT)/g' $FILE
sed -i 's/"Experimental Labs"/com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.EXPERIMENTAL_LABS)/g' $FILE

# 4. Update sectionTitles2
sed -i 's/val sectionTitles2 = remember(isRightFlankUnified) {/val sectionTitles2 = remember(isRightFlankUnified, currentLanguageMode) {/g' $FILE
sed -i 's/"Right Deflector — Flank Vector Zones (Upper & Lower)"/"${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Flank Vector Zones (Upper & Lower)"/g' $FILE
sed -i 's/"Right Deflector — Astrogation Core Zone"/"${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Astrogation Core Zone"/g' $FILE
sed -i 's/"Right Deflector — Upper Vector Zone"/"${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Upper Vector Zone"/g' $FILE
sed -i 's/"Right Deflector — Lower Vector Zone"/"${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Lower Vector Zone"/g' $FILE

# 5. Update tabTitles
sed -i 's/val tabTitles = listOf("◀ Deflectors", "HUD STRIP", "Deflectors ▶")/val deflStr = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.DEFLECTORS).replace("Left \& Right ", ""); val hudStr = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.HUD_STRIP).uppercase(); val tabTitles = listOf("◀ $deflStr", hudStr, "$deflStr ▶")/g' $FILE

echo "Language Engine Hooked up successfully."

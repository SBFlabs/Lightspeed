import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'r') as f:
    content = f.read()

helper_func = """    private fun intentToAmStartCommand(intent: Intent): String {
        val sb = java.lang.StringBuilder("am start ")
        intent.action?.let { sb.append("-a $it ") }
        intent.dataString?.let { sb.append("-d '$it' ") }
        intent.type?.let { sb.append("-t '$it' ") }
        intent.component?.let { sb.append("-n ${it.flattenToShortString()} ") }
        intent.extras?.keySet()?.forEach { key ->
            when (val value = intent.extras?.get(key)) {
                is String -> sb.append("--es '$key' '${value.replace("'", "'\\''")}' ")
                is Boolean -> sb.append("--ez '$key' $value ")
                is Int -> sb.append("--ei '$key' $value ")
                is Long -> sb.append("--el '$key' $value ")
                is Float -> sb.append("--ef '$key' $value ")
            }
        }
        return sb.toString().trim()
    }
}"""

content = content.replace("}", helper_func) # wait, replacing '}' will replace all of them.

# Let's do it safely

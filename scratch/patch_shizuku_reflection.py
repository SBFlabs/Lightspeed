import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedAppSovereigntyEngine.kt', 'r') as f:
    engine_content = f.read()

# Replace Shizuku.newProcess with reflection
old_call = """val process = Shizuku.newProcess(cmd, null, null)"""
new_call = """val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                method.isAccessible = true
                val process = method.invoke(null, cmd, null, null) as Process"""
engine_content = engine_content.replace(old_call, new_call)

old_get_call = """val process = Shizuku.newProcess(arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME"), null, null)"""
new_get_call = """val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME"), null, null) as Process"""
engine_content = engine_content.replace(old_get_call, new_get_call)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedAppSovereigntyEngine.kt', 'w') as f:
    f.write(engine_content)

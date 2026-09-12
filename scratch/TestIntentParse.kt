import android.content.Intent

fun main() {
    val uri = "intent:0122%20717%208085#Intent;scheme=tel;action=com.android.dialer.shortcuts.CALL_CONTACT;launchFlags=0x4000000;extendedLaunchFlags=0x4;package=com.sh.smart.caller;component=com.sh.smart.caller/com.android.dialer.shortcuts.CallContactActivity;S.shortcut_action=android.intent.action.CALL;end"
    val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
    println("Action: ${intent.action}")
    println("Has Extra shortcut_action: ${intent.hasExtra("shortcut_action")}")
    println("Extra shortcut_action: ${intent.getStringExtra("shortcut_action")}")
    
    if (intent.hasExtra("shortcut_action")) {
        intent.action = intent.getStringExtra("shortcut_action")
        intent.component = null
    }
    
    println("New Action: ${intent.action}")
    println("New Component: ${intent.component}")
    println("New Data: ${intent.data}")
}

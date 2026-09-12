import android.content.Intent
import android.net.Uri

fun main() {
    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:123456789"))
    println(intent.toUri(Intent.URI_INTENT_SCHEME))
}

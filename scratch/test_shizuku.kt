import rikka.shizuku.Shizuku

fun muteApp(pkg: String) {
    if (Shizuku.pingBinder()) {
        val process = Shizuku.newProcess(arrayOf("cmd", "appops", "set", pkg, "AUDIO_MEDIA_VOLUME", "deny"), null, null)
        process.waitFor()
    }
}

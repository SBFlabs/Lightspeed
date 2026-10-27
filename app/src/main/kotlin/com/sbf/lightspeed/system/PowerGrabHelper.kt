package com.sbf.lightspeed.system

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

object PowerGrabHelper {

    fun findPowerDevice(): String? = "auto"

    private fun resolveLsinputdPath(context: Context): String {
        val nativeFile = java.io.File(context.applicationInfo.nativeLibraryDir, "liblsinputd.so")
        if (nativeFile.exists() && nativeFile.length() > 0L) {
            return nativeFile.absolutePath
        }
        val extracted = java.io.File(context.codeCacheDir, "liblsinputd.so")
        if (!extracted.exists() || extracted.length() == 0L) {
            try {
                java.util.zip.ZipFile(context.applicationInfo.sourceDir).use { zip ->
                    val entry = zip.getEntry("lib/arm64-v8a/liblsinputd.so")
                        ?: zip.getEntry("lib/armeabi-v7a/liblsinputd.so")
                    if (entry != null) {
                        zip.getInputStream(entry).use { input ->
                            extracted.outputStream().use { output -> input.copyTo(output) }
                        }
                        extracted.setReadable(true, false)
                        extracted.setExecutable(true, false)
                    }
                }
            } catch (e: Exception) {
                logSwallowed("PowerGrabHelper", "extractLsinputd", e)
            }
        }
        return if (extracted.exists()) extracted.absolutePath else nativeFile.absolutePath
    }

    fun start(context: Context): Process? {
        val devicePath = findPowerDevice()
        if (devicePath == null) {
            Log.w("PowerGrabHelper", "start: Aborting PowerGrabHelper start, no safe power device found")
            return null
        }
        val binPath = resolveLsinputdPath(context)
        val cmd = "cp '$binPath' /data/local/tmp/lsinputd 2>/dev/null && chmod 755 /data/local/tmp/lsinputd && /data/local/tmp/lsinputd $devicePath 3600 || '$binPath' $devicePath 3600"
        val proc = ElevatedTaskCloser.execShizuku(cmd) ?: return null
        return InterceptingProcess(proc)
    }

    fun sendCommand(proc: Process?, command: String) {
        if (proc == null) return
        try {
            val os = proc.outputStream
            os.write("$command\n".toByteArray(Charsets.UTF_8))
            os.flush()
        } catch (e: IOException) {
            logSwallowed("PowerGrabHelper", "sendCommand", e)
        }
    }

    fun sendDown(proc: Process?) = sendCommand(proc, "DOWN")
    fun sendUp(proc: Process?) = sendCommand(proc, "UP")
    fun sendTap(proc: Process?) = sendCommand(proc, "TAP")
    fun sendBack(proc: Process?) = sendCommand(proc, "BACK")

    private class InterceptingProcess(private val original: Process) : Process() {
        private val wrappedStream by lazy { LoggingInputStream(original.inputStream) }
        override fun getOutputStream() = original.outputStream
        override fun getInputStream(): InputStream = wrappedStream
        override fun getErrorStream() = original.errorStream
        override fun waitFor() = original.waitFor()
        override fun exitValue() = original.exitValue()
        override fun destroy() = original.destroy()
    }

    private class LoggingInputStream(private val delegate: InputStream) : InputStream() {
        private val buffer = ByteArrayOutputStream()

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) {
                if (b == '\n'.code) {
                    processLine(buffer.toString("UTF-8"))
                    buffer.reset()
                } else if (b != '\r'.code) {
                    buffer.write(b)
                }
            } else if (buffer.size() > 0) {
                processLine(buffer.toString("UTF-8"))
                buffer.reset()
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = delegate.read(b, off, len)
            if (bytesRead > 0) {
                for (i in off until (off + bytesRead)) {
                    val byteVal = b[i].toInt() and 0xFF
                    if (byteVal == '\n'.code) {
                        processLine(buffer.toString("UTF-8"))
                        buffer.reset()
                    } else if (byteVal != '\r'.code) {
                        buffer.write(byteVal)
                    }
                }
            } else if (bytesRead == -1 && buffer.size() > 0) {
                processLine(buffer.toString("UTF-8"))
                buffer.reset()
            }
            return bytesRead
        }

        private fun processLine(line: String) {
            val trimmed = line.trim()
            if (trimmed.startsWith("LSINPUTD_DEV")) {
                Log.i("PowerGrabHelper", "findPowerDevice: Selected power device ${trimmed.removePrefix("LSINPUTD_DEV").trim()}")
            } else if (trimmed.startsWith("LSINPUTD_EXIT")) {
                Log.w("PowerGrabHelper", "findPowerDevice: No safe power input device found ($trimmed)")
            }
        }

        override fun close() {
            delegate.close()
        }
    }
}

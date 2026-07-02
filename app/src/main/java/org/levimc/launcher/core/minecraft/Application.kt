package org.levimc.launcher.core.minecraft

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.levimc.launcher.core.crash.CrashReporter
import org.levimc.launcher.settings.FeatureSettings
import org.levimc.launcher.ui.dialogs.LogcatOverlayManager
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class LauncherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        installJavaConnectRuntimeFiles()
        FeatureSettings.init(applicationContext)
        CrashReporter.init(this)
        val processName = Application.getProcessName()
        if (processName.endsWith(":crash")) return

        LogcatOverlayManager.init(this)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    private fun installJavaConnectRuntimeFiles() {
        runCatching { installJavaConnectJre() }
            .onFailure { Log.w("JavaConnect", "Java runtime install failed", it) }
    }

    private fun installJavaConnectJre() {
        val tarAssetName = "javaconnect/jre21-android-arm64.tar"
        val xzAssetName = "javaconnect/jre21-android-arm64.tar.xz"
        val assetName = when {
            assetExists(tarAssetName) -> tarAssetName
            assetExists(xzAssetName) -> xzAssetName
            else -> tarAssetName
        }
        val jreDir = File(filesDir, "JavaConnect/jre")
        val marker = File(jreDir, ".javaconnect_launcher")
        val markerText = "asset=$assetName\nversion=4\n"
        val existingJava = findJavaBin(jreDir)
        if (existingJava != null && marker.isFile && marker.readText() == markerText) {
            chmodRuntime(jreDir)
            return
        }

        Log.i("JavaConnect", "Extracting bundled Java 21 runtime to ${jreDir.absolutePath}")
        jreDir.deleteRecursively()
        jreDir.mkdirs()

        assets.open(assetName).use { assetInput ->
            if (assetName.endsWith(".xz")) {
                XZInputStream(assetInput).use { xzInput ->
                    extractTar(xzInput, jreDir)
                }
            } else {
                extractTar(assetInput, jreDir)
            }
        }

        chmodRuntime(jreDir)
        val javaBin = findJavaBin(jreDir)
            ?: throw IllegalStateException("bin/java missing after Java runtime extraction")
        javaBin.setExecutable(true, false)
        marker.writeText(markerText)
        Log.i("JavaConnect", "Bundled Java 21 runtime ready: ${javaBin.parentFile?.parentFile?.absolutePath ?: jreDir.absolutePath}")
    }

    private fun assetExists(name: String): Boolean = runCatching {
        assets.open(name).close()
        true
    }.getOrDefault(false)

    private fun extractTar(input: InputStream, root: File) {
        val header = ByteArray(512)
        val links = ArrayList<Pair<File, String>>()
        while (true) {
            if (!readBlock(input, header)) break
            if (header.all { it.toInt() == 0 }) break

            val rawName = tarString(header, 0, 100)
            val prefix = tarString(header, 345, 155)
            val name = safeTarPath(if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName)
            val size = tarOctal(header, 124, 12)
            val mode = tarOctal(header, 100, 8).toInt()
            val type = header[156].toInt().toChar()
            val linkName = tarString(header, 157, 100)
            val padded = ((size + 511L) / 512L) * 512L

            if (name == null) {
                skipBytes(input, padded)
                continue
            }

            val out = File(root, name)
            when (type) {
                '5' -> out.mkdirs()
                '2', '1' -> links.add(out to linkName)
                '0', '\u0000' -> {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { copyBytes(input, it, size) }
                    out.setReadable(true, false)
                    out.setWritable(true, true)
                    if ((mode and 0b001001001) != 0) out.setExecutable(true, false)
                    skipBytes(input, padded - size)
                    continue
                }
                else -> Unit
            }
            skipBytes(input, padded)
        }

        for ((out, targetName) in links) {
            val target = safeTarPath(targetName)?.let { File(root, it) }
            if (target != null && target.isFile) {
                out.parentFile?.mkdirs()
                target.inputStream().use { inputStream ->
                    out.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
                }
                out.setReadable(true, false)
                out.setWritable(true, true)
                if (out.parentFile?.name == "bin" || out.name.endsWith(".so")) out.setExecutable(true, false)
            }
        }
    }

    private fun findJavaBin(root: File): File? =
        root.walkTopDown().firstOrNull { it.isFile && it.name == "java" && it.parentFile?.name == "bin" }

    private fun chmodRuntime(dir: File) {
        if (!dir.exists()) return
        dir.walkTopDown().forEach { file ->
            if (file.isDirectory) {
                file.setReadable(true, false)
                file.setExecutable(true, false)
            } else {
                file.setReadable(true, false)
                file.setWritable(true, true)
                if (file.parentFile?.name == "bin" || file.name.endsWith(".so")) {
                    file.setExecutable(true, false)
                }
            }
        }
    }

    private fun readBlock(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) return offset == 0
            offset += n
        }
        return true
    }

    private fun copyBytes(input: InputStream, output: OutputStream, bytes: Long) {
        val buffer = ByteArray(128 * 1024)
        var left = bytes
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) throw IllegalStateException("Unexpected EOF in tar")
            output.write(buffer, 0, n)
            left -= n
        }
    }

    private fun skipBytes(input: InputStream, bytes: Long) {
        val buffer = ByteArray(32 * 1024)
        var left = bytes
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
            } else {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) break
                left -= n
            }
        }
    }

    private fun tarString(buffer: ByteArray, start: Int, len: Int): String {
        var end = start
        val max = start + len
        while (end < max && buffer[end].toInt() != 0) end++
        return buffer.copyOfRange(start, end).toString(Charsets.UTF_8).trim()
    }

    private fun tarOctal(buffer: ByteArray, start: Int, len: Int): Long {
        var i = start
        val end = start + len
        while (i < end && (buffer[i].toInt() == 0 || buffer[i].toInt().toChar().isWhitespace())) i++
        var value = 0L
        while (i < end) {
            val c = buffer[i].toInt()
            if (c < '0'.code || c > '7'.code) break
            value = value * 8L + (c - '0'.code)
            i++
        }
        return value
    }

    private fun safeTarPath(path: String): String? {
        var p = path.replace('\\', '/').removePrefix("./")
        while (p.startsWith('/')) p = p.removePrefix("/")
        if (p.isBlank()) return null
        val parts = p.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == ".." || it.indexOf('\u0000') >= 0 }) return null
        return parts.joinToString("/")
    }

    companion object {
        @JvmStatic
        lateinit var context: Context
            private set

        @JvmStatic
        lateinit var preferences: SharedPreferences
            private set
    }
}

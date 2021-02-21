package moe.shizuku.manager.starter

import android.content.Context
import android.os.Build
import android.os.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ktx.createDeviceProtectedStorageContextCompat
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.ktx.loge
import rikka.core.os.FileUtils
import java.io.*
import java.util.zip.ZipFile

object Starter {

    private var commandInternal = arrayOfNulls<String>(2)

    val dataCommand get() = commandInternal[0]!!

    val sdcardCommand get() = commandInternal[1]!!

    val adbCommand: String
        get() = "adb shell $sdcardCommand"

    fun writeSdcardFilesAsync(context: Context) {
        GlobalScope.launch(Dispatchers.IO) { writeSdcardFiles(context.applicationContext) }
    }

    private fun writeSdcardFiles(context: Context) {
        if (commandInternal[1] != null) {
            logd("already written")
            return
        }

        val um = context.getSystemService(UserManager::class.java)!!
        val unlocked = Build.VERSION.SDK_INT < 24 || um.isUserUnlocked
        if (!unlocked) return

        try {
            val dir = context.getExternalFilesDir(null)?.parentFile ?: return
            val starter = copyStarter(context, File(dir, "starter"))
            val sh = writeScript(context, File(dir, "start.sh"), starter)
            commandInternal[1] = "sh $sh"
            logd(commandInternal[1]!!)
        } catch (e: IOException) {
            loge("write files", e)
        }
    }

    fun writeDataFiles(context: Context) {
        if (commandInternal[0] != null) {
            logd("already written")
            return
        }

        try {
            val dir = context.createDeviceProtectedStorageContextCompat().filesDir?.parentFile ?: return
            val starter = copyStarter(context, File(dir, "starter"))
            val sh = writeScript(context, File(dir, "start.sh"), starter)
            commandInternal[0] = "sh $sh"
            logd(commandInternal[0]!!)
        } catch (e: IOException) {
            loge("write files", e)
        }
    }

    private fun copyStarter(context: Context, out: File): String {
        val so = "lib/${Build.SUPPORTED_ABIS[0]}/libshizuku.so"
        val ai = context.applicationInfo

        val fos = FileOutputStream(out)
        val apk = ZipFile(ai.sourceDir)
        val entries = apk.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement() ?: break
            if (entry.name != so) continue

            val buf = ByteArray(entry.size.toInt())
            val dis = DataInputStream(apk.getInputStream(entry))
            dis.readFully(buf)
            FileUtils.copy(ByteArrayInputStream(buf), fos)
            break
        }
        return out.absolutePath
    }

    private fun writeScript(context: Context, out: File, starter: String): String {
        if (!out.exists()) {
            out.createNewFile()
        }
        val `is` = BufferedReader(InputStreamReader(context.resources.openRawResource(R.raw.start)))
        val os = PrintWriter(FileWriter(out))
        var line: String?
        while (`is`.readLine().also { line = it } != null) {
            os.println(line!!.replace("%%%STARTER_PATH%%%", starter))
        }
        os.flush()
        os.close()
        return out.absolutePath
    }
}
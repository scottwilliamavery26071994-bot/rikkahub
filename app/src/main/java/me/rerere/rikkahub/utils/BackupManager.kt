package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    suspend fun export(context: Context, dest: Uri): String = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "backup.zip")
        ZipOutputStream(FileOutputStream(tmp)).use { zip ->
            val ws = File(context.filesDir, "workspace")
            if (ws.exists()) addDir(ws, "workspace/", zip)
        }
        context.contentResolver.openOutputStream(dest)?.use { out ->
            tmp.inputStream().use { it.copyTo(out) }
        }
        val kb = tmp.length() / 1024; tmp.delete()
        "导出完成: ${kb}KB"
    }

    suspend fun restore(context: Context, src: Uri): String = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "restore.zip")
        context.contentResolver.openInputStream(src)?.use { input ->
            FileOutputStream(tmp).use { out -> input.copyTo(out) }
        } ?: return@withContext "无法读取"
        val base = context.filesDir
        ZipInputStream(tmp.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val target = File(base, e.name)
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { o -> zip.copyTo(o) }
                }
                e = zip.nextEntry
            }
        }
        tmp.delete()
        "恢复完成，请重启"
    }

    private fun addDir(dir: File, prefix: String, zip: ZipOutputStream) {
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                zip.putNextEntry(ZipEntry(prefix + f.relativeTo(dir).path))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

package com.kabo.lotus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.kabo.lotus.FileExtension.createFileIfNotExist
import com.kabo.lotus.FileExtension.separator
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/***
 *  Write Logcat in the application folder.
 *  Zip compress and save the files according to the specified total of files.
 *  @param context Context
 *  @param iLogCollect ILogCollect. Receive a Zip compressed file of log files.
 *  @param commandWithOption Array<String>. Command to retrieve, starting with logcat. Options are array.
 *  @param linePerFile Int. Maximum number of lines per file. Minus is unlimited.
 *  @param excludedWords Array<String>?. Words to exclude.
 *  @param totalOfFiles Int. Total of files to zip. Minus is unlimited.
 */
class LogCollect(
    private val context: Context,
    private val iLogCollect: ILogCollect,
    private val commandWithOption: Array<String> = arrayOf("logcat", "-b default", "*:D"),
    private val linePerFile: Int = 50000,
    private val excludedWords: Array<String>? = null,
    private val totalOfFiles: Int = 5,
) {
    interface ILogCollect {
        /***
         * Get the compressed file.
         * @param zipFile File.
         */
        fun onReceivedZipFile(zipFile: File)
    }

    private companion object {
        const val TAG = "LogCollect"
        const val LOG_DIR = "log"
        const val ZIP_DIR = "zip"
        const val INDEX_FILE = "index"
    }

    private val isCollecting: AtomicBoolean = AtomicBoolean(false)
    private var logWriter: LogWriter? = null
    private var logReader: LogReader? = null

    /***
     * Returns whether the log is being collected.
     * @return true == collecting.
     */
    fun hasCollecting(): Boolean {
        return isCollecting.get()
    }

    /***
     * Start collecting log.
     * @RequiresPermission Manifest.permission.READ_LOGS
     */
    @RequiresPermission(Manifest.permission.READ_LOGS)
    fun start() {
        if (context.checkSelfPermission(android.Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Required READ_LOGS.")
            return
        }

        if (hasCollecting()) {
            Log.e(TAG, "LogCollect has already stared.")
            return
        }

        isCollecting.set(true)
        logWriter = LogWriter(context, iLogCollect, totalOfFiles)
        logWriter?.init()

        logReader = LogReader(
            object : ILogListener {
                override fun readLine(line: String) {
                    logWriter?.writeWithDivided(line, linePerFile)
                }

                override fun error(message: String) {
                    Log.e(TAG, message)
                }
            },
            excludedWords, commandWithOption
        )
        logReader?.start()
    }

    /***
     * Stop collecting log.
     */
    fun stop() {
        if (!hasCollecting()) {
            Log.e(TAG, "LogCollect has not stared.")
            return
        }

        isCollecting.set(false)
        logReader?.stop()
        logWriter?.close()
    }

    private class LogCompression(private val context: Context) {
        companion object {
            private const val zipBufferSize = 1024 * 1024 // 1MB
        }

        fun compress(targetDir: File): File? {
            Log.v(TAG, "compress")
            val destFile = createDestFile() ?: return null
            if (!zipDir(targetDir, destFile)) {
                Log.e(TAG, "failed zipDir")
                // ng
                if (destFile.exists() && destFile.isFile) {
                    destFile.delete()
                }
                return null
            }

            // ok
            // remove logDir
            if (targetDir.isDirectory) {
                Log.v(TAG, "Delete LogDir")
                targetDir.deleteRecursively()
            }

            return destFile
        }

        private fun createDestFile(): File? {
            val zipDir: File = context.getDir(ZIP_DIR, Context.MODE_PRIVATE)
            val indexFile = File(zipDir, INDEX_FILE)
            if (!indexFile.createFileIfNotExist()) {
                return null
            }
            val index = indexFile.readLines().size
            val file = File(
                zipDir,
                "${index + 1}_${SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(Date())}.zip"
            )
            if (!file.createFileIfNotExist()) {
                return null
            }
            indexFile.appendText(file.name + separator)

            return file
        }

        private fun zipDir(
            targetDir: File,
            destFile: File,
            zipFileCoding: Charset = StandardCharsets.UTF_8
        ): Boolean {
            if (!targetDir.exists() || !targetDir.isDirectory) {
                return false
            }

            if (destFile.exists() && destFile.isFile) {
                destFile.delete()
            }
            if (!destFile.createNewFile()) {
                return false
            }

            kotlin.runCatching {
                zipDirInternal(targetDir, destFile, zipFileCoding)
            }.onFailure {
                return false
            }
            return true
        }

        private fun zipDirInternal(targetDir: File, destFile: File, zipFileCoding: Charset) {
            destFile.outputStream().use { fileOutputStream ->
                ZipOutputStream(
                    BufferedOutputStream(fileOutputStream),
                    zipFileCoding
                ).use { zipOutStream ->
                    val files = targetDir.listFiles() ?: throw RuntimeException("Not found files")
                    files.forEach { targetFile ->
                        if (targetFile.isFile) {
                            zipOutStream.putNextEntry(ZipEntry(targetFile.name))
                            FileInputStream(targetFile).use { inputStream ->
                                val bytes = ByteArray(zipBufferSize)
                                var length: Int
                                while (inputStream.read(bytes).also { length = it } >= 0) {
                                    zipOutStream.write(bytes, 0, length)
                                }
                            }
                        }
                    }
                }
            }
        }

    }


    /***
     * Log writer
     */
    private class LogWriter(
        private val context: Context,
        private val iLogCollectListener: ILogCollect,
        private val totalOfFiles: Int = 5
    ) {
        private var logFileOutputStream: FileOutputStream? = null
        private val writeLineCounter = AtomicInteger()
        private val logCompression = LogCompression(context)

        /***
         * Configure the log folder, Index.
         *  When the number of files reaches the required limit,
         *  the files are zipped and the zipped files are deleted.
         */
        fun init() {
            if (logFileOutputStream != null) {
                return
            }

            // create app_log dir.
            val logDir: File = context.getDir(LOG_DIR, Context.MODE_PRIVATE)
            // create app_log/index file.
            val indexFile = File(logDir, INDEX_FILE)
            if (!indexFile.createFileIfNotExist()) {
                return
            }
            var index = indexFile.readLines().size

            if (totalOfFiles < 0) {
                // skip
            } else if (index >= totalOfFiles) {
                // compress log files.
                val zippedLogFile = logCompression.compress(logDir)
                if (zippedLogFile != null) {
                    // Success: LogDir and index are restored.
                    Log.v(TAG, zippedLogFile.absolutePath.toString())
                    if (!logDir.exists()) {
                        logDir.mkdirs()
                    }
                    index = 0
                    indexFile.createFileIfNotExist()
                    iLogCollectListener.onReceivedZipFile(zippedLogFile)
                }
            }

            // create log file.
            val logFile = getLogFile(logDir, index + 1) ?: return
            // insert log file name to index.
            indexFile.appendText(logFile.name + separator)

            logFileOutputStream = FileOutputStream(logFile, true)
            writeLineCounter.set(0)
        }

        private fun getLogFile(dir: File, index: Int, format: String = "log"): File? {
            val file = File(
                dir,
                "${index}_${SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(Date())}.${format}"
            )
            if (!file.createFileIfNotExist()) {
                return null
            }
            return file
        }

        @Synchronized
        fun writeWithDivided(line: String, linePerFile: Int) {
            write(line)
            val current = writeLineCounter.addAndGet(1)

            if (linePerFile < 0) {
                return
            }
            if (current >= linePerFile) {
                Log.v(TAG, "divide file")
                close()
                init()
            }
        }

        private fun write(line: String) {
            if (logFileOutputStream == null) {
                return
            }
            val bytes = (line + separator).toByteArray()
            kotlin.runCatching {
                logFileOutputStream?.write(bytes)
            }.onFailure {
                Log.w(TAG, it.message.toString())
            }
        }

        fun close() {
            kotlin.runCatching {
                logFileOutputStream?.close()
            }.onFailure {
                Log.w(TAG, it.message.toString())
            }
            logFileOutputStream = null
        }
    }

    /***
     * LogReader & LogWriter Interface.
     */
    private interface ILogListener {
        fun readLine(line: String)
        fun error(message: String)
    }

    /***
     * Read logcat class
     */
    private class LogReader(
        private val listener: ILogListener,
        private val excludedWords: Array<String>? = null,
        commandWithOption: Array<String>
    ) {
        private val isRunning = AtomicBoolean(false)
        private val logcatThread = Thread {
            logcat(commandWithOption)
        }

        fun start() {
            Log.v(TAG, "start")
            if (isRunning.get()) {
                Log.e(TAG, "LogCollect has already started")
                return
            }
            isRunning.set(true)
            logcatThread.start()
        }

        fun stop() {
            Log.v(TAG, "stop")
            if (!isRunning.get()) {
                Log.e(TAG, "LogCollect has already stopped")
                return
            }
            isRunning.set(false)
            logcatThread.interrupt()
        }

        private fun logcat(commandLine: Array<String>) {
            var counter = 0
            kotlin.runCatching {
                val process = Runtime.getRuntime().exec(commandLine)
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String
                while (bufferedReader.readLine().also { line = it } != null) {
                    if (!isRunning.get()) {
                        break
                    }
                    if (excludedWords != null) {
                        var isSkip = false
                        for (exclude in excludedWords) {
                            if (line.contains(exclude)) {
                                isSkip = true
                                break
                            }
                        }
                        if (isSkip) continue
                    }

                    counter++
                    if (counter >= 100) {
                        counter = 0
                        Log.v(TAG, "Lotus collecting log now ...")
                    }
                    listener.readLine(line)
                }
            }.onFailure {
                listener.error(it.message.toString())
            }
        }
    }
}

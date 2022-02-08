package com.kabo.lotus

import java.io.File

/***
 * FileExtension
 */
object FileExtension {
    val separator: String
        get() = System.getProperty("line.separator")

    fun File.createFileIfNotExist(): Boolean {
        if (!this.exists()) {
            if (!this.createNewFile()) {
                return false
            }
        }
        return true
    }
}

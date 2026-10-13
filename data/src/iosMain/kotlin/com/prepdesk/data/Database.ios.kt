package com.prepdesk.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSFileManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun buildDatabase(): PrepDatabase {
    val directory = NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)
        ?.path ?: error("Could not locate app documents directory")
    return Room.databaseBuilder<PrepDatabase>(name = "$directory/prepdesk.db")
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
}

private val appDatabase by lazy { buildDatabase() }
fun createDatabase(): PrepDatabase = appDatabase

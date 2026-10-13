package com.prepdesk.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

private object DatabaseHolder {
    @Volatile var instance: PrepDatabase? = null
}

fun createDatabase(context: Context): PrepDatabase = DatabaseHolder.instance ?: synchronized(DatabaseHolder) {
    DatabaseHolder.instance ?: Room.databaseBuilder<PrepDatabase>(
        context.applicationContext, context.applicationContext.getDatabasePath("prepdesk.db").absolutePath,
    ).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        .also { DatabaseHolder.instance = it }
}

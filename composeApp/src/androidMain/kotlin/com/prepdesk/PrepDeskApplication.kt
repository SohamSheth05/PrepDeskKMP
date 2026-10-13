package com.prepdesk

import android.app.Application
import com.prepdesk.data.SessionVault
import com.prepdesk.data.androidSessionVault
import com.prepdesk.data.createDatabase
import com.prepdesk.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class PrepDeskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(module {
            single { createDatabase(this@PrepDeskApplication) }
            single<SessionVault> { androidSessionVault(this@PrepDeskApplication) }
        }) { androidContext(this@PrepDeskApplication) }
    }
}

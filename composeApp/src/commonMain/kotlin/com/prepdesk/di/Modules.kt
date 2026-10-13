package com.prepdesk.di

import com.prepdesk.data.dataModule
import com.prepdesk.ui.viewModelModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatform

/** Starts Koin once per process. Platforms pass their database, secure session store, and any extra setup. */
fun initKoin(platformModule: Module, config: KoinApplication.() -> Unit = {}) {
    if (KoinPlatform.getKoinOrNull() != null) return
    startKoin { config(); modules(platformModule, dataModule, viewModelModule) }
}

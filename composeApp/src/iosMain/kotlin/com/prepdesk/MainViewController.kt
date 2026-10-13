package com.prepdesk

import androidx.compose.ui.window.ComposeUIViewController
import com.prepdesk.data.SessionVault
import com.prepdesk.data.createDatabase
import com.prepdesk.data.iosSessionVault
import com.prepdesk.di.initKoin
import org.koin.dsl.module

fun MainViewController(): platform.UIKit.UIViewController {
    initKoin(module { single { createDatabase() }; single<SessionVault> { iosSessionVault() } })
    return ComposeUIViewController { App() }
}

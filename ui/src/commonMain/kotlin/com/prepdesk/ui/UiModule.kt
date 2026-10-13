package com.prepdesk.ui

import com.prepdesk.ui.account.AccountViewModel
import com.prepdesk.ui.ask.AskViewModel
import com.prepdesk.ui.cards.CardsViewModel
import com.prepdesk.ui.focus.FocusViewModel
import com.prepdesk.ui.history.HistoryViewModel
import com.prepdesk.ui.library.LibraryViewModel
import com.prepdesk.ui.mock.MockViewModel
import com.prepdesk.ui.plan.PlanViewModel
import com.prepdesk.ui.play.PlayViewModel
import com.prepdesk.ui.practical.PracticalViewModel
import com.prepdesk.ui.practice.PracticeViewModel
import com.prepdesk.ui.prepare.PrepareViewModel
import com.prepdesk.ui.today.TodayViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** One ViewModel per screen; each is retained across configuration changes. Repositories come from :data. */
val viewModelModule = module {
    single { Navigator() }
    viewModelOf(::AppViewModel)
    viewModelOf(::TodayViewModel)
    viewModelOf(::PlayViewModel)
    viewModelOf(::PrepareViewModel)
    viewModelOf(::PracticeViewModel)
    viewModelOf(::MockViewModel)
    viewModelOf(::LibraryViewModel)
    viewModelOf(::PlanViewModel)
    viewModelOf(::FocusViewModel)
    viewModelOf(::CardsViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::PracticalViewModel)
    viewModelOf(::AskViewModel)
}

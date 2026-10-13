package com.prepdesk.data

import com.prepdesk.ServiceConfig
import com.prepdesk.domain.*
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Repositories and services shared for the whole process, bound to the contracts in :domain.
 * The platform module supplies the [PrepDatabase] and [SessionVault].
 */
val dataModule = module {
    // Application-lifetime scope: queued saves finish even if the screen that started them is gone.
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { OperationQueue(get()) } bind AppStatus::class
    single { Settings() }
    single { PrepRepository(get(), get()) }
    single { SupabaseApi(ServiceConfig.url, ServiceConfig.publicKey, platformHttpClient()) }
    singleOf(::ProgressStore) { bind<ProgressSource>() }
    singleOf(::LocalDeckRepository) { bind<DeckRepository>() }
    singleOf(::LocalStudyRepository) { bind<StudyRepository>() }
    singleOf(::LocalMockRepository) { bind<MockRepository>() }
    singleOf(::LocalFocusRepository) { bind<FocusRepository>() }
    singleOf(::LocalRewardsRepository) { bind<RewardsRepository>() }
    singleOf(::LocalBackupRepository) { bind<BackupRepository>() }
    singleOf(::SupabaseAccountRepository) { bind<AccountRepository>() }
    singleOf(::SupabaseToolsRepository) { bind<ToolsRepository>() }
    singleOf(::SupabaseTutorRepository) { bind<TutorRepository>() }
    single { SupportContact(ServiceConfig.privacyUrl, ServiceConfig.supportEmail) }
}

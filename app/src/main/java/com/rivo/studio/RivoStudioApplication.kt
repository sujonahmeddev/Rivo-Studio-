package com.rivo.studio

import android.app.Application
import com.rivo.studio.core.ai.AiProviderManager
import com.rivo.studio.core.ai.ConversationRepository
import com.rivo.studio.core.agent.AgentCoordinator
import com.rivo.studio.core.data.StudioDatabase
import com.rivo.studio.core.project.LocalRecoveryStore
import com.rivo.studio.core.project.ProjectRepository
import com.rivo.studio.core.runtime.ChaquopyExecutionEngine
import com.rivo.studio.core.security.EncryptedProviderVault
import com.rivo.studio.core.settings.LocalSettings

class RivoStudioApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database = StudioDatabase.get(application)
    val settings = LocalSettings(application)
    val providerVault = EncryptedProviderVault(application)
    val projects = ProjectRepository(application, database)
    val recovery = LocalRecoveryStore(application, database)
    val aiProviders = AiProviderManager()
    val conversations = ConversationRepository(database)
    val execution = ChaquopyExecutionEngine(application, projects, database)
    val agent = AgentCoordinator(database, projects, recovery, aiProviders, execution)
}

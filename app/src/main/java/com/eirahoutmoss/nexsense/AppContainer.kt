package com.eirahoutmoss.nexsense

import android.content.Context
import com.eirahoutmoss.nexsense.core.store.FileProjectStorage
import com.eirahoutmoss.nexsense.core.store.ProjectRepository
import com.eirahoutmoss.nexsense.data.ProjectActions
import com.eirahoutmoss.nexsense.sensors.MicMeter
import com.eirahoutmoss.nexsense.sensors.SensorHub
import com.eirahoutmoss.nexsense.settings.SettingsStore
import com.eirahoutmoss.nexsense.update.UpdateManager
import java.io.File

/**
 * Elle kurulan bağımlılık kabı (NexTools/NexYapı ile aynı yaklaşım): DI kütüphanesi yok,
 * daha az sihir, hatada okunur yığın izi.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    /** Projeler cihazın uygulamaya özel klasöründe durur; başka uygulamalar erişemez, buluta gitmez. */
    val repository: ProjectRepository by lazy {
        ProjectRepository(FileProjectStorage(File(appContext.filesDir, "projects")))
    }
    val actions: ProjectActions by lazy { ProjectActions(appContext, repository, settings) }
    val sensorHub: SensorHub by lazy { SensorHub(appContext) }
    val micMeter: MicMeter by lazy { MicMeter(appContext) }
    val listenStore: com.eirahoutmoss.nexsense.data.ListenStore by lazy { com.eirahoutmoss.nexsense.data.ListenStore(appContext) }
    val updateManager: UpdateManager by lazy { UpdateManager(appContext) }
}

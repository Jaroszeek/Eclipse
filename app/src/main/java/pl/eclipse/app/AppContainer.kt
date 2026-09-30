package pl.eclipse.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import pl.eclipse.app.data.EclipseDatabase
import pl.eclipse.app.data.SchoolRepository
import pl.eclipse.app.data.SettingsStore
import pl.eclipse.core.source.DataSource
import pl.eclipse.core.source.demo.DemoSource
import pl.eclipse.core.source.librus.LibrusSource

/** Ręczne składanie zależności (bez Hilta) — jedno miejsce, gdzie powstają bazy i źródła danych. */
class AppContainer(context: Context) {
    val database: EclipseDatabase = Room.databaseBuilder(context, EclipseDatabase::class.java, "eclipse.db").build()
    val school = SchoolRepository(database)
    val settings = SettingsStore(context)

    fun dataSource(demo: Boolean): DataSource = if (demo) DemoSource() else LibrusSource()
}

class EclipseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as EclipseApp).container

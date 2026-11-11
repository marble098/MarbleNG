package com.marbleng.app.data

import android.app.Application
import android.content.Context
import com.marbleng.app.model.PingBudget
import com.marbleng.app.model.PingParallelMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PingDefaultsTest {
    @Test fun newAndExistingPreferencesKeepTheirIntendedParallelism() {
        val context = RuntimeEnvironment.getApplication() as Context
        val prefs = context.getSharedPreferences("marbleng-store", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = AppStore(context)
        val fresh = store.settings()
        assertEquals(PingParallelMode.MANUAL, fresh.pingParallelMode)
        assertEquals(PingBudget.CONCURRENCY_MAX, fresh.pingConcurrency)

        prefs.edit().clear().putInt("pingConcurrency", 16).commit()
        assertEquals(PingParallelMode.AUTO, store.settings().pingParallelMode)
        prefs.edit().putInt("pingConcurrency", 12).commit()
        assertEquals(PingParallelMode.MANUAL, store.settings().pingParallelMode)
        prefs.edit().putString("pingParallelMode", "AUTO").putInt("pingConcurrency", 64).commit()
        assertEquals(PingParallelMode.AUTO, store.settings().pingParallelMode)
        store.saveSettings(fresh)
        assertEquals(PingParallelMode.MANUAL, store.settings().pingParallelMode)
        assertEquals(PingBudget.CONCURRENCY_MAX, store.settings().pingConcurrency)
    }
}

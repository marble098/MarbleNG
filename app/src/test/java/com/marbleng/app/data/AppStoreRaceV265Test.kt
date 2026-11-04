package com.marbleng.app.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.marbleng.app.core.IpFamilyScan
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MARBLE_STORE_RACE_V265 — the lost-update regression test for the parallel server scanners.
 *
 * The audit (2026-11-04) found that [AppStore.saveServerLocation] and [AppStore.saveIpFamilyScan]
 * are load-all → put-one → save-all, and the scanners call them from a fixed thread pool: two
 * interleaved saves lose one writer's record. The fix serializes the whole read-modify-write on
 * one lock; this test hammers both savers from 8 threads with distinct keys and demands that
 * every single write survives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppStoreRaceV265Test {

    @Test
    fun parallelServerLocationSavesNeverLoseAWrites() {
        val store = AppStore(ApplicationProvider.getApplicationContext())
        val keys = (1..240).map { "profile-$it" }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = keys.map { key ->
                pool.submit { store.saveServerLocation(key, "DE") }
            }
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        val loaded = store.loadServerLocations()
        keys.forEach { key ->
            assertTrue("lost update: $key vanished from serverLocations", loaded.containsKey(key))
            assertEquals("DE", loaded[key]?.first)
        }
    }

    @Test
    fun parallelIpFamilyScanSavesNeverLoseAWrites() {
        val store = AppStore(RuntimeEnvironment.getApplication())
        val endpoints = (1..240).map { "203.0.113.$it:443" }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = endpoints.map { endpoint ->
                pool.submit {
                    store.saveIpFamilyScan(
                        IpFamilyScan(endpoint = endpoint, hasIpv4 = true, scannedAtMs = 1L)
                    )
                }
            }
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        val loaded = store.loadIpFamilyScans()
        endpoints.forEach { endpoint ->
            assertTrue("lost update: $endpoint vanished from ipFamilyScans", loaded.containsKey(endpoint))
        }
    }
}

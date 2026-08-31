package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object MarbleFreedomSmartRanker {

    private val FILTERED_TARGETS = listOf(
        "www.instagram.com" to "/",
        "www.youtube.com" to "/",
        "twitter.com" to "/",
        "www.reddit.com" to "/"
    )

    fun bestProfile(
        settings: AppSettings,
        iranMode: IranModeState,
        xray: XrayManager,
        intelligence: MarbleIntelligence?,
        onProgress: (String) -> Unit
    ): ProxyProfile {
        val profiles = ServerlessFreedomEngine.profiles(settings, iranMode)
        val networkKey = intelligence?.currentSnapshot()?.key() ?: "unknown"
        
        if (intelligence != null && settings.healthHistoryEnabled) {
            val scores = profiles.mapNotNull { p ->
                val health = intelligence.get(p.id, networkKey)
                if (health != null && health.successEwma >= 50.0 && health.latencyEwma < 9000.0) {
                    p to health.latencyEwma
                } else null
            }
            if (scores.isNotEmpty()) {
                val bestKnown = scores.minByOrNull { it.second }?.first
                if (bestKnown != null) {
                    onProgress("Smart Aegis: Found ${bestKnown.name} for this network")
                    return bestKnown
                }
            }
        }
        
        onProgress("Smart Aegis: Testing filters...")
        
        val results = mutableMapOf<String, Double>()
        val latch = CountDownLatch(profiles.size)
        
        for (profile in profiles) {
            Thread {
                var bestMs = 9999.0
                runCatching {
                    xray.temporary(profile, 0, settings.copy(benchSamples = 1)) { port ->
                        val target = FILTERED_TARGETS.random()
                        val r = SocksHttpClient.get(port, target.first, target.second, 4000, 8192)
                        if (r.status in 200..499) {
                            bestMs = r.elapsedMs
                        }
                    }
                }
                synchronized(results) {
                    results[profile.id] = bestMs
                }
                latch.countDown()
            }.start()
        }
        
        latch.await(6, TimeUnit.SECONDS)
        
        val validResults = results.filter { it.value < 9000.0 }
        val bestId = if (validResults.isNotEmpty()) {
            validResults.minByOrNull { it.value }?.key
        } else {
            profiles.first().id
        }
        
        val best = profiles.firstOrNull { it.id == bestId } ?: profiles.first()
        
        if (validResults.containsKey(best.id) && intelligence != null && settings.healthHistoryEnabled) {
            intelligence.recordBenchmark(
                best, 
                com.marbleng.app.model.BenchmarkResult(
                    profileId = best.id,
                    name = best.name,
                    success = 100,
                    latencyMs = validResults[best.id]!!,
                    bytesPerSecond = 0.0,
                    score = 90.0,
                    probeKind = "HTTPS"
                ),
                settings
            )
        }
        
        onProgress("Smart Aegis: Selected ${best.name}")
        return best
    }
}

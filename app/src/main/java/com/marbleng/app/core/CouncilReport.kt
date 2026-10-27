package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * MARBLE_NETWORK_COUNCIL_V236 — what is left on disk after the council has spoken.
 *
 * The evidence is deliberately *not* persisted: a signal bar from last Tuesday is not evidence
 * about today, and storing it invites a page that presents stale numbers as measurements. What is
 * kept is the part a user can still act on — the verdict, what each seat said, and exactly which
 * knobs were written, with the value they replaced.
 */
data class CouncilReport(
    val atMs: Long = 0L,
    val transport: NetworkCouncil.LinkTransport = NetworkCouncil.LinkTransport.NONE,
    val operatorName: String = "",
    val networkType: String = "",
    val score: Int = 0,
    val grade: String = "",
    val applied: Boolean = false,
    val findings: List<NetworkCouncil.CouncilFinding> = emptyList(),
    val changes: List<NetworkCouncil.TuningChange> = emptyList()
) {
    val summary: String
        get() {
            if (atMs == 0L) return ""
            val where = operatorName.ifBlank { networkType.ifBlank { transport.label } }
            return "Score $score ($grade) · $where · ${changes.size} settings"
        }

    fun toJson(): String {
        val findingArray = JSONArray()
        findings.forEach { finding ->
            findingArray.put(
                JSONObject()
                    .put("seat", finding.seat.name)
                    .put("severity", finding.severity.name)
                    .put("headline", finding.headline)
                    .put("detail", finding.detail)
            )
        }
        val changeArray = JSONArray()
        changes.forEach { change ->
            changeArray.put(
                JSONObject()
                    .put("field", change.field.name)
                    .put("value", change.value)
                    .put("previous", change.previous)
                    .put("seat", change.seat.name)
                    .put("confidence", change.confidence)
                    .put("reason", change.reason)
                    .put("dissent", change.dissent)
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("atMs", atMs)
            .put("transport", transport.name)
            .put("operatorName", operatorName)
            .put("networkType", networkType)
            .put("score", score)
            .put("grade", grade)
            .put("applied", applied)
            .put("findings", findingArray)
            .put("changes", changeArray)
            .toString()
    }

    companion object {
        const val FORMAT: String = "marbleng-network-council"
        const val VERSION: Int = 1

        fun fromJson(raw: String): CouncilReport? {
            if (raw.isBlank()) return null
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            if (root.optString("format") != FORMAT) return null
            if (root.optInt("version") != VERSION) return null

            val findings = mutableListOf<NetworkCouncil.CouncilFinding>()
            val findingArray = root.optJSONArray("findings") ?: JSONArray()
            for (index in 0 until findingArray.length()) {
                val entry = findingArray.optJSONObject(index) ?: continue
                val seat = runCatching {
                    NetworkCouncil.CouncilSeat.valueOf(entry.optString("seat"))
                }.getOrNull() ?: continue
                val severity = runCatching {
                    NetworkCouncil.CouncilSeverity.valueOf(entry.optString("severity"))
                }.getOrDefault(NetworkCouncil.CouncilSeverity.GOOD)
                findings += NetworkCouncil.CouncilFinding(
                    seat = seat,
                    severity = severity,
                    headline = entry.optString("headline"),
                    detail = entry.optString("detail")
                )
            }

            val changes = mutableListOf<NetworkCouncil.TuningChange>()
            val changeArray = root.optJSONArray("changes") ?: JSONArray()
            for (index in 0 until changeArray.length()) {
                val entry = changeArray.optJSONObject(index) ?: continue
                val field = runCatching {
                    NetworkCouncil.TuningField.valueOf(entry.optString("field"))
                }.getOrNull() ?: continue
                val seat = runCatching {
                    NetworkCouncil.CouncilSeat.valueOf(entry.optString("seat"))
                }.getOrDefault(NetworkCouncil.CouncilSeat.CORE)
                changes += NetworkCouncil.TuningChange(
                    field = field,
                    value = entry.optString("value"),
                    previous = entry.optString("previous"),
                    seat = seat,
                    confidence = entry.optInt("confidence"),
                    reason = entry.optString("reason"),
                    dissent = entry.optString("dissent")
                )
            }

            return CouncilReport(
                atMs = root.optLong("atMs"),
                transport = runCatching {
                    NetworkCouncil.LinkTransport.valueOf(root.optString("transport"))
                }.getOrDefault(NetworkCouncil.LinkTransport.NONE),
                operatorName = root.optString("operatorName"),
                networkType = root.optString("networkType"),
                score = root.optInt("score").coerceIn(0, 100),
                grade = root.optString("grade"),
                applied = root.optBoolean("applied"),
                findings = findings,
                changes = changes
            )
        }
    }
}

package com.marbleng.app.core

import java.time.Instant

/**
 * MARBLE_LATEST_VERSION_RULE_V235 — the one update rule, stated once and used everywhere:
 *
 *     Upgrade to the newest published version that exists — beta, release, or any
 *     other label upstream put on it.
 *
 * "Newest published" is a statement about *time*, not about a channel: among the
 * non-draft releases, the one with the latest `published_at` wins. A stable release
 * published after a beta is therefore the update (the case the old code got wrong: the
 * in-app check asked GitHub's `/releases/latest`, which by definition cannot see a
 * pre-release, and a version on the beta line could sit un-offered forever — while the
 * core updater's `prerelease` channel had the mirror defect and could not see a newer
 * stable). Labels (`prerelease`, `-beta`, `-rc`) never decide eligibility; they are
 * carried through so the UI can say what kind of build is being offered.
 *
 * The complete rule, in order:
 *  1. Drafts do not exist. An unpublished release is not "available" to anyone.
 *  2. The winner is the maximum by (`published_at`, version). The timestamp is the
 *     primary key; the version only breaks a same-instant tie, deterministically.
 *  3. The winner is an *update* only if its version is strictly newer than the
 *     installed one — the date picks the candidate, the version guards the direction,
 *     so a freshly published hotfix of an old line can never offer a downgrade.
 *
 * `published_at` falls back to `created_at` when a payload omits it (GitHub sets
 * `published_at` for every non-draft release today; the fallback keeps a partial
 * payload from silently dropping the newest build). A candidate whose dates do not
 * parse is ignored rather than guessed at: an update rule that guesses is two rules.
 *
 * The same rule, word for word, lives in `scripts/update-core-lock.sh` for the native
 * cores and in `scripts/resolve-toolchain.py` for the build toolchain. This file is
 * pure (no Android, no JSON) so the JVM tests can pin every branch.
 */
object LatestReleaseRule {

    /** One published-or-not release entry, reduced to the fields the rule reads. */
    data class Candidate(
        val tag: String,
        val publishedAtEpochMs: Long,
        val prerelease: Boolean,
        val draft: Boolean
    )

    /** GitHub timestamps are UTC ISO-8601 (`2026-10-21T12:07:11Z`); anything else is not a date. */
    fun parseIsoEpochMs(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
    }

    fun candidate(
        tag: String,
        publishedAt: String?,
        createdAt: String?,
        prerelease: Boolean,
        draft: Boolean
    ): Candidate? {
        val cleanTag = tag.trim()
        if (cleanTag.isEmpty()) return null
        val at = parseIsoEpochMs(publishedAt) ?: parseIsoEpochMs(createdAt) ?: return null
        return Candidate(cleanTag, at, prerelease, draft)
    }

    /**
     * The winner under the one rule: newest `published_at` among non-drafts,
     * version as the same-instant tie-break. Null when nothing is published.
     */
    fun newest(candidates: List<Candidate>): Candidate? =
        candidates
            .filterNot { it.draft }
            .maxWithOrNull { a, b ->
                val byDate = a.publishedAtEpochMs.compareTo(b.publishedAtEpochMs)
                if (byDate != 0) byDate else compareVersions(a.tag, b.tag)
            }

    /** Rule 3: the date picks the candidate, the version guards the direction. */
    fun isUpdate(candidateTag: String, currentVersion: String): Boolean =
        compareVersions(candidateTag, currentVersion) > 0

    /** `v11.3.0-beta.1` renders as `11.3.0-beta.1`; the qualifier is part of the version. */
    fun displayVersion(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    /**
     * Maven-style version order (the same order `scripts/resolve-toolchain.py` uses):
     * numeric segments compare numerically, a known pre-release qualifier sorts below
     * the release it precedes (`1.0.0-beta < 1.0.0`), an unknown qualifier sorts above
     * it (`1.0.0-extended > 1.0.0`), and a missing segment reads as the release marker
     * (`1.0 == 1.0.0`, `2.6.0-beta < 2.6.0`). Build metadata (`+…`) never orders.
     */
    fun compareVersions(left: String, right: String): Int {
        val a = tokens(left)
        val b = tokens(right)
        for (i in 0 until maxOf(a.size, b.size)) {
            val result = compareToken(
                a.getOrElse(i) { RELEASE_MARKER },
                b.getOrElse(i) { RELEASE_MARKER }
            )
            if (result != 0) return result
        }
        return 0
    }

    // A token is (rank-cases): number → (2, n, ""), known pre-release qualifier →
    // (0, its ladder step, name), anything else → (1, 0, name). The release marker —
    // "the version simply ended here" — is (1, 0, ""), above every pre-release
    // qualifier and below any further number, which is what makes `1.0.0-beta < 1.0.0`
    // and `1.0 < 1.0.1` fall out of one comparison.
    private data class Token(val kind: Int, val number: Long, val word: String)

    private val RELEASE_MARKER = Token(1, 0, "")

    private val QUALIFIER_STEPS = mapOf(
        "dev" to -6, "snapshot" to -5, "alpha" to -4, "a" to -4,
        "eap" to -3, "beta" to -3, "b" to -3, "pre" to -3, "preview" to -3,
        "milestone" to -2, "m" to -2, "rc" to -1, "cr" to -1
    )

    private fun tokens(version: String): List<Token> {
        var text = version.trim()
        if ((text.startsWith("v") || text.startsWith("V")) && text.getOrNull(1)?.isDigit() == true) {
            text = text.substring(1)
        }
        text = text.substringBefore('+')
        val out = ArrayList<Token>()
        for (raw in text.split('.', '-', '_')) {
            if (raw.isEmpty()) continue
            // Split digit/letter runs so `beta01`, `rc1` and `1extended2` compare part by part.
            var index = 0
            while (index < raw.length) {
                val digits = raw[index].isDigit()
                var end = index
                while (end < raw.length && raw[end].isDigit() == digits) end++
                val part = raw.substring(index, end)
                if (digits) {
                    out += Token(2, part.toLongOrNull() ?: Long.MAX_VALUE, "")
                } else {
                    val word = part.lowercase()
                    val step = QUALIFIER_STEPS[word]
                    out += if (step != null) Token(0, step.toLong(), word) else Token(1, 0, word)
                }
                index = end
            }
        }
        return out
    }

    private fun compareToken(a: Token, b: Token): Int {
        // A numeric zero *is* the release marker (1.0 == 1.0.0, and 1.0.0-beta still
        // sorts below 1.0.0) — the same exception Maven and resolve-toolchain.py make.
        if (a.kind == 2 && a.number == 0L) return compareToken(RELEASE_MARKER, b)
        if (b.kind == 2 && b.number == 0L) return compareToken(a, RELEASE_MARKER)
        if (a.kind != b.kind) return a.kind.compareTo(b.kind)
        if (a.number != b.number) return a.number.compareTo(b.number)
        return a.word.compareTo(b.word)
    }
}

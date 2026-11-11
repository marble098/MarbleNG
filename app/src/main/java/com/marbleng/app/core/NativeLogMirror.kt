package com.marbleng.app.core

/**
 * MARBLE_LIVE_STREAM_TRUTH_V278 — what the debug session's "LIVE STREAM" may contain.
 *
 * The mirror tails the cores' own log files (xray.log, hev-native.log, tun2socks.log) into the
 * debug report. Its offsets used to start at zero, so switching debug mode on replayed whatever
 * the files already held — days-old Xray lines, and a tun2socks log from a session that had
 * since moved to HEV — under the heading "LIVE STREAM", each stamped with the current time.
 * Readers took refused-SOCKS lines from the 5th for a fault of the 11th.
 *
 * The rule is the one `tail -f` follows: a file that exists when the session starts is entered
 * at its end; a file that appears later is read from its beginning; a file that shrank was
 * rotated and is read from its beginning again.
 */
object NativeLogMirror {
    /** Where to start reading [length] bytes of a file given the remembered [offset]. */
    fun resumeOffset(offset: Long?, length: Long): Long = when {
        offset == null -> 0L
        length < offset -> 0L
        else -> offset
    }

    /** The offset a file of [length] bytes gets when a debug session begins. */
    fun sessionStartOffset(length: Long): Long = length.coerceAtLeast(0L)

    /**
     * ApplicationExitInfo hands a native crash over as a protobuf tombstone, not text. Read as
     * text it is noise, which is why the report showed a signal number and nothing else. The
     * strings inside it — thread name, abort message, the "stack pointer is not in a rw map"
     * cause, library paths and function names of the backtrace — are stored verbatim, so the
     * printable runs are the readable part of the record.
     */
    fun printableRuns(bytes: ByteArray, minLength: Int = 4, maxRuns: Int = 160): List<String> {
        val runs = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.length >= minLength) {
                val text = current.toString().trim()
                if (text.length >= minLength && runs.lastOrNull() != text) runs += text
            }
            current.setLength(0)
        }
        for (byte in bytes) {
            if (runs.size >= maxRuns) break
            val c = byte.toInt() and 0xFF
            if (c in 0x20..0x7E) current.append(c.toChar()) else flush()
        }
        if (runs.size < maxRuns) flush()
        return runs
    }

    /** True when [bytes] is the text a Java crash or an ANR leaves, false for a protobuf. */
    fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val sample = bytes.take(2_048)
        val binary = sample.count { b ->
            val c = b.toInt() and 0xFF
            c < 0x09 || (c in 0x0E..0x1F)
        }
        return binary * 50 < sample.size
    }
}

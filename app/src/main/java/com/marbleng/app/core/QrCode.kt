package com.marbleng.app.core

// MARBLE_SERVERS_QR_EXPORT_V120
//
// MarbleNG is an offline-first product: it ships no imaging library and refuses to upload a
// user's config link to a third-party "QR image" web service. Exporting a server as a QR code
// therefore happens fully on-device through this encoder, which implements ISO/IEC 18004:2015
// Model 2 in byte mode:
//
//   • automatic version selection (1..20) for the requested error-correction level,
//   • Reed-Solomon error correction over GF(256) with the standard primitive polynomial,
//   • block splitting + interleaving exactly as the specification prescribes,
//   • all eight data masks scored with the four penalty rules so the printed symbol stays
//     readable for a phone camera,
//   • BCH(15,5) format information and BCH(18,6) version information.
//
// The class is deliberately pure Kotlin with no Android imports, so it is covered by ordinary
// JVM unit tests and can never touch the network, the filesystem or the clipboard.

/** Error-correction level of a QR symbol; higher levels survive more damage but hold less data. */
enum class QrEcc(val bits: Int, val label: String) {
    /** ~7% recovery. Largest payload — the right default for a config link. */
    L(0b01, "L"),

    /** ~15% recovery. */
    M(0b00, "M"),

    /** ~25% recovery. */
    Q(0b11, "Q"),

    /** ~30% recovery. Smallest payload. */
    H(0b10, "H")
}

/**
 * An encoded QR symbol: [size] is the module count of one edge (quiet zone excluded) and
 * [modules] is a row-major grid where `true` is a dark module.
 */
class QrCode(val version: Int, val ecc: QrEcc, val modules: BooleanArray) {
    val size: Int get() = modules.size / modulesPerRow()

    private fun modulesPerRow(): Int = 17 + 4 * version

    fun isDark(row: Int, column: Int): Boolean = modules[row * modulesPerRow() + column]

    /** Compact ASCII rendering used by tests and by the plain-text share fallback. */
    fun toAscii(dark: String = "██", light: String = "  "): String = buildString {
        val edge = modulesPerRow()
        for (row in 0 until edge) {
            for (column in 0 until edge) append(if (isDark(row, column)) dark else light)
            append('\n')
        }
    }

    companion object {

        /** Versions 1..20 cover every realistic proxy link; larger symbols are unusable on a phone. */
        const val MAX_VERSION = 20

        private const val MODE_BYTE = 0b0100

        /**
         * Reed-Solomon block table, indexed `[version - 1][eccIndex]` where the ECC order is
         * L, M, Q, H. Each row is a flat list of `(blockCount, totalCodewords, dataCodewords)`
         * triples, exactly as published in ISO/IEC 18004 Table 13.
         */
        private val RS_BLOCKS: Array<Array<IntArray>> = arrayOf(
            arrayOf(intArrayOf(1, 26, 19), intArrayOf(1, 26, 16), intArrayOf(1, 26, 13), intArrayOf(1, 26, 9)),
            arrayOf(intArrayOf(1, 44, 34), intArrayOf(1, 44, 28), intArrayOf(1, 44, 22), intArrayOf(1, 44, 16)),
            arrayOf(intArrayOf(1, 70, 55), intArrayOf(1, 70, 44), intArrayOf(2, 35, 17), intArrayOf(2, 35, 13)),
            arrayOf(intArrayOf(1, 100, 80), intArrayOf(2, 50, 32), intArrayOf(2, 50, 24), intArrayOf(4, 25, 9)),
            arrayOf(intArrayOf(1, 134, 108), intArrayOf(2, 67, 43), intArrayOf(2, 33, 15, 2, 34, 16), intArrayOf(2, 33, 11, 2, 34, 12)),
            arrayOf(intArrayOf(2, 86, 68), intArrayOf(4, 43, 27), intArrayOf(4, 43, 19), intArrayOf(4, 43, 15)),
            arrayOf(intArrayOf(2, 98, 78), intArrayOf(4, 49, 31), intArrayOf(2, 32, 14, 4, 33, 15), intArrayOf(4, 39, 13, 1, 40, 14)),
            arrayOf(intArrayOf(2, 121, 97), intArrayOf(2, 60, 38, 2, 61, 39), intArrayOf(4, 40, 18, 2, 41, 19), intArrayOf(4, 40, 14, 2, 41, 15)),
            arrayOf(intArrayOf(2, 146, 116), intArrayOf(3, 58, 36, 2, 59, 37), intArrayOf(4, 36, 16, 4, 37, 17), intArrayOf(4, 36, 12, 4, 37, 13)),
            arrayOf(intArrayOf(2, 86, 68, 2, 87, 69), intArrayOf(4, 69, 43, 1, 70, 44), intArrayOf(6, 43, 19, 2, 44, 20), intArrayOf(6, 43, 15, 2, 44, 16)),
            arrayOf(intArrayOf(4, 101, 81), intArrayOf(1, 80, 50, 4, 81, 51), intArrayOf(4, 50, 22, 4, 51, 23), intArrayOf(3, 36, 12, 8, 37, 13)),
            arrayOf(intArrayOf(2, 116, 92, 2, 117, 93), intArrayOf(6, 58, 36, 2, 59, 37), intArrayOf(4, 46, 20, 6, 47, 21), intArrayOf(7, 42, 14, 4, 43, 15)),
            arrayOf(intArrayOf(4, 133, 107), intArrayOf(8, 59, 37, 1, 60, 38), intArrayOf(8, 44, 20, 4, 45, 21), intArrayOf(12, 33, 11, 4, 34, 12)),
            arrayOf(intArrayOf(3, 145, 115, 1, 146, 116), intArrayOf(4, 64, 40, 5, 65, 41), intArrayOf(11, 36, 16, 5, 37, 17), intArrayOf(11, 36, 12, 5, 37, 13)),
            arrayOf(intArrayOf(5, 109, 87, 1, 110, 88), intArrayOf(5, 65, 41, 5, 66, 42), intArrayOf(5, 54, 24, 7, 55, 25), intArrayOf(11, 36, 12, 7, 37, 13)),
            arrayOf(intArrayOf(5, 122, 98, 1, 123, 99), intArrayOf(7, 73, 45, 3, 74, 46), intArrayOf(15, 43, 19, 2, 44, 20), intArrayOf(3, 45, 15, 13, 46, 16)),
            arrayOf(intArrayOf(1, 135, 107, 5, 136, 108), intArrayOf(10, 74, 46, 1, 75, 47), intArrayOf(1, 50, 22, 15, 51, 23), intArrayOf(2, 42, 14, 17, 43, 15)),
            arrayOf(intArrayOf(5, 150, 120, 1, 151, 121), intArrayOf(9, 69, 43, 4, 70, 44), intArrayOf(17, 50, 22, 1, 51, 23), intArrayOf(2, 42, 14, 19, 43, 15)),
            arrayOf(intArrayOf(3, 141, 113, 4, 142, 114), intArrayOf(3, 70, 44, 11, 71, 45), intArrayOf(17, 47, 21, 4, 48, 22), intArrayOf(9, 39, 13, 16, 40, 14)),
            arrayOf(intArrayOf(3, 135, 107, 5, 136, 108), intArrayOf(3, 67, 41, 13, 68, 42), intArrayOf(15, 54, 24, 5, 55, 25), intArrayOf(15, 43, 15, 10, 44, 16))
        )

        /** Centre coordinates of the alignment patterns, indexed by version (ISO/IEC 18004 Annex E). */
        private val ALIGNMENT_POSITIONS: Array<IntArray> = arrayOf(
            intArrayOf(),
            intArrayOf(6, 18), intArrayOf(6, 22), intArrayOf(6, 26), intArrayOf(6, 30), intArrayOf(6, 34),
            intArrayOf(6, 22, 38), intArrayOf(6, 24, 42), intArrayOf(6, 26, 46), intArrayOf(6, 28, 50),
            intArrayOf(6, 30, 54), intArrayOf(6, 32, 58), intArrayOf(6, 34, 62),
            intArrayOf(6, 26, 46, 66), intArrayOf(6, 26, 48, 70), intArrayOf(6, 26, 50, 74),
            intArrayOf(6, 30, 54, 78), intArrayOf(6, 30, 56, 82), intArrayOf(6, 30, 58, 86),
            intArrayOf(6, 34, 62, 90)
        )

        /** Total remainder bits appended after interleaving, indexed by version. */
        private val REMAINDER_BITS: IntArray = intArrayOf(
            0, 7, 7, 7, 7, 7, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 3
        )

        private fun eccIndex(ecc: QrEcc): Int = when (ecc) {
            QrEcc.L -> 0
            QrEcc.M -> 1
            QrEcc.Q -> 2
            QrEcc.H -> 3
        }

        /** Flat list of `(blockCount, totalCodewords, dataCodewords)` triples for one version/ECC pair. */
        private fun blocks(version: Int, ecc: QrEcc): IntArray =
            RS_BLOCKS[version - 1][eccIndex(ecc)]

        private fun dataCodewordCount(version: Int, ecc: QrEcc): Int {
            val table = blocks(version, ecc)
            var total = 0
            for (index in table.indices step 3) total += table[index] * table[index + 2]
            return total
        }

        /** Byte mode carries an 8-bit character count up to version 9 and a 16-bit one after that. */
        private fun countBits(version: Int): Int = if (version <= 9) 8 else 16

        /**
         * Smallest version that holds [length] bytes at [ecc], or `null` when even version
         * [MAX_VERSION] is too small.
         */
        fun versionFor(length: Int, ecc: QrEcc): Int? {
            if (length <= 0) return null
            for (version in 1..MAX_VERSION) {
                val bits = 4 + countBits(version) + 8 * length
                if (bits <= dataCodewordCount(version, ecc) * 8) return version
            }
            return null
        }

        /**
         * Encode [text] as a byte-mode QR symbol.
         *
         * @throws QrCapacityException when the payload needs more than version [MAX_VERSION].
         */
        fun encode(text: String, ecc: QrEcc = QrEcc.L): QrCode {
            val data = text.toByteArray(Charsets.UTF_8)
            val version = versionFor(data.size, ecc)
                ?: throw QrCapacityException(
                    "Payload of ${data.size} bytes does not fit in a QR version $MAX_VERSION symbol at ECC ${ecc.label}"
                )
            val codewords = buildCodewords(data, version, ecc)
            val matrix = SymbolMatrix(version)
            matrix.drawFunctionPatterns()
            matrix.placeData(codewords)
            val mask = matrix.bestMask(ecc)
            matrix.applyMask(mask)
            matrix.drawFormatInfo(ecc, mask)
            matrix.drawVersionInfo()
            return QrCode(version, ecc, matrix.grid)
        }

        // ---------------------------------------------------------------------------------------
        // Data codewords
        // ---------------------------------------------------------------------------------------

        private fun buildCodewords(data: ByteArray, version: Int, ecc: QrEcc): IntArray {
            val totalData = dataCodewordCount(version, ecc)
            val bits = BitBuffer()
            bits.append(MODE_BYTE, 4)
            bits.append(data.size, countBits(version))
            data.forEach { byte -> bits.append(byte.toInt() and 0xFF, 8) }

            // Terminator: up to four zero bits, shortened when the payload already fills the symbol.
            val capacityBits = totalData * 8
            bits.append(0, minOf(4, capacityBits - bits.size))
            bits.padToByteBoundary()

            val padded = bits.codewords()
            require(padded.size <= totalData) { "Encoded payload overflowed the data region" }

            // Pad codewords alternate 0xEC / 0x11 to the end of the data region.
            val dataCodewords = IntArray(totalData)
            padded.forEachIndexed { index, value -> dataCodewords[index] = value }
            var padToggle = 0
            for (index in padded.size until totalData) {
                dataCodewords[index] = if (padToggle++ % 2 == 0) 0xEC else 0x11
            }

            return interleave(dataCodewords, version, ecc)
        }

        /** Split into Reed-Solomon blocks, then interleave data and error-correction codewords. */
        private fun interleave(dataCodewords: IntArray, version: Int, ecc: QrEcc): IntArray {
            val table = blocks(version, ecc)
            val dataBlocks = mutableListOf<IntArray>()
            val ecBlocks = mutableListOf<IntArray>()
            var offset = 0

            for (index in table.indices step 3) {
                val blockCount = table[index]
                val totalPerBlock = table[index + 1]
                val dataPerBlock = table[index + 2]
                val ecPerBlock = totalPerBlock - dataPerBlock
                repeat(blockCount) {
                    val block = dataCodewords.copyOfRange(offset, offset + dataPerBlock)
                    offset += dataPerBlock
                    dataBlocks += block
                    ecBlocks += reedSolomon(block, ecPerBlock)
                }
            }
            require(offset == dataCodewords.size) { "Reed-Solomon block table disagrees with the data length" }

            val maxData = dataBlocks.maxOf { it.size }
            val maxEc = ecBlocks.maxOf { it.size }
            val out = IntArray(dataCodewords.size + ecBlocks.sumOf { it.size })
            var cursor = 0
            for (column in 0 until maxData) {
                for (block in dataBlocks) {
                    if (column < block.size) out[cursor++] = block[column]
                }
            }
            for (column in 0 until maxEc) {
                for (block in ecBlocks) {
                    if (column < block.size) out[cursor++] = block[column]
                }
            }
            return out
        }

        // ---------------------------------------------------------------------------------------
        // GF(256) arithmetic with the QR primitive polynomial 0x11D
        // ---------------------------------------------------------------------------------------

        private val EXP_TABLE = IntArray(512)
        private val LOG_TABLE = IntArray(256)

        init {
            var value = 1
            for (index in 0 until 255) {
                EXP_TABLE[index] = value
                LOG_TABLE[value] = index
                value = value shl 1
                if (value and 0x100 != 0) value = value xor 0x11D
            }
            for (index in 255 until 512) EXP_TABLE[index] = EXP_TABLE[index - 255]
        }

        private fun gfMultiply(a: Int, b: Int): Int =
            if (a == 0 || b == 0) 0 else EXP_TABLE[LOG_TABLE[a] + LOG_TABLE[b]]

        /** Generator polynomial coefficients (highest power first) for [degree] error codewords. */
        private fun generatorPolynomial(degree: Int): IntArray {
            var polynomial = intArrayOf(1)
            for (index in 0 until degree) {
                val next = IntArray(polynomial.size + 1)
                for (cursor in polynomial.indices) {
                    next[cursor] = next[cursor] xor polynomial[cursor]
                    next[cursor + 1] = next[cursor + 1] xor gfMultiply(polynomial[cursor], EXP_TABLE[index])
                }
                polynomial = next
            }
            return polynomial
        }

        /** Reed-Solomon error-correction codewords for one data block. */
        private fun reedSolomon(data: IntArray, ecCount: Int): IntArray {
            val generator = generatorPolynomial(ecCount)
            val remainder = IntArray(ecCount)
            for (byte in data) {
                val factor = byte xor remainder[0]
                System.arraycopy(remainder, 1, remainder, 0, ecCount - 1)
                remainder[ecCount - 1] = 0
                for (index in 0 until ecCount) {
                    remainder[index] = remainder[index] xor gfMultiply(generator[index + 1], factor)
                }
            }
            return remainder
        }

        // ---------------------------------------------------------------------------------------
        // Bit helpers
        // ---------------------------------------------------------------------------------------

        private class BitBuffer {
            private val bits = mutableListOf<Boolean>()
            val size: Int get() = bits.size

            fun append(value: Int, length: Int) {
                for (index in length - 1 downTo 0) bits += ((value ushr index) and 1) == 1
            }

            fun padToByteBoundary() {
                while (bits.size % 8 != 0) bits += false
            }

            fun codewords(): IntArray {
                val out = IntArray(bits.size / 8)
                for (index in out.indices) {
                    var value = 0
                    for (bit in 0 until 8) value = (value shl 1) or (if (bits[index * 8 + bit]) 1 else 0)
                    out[index] = value
                }
                return out
            }
        }

        // ---------------------------------------------------------------------------------------
        // Symbol matrix
        // ---------------------------------------------------------------------------------------

        private class SymbolMatrix(val version: Int) {
            val edge: Int = 17 + 4 * version
            val grid = BooleanArray(edge * edge)
            /** True where a function pattern already owns the module, so data placement skips it. */
            private val reserved = BooleanArray(edge * edge)

            private fun index(row: Int, column: Int) = row * edge + column

            private fun inBounds(row: Int, column: Int) =
                row in 0 until edge && column in 0 until edge

            fun setFunction(row: Int, column: Int, dark: Boolean) {
                if (!inBounds(row, column)) return
                grid[index(row, column)] = dark
                reserved[index(row, column)] = true
            }

            fun isReserved(row: Int, column: Int) = reserved[index(row, column)]

            fun drawFunctionPatterns() {
                // Timing runs the full span first; the finder patterns are painted over its ends so
                // the separator stays light and the 1:1:3:1:1 ring stays intact.
                drawTiming()
                drawFinder(0, 0)
                drawFinder(0, edge - 7)
                drawFinder(edge - 7, 0)
                drawAlignment()
                // The single always-dark module beside the bottom-left finder pattern.
                setFunction(edge - 8, 8, true)
                reserveFormatAreas()
                reserveVersionAreas()
            }

            private fun drawFinder(topRow: Int, leftColumn: Int) {
                for (row in -1..7) {
                    for (column in -1..7) {
                        val r = topRow + row
                        val c = leftColumn + column
                        if (!inBounds(r, c)) continue
                        val ring = maxOf(kotlin.math.abs(row - 3), kotlin.math.abs(column - 3))
                        setFunction(r, c, ring != 2 && ring <= 3)
                    }
                }
            }

            private fun drawTiming() {
                // The full span: the alternating line reaches both edges and the finder patterns are
                // drawn over its ends afterwards.
                for (position in 0 until edge) {
                    val dark = position % 2 == 0
                    setFunction(6, position, dark)
                    setFunction(position, 6, dark)
                }
            }

            private fun drawAlignment() {
                val positions = ALIGNMENT_POSITIONS[version - 1]
                for (row in positions) {
                    for (column in positions) {
                        // The three corners belong to the finder patterns.
                        val isFinderCorner = (row == 6 && column == 6) ||
                            (row == 6 && column == positions.last()) ||
                            (row == positions.last() && column == 6)
                        if (isFinderCorner) continue
                        for (r in -2..2) {
                            for (c in -2..2) {
                                val ring = maxOf(kotlin.math.abs(r), kotlin.math.abs(c))
                                setFunction(row + r, column + c, ring != 1)
                            }
                        }
                    }
                }
            }

            private fun reserveFormatAreas() {
                for (position in 0..8) {
                    if (!isReserved(8, position)) setFunction(8, position, false)
                    if (!isReserved(position, 8)) setFunction(position, 8, false)
                }
                for (position in 0..7) {
                    setFunction(8, edge - 1 - position, false)
                    setFunction(edge - 1 - position, 8, false)
                }
                setFunction(edge - 8, 8, true)
            }

            private fun reserveVersionAreas() {
                if (version < 7) return
                for (row in 0 until 6) {
                    for (column in 0 until 3) {
                        setFunction(row, edge - 11 + column, false)
                        setFunction(edge - 11 + column, row, false)
                    }
                }
            }

            /** Zigzag placement of the interleaved codewords, skipping every function module. */
            fun placeData(codewords: IntArray) {
                val bits = BitBuffer()
                codewords.forEach { value -> bits.append(value, 8) }
                // Trailing remainder bits stay light.
                repeat(REMAINDER_BITS[version - 1]) { bits.append(0, 1) }

                val payload = bits.codewords()
                var cursor = 0
                var upward = true
                var column = edge - 1
                while (column > 0) {
                    // The vertical timing pattern splits the two-column sweep.
                    if (column == 6) column--
                    for (step in 0 until edge) {
                        val row = if (upward) edge - 1 - step else step
                        for (offset in 0..1) {
                            val c = column - offset
                            if (isReserved(row, c)) continue
                            // `payload` holds codewords while `cursor` counts bits.
                            val dark = if (cursor < payload.size * 8) {
                                ((payload[cursor / 8] ushr (7 - cursor % 8)) and 1) == 1
                            } else {
                                false
                            }
                            cursor++
                            grid[index(row, c)] = dark
                        }
                    }
                    upward = !upward
                    column -= 2
                }
            }

            /** Score all eight masks with the four specification penalty rules and keep the best. */
            fun bestMask(ecc: QrEcc): Int {
                var best = 0
                var bestPenalty = Int.MAX_VALUE
                for (mask in 0..7) {
                    applyMask(mask)
                    drawFormatInfo(ecc, mask)
                    val penalty = penalty()
                    applyMask(mask)
                    if (penalty < bestPenalty) {
                        bestPenalty = penalty
                        best = mask
                    }
                }
                return best
            }

            fun applyMask(mask: Int) {
                for (row in 0 until edge) {
                    for (column in 0 until edge) {
                        if (isReserved(row, column)) continue
                        if (maskMatches(mask, row, column)) grid[index(row, column)] =
                            !grid[index(row, column)]
                    }
                }
            }

            private fun maskMatches(mask: Int, row: Int, column: Int): Boolean = when (mask) {
                0 -> (row + column) % 2 == 0
                1 -> row % 2 == 0
                2 -> column % 3 == 0
                3 -> (row + column) % 3 == 0
                4 -> (row / 2 + column / 3) % 2 == 0
                5 -> (row * column) % 2 + (row * column) % 3 == 0
                6 -> ((row * column) % 2 + (row * column) % 3) % 2 == 0
                else -> ((row + column) % 2 + (row * column) % 3) % 2 == 0
            }

            fun drawFormatInfo(ecc: QrEcc, mask: Int) {
                val data = (ecc.bits shl 3) or mask
                var bits = data shl 10
                val generator = 0b101_0011_0111
                while (bitLength(bits) - 1 >= 10) {
                    bits = bits xor (generator shl (bitLength(bits) - 11))
                }
                val format = ((data shl 10) or bits) xor 0b101_0100_0001_0010

                // Copy one: the column beside the top-left finder, continuing down beside the
                // bottom-left one. Rows 6 and 8 stay out — the timing pattern owns row 6 and the
                // corner module (8,8) belongs to bit 7.
                for (bit in 0..14) {
                    val row = when {
                        bit < 6 -> bit
                        bit < 8 -> bit + 1
                        else -> edge - 15 + bit
                    }
                    setFunction(row, 8, bitSet(format, bit))
                }

                // Copy two: the row under the top-right finder, wrapping back under the top-left one.
                for (bit in 0..14) {
                    val column = when {
                        bit < 8 -> edge - 1 - bit
                        bit == 8 -> 7
                        else -> 14 - bit
                    }
                    setFunction(8, column, bitSet(format, bit))
                }

                // The single always-dark module that anchors both strips.
                setFunction(edge - 8, 8, true)
            }

            fun drawVersionInfo() {
                if (version < 7) return
                var bits = version shl 12
                val generator = 0b1_1111_0010_0101
                while (bitLength(bits) - 1 >= 12) {
                    bits = bits xor (generator shl (bitLength(bits) - 13))
                }
                val info = (version shl 12) or bits
                for (bit in 0..17) {
                    val dark = bitSet(info, bit)
                    setFunction(bit / 3, edge - 11 + bit % 3, dark)
                    setFunction(edge - 11 + bit % 3, bit / 3, dark)
                }
            }

            private fun bitLength(value: Int): Int {
                var length = 0
                var shifted = value
                while (shifted != 0) {
                    length++
                    shifted = shifted ushr 1
                }
                return length
            }

            private fun bitSet(value: Int, bit: Int): Boolean = ((value ushr bit) and 1) == 1

            /** The four ISO/IEC 18004 penalty rules; lower is easier for a camera to decode. */
            fun penalty(): Int {
                var total = 0
                total += runPenalty()
                total += blockPenalty()
                total += finderLikePenalty()
                total += balancePenalty()
                return total
            }

            private fun runPenalty(): Int {
                var total = 0
                for (row in 0 until edge) {
                    total += linePenalty { column -> grid[index(row, column)] }
                }
                for (column in 0 until edge) {
                    total += linePenalty { row -> grid[index(row, column)] }
                }
                return total
            }

            private fun linePenalty(darkAt: (Int) -> Boolean): Int {
                var total = 0
                var run = 1
                for (position in 1 until edge) {
                    if (darkAt(position) == darkAt(position - 1)) {
                        run++
                    } else {
                        if (run >= 5) total += 3 + (run - 5)
                        run = 1
                    }
                }
                if (run >= 5) total += 3 + (run - 5)
                return total
            }

            private fun blockPenalty(): Int {
                var total = 0
                for (row in 0 until edge - 1) {
                    for (column in 0 until edge - 1) {
                        val topLeft = grid[index(row, column)]
                        if (
                            grid[index(row, column + 1)] == topLeft &&
                            grid[index(row + 1, column)] == topLeft &&
                            grid[index(row + 1, column + 1)] == topLeft
                        ) {
                            total += 3
                        }
                    }
                }
                return total
            }

            /**
             * Rule 3: a 1:1:3:1:1 dark-light ratio with four light modules on either side reads like a
             * finder pattern to a decoder, so each occurrence costs 40 points.
             */
            private fun finderLikePenalty(): Int {
                val pattern = booleanArrayOf(true, false, true, true, true, false, true)
                var total = 0

                fun scan(values: BooleanArray) {
                    for (start in values.indices) {
                        if (!matchesAt(values, start, pattern)) continue
                        if (isQuiet(values, start - 4) || isQuiet(values, start + 7)) total += 40
                    }
                }

                for (row in 0 until edge) {
                    scan(BooleanArray(edge) { grid[index(row, it)] })
                }
                for (column in 0 until edge) {
                    scan(BooleanArray(edge) { grid[index(it, column)] })
                }
                return total
            }

            private fun matchesAt(values: BooleanArray, start: Int, pattern: BooleanArray): Boolean {
                if (start < 0 || start + pattern.size > values.size) return false
                for (offset in pattern.indices) {
                    if (values[start + offset] != pattern[offset]) return false
                }
                return true
            }

            /** True when four modules starting at [from] exist and are all light. */
            private fun isQuiet(values: BooleanArray, from: Int): Boolean {
                if (from < 0 || from + 4 > values.size) return false
                for (offset in 0 until 4) {
                    if (values[from + offset]) return false
                }
                return true
            }

            private fun balancePenalty(): Int {
                var dark = 0
                for (value in grid) if (value) dark++
                val percent = dark * 100 / (edge * edge)
                val deviation = kotlin.math.abs(percent - 50) / 5
                return deviation * 10
            }
        }
    }
}

/** Thrown when the payload cannot be represented at the requested error-correction level. */
class QrCapacityException(message: String) : IllegalArgumentException(message)


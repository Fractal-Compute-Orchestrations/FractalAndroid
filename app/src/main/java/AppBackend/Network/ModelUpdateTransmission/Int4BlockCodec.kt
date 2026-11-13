package AppBackend.Network.ModelUpdateTransmission

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Wire-compatible Kotlin implementation of FRACTAL's FRQ4 / FRQD micro-block
 * INT4 codec (`fractal_quant.codec` in FractalCore).
 *
 * Encodes float32 tensors into signed 4-bit [-8, 7] blocks with per-block
 * float32 scale factors so 7.1x wire-compressed updates from Android clients
 * decode identically on the Python aggregation coordinator.
 */
object Int4BlockCodec {
    val FRQ4_MAGIC: ByteArray = byteArrayOf('F'.code.toByte(), 'R'.code.toByte(), 'Q'.code.toByte(), '4'.code.toByte())
    val FRQD_MAGIC: ByteArray = byteArrayOf('F'.code.toByte(), 'R'.code.toByte(), 'Q'.code.toByte(), 'D'.code.toByte())
    const val FORMAT_VERSION: Byte = 1
    const val FLAG_STOCHASTIC: Int = 1
    const val FLAG_ERROR_FEEDBACK: Int = 2
    const val DEFAULT_BLOCK_SIZE: Int = 64

    data class DecodedTensor(
        val data: FloatArray,
        val shape: IntArray,
        val blockSize: Int,
        val flags: Int,
        val numBlocks: Int,
    )

    data class CompressionStats(
        val originalBytes: Int,
        val compressedBytes: Int,
        val numElements: Int,
        val cosineSimilarity: Double,
    ) {
        val compressionRatio: Double
            get() = if (compressedBytes == 0) 0.0 else originalBytes.toDouble() / compressedBytes.toDouble()
    }

    fun encodeTensorPacket(
        data: FloatArray,
        shape: IntArray = intArrayOf(data.size),
        blockSize: Int = DEFAULT_BLOCK_SIZE,
        errorFeedback: Boolean = false,
    ): Pair<ByteArray, FloatArray> {
        require(blockSize in 1..65535) { "blockSize must be in [1, 65535], got $blockSize" }
        val ndim = shape.size
        require(ndim <= 255) { "ndim must be <= 255, got $ndim" }
        val expectedSize = if (shape.isEmpty()) 0 else shape.fold(1) { acc, dim -> acc * dim }
        require(expectedSize == data.size) {
            "Shape product ($expectedSize) does not match data length (${data.size})"
        }

        val numel = data.size
        val flags = if (errorFeedback) FLAG_ERROR_FEEDBACK else 0

        if (numel == 0) {
            val headerLen = 4 + 1 + 1 + 2 + 1 + (4 * ndim) + 4 + 4
            val buf = ByteBuffer.allocate(headerLen).order(ByteOrder.LITTLE_ENDIAN)
            buf.put(FRQ4_MAGIC)
            buf.put(FORMAT_VERSION)
            buf.put(flags.toByte())
            buf.putShort(blockSize.toShort())
            buf.put(ndim.toByte())
            for (dim in shape) buf.putInt(dim)
            buf.putInt(0)
            buf.putInt(0)
            return Pair(buf.array(), FloatArray(0))
        }

        val numBlocks = (numel + blockSize - 1) / blockSize
        val scales = FloatArray(numBlocks)
        val qSigned = IntArray(numel)
        val reconstructed = FloatArray(numel)

        for (blockIdx in 0 until numBlocks) {
            val start = blockIdx * blockSize
            val end = min(start + blockSize, numel)
            var maxAbs = 0.0f
            for (i in start until end) {
                val v = abs(data[i])
                if (v > maxAbs) maxAbs = v
            }
            val scale = maxAbs / 7.0f
            scales[blockIdx] = scale
            if (scale == 0.0f) {
                for (i in start until end) {
                    qSigned[i] = 0
                    reconstructed[i] = 0.0f
                }
            } else {
                for (i in start until end) {
                    val q = round(data[i] / scale).toInt().coerceIn(-8, 7)
                    qSigned[i] = q
                    reconstructed[i] = q * scale
                }
            }
        }

        val packedLen = (numel + 1) / 2
        val totalLen = 4 + 1 + 1 + 2 + 1 + (4 * ndim) + 4 + (numBlocks * 4) + packedLen
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(FRQ4_MAGIC)
        buf.put(FORMAT_VERSION)
        buf.put(flags.toByte())
        buf.putShort(blockSize.toShort())
        buf.put(ndim.toByte())
        for (dim in shape) buf.putInt(dim)
        buf.putInt(numBlocks)
        for (s in scales) buf.putFloat(s)

        for (pairIdx in 0 until packedLen) {
            val evenIdx = pairIdx * 2
            val oddIdx = evenIdx + 1
            val evenNibble = (qSigned[evenIdx] + 8) and 0x0F
            val oddVal = if (oddIdx < numel) (qSigned[oddIdx] + 8) else 8
            val oddNibble = (oddVal and 0x0F) shl 4
            buf.put((evenNibble or oddNibble).toByte())
        }

        return Pair(buf.array(), reconstructed)
    }

    fun decodeTensorPacket(packet: ByteArray): DecodedTensor {
        require(packet.size >= 13 && packet.copyOfRange(0, 4).contentEquals(FRQ4_MAGIC)) {
            "Invalid FRQ4 packet header"
        }
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val version = buf.get()
        require(version == FORMAT_VERSION) { "Unsupported FRQ4 version: $version" }
        val flags = buf.get().toInt() and 0xFF
        val blockSize = buf.short.toInt() and 0xFFFF
        require(blockSize > 0) { "Invalid FRQ4 blockSize: $blockSize" }
        val ndim = buf.get().toInt() and 0xFF
        require(packet.size >= 13 + 4 * ndim) { "Truncated FRQ4 shape header" }

        val shape = IntArray(ndim) { buf.int }
        val numBlocks = buf.int
        val numel = if (ndim == 0 && numBlocks == 0) 0 else shape.fold(1) { acc, dim -> acc * dim }
        if (numel == 0 || numBlocks == 0) {
            return DecodedTensor(FloatArray(numel), shape, blockSize, flags, numBlocks)
        }

        val packedLen = (numel + 1) / 2
        val neededBytes = (numBlocks * 4) + packedLen
        require(buf.remaining() >= neededBytes) {
            "Truncated FRQ4 payload: expected at least $neededBytes bytes, got ${buf.remaining()}"
        }

        val scales = FloatArray(numBlocks) { buf.float }
        val packed = ByteArray(packedLen)
        buf.get(packed)

        val out = FloatArray(numel)
        for (i in 0 until numel) {
            val byteVal = packed[i / 2].toInt() and 0xFF
            val nibble = if (i % 2 == 0) (byteVal and 0x0F) else ((byteVal ushr 4) and 0x0F)
            val q = nibble - 8
            out[i] = q * scales[i / blockSize]
        }
        return DecodedTensor(out, shape, blockSize, flags, numBlocks)
    }

    fun encodeStateDict(
        stateDict: Map<String, Pair<FloatArray, IntArray>>,
        blockSize: Int = DEFAULT_BLOCK_SIZE,
    ): Pair<ByteArray, CompressionStats> {
        val sortedNames = stateDict.keys.sorted()
        val encodedEntries = ArrayList<Triple<ByteArray, ByteArray, Int>>(sortedNames.size)
        var totalOrigBytes = 0
        var totalNumel = 0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        var bundleSize = 8
        for (name in sortedNames) {
            val (values, shape) = stateDict.getValue(name)
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val (pkt, rec) = encodeTensorPacket(values, shape = shape, blockSize = blockSize)
            encodedEntries.add(Triple(nameBytes, pkt, values.size))
            totalOrigBytes += values.size * 4
            totalNumel += values.size
            for (i in values.indices) {
                val a = values[i].toDouble()
                val b = rec[i].toDouble()
                dot += a * b
                normA += a * a
                normB += b * b
            }
            bundleSize += 2 + nameBytes.size + 4 + pkt.size
        }

        val buf = ByteBuffer.allocate(bundleSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(FRQD_MAGIC)
        buf.putInt(encodedEntries.size)
        for ((nameBytes, pkt, _) in encodedEntries) {
            buf.putShort(nameBytes.size.toShort())
            buf.put(nameBytes)
            buf.putInt(pkt.size)
            buf.put(pkt)
        }

        val denom = sqrt(normA) * sqrt(normB)
        val cosine = if (totalNumel == 0) 1.0 else if (denom < 1e-12) 0.0 else dot / denom
        return Pair(
            buf.array(),
            CompressionStats(
                originalBytes = totalOrigBytes,
                compressedBytes = bundleSize,
                numElements = totalNumel,
                cosineSimilarity = cosine,
            ),
        )
    }

    fun decodeStateDict(bundle: ByteArray): Map<String, DecodedTensor> {
        require(bundle.size >= 8 && bundle.copyOfRange(0, 4).contentEquals(FRQD_MAGIC)) {
            "Invalid FRQD state bundle magic header"
        }
        val buf = ByteBuffer.wrap(bundle).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val count = buf.int
        val restored = LinkedHashMap<String, DecodedTensor>(count)
        for (idx in 0 until count) {
            val nameLen = buf.short.toInt() and 0xFFFF
            val nameBytes = ByteArray(nameLen)
            buf.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8)
            val pktLen = buf.int
            val pkt = ByteArray(pktLen)
            buf.get(pkt)
            restored[name] = decodeTensorPacket(pkt)
        }
        return restored
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Array sizes must match: ${a.size} vs ${b.size}" }
        if (a.isEmpty()) return 1.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val va = a[i].toDouble()
            val vb = b[i].toDouble()
            dot += va * vb
            normA += va * va
            normB += vb * vb
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom < 1e-12) 0.0 else dot / denom
    }
}

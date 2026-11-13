package com.example.fractal

import AppBackend.Network.ModelUpdateTransmission.Int4BlockCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Int4BlockCodecTest {

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { idx ->
            hex.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun frq4MatchesPythonCodecWireFormatBitForBit() {
        val values = floatArrayOf(0.0f, 1.0f, -1.0f, 3.5f, -7.0f, 0.25f, -0.5f, 2.0f, 10.0f, -5.0f)
        val expectedHex = "4652513401000400020200000005000000030000000000003f0000803f6edbb63fa8f681a84f"

        val (packet, reconstructed) = Int4BlockCodec.encodeTensorPacket(
            data = values,
            shape = intArrayOf(2, 5),
            blockSize = 4,
        )

        assertEquals(expectedHex, bytesToHex(packet))

        val decoded = Int4BlockCodec.decodeTensorPacket(hexToBytes(expectedHex))
        assertArrayEquals(intArrayOf(2, 5), decoded.shape)
        assertEquals(4, decoded.blockSize)
        assertArrayEquals(reconstructed, decoded.data, 1e-6f)
    }

    @Test
    fun frqdStateBundleMatchesPythonBundleBitForBit() {
        val weight = floatArrayOf(0.0f, 1.0f, -1.0f, 3.5f, -7.0f, 0.25f, -0.5f, 2.0f, 10.0f, -5.0f)
        val bias = floatArrayOf(0.5f, -0.5f)
        val expectedHex =
            "46525144020000000a006c617965722e626961731600000046525134010004000102000000010000002549923d1f0c006c617965722e776569676874260000004652513401000400020200000005000000030000000000003f0000803f6edbb63fa8f681a84f"

        val (bundle, stats) = Int4BlockCodec.encodeStateDict(
            mapOf(
                "layer.weight" to (weight to intArrayOf(2, 5)),
                "layer.bias" to (bias to intArrayOf(2)),
            ),
            blockSize = 4,
        )

        assertEquals(expectedHex, bytesToHex(bundle))
        assertTrue(stats.cosineSimilarity >= 0.99)

        val unpacked = Int4BlockCodec.decodeStateDict(bundle)
        assertEquals(setOf("layer.bias", "layer.weight"), unpacked.keys)
        assertEquals(10, unpacked.getValue("layer.weight").data.size)
        assertEquals(2, unpacked.getValue("layer.bias").data.size)
    }

    @Test
    fun outlierResilientMicroBlocksAchieveHighCompressionAndFidelity() {
        val data = FloatArray(4096) { idx ->
            val sign = if (idx % 2 == 0) 1.0f else -1.0f
            sign * ((idx % 29) / 29.0f)
        }
        data[17] = 45.0f
        data[2049] = -40.0f

        val (bundle, stats) = Int4BlockCodec.encodeStateDict(
            mapOf("decoder.layers.0.self_attn.q_proj.weight" to (data to intArrayOf(64, 64))),
            blockSize = 64,
        )

        assertTrue("Expected > 6.5x ratio, got ${stats.compressionRatio}", stats.compressionRatio >= 6.5)
        assertTrue("Expected cosine >= 0.985, got ${stats.cosineSimilarity}", stats.cosineSimilarity >= 0.985)

        val restored = Int4BlockCodec.decodeStateDict(bundle).getValue("decoder.layers.0.self_attn.q_proj.weight")
        val cosine = Int4BlockCodec.cosineSimilarity(data, restored.data)
        assertTrue("Re-decoded cosine $cosine below 0.985", cosine >= 0.985)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPacketHeader() {
        Int4BlockCodec.decodeTensorPacket(ByteArray(16) { 0 })
    }
}

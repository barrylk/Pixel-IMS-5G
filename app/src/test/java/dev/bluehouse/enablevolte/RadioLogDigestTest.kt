package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class RadioLogDigestTest {
    @Test
    fun redactionKeepsTimestampsPidsAndRadioNumbers() {
        val line = "10-29 09:12:05.430  1283  1845 D RILJ: earfcn=1725 pci=160 band=3 nrState=2"
        assertEquals(line, RadioLogDigest.redact(line))
    }

    @Test
    fun redactionRemovesSubscriberIdentifiers() {
        val raw = "imsi=413020000000001 iccid: 8994020000000000001 IMEI=350000000000001 " +
            "caller +94 77 123 4567 msisdn=0771234567 ip 10.20.30.40 v6 2001:db8:0:0:0:0:0:1"
        val out = RadioLogDigest.redact(raw)
        listOf("413020000000001", "8994020000000000001", "350000000000001", "123 4567", "0771234567", "10.20.30.40", "2001:db8")
            .forEach { assertFalse("leaked $it in $out", out.contains(it)) }
        assertTrue(out.contains("imsi=[redacted]"))
    }

    @Test
    fun textDetectionRejectsBinary() {
        assertTrue(RadioLogDigest.isProbablyText("ENDCCapaInd Capability=0 Cause=3\n".toByteArray()))
        assertFalse(RadioLogDigest.isProbablyText(byteArrayOf(0x08, 0x00, 0x12, 0x7f)))
    }

    @Test
    fun endcEventsAreExtractedNewestLast() {
        val log = """
            10-29 09:12:01 D SITRIL: unrelated
            10-29 09:12:02 I SITRIL: [MiscService] ENDCCapaInd Capability=1 Cause=0
            10-29 09:15:40 I SITRIL: [MiscService] ENDCCapaInd Capability=0 Cause=7
        """.trimIndent()
        val events = RadioLogDigest.endcEvents(log)
        assertEquals(2, events.size)
        assertTrue(events.last().contains("Cause=7"))
    }

    @Test
    fun uecapLogDecodesEndcCombinations() {
        // Two combos: B3+n78 (EN-DC) and B1+B3 (LTE only).
        val bytes = message {
            varint(1, 889611834)
            bytes(3, message { bytes(2, combo(3, 10078)); bytes(2, combo(1, 3)) })
        }
        val summary = RadioLogDigest.decodeUecapLog(bytes)
        assertNotNull(summary)
        assertEquals(2, summary!!.combos)
        assertEquals(1, summary.endc)
        assertEquals(listOf(1, 3), summary.lteBands)
        assertEquals(listOf(78), summary.nrBands)
        assertEquals(listOf("B3+n78"), summary.samples)
    }

    @Test
    fun uecapLogWithOnlyFeatureTablesHasNoCombinations() {
        val bytes = message { varint(1, 889611834); bytes(6, message { varint(1, 1) }) }
        assertEquals(0, RadioLogDigest.decodeUecapLog(bytes)!!.combos)
        assertEquals("no band combinations", RadioLogDigest.decodeUecapLog(bytes)!!.describe())
    }

    private fun combo(vararg bands: Int): ByteArray = message {
        bands.forEach { band -> bytes(1, message { varint(1, band.toLong()) }) }
    }

    private class Proto {
        val out = ByteArrayOutputStream()

        private fun raw(v: Long) {
            var x = v
            while (x >= 0x80) {
                out.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
            out.write(x.toInt())
        }

        fun varint(field: Int, value: Long) {
            raw((field shl 3).toLong())
            raw(value)
        }

        fun varint(field: Int, value: Int) = varint(field, value.toLong())

        fun bytes(field: Int, data: ByteArray) {
            raw(((field shl 3) or 2).toLong())
            raw(data.size.toLong())
            out.write(data)
        }
    }

    private fun message(build: Proto.() -> Unit): ByteArray = Proto().apply(build).out.toByteArray()
}

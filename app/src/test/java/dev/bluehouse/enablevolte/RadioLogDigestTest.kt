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

    @Test
    fun registrationDumpsAreNotEndcEvents() {
        // 2.2.8 matched these because they print isEnDcAvailable; they are not events.
        val log = "11-03 22:02:08.892  3391  3391 D SST-0   : handlePollStateResultMessage: PS cellular. " +
            "NetworkRegistrationInfo{ domain=PS isDcNrRestricted = false isNrAvailable = true isEnDcAvailable = false }"
        assertTrue(RadioLogDigest.endcEvents(log).isEmpty())
    }

    @Test
    fun timelineRecordsOnlyChangesPerSlot() {
        fun nri(time: String, nr: Boolean, endc: Boolean) =
            "11-03 $time  3391  3391 D SST-0   : handlePollStateResultMessage: PS cellular. NetworkRegistrationInfo{ " +
                "domain=PS isDcNrRestricted = false isNrAvailable = $nr isEnDcAvailable = $endc }"
        val log = listOf(
            nri("21:58:28.200", true, false),
            nri("21:58:29.000", true, false),
            "11-03 22:00:20.484  3391  3391 D NetworkTypeController: [0] Transition with primary timer from legacy to not_restricted_rrc_con",
            "11-03 22:00:20.485  3391  3391 D DIC-0   : TelephonyDisplayInfo changed from TelephonyDisplayInfo {network=LTE, overrideNetwork=NONE} " +
                "to TelephonyDisplayInfo {network=LTE, overrideNetwork=NR_NSA, isRoaming=false}",
            nri("22:00:20.500", true, true),
            nri("22:00:43.000", true, true),
        ).joinToString("\n")
        assertEquals(
            listOf(
                "11-03 21:58:28.200  SIM slot 0: NR advertised=true, EN-DC available=false",
                "11-03 22:00:20.484  SIM slot 0: Transition with primary timer from legacy to not_restricted_rrc_con",
                "11-03 22:00:20.485  SIM slot 0: status bar LTE / NR_NSA",
                "11-03 22:00:20.500  SIM slot 0: NR advertised=true, EN-DC available=true",
            ),
            RadioLogDigest.nrTimeline(log),
        )
    }

    @Test
    fun timelineCarriesTheServingChannel() {
        val line = "11-03 22:00:20.600  3391  3391 D SST-0   : combine mChannelNumber=400 domain=PS isNrAvailable = true isEnDcAvailable = true"
        assertEquals(
            listOf("11-03 22:00:20.600  SIM slot 0: NR advertised=true, EN-DC available=true (channel 400)"),
            RadioLogDigest.nrTimeline(line),
        )
    }

    @Test
    fun aBurstOfReportsCollapsesToWhereItSettled() {
        fun nri(time: String, endc: Boolean, tag: String = "SST-0") =
            "11-03 $time  3391  3391 D $tag   : poll mChannelNumber=400 domain=PS isNrAvailable = true isEnDcAvailable = $endc"
        val log = listOf(
            nri("22:00:40.000", true),
            // One change reported by several layers within ~100 ms, ending at false.
            nri("22:00:42.306", false),
            nri("22:00:42.339", true),
            nri("22:00:42.340", false),
            // An untagged repeat of the same state is ignored.
            "11-03 22:00:42.347  3391  3391 D Other   : domain=PS isNrAvailable = true isEnDcAvailable = true",
            // A flip that returns to the prior state within the burst window disappears.
            nri("22:00:50.000", true),
            nri("22:00:50.100", false),
            "11-03 22:00:50.200  3391  3391 D SST-0   : poll mChannelNumber=65535 domain=PS isNrAvailable = true isEnDcAvailable = false",
        ).joinToString("\n")
        assertEquals(
            listOf(
                "11-03 22:00:40.000  SIM slot 0: NR advertised=true, EN-DC available=true (channel 400)",
                "11-03 22:00:42.340  SIM slot 0: NR advertised=true, EN-DC available=false (channel 400)",
            ),
            RadioLogDigest.nrTimeline(log),
        )
    }

    @Test
    fun rilCapabilityEventsStayInTheTimeline() {
        val line = "11-03 22:01:00.000  1327  1352 I SITRIL  : [MiscService] ENDCCapaInd Capability=0 Cause=7"
        assertEquals(listOf(line), RadioLogDigest.nrTimeline(line))
    }

    @Test
    fun capabilityLogsAreDatedAgainstTheLastBoot() {
        val boot = 1_000_000_000L
        val minute = 60_000L
        assertEquals("before last boot", RadioLogDigest.bootRelation(boot - 60 * minute, boot))
        assertEquals("at last boot", RadioLogDigest.bootRelation(boot + 3 * minute, boot))
        assertEquals("since last boot", RadioLogDigest.bootRelation(boot + 60 * minute, boot))
        assertEquals("boot time unknown", RadioLogDigest.bootRelation(boot, 0))
    }

    @Test
    fun modemLoggingIsRunningOnlyWhenTheLoggerSaysSo() {
        val loc = "/data/vendor/slog"
        assertEquals(RadioLogDigest.ModemLoggingState.OFF, RadioLogDigest.modemLoggingState("false", "false", "false", loc))
        assertEquals(RadioLogDigest.ModemLoggingState.STARTING, RadioLogDigest.modemLoggingState("true", "false", "false", loc))
        assertEquals(RadioLogDigest.ModemLoggingState.STARTING, RadioLogDigest.modemLoggingState("true", "true", "", loc))
        assertEquals(RadioLogDigest.ModemLoggingState.RUNNING, RadioLogDigest.modemLoggingState("true", "true", "true", loc))
        assertEquals(RadioLogDigest.ModemLoggingState.STOPPING, RadioLogDigest.modemLoggingState("false", "true", "true", loc))
        assertEquals(RadioLogDigest.ModemLoggingState.UNSUPPORTED, RadioLogDigest.modemLoggingState("", "", "", ""))
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

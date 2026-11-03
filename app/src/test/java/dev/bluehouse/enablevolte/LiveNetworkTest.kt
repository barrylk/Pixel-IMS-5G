package dev.bluehouse.enablevolte

import org.junit.Assert.*
import org.junit.Test

class LiveNetworkTest {
    @Test fun pixel10AdvertisedNrWithoutEndcDoesNotClaimAvailable5gLink() {
        val view = resolveLiveConnection(listOf(cell()), "41302", "LTE", 0,
            true, true, false)
        assertFalse(view.nrConnected)
        assertEquals("5G advertised · LTE only", view.fiveGStatus)
        assertTrue(view.explanation.contains("not reporting an EN-DC link"))
    }

    @Test fun explicitRestrictionTakesPrecedenceOverAvailabilityFlags() {
        val view = resolveLiveConnection(listOf(cell()), "41302", "LTE", 1,
            false, true, true)
        assertEquals("5G restricted by the network", view.fiveGStatus)
    }

    private fun cell(rat: String = "LTE", band: String = "B3", channel: Int = 1725, pci: Int = 62,
        plmn: String = "413-02", registered: Boolean = true, status: Int = 1) = SubscriptionModer.CellSnapshot(
        rat, registered, band, channel, pci, 50089, "5871874", -109, 1, -109, -11, 10, -77, 9, 5,
        20000, null, "[$band]", null, null, null, null, null, status, plmn,
    )

    @Test fun numericCarrierUsesHumanNameAndDoesNotLeakDump() {
        assertEquals("Dialog", liveCarrierName("413-02", "41302", "Dialog Work"))
        assertEquals("SLT-MOBITEL", liveCarrierName("{mVoiceRegState=0}", "41301", "Personal"))
        assertEquals("Example Mobile", liveCarrierName("Example Mobile", "99901", "SIM 1"))
    }

    @Test fun physicalChannelsKeepPhoneScopeAndIgnoreHistory() {
        val raw = """
            Phone Id=0
              mPhysicalChannelConfigs=[{mConnectionStatus=PrimaryServing, mNetworkType=LTE, mBand=3, mPhysicalCellId=62, mDownlinkChannelNumber=1725, mCellBandwidthDownlinkKhz=20000}]
            Phone Id=1
              mPhysicalChannelConfigs=[{mConnectionStatus=SecondaryServing, mNetworkType=LTE, mBand=1, mPhysicalCellId=355, mDownlinkChannelNumber=525, mCellBandwidthDownlinkKhz=15000}]
            local logs:
              {mConnectionStatus=SecondaryServing, mNetworkType=NR, mBand=78}
        """.trimIndent()
        assertEquals(listOf(3), parseLivePhysicalChannels(raw, 0).map { it.band })
        assertEquals(listOf(1), parseLivePhysicalChannels(raw, 1).map { it.band })
        assertTrue(parseLivePhysicalChannels(raw, 2).isEmpty())
    }

    @Test fun neighboursAndForeignSimNeverBecomeCarriers() {
        val cells = listOf(cell(), cell(band="B1", channel=525, registered=false, status=0),
            cell(band="n78", rat="NR", plmn="413-01", status=2))
        val view = resolveLiveConnection(cells, "41302", "LTE", 2, false, true, true)
        assertEquals("4G connected", view.title)
        assertEquals(1, view.carriers.size)
        assertFalse(view.nrConnected)
        assertEquals("5G available · not connected", view.fiveGStatus)
    }

    @Test fun repeatedLteBandsArePreservedAsDifferentCarriers() {
        val cells = listOf(cell(), cell(band="B1", channel=525, pci=355, status=2),
            cell(band="B1", channel=400, pci=353, status=2))
        val view = resolveLiveConnection(cells, "41302", "LTE", 2, true, true, true)
        assertEquals("B3 + B1 + B1", view.bandCombination)
        assertEquals("4G+ connected", view.title)
        assertEquals(2, view.carriers.count { it.role == "Extra 4G carrier" })
    }

    @Test fun nsaConnectedStateKeepsAnchorEvenWhenNrCellHidden() {
        val view = resolveLiveConnection(listOf(cell()), "41302", "LTE", 3, false, true, true)
        assertEquals("5G NSA connected", view.title)
        assertTrue(view.nrConnected)
        assertEquals("4G anchor", view.carriers.first().role)
        assertTrue(view.carriers.any { it.role == "5G carrier" && it.cell == null })
    }

    @Test fun saDoesNotInventLteAnchor() {
        val view = resolveLiveConnection(listOf(cell(rat="NR", band="n78")), "41302", "NR", 0, false, true, true)
        assertEquals("5G SA connected", view.title)
        assertFalse(view.carriers.any { it.role == "4G anchor" })
    }

    @Test fun caFlagDoesNotInventExtraBands() {
        val view = resolveLiveConnection(listOf(cell()), "41302", "LTE", 2, true, true, true)
        assertEquals("4G+ reported", view.title)
        assertEquals("B3", view.bandCombination)
        assertTrue(view.carrierNote.contains("not reporting"))
    }

    @Test fun emptyRegisteredSetDoesNotPromoteNeighbourToServing() {
        val view = resolveLiveConnection(listOf(cell(registered=false, status=0)), "41302", "LTE", 0, false, false, false)
        assertTrue(view.carriers.isEmpty())
    }

    @Test fun unavailableSentinelsNeverRenderAsMeasurements() {
        assertEquals("Not reported", radioReading(Int.MAX_VALUE, "dBm"))
        assertEquals("Not reported", radioReading(null, "MHz"))
        assertEquals("−109 dBm", radioReading(-109, "dBm"))
    }

    @Test fun contradictoryNrCellCannotOverrideLteRegistration() {
        val view = resolveLiveConnection(listOf(cell(), cell(rat="NR", band="n78", status=2)),
            "41302", "LTE", 2, false, true, true)
        assertFalse(view.nrConnected)
        assertFalse(view.carriers.any { it.rat == "NR" })
        assertTrue(view.neighbours.any { it.type == "NR" })
    }

    @Test fun currentPhoneChannelsSupplyExtraCarriersWithoutNeighbourMetrics() {
        val raw = "Phone Id=0\n mPhysicalChannelConfigs=[{mConnectionStatus=PrimaryServing, mNetworkType=LTE, mBand=3, mPhysicalCellId=62, mDownlinkChannelNumber=1725, mCellBandwidthDownlinkKhz=20000}, {mConnectionStatus=SecondaryServing, mNetworkType=LTE, mBand=1, mPhysicalCellId=355, mDownlinkChannelNumber=525, mCellBandwidthDownlinkKhz=15000}]"
        val view = resolveLiveConnection(listOf(cell()), "41302", "LTE", 2, true, true, true, parseLivePhysicalChannels(raw,0))
        assertEquals("B3 + B1", view.bandCombination)
        assertNull(view.carriers.last().cell)
        assertEquals(15000, view.carriers.last().bandwidthKhz)
    }

    @Test fun saRegistrationDoesNotPromoteStaleLteReading() {
        val view = resolveLiveConnection(listOf(cell(), cell(rat="NR", band="n78")),
            "41302", "NR", 0, false, true, true)
        assertFalse(view.carriers.any { it.rat == "LTE" })
        assertTrue(view.neighbours.any { it.type == "LTE" })
    }

    @Test fun scopedPhysicalCarrierRecoversMetricsFromSecondaryCellWithoutPlmn() {
        val secondary = cell(band="B41", channel=41090, pci=184, plmn="", registered=false, status=2)
        val physical = LivePhysicalChannel(0, "LTE", 2, 41, 184, 41090, 20000, null, null, 2640000, null)
        val view = resolveLiveConnection(listOf(cell(), secondary), "41302", "LTE", 2, true, true, false, listOf(physical))
        assertSame(secondary, view.carriers.first { it.band == "B41" }.cell)
        assertFalse(view.neighbours.contains(secondary))
    }

    @Test fun physicalMatchNeverBorrowsForeignOperatorMeasurements() {
        val foreign = cell(band="B41", channel=41090, pci=184, plmn="413-01", status=2)
        val physical = LivePhysicalChannel(0, "LTE", 2, 41, 184, 41090, 20000, null, null, null, null)
        val view = resolveLiveConnection(listOf(cell(), foreign), "41302", "LTE", 2, true, true, false, listOf(physical))
        assertNull(view.carriers.first { it.band == "B41" }.cell)
    }

    @Test fun scopedSecondaryCellsWithoutPlmnKeepCaWhenPhysicalReportIsEmpty() {
        val cells = listOf(cell(band="B1", channel=525, pci=353),
            cell(plmn="", registered=false, status=2),
            cell(band="B41", channel=41090, pci=184, plmn="", registered=false, status=2))
        val view = resolveLiveConnection(cells, "41302", "LTE", 2, true, true, false,
            cellsScopedToSubscription=true)
        assertEquals("B1 + B3 + B41", view.bandCombination)
        assertEquals(3, view.carriers.size)
    }

    @Test fun globalUnattributedSecondaryCellsAreNotBorrowedWithoutPhysicalEvidence() {
        val view = resolveLiveConnection(listOf(cell(), cell(band="B41", plmn="", status=2)),
            "41302", "LTE", 2, true, true, false)
        assertEquals("B3", view.bandCombination)
    }

    @Test fun currentPhysicalComponentsReplaceOlderServingCellCombination() {
        val older = listOf(cell(), cell(band="B1", channel=525, pci=353, status=2),
            cell(band="B41", channel=41090, pci=184, status=2))
        fun p(band: Int, channel: Int, pci: Int, width: Int, status: Int) =
            LivePhysicalChannel(0, "LTE", status, band, pci, channel, width, null, null, null, null)
        val three = resolveLiveConnection(older, "41302", "LTE", 2, true, true, true,
            listOf(p(3,1725,62,20000,1),p(1,525,353,15000,2),p(41,41090,184,20000,2)))
        assertEquals(listOf(20000,15000,20000), three.caBandwidth.componentWidthsKhz)
        val two = resolveLiveConnection(older, "41302", "LTE", 2, true, true, true,
            listOf(p(3,1725,62,10000,1),p(1,525,353,15000,2)))
        assertEquals("B3 + B1", two.bandCombination)
        assertEquals(listOf(10000,15000), two.caBandwidth.componentWidthsKhz)
        assertEquals(25000, two.caBandwidth.totalKhz)
        val one = resolveLiveConnection(older, "41302", "LTE", 2, true, true, true,
            listOf(p(3,1725,62,10000,1)))
        assertEquals("B3", one.bandCombination)
        assertEquals(1, one.caBandwidth.componentCount)
        assertEquals(10000, one.caBandwidth.totalKhz)
    }

    @Test fun unknownWidthDoesNotProduceAnInventedTotal() {
        val view = resolveLiveConnection(listOf(cell().copy(bandwidthKhz=null)), "41302", "LTE", 2, true, true, true)
        assertEquals(listOf<Int?>(null), view.caBandwidth.componentWidthsKhz)
        assertNull(view.caBandwidth.totalKhz)
    }
}

package com.dm20remote.net

import com.dm20remote.proto.MicInBusModel
import com.dm20remote.proto.MicInBusSendModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The send mapping is the whole feature: a `107` event names one bus with
 * `bus_param_id`, and a snapshot holds nine of them in a fixed order. Getting the
 * table or the fold wrong silently moves the wrong bus, so both are pinned here.
 *
 * The ids come from `ModuleConst$BusCommandId` in the shipped APK, see
 * `docs/protocol-spec.md` §5.1.1.
 */
class BusSendsTest {

    @Test
    fun `the recovered parameter ids are in bus order`() {
        assertEquals(
            listOf(168, 176, 184, 192, 680, 688, 696, 704, 1192),
            BusSends.PARAM_IDS.toList(),
        )
    }

    @Test
    fun `every slot maps to a unique parameter id and back`() {
        val ids = (0 until BusSends.SLOT_COUNT).map { BusSends.paramIdOf(it) }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEachIndexed { slot, id ->
            assertEquals(id, BusSends.paramIdOf(slot))
            assertEquals(slot, BusSends.slotOf(id))
        }
    }

    @Test
    fun `an unknown parameter id has no slot`() {
        assertNull(BusSends.slotOf(0))
        assertNull(BusSends.slotOf(200))
    }

    @Test
    fun `out of range slots clamp instead of throwing`() {
        assertEquals(168, BusSends.paramIdOf(-1))
        assertEquals(1192, BusSends.paramIdOf(BusSends.SLOT_COUNT))
    }

    @Test
    fun `slotAt reads bus1 to bus8 then the L R master`() {
        val model = MicInBusSendModel.newBuilder()
            .setModuleId(3)
            .setBus1(send(101f))
            .setBus2(send(102f))
            .setBus3(send(103f))
            .setBus4(send(104f))
            .setBus5(send(105f))
            .setBus6(send(106f))
            .setBus7(send(107f))
            .setBus8(send(108f))
            .setBusLnR(send(109f))
            .build()

        assertEquals((101..109).toList(), model.slots().map { it.gainValue.toInt() })
        assertEquals(109f, model.slotAt(BusSends.LR).gainValue, 0f)
        assertEquals(0f, model.slotAt(99).gainValue, 0f)
    }

    @Test
    fun `withSlot writes only that slot`() {
        val model = MicInBusSendModel.newBuilder()
            .setBus1(send(10f))
            .setBus2(send(20f))
            .build()

        val updated = model.withSlot(3, send(99f))

        assertEquals(10f, updated.bus1.gainValue, 0f)
        assertEquals(20f, updated.bus2.gainValue, 0f)
        assertEquals(99f, updated.bus4.gainValue, 0f)
        assertEquals(0f, updated.bus3.gainValue, 0f)
        assertEquals(5f, model.withSlot(BusSends.LR, send(5f)).busLnR.gainValue, 0f)
        // An out-of-range slot is a no-op rather than a crash or a clamp onto bus 1.
        assertEquals(model, model.withSlot(-1, send(1f)))
    }

    @Test
    fun `a sparse event folds into the slot its bus parameter names`() {
        val model = MicInBusSendModel.newBuilder().build()
        // 688 is BusParam6, i.e. slot 5 (bus6).
        val incoming = MicInBusModel.newBuilder()
            .setModuleId(12)
            .setBusParamId(688)
            .setGainValue(755f)
            .build()

        val updated = model.folded(incoming)

        assertEquals(755f, updated.bus6.gainValue, 0f)
        assertEquals(12, updated.bus6.moduleId)
        assertEquals(688, updated.bus6.busParamId)
        assertEquals(0f, updated.bus5.gainValue, 0f)
        assertEquals(0f, updated.bus7.gainValue, 0f)
    }

    @Test
    fun `a fold keeps the fields the event omitted`() {
        val model = MicInBusSendModel.newBuilder()
            .setBus6(
                MicInBusModel.newBuilder()
                    .setModuleId(12)
                    .setBusParamId(688)
                    .setIsBusEnable(true)
                    .setIsPrefader(true)
                    .setGainValue(500f)
                    .setPanValue(-25f),
            )
            .build()

        // The captured gain-only frame for bus 688: module_id + bus_param_id + gain.
        val updated = model.folded(
            MicInBusModel.newBuilder()
                .setModuleId(12)
                .setBusParamId(688)
                .setGainValue(755f)
                .build(),
        )

        val bus6 = updated.bus6
        assertEquals(755f, bus6.gainValue, 0f)
        assertTrue(bus6.isBusEnable)
        assertTrue(bus6.isPrefader)
        assertEquals(-25f, bus6.panValue, 0f)
        assertEquals(12, bus6.moduleId)
        assertEquals(688, bus6.busParamId)
    }

    @Test
    fun `an event without a bus parameter is ignored`() {
        val model = MicInBusSendModel.newBuilder().setBus1(send(10f)).build()

        // proto3 decodes an absent bus_param_id as 0, which must not be taken as
        // "the first slot".
        val incoming = MicInBusModel.newBuilder().setModuleId(4).setGainValue(999f).build()
        assertFalse(incoming.hasBusParamId())

        assertEquals(model, model.folded(incoming))
    }

    @Test
    fun `an event with an unknown bus parameter is ignored`() {
        val model = MicInBusSendModel.newBuilder().setBus1(send(10f)).build()
        val incoming = MicInBusModel.newBuilder()
            .setModuleId(4)
            .setBusParamId(200)
            .setGainValue(999f)
            .build()

        assertEquals(model, model.folded(incoming))
    }

    @Test
    fun `an explicit false on the wire clears the send enable`() {
        val model = MicInBusSendModel.newBuilder()
            .setBus1(
                MicInBusModel.newBuilder()
                    .setModuleId(0)
                    .setBusParamId(168)
                    .setIsBusEnable(true)
                    .setGainValue(480f)
                    .setPanValue(-20f),
            )
            .build()

        // Exactly what the console mirrors back after `168 on=false` on channel 0:
        // an explicitly encoded module_id 0, an explicitly encoded false, and the
        // fields that identify the bus. Nothing else, because nothing else changed.
        val echo = MicInBusModel.parseFrom("080010a8011800".hex())

        assertTrue(echo.hasModuleId())
        assertTrue(echo.hasBusParamId())
        assertTrue(echo.hasIsBusEnable())
        assertFalse(model.folded(echo).bus1.isBusEnable)
    }

    @Test
    fun `an explicit false on the wire also clears a pre-fader send`() {
        val model = MicInBusSendModel.newBuilder()
            .setBusLnR(
                MicInBusModel.newBuilder()
                    .setModuleId(3)
                    .setBusParamId(BusSends.PARAM_IDS[BusSends.LR])
                    .setIsPrefader(true),
            )
            .build()

        val echo = MicInBusModel.parseFrom("080310a8092000".hex())

        assertTrue(echo.hasIsPrefader())
        assertFalse(model.folded(echo).busLnR.isPrefader)
        // The enable flag was not on the wire, so it keeps whatever it had.
        assertFalse(model.folded(echo).busLnR.isBusEnable)
        // Nothing landed on the buses before it.
        assertEquals(0, model.folded(echo).bus1.moduleId)
    }

    private fun send(gain: Float) = MicInBusModel.newBuilder()
        .setModuleId(0)
        .setBusParamId(168)
        .setGainValue(gain)
        .build()

    private fun String.hex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

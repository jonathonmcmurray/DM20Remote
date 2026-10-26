package com.dm20remote.net

import com.dm20remote.proto.MicInBusModel
import com.dm20remote.proto.MicInBusSendModel

/**
 * How a bus send is addressed on the wire.
 *
 * A snapshot carries the whole [MicInBusSendModel] — all eight buses plus the L/R
 * master in a fixed layout — but the `107` change event and the `108` command carry
 * a *single* [MicInBusModel]. That message identifies its bus with `bus_param_id`
 * and its input channel with `module_id`, so folding one into the other is a lookup
 * on `bus_param_id`, never a positional guess.
 *
 * The values are the `ModuleConst.BusCommandId` constants, read out of the shipped
 * `DM20_1.0.3_APKPure.apk` (encoded static field values). The original client
 * assigns them literally in `MicInBusSendModel(int moduleId)` and folds on them in
 * `IN1View.setBusSendViewData`; see `docs/protocol-spec.md` §5.1.1.
 */
object BusSends {
    /** Slots in a [MicInBusSendModel]: `bus1..bus8`, then the L/R master. */
    const val SLOT_COUNT = 9

    /** The index of the stereo L/R master slot. */
    const val LR = 8

    /** `ModuleConst.BusCommandId.BusParam1..BusParam8`, then `BusParamSM`. */
    val PARAM_IDS = intArrayOf(168, 176, 184, 192, 680, 688, 696, 704, 1192)

    /** The `bus_param_id` that addresses [slot]; out-of-range slots clamp like the desk. */
    fun paramIdOf(slot: Int): Int = PARAM_IDS[slot.coerceIn(0, SLOT_COUNT - 1)]

    /** The slot a `bus_param_id` addresses, or null for a bus this client does not model. */
    fun slotOf(busParamId: Int): Int? = PARAM_IDS.indexOf(busParamId).takeIf { it >= 0 }

    fun isSlot(slot: Int): Boolean = slot in 0 until SLOT_COUNT

    fun isLr(slot: Int): Boolean = slot == LR
}

/** One slot of the snapshot message, in the order [BusSends.PARAM_IDS] describes. */
fun MicInBusSendModel.slotAt(slot: Int): MicInBusModel = when (slot) {
    0 -> bus1
    1 -> bus2
    2 -> bus3
    3 -> bus4
    4 -> bus5
    5 -> bus6
    6 -> bus7
    7 -> bus8
    BusSends.LR -> busLnR
    else -> MicInBusModel.getDefaultInstance()
}

/** A copy with [model] written into [slot]; any other slot is untouched. */
fun MicInBusSendModel.withSlot(slot: Int, model: MicInBusModel): MicInBusSendModel {
    val builder = toBuilder()
    when (slot) {
        0 -> builder.bus1 = model
        1 -> builder.bus2 = model
        2 -> builder.bus3 = model
        3 -> builder.bus4 = model
        4 -> builder.bus5 = model
        5 -> builder.bus6 = model
        6 -> builder.bus7 = model
        7 -> builder.bus8 = model
        BusSends.LR -> builder.busLnR = model
        else -> return this
    }
    return builder.build()
}

/** All nine slots in address order. */
fun MicInBusSendModel.slots(): List<MicInBusModel> =
    List(BusSends.SLOT_COUNT) { slotAt(it) }

/**
 * Merges a sparse `107` event into the slot its `bus_param_id` names.
 *
 * Only the fields the event actually carries are copied (`mergeFrom` respects
 * proto3 presence — see §4.1), so a level move does not clear the send's enable or
 * pre-fader flags. An event without a `bus_param_id` cannot be placed and is
 * returned unchanged: the field decodes as `0` when absent, which would otherwise
 * land on whichever slot happened to be first.
 */
fun MicInBusSendModel.folded(incoming: MicInBusModel): MicInBusSendModel {
    if (!incoming.hasBusParamId()) return this
    val slot = BusSends.slotOf(incoming.busParamId) ?: return this
    val merged = slotAt(slot).toBuilder().mergeFrom(incoming).build()
    return withSlot(slot, merged)
}

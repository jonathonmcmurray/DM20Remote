package com.dm20remote.net

import com.dm20remote.proto.EqPartModel

/**
 * `EqPartModel` band indices, in the same order as the wire `EqFragment` enum, plus
 * the band-type codes the original EQ view writes into `highType`/`lowType`.
 *
 * Recovered from `com.esunny.sound.ui.view.innerview.EQView`: the shelf buttons flip
 * the band between [BELL] (0) and a shelf code — `lowType` uses 1 while `highType`
 * uses 2, so the two bands do not share the same shelf value.
 */
object EqBands {
    const val HIGH = 0
    const val HIGH_MID = 1
    const val LOW_MID = 2
    const val LOW = 3
    const val COUNT = 4

    /** A bell / fully parametric band. */
    const val BELL = 0

    /** `EqPartModel.lowType` when the low band is a shelf. */
    const val LOW_SHELF = 1

    /** `EqPartModel.highType` when the high band is a shelf (note: 2, not 1). */
    const val HIGH_SHELF = 2

    val names = listOf("High", "HiMid", "LoMid", "Low")
}

/** The frequency of one band, in Hz (the wire value). */
fun EqPartModel.bandFrequency(band: Int): Float = when (band) {
    EqBands.HIGH -> highFreqValue
    EqBands.HIGH_MID -> highMidFreqValue
    EqBands.LOW_MID -> lowMidFreqValue
    else -> lowFreqValue
}

/** The gain of one band, in dB. */
fun EqPartModel.bandGain(band: Int): Float = when (band) {
    EqBands.HIGH -> highGainValue
    EqBands.HIGH_MID -> highMidGainValue
    EqBands.LOW_MID -> lowMidGainValue
    else -> lowGainValue
}

/** The Q of one band. */
fun EqPartModel.bandQ(band: Int): Float = when (band) {
    EqBands.HIGH -> highQValue
    EqBands.HIGH_MID -> highMidQValue
    EqBands.LOW_MID -> lowMidQValue
    else -> lowQValue
}

/**
 * Whether a band is a shelf. Only the high and low bands have a shelf mode; the
 * two mid bands are always bells.
 */
fun EqPartModel.isBandShelf(band: Int): Boolean = when (band) {
    EqBands.HIGH -> highType == EqBands.HIGH_SHELF
    EqBands.LOW -> lowType == EqBands.LOW_SHELF
    else -> false
}
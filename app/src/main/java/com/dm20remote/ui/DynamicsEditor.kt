package com.dm20remote.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.InputChannel
import com.dm20remote.net.Units

/**
 * Gate and compressor editor for one input channel.
 *
 * Each field is edited in the engineering units the wire carries (dB, ms, ratio)
 * — see `Units.DynamicsRanges` and `docs/protocol-spec.md` §6.1.
 */
@Composable
internal fun DynamicsEditor(channel: InputChannel) {
    val d = channel.dynamics

    Text("Gate", style = MaterialTheme.typography.titleSmall)
    SwitchRow("Gate enabled", checked = d.isGateEnable) { enabled ->
        ConsoleSession.setGateEnable(channel, enabled)
    }
    ValueSlider("Threshold", value = d.gateThresholdValue, range = Units.DynamicsRanges.gateThreshold) { db ->
        ConsoleSession.setGateThreshold(channel, db)
    }
    ValueSlider("Attack", value = d.gateAttackValue, range = Units.DynamicsRanges.gateAttack) { ms ->
        ConsoleSession.setGateAttack(channel, ms)
    }
    ValueSlider("Release", value = d.gateReleaseValue, range = Units.DynamicsRanges.gateRelease) { ms ->
        ConsoleSession.setGateRelease(channel, ms)
    }
    ValueSlider("Hold", value = d.gateHoldValue, range = Units.DynamicsRanges.gateHold) { ms ->
        ConsoleSession.setGateHold(channel, ms)
    }
    ValueSlider("Depth", value = d.gateDepthValue, range = Units.DynamicsRanges.gateDepth) { db ->
        ConsoleSession.setGateDepth(channel, db)
    }

    Spacer(Modifier.height(12.dp))
    Text("Compressor", style = MaterialTheme.typography.titleSmall)
    SwitchRow("Compressor enabled", checked = d.isCompressorEnable) { enabled ->
        ConsoleSession.setCompressorEnable(channel, enabled)
    }
    ValueSlider("Threshold", value = d.compressorTresholdValue, range = Units.DynamicsRanges.compressorThreshold) { db ->
        ConsoleSession.setCompressorThreshold(channel, db)
    }
    ValueSlider("Attack", value = d.compressorAttackValue, range = Units.DynamicsRanges.compressorAttack) { ms ->
        ConsoleSession.setCompressorAttack(channel, ms)
    }
    ValueSlider("Release", value = d.compressorReleaseValue, range = Units.DynamicsRanges.compressorRelease) { ms ->
        ConsoleSession.setCompressorRelease(channel, ms)
    }
    ValueSlider("Ratio", value = d.compressorRatioValue, range = Units.DynamicsRanges.compressorRatio) { ratio ->
        ConsoleSession.setCompressorRatio(channel, ratio)
    }
    ValueSlider("Make-up gain", value = d.compressorGainValue, range = Units.DynamicsRanges.compressorGain) { db ->
        ConsoleSession.setCompressorGain(channel, db)
    }
}
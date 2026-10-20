package com.dm20remote.model

import com.dm20remote.net.ConsoleEvent
import com.dm20remote.net.Inbound
import com.dm20remote.net.ProtoIds
import com.dm20remote.proto.PlayCommand
import com.dm20remote.proto.SKDMCData
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Folds decoded console traffic into an immutable [ConsoleState] snapshot,
 * ready for Compose to observe.
 */
class ConsoleStore {
    private val _state = MutableStateFlow(ConsoleState())
    val state: StateFlow<ConsoleState> = _state.asStateFlow()

    fun onEvent(event: ConsoleEvent) {
        when (event) {
            is ConsoleEvent.Connection -> _state.update {
                it.copy(
                    controlConnected = event.control,
                    meterConnected = event.meter,
                    musicConnected = event.music,
                    lastError = if (event.control) "" else it.lastError,
                )
            }

            is ConsoleEvent.Meters -> _state.update {
                it.copy(meters = event.db, meterFrames = it.meterFrames + 1)
            }

            is ConsoleEvent.Failure -> _state.update { it.copy(lastError = event.message) }

            is ConsoleEvent.Message -> onMessage(event.inbound)
        }
    }

    private fun onMessage(inbound: Inbound) {
        when (inbound) {
            is Inbound.Snapshot -> applySnapshot(inbound.data)

            // Change events are sparse: the console sends only the fields that
            // changed (see docs/protocol-spec.md 4.1), so each event is merged
            // into the stored model rather than replacing it. Replacing would
            // reset every field the event omitted - mute and solo returning to
            // false on any unrelated fader change, for instance.
            is Inbound.MicOutputPart -> {
                val model = inbound.model
                if (model.hasModuleId()) {
                    updateInput(model.moduleId) { it.copy(output = it.output.mergeWith(model)) }
                }
            }

            is Inbound.MicInputPart -> {
                val model = inbound.model
                if (model.hasModuleId()) {
                    updateInput(model.moduleId) { it.copy(input = it.input.mergeWith(model)) }
                }
            }

            is Inbound.MicEqPart -> {
                val model = inbound.model
                if (model.hasModuleId()) {
                    updateInput(model.moduleId) { it.copy(eq = it.eq.mergeWith(model)) }
                }
            }

            is Inbound.MicDynamicPart -> {
                val model = inbound.model
                if (model.hasModuleId()) {
                    updateInput(model.moduleId) { it.copy(dynamics = it.dynamics.mergeWith(model)) }
                }
            }

            is Inbound.BusOutputPart -> {
                val model = inbound.model
                if (model.hasModuleId()) {
                    updateBus(model.moduleId) { it.copy(output = it.output.mergeWith(model)) }
                }
            }

            is Inbound.MediaState -> onTransportFrame(inbound.model)

            is Inbound.MusicTime -> onTransportFrame(inbound.model)

            is Inbound.MusicList -> _state.update {
                it.copy(musicList = inbound.model.itemsList.map { item -> MusicTrack(item.fileName, item.filePathName) })
            }

            is Inbound.SetupInfoPart -> _state.update {
                it.copy(
                    apkVersion = inbound.model.apkVersion,
                    dspVersion = inbound.model.dspVersion,
                    serialId = inbound.model.serialID,
                )
            }

            else -> Unit
        }
    }

    /**
     * Folds a transport frame from 191/195 or the music channel. The music channel is a
     * bare [PlayCommand] the original app used only as a tick, so a frame that omits the
     * path (or a duration) must not blank the title the control channel already reported.
     */
    private fun onTransportFrame(model: PlayCommand) {
        _state.update { state ->
            val incoming = model.toTransport()
            state.copy(
                transport = incoming.copy(
                    songPath = incoming.songPath.ifBlank { state.transport.songPath },
                    durationMs = incoming.durationMs.takeIf { it > 0 } ?: state.transport.durationMs,
                ),
            )
        }
    }

    private fun applySnapshot(data: SKDMCData) {
        val inputs = snapshotChannels(data).mapIndexed { index, model ->
            InputChannel(
                moduleId = index,
                name = model.channelName.ifBlank { ProtoIds.defaultInputName(index) },
                output = model.outputPartModel,
                input = model.inputPartModel,
                eq = model.eqPartModel,
                dynamics = model.dymanicPartModel,
                busSend = model.busSendPartModel,
            )
        }

        val buses = listOf(
            data.busModel.outBus1Model,
            data.busModel.outBus2Model,
            data.busModel.outBus3Model,
            data.busModel.outBus4Model,
            data.busModel.outBus5Model,
            data.busModel.outBus6Model,
            data.busModel.outBus7Model,
            data.busModel.outBus8Model,
        ).mapIndexed { index, model ->
            val moduleId = ProtoIds.BUS1 + index
            BusChannel(
                moduleId = moduleId,
                name = model.busName.ifBlank { ProtoIds.defaultBusName(moduleId) },
                output = model.outputModel,
            )
        }

        val info = data.setupModel.infoModel

        _state.update {
            it.copy(
                inputs = inputs,
                buses = buses,
                scenes = data.sceneModelListList.map { scene -> scene.name },
                currentSceneGuid = data.sceneGuid,
                apkVersion = info.apkVersion,
                dspVersion = info.dspVersion,
                serialId = info.serialID,
                lastError = "",
            )
        }
    }

    private fun snapshotChannels(data: SKDMCData) = with(data) {
        val in1 = in1Model
        val in2 = in2Model
        listOf(
            in1.ch1Model, in1.ch2Model, in1.ch3Model, in1.ch4Model,
            in1.ch5Model, in1.ch6Model, in1.ch7Model, in1.ch8Model,
            in2.ch9Model, in2.ch10Model, in2.ch11Model, in2.ch12Model,
            in2.st1Model, in2.st2Model, in2.getUSBModel(), in2.getSPDIFModel(),
        )
    }

    /**
     * Folds a sparse change event into a stored model.
     *
     * The console sends only the fields it changed, and protobuf reports an absent
     * scalar as its default value, so replacing the stored model with the parsed event
     * would reset everything the event left out — mute and solo flipping back to false
     * whenever the fader moved, for instance. `mergeFrom` copies only the fields the
     * event actually carries, which is exactly the delta the console described.
     */
    private fun <T : MessageLite> T.mergeWith(incoming: T): T {
        @Suppress("UNCHECKED_CAST")
        return toBuilder().mergeFrom(incoming).build() as T
    }

    private fun updateInput(moduleId: Int, transform: (InputChannel) -> InputChannel) {
        if (moduleId !in 0 until ProtoIds.INPUT_COUNT) return
        _state.update { state ->
            val current = state.inputs.getOrNull(moduleId) ?: return@update state
            state.copy(inputs = state.inputs.toMutableList().also { it[moduleId] = transform(current) })
        }
    }

    private fun updateBus(moduleId: Int, transform: (BusChannel) -> BusChannel) {
        val index = moduleId - ProtoIds.BUS1
        if (index !in 0 until ProtoIds.BUS_COUNT) return
        _state.update { state ->
            val current = state.buses.getOrNull(index) ?: return@update state
            state.copy(buses = state.buses.toMutableList().also { it[index] = transform(current) })
        }
    }
}

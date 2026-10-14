package com.dm20remote.net

/**
 * Protobuf message ids and module ids used on the wire.
 *
 * Recovered from `SendCommandManager` / `CmdHandler` in `com.esunny.sound` 1.0.3.
 * Client -> console commands use even ids in `10x..13x`; the matching
 * console -> client change events use the preceding odd id.
 */
object ProtoIds {
    /** Console -> client: full state snapshot, also the client's init request id. */
    const val REQUEST_INIT_DATA = 1

    /**
     * Sentinel used by [ProtoCodec] for the music channel, which carries no protoId
     * at all — only a bare delimited message. Real protoIds are always >= 1.
     */
    const val MUSIC_CHANNEL = 0

    // ---- console -> client change events ----
    const val IN_ALL_DATA = 1
    const val IN_MIC_INPUT_PART = 101
    const val IN_MIC_EQ_PART = 103
    const val IN_MIC_DYNAMIC_PART = 105
    const val IN_MIC_BUS_PART = 107
    const val IN_MIC_OUTPUT_PART = 109
    const val IN_BUS_INPUT_PART = 111
    const val IN_BUS_OUTPUT_PART = 113
    const val IN_SETUP_INFO_PART = 115
    const val IN_SETUP_PATCH_PART = 117
    const val IN_SETUP_FX_MODUL_PART = 119
    const val IN_SETUP_FX_DELAY_PART = 121
    const val IN_SETUP_FX_REVERB_PART = 123
    const val IN_SETUP_FX_GEQ_PART = 125
    const val IN_SETUP_MONITOR_PART = 127
    const val IN_SETUP_METER_PART = 129
    const val IN_IN_CHANNEL_NAME = 131
    const val IN_OUT_CHANNEL_NAME = 133
    const val IN_SCENE_SELECT = 141
    const val IN_SCENE_SELECTED = 143
    const val IN_SCENE_DELETE = 145
    const val IN_SCENE_RENAME = 147
    const val IN_SCENE_COPY = 149
    const val IN_SCENE_NEW = 151
    const val IN_SCENE_SAVE = 153
    const val IN_SCENE_LOAD = 155
    const val IN_SCENE_UP = 157
    const val IN_SCENE_DOWN = 159
    const val IN_SCENE_PREV = 161
    const val IN_SCENE_NEXT = 163
    const val IN_EQ_LIBRARY_SAVE = 173
    const val IN_DYNAMIC_LIBRARY_SAVE = 177
    const val IN_MEDIA_CHANGED = 191
    const val IN_MUSIC_LIST = 193
    const val IN_MUSIC_STATE = 195
    const val IN_DYNAMIC_LIBRARY = 201
    const val IN_PEQ_LIBRARY = 203
    const val IN_FX_GEQ_LIBRARY = 205
    const val IN_OUT_INPUT_PART = 207
    const val IN_SCENE_EXPORT = 211
    const val IN_IMPORT_FILE_LIST = 213
    const val IN_IMPORT_FILE = 215
    const val IN_FX_MODULE_LIBRARY = 221
    const val IN_FX_DELAY_LIBRARY = 227
    const val IN_FX_REVERB_LIBRARY = 233

    // ---- client -> console commands ----
    const val OUT_MIC_INPUT_PART = 102
    const val OUT_MIC_EQ_PART = 104
    const val OUT_MIC_DYNAMIC_PART = 106
    const val OUT_MIC_BUS_PART = 108
    const val OUT_MIC_OUTPUT_PART = 110
    const val OUT_BUS_INPUT_PART = 112
    const val OUT_BUS_OUTPUT_PART = 114
    const val OUT_SETUP_INFO_PART = 116
    const val OUT_SETUP_PATCH_PART = 118
    const val OUT_SETUP_FX_MODUL_PART = 120
    const val OUT_SETUP_FX_DELAY_PART = 122
    const val OUT_SETUP_FX_REVERB_PART = 124
    const val OUT_SETUP_FX_GEQ_PART = 126
    const val OUT_SETUP_MONITOR_PART = 128
    const val OUT_SETUP_METER_PART = 130
    const val OUT_RECORDER_ITEM = 192

    // ---- module ids ----
    const val MODULE_MONITOR = 273
    const val MODULE_OUTPUT_RETURN = 255
    const val BUS1 = 128
    const val BUS2 = 129
    const val BUS3 = 130
    const val BUS4 = 131
    const val BUS5 = 132
    const val BUS6 = 133
    const val BUS7 = 134
    const val BUS8 = 135

    /** Input module ids 0..15: IN1..IN12, ST1, ST2, USB, SPDIF. */
    const val INPUT_COUNT = 16
    const val BUS_COUNT = 8

    fun isBus(moduleId: Int): Boolean = moduleId in BUS1..(BUS1 + BUS_COUNT - 1)

    private val defaultNames = listOf(
        "IN 1", "IN 2", "IN 3", "IN 4", "IN 5", "IN 6", "IN 7", "IN 8",
        "IN 9", "IN 10", "IN 11", "IN 12", "ST 1", "ST 2", "USB", "SPDIF",
    )

    fun defaultInputName(moduleId: Int): String =
        defaultNames.getOrElse(moduleId) { "IN ${moduleId + 1}" }

    fun defaultBusName(moduleId: Int): String =
        if (isBus(moduleId)) "BUS ${moduleId - BUS1 + 1}" else "BUS ?"

    /**
     * Human-readable name for a protoId, for the wire log. Value `1` is both the
     * init request and the snapshot, so it is labelled `IN_ALL_DATA`.
     */
    fun name(protoId: Int): String = when (protoId) {
        IN_ALL_DATA -> "IN_ALL_DATA"
        IN_MIC_INPUT_PART -> "IN_MIC_INPUT_PART"
        IN_MIC_EQ_PART -> "IN_MIC_EQ_PART"
        IN_MIC_DYNAMIC_PART -> "IN_MIC_DYNAMIC_PART"
        IN_MIC_BUS_PART -> "IN_MIC_BUS_PART"
        IN_MIC_OUTPUT_PART -> "IN_MIC_OUTPUT_PART"
        IN_BUS_INPUT_PART -> "IN_BUS_INPUT_PART"
        IN_BUS_OUTPUT_PART -> "IN_BUS_OUTPUT_PART"
        IN_SETUP_INFO_PART -> "IN_SETUP_INFO_PART"
        IN_SETUP_PATCH_PART -> "IN_SETUP_PATCH_PART"
        IN_SETUP_FX_MODUL_PART -> "IN_SETUP_FX_MODUL_PART"
        IN_SETUP_FX_DELAY_PART -> "IN_SETUP_FX_DELAY_PART"
        IN_SETUP_FX_REVERB_PART -> "IN_SETUP_FX_REVERB_PART"
        IN_SETUP_FX_GEQ_PART -> "IN_SETUP_FX_GEQ_PART"
        IN_SETUP_MONITOR_PART -> "IN_SETUP_MONITOR_PART"
        IN_SETUP_METER_PART -> "IN_SETUP_METER_PART"
        IN_IN_CHANNEL_NAME -> "IN_IN_CHANNEL_NAME"
        IN_OUT_CHANNEL_NAME -> "IN_OUT_CHANNEL_NAME"
        IN_SCENE_SELECT -> "IN_SCENE_SELECT"
        IN_SCENE_SELECTED -> "IN_SCENE_SELECTED"
        IN_SCENE_DELETE -> "IN_SCENE_DELETE"
        IN_SCENE_RENAME -> "IN_SCENE_RENAME"
        IN_SCENE_COPY -> "IN_SCENE_COPY"
        IN_SCENE_NEW -> "IN_SCENE_NEW"
        IN_SCENE_SAVE -> "IN_SCENE_SAVE"
        IN_SCENE_LOAD -> "IN_SCENE_LOAD"
        IN_SCENE_UP -> "IN_SCENE_UP"
        IN_SCENE_DOWN -> "IN_SCENE_DOWN"
        IN_SCENE_PREV -> "IN_SCENE_PREV"
        IN_SCENE_NEXT -> "IN_SCENE_NEXT"
        IN_EQ_LIBRARY_SAVE -> "IN_EQ_LIBRARY_SAVE"
        IN_DYNAMIC_LIBRARY_SAVE -> "IN_DYNAMIC_LIBRARY_SAVE"
        IN_MEDIA_CHANGED -> "IN_MEDIA_CHANGED"
        IN_MUSIC_LIST -> "IN_MUSIC_LIST"
        IN_MUSIC_STATE -> "IN_MUSIC_STATE"
        IN_DYNAMIC_LIBRARY -> "IN_DYNAMIC_LIBRARY"
        IN_PEQ_LIBRARY -> "IN_PEQ_LIBRARY"
        IN_FX_GEQ_LIBRARY -> "IN_FX_GEQ_LIBRARY"
        IN_OUT_INPUT_PART -> "IN_OUT_INPUT_PART"
        IN_SCENE_EXPORT -> "IN_SCENE_EXPORT"
        IN_IMPORT_FILE_LIST -> "IN_IMPORT_FILE_LIST"
        IN_IMPORT_FILE -> "IN_IMPORT_FILE"
        IN_FX_MODULE_LIBRARY -> "IN_FX_MODULE_LIBRARY"
        IN_FX_DELAY_LIBRARY -> "IN_FX_DELAY_LIBRARY"
        IN_FX_REVERB_LIBRARY -> "IN_FX_REVERB_LIBRARY"
        OUT_MIC_INPUT_PART -> "OUT_MIC_INPUT_PART"
        OUT_MIC_EQ_PART -> "OUT_MIC_EQ_PART"
        OUT_MIC_DYNAMIC_PART -> "OUT_MIC_DYNAMIC_PART"
        OUT_MIC_BUS_PART -> "OUT_MIC_BUS_PART"
        OUT_MIC_OUTPUT_PART -> "OUT_MIC_OUTPUT_PART"
        OUT_BUS_INPUT_PART -> "OUT_BUS_INPUT_PART"
        OUT_BUS_OUTPUT_PART -> "OUT_BUS_OUTPUT_PART"
        OUT_SETUP_INFO_PART -> "OUT_SETUP_INFO_PART"
        OUT_SETUP_PATCH_PART -> "OUT_SETUP_PATCH_PART"
        OUT_SETUP_FX_MODUL_PART -> "OUT_SETUP_FX_MODUL_PART"
        OUT_SETUP_FX_DELAY_PART -> "OUT_SETUP_FX_DELAY_PART"
        OUT_SETUP_FX_REVERB_PART -> "OUT_SETUP_FX_REVERB_PART"
        OUT_SETUP_FX_GEQ_PART -> "OUT_SETUP_FX_GEQ_PART"
        OUT_SETUP_MONITOR_PART -> "OUT_SETUP_MONITOR_PART"
        OUT_SETUP_METER_PART -> "OUT_SETUP_METER_PART"
        OUT_RECORDER_ITEM -> "OUT_RECORDER_ITEM"
        else -> "protoId"
    }
}

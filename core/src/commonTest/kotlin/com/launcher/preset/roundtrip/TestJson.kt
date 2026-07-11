package com.launcher.preset.roundtrip

import kotlinx.serialization.json.Json

internal val testJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

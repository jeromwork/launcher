package com.launcher.api.media

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class BlobReference(
    val uuid: Uuid,
    val linkId: String,
    val refSource: String,
    val refUpdatedAt: Long,
)

package com.launcher.api

interface FlowRepository {
    suspend fun loadFlows(): List<FlowDescriptor>
    fun availableTemplates(presetId: String): List<FlowTemplate>
}

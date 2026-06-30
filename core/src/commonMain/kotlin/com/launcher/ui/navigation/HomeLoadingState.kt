package com.launcher.ui.navigation

sealed class HomeLoadingState {
    data object Loading : HomeLoadingState()
    data class Ready(val activeFlowId: String) : HomeLoadingState()
    data class Error(val reason: String) : HomeLoadingState()
}

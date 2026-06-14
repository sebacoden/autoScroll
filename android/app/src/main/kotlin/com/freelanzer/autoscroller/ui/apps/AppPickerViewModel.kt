package com.freelanzer.autoscroller.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freelanzer.autoscroller.core.apps.InstalledApp
import com.freelanzer.autoscroller.core.apps.InstalledAppsProvider
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado del selector: apps lanzables, marcando las que ya están en la allowlist. */
data class AppPickerUiState(
    val loading: Boolean = true,
    val apps: List<AppPickerItem> = emptyList(),
)

data class AppPickerItem(
    val packageName: String,
    val label: String,
    val alreadyAdded: Boolean,
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loaded = MutableStateFlow(false)

    val uiState: StateFlow<AppPickerUiState> = combine(
        installed,
        settingsRepository.activationAppPackagesFlow,
        loaded,
    ) { apps, allowlist, isLoaded ->
        AppPickerUiState(
            loading = !isLoaded,
            apps = apps.map {
                AppPickerItem(it.packageName, it.label, it.packageName in allowlist)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPickerUiState(),
    )

    init {
        viewModelScope.launch {
            installed.value = installedAppsProvider.launchableApps()
            loaded.value = true
        }
    }

    fun onToggleApp(item: AppPickerItem) {
        viewModelScope.launch {
            if (item.alreadyAdded) {
                settingsRepository.removeActivationApp(item.packageName)
            } else {
                settingsRepository.addActivationApp(item.packageName)
            }
        }
    }
}

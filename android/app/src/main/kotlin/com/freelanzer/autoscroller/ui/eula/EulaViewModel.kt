package com.freelanzer.autoscroller.ui.eula

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * ViewModel mínimo del flujo EULA. Solo expone la acción de aceptación, que persiste el
 * flag en DataStore; el cambio se observa desde el grafo de navegación para decidir la
 * pantalla inicial.
 */
@HiltViewModel
class EulaViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    fun acceptEula(onAccepted: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setEulaAccepted(true)
            onAccepted()
        }
    }
}

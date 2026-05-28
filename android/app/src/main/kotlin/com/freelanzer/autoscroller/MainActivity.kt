package com.freelanzer.autoscroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.freelanzer.autoscroller.core.ui.theme.AutoScrollerTheme
import com.freelanzer.autoscroller.ui.home.HomeScreen
import com.freelanzer.autoscroller.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoScrollerTheme {
                HomeScreen(viewModel = homeViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El usuario puede haber habilitado el servicio en Ajustes y vuelto a la app.
        lifecycleScope.launch { homeViewModel.refreshServiceStatus() }
    }
}

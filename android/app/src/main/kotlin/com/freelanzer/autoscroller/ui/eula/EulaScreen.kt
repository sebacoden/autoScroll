package com.freelanzer.autoscroller.ui.eula

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.freelanzer.autoscroller.R
import com.freelanzer.autoscroller.service.accessibility.AutoScrollService

/**
 * Flujo unificado de primer arranque: la EULA encapsula también las solicitudes de
 * permisos. Un único tap a "Aceptar y continuar" encadena:
 *   1. Persiste el flag `eulaAccepted` en DataStore.
 *   2. Solicita `POST_NOTIFICATIONS` (Android 13+) si no está concedido.
 *   3. Lanza el deep-link al detalle del servicio de accesibilidad de AutoScroller
 *      (`ACTION_ACCESSIBILITY_DETAILS_SETTINGS`) — el toggle del servicio lo tiene
 *      que activar el usuario manualmente; es restricción de seguridad del sistema,
 *      ninguna app puede hacerlo por su cuenta.
 *   4. Navega a Home; al volver, `MainActivity.onResume` refresca el estado del
 *      servicio y la UI se actualiza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EulaScreen(
    onAccepted: () -> Unit,
    viewModel: EulaViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // Independiente del resultado del permiso, continuamos: si lo deniegan,
        // el WellbeingNotifier maneja la ausencia silenciosamente.
        openAccessibilityDetails(context)
        onAccepted()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.eula_title)) }) },
    ) { innerPadding ->
        EulaContent(
            innerPadding = innerPadding,
            onAccept = {
                viewModel.acceptEula {
                    proceedAfterEula(
                        context = context,
                        requestNotifications = { permission ->
                            notificationLauncher.launch(permission)
                        },
                        onAllPermissionsHandled = {
                            openAccessibilityDetails(context)
                            onAccepted()
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun EulaContent(
    innerPadding: PaddingValues,
    onAccept: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.eula_intro))

        EulaSection(
            title = stringResource(R.string.eula_license_title),
            body = stringResource(R.string.eula_license_body),
        )
        EulaSection(
            title = stringResource(R.string.eula_wellbeing_title),
            body = stringResource(R.string.eula_wellbeing_body),
        )
        EulaSection(
            title = stringResource(R.string.eula_data_title),
            body = stringResource(R.string.eula_data_body),
        )

        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.eula_accept))
        }
    }
}

@Composable
private fun EulaSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Decide si hay que solicitar `POST_NOTIFICATIONS` y delega o continúa.
 * En Android < 13 el permiso es automático, así que se salta directo al paso siguiente.
 */
private fun proceedAfterEula(
    context: Context,
    requestNotifications: (String) -> Unit,
    onAllPermissionsHandled: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onAllPermissionsHandled()
        return
    }
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (granted) {
        onAllPermissionsHandled()
    } else {
        requestNotifications(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Abre el detalle del servicio de accesibilidad de AutoScroller (con el toggle ya
 * visible). En dispositivos que no resuelvan el detalle se cae a la lista general.
 *
 * El action `android.settings.ACCESSIBILITY_DETAILS_SETTINGS` no está expuesto en
 * `android.provider.Settings` como constante pública (es del framework interno) pero
 * sí está documentado y resuelto por las apps de Settings de AOSP y la mayoría de OEM.
 */
private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
    "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

private fun openAccessibilityDetails(context: Context) {
    val componentName = ComponentName(context, AutoScrollService::class.java)
    val detailsIntent = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
        putExtra(Intent.EXTRA_COMPONENT_NAME, componentName.flattenToString())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(detailsIntent) }
        .recoverCatching { context.startActivity(fallbackIntent) }
}

package com.freelanzer.autoscroller.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freelanzer.autoscroller.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    RequestNotificationPermissionEffect()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.home_open_settings_action),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        HomeContent(
            state = state,
            innerPadding = innerPadding,
            onOpenAccessibilitySettings = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onToggleScroll = viewModel::onToggleScroll,
        )
    }
}

/**
 * Pide `POST_NOTIFICATIONS` en Android 13+ una sola vez por composición; sin esta
 * permission la notificación ongoing del WellbeingService no es visible (la app sigue
 * funcionando, pero el feedback al usuario se pierde).
 */
@Composable
private fun RequestNotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* No-op: el WellbeingService maneja la ausencia de permiso silenciosamente. */ },
    )
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    innerPadding: PaddingValues,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleScroll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServiceStatusCard(
            isEnabled = state.isServiceEnabled,
            onOpenSettings = onOpenAccessibilitySettings,
        )

        if (state.isServiceEnabled) {
            UsageInstructionsCard()
            ScrollToggleButton(
                isScrolling = state.isScrolling,
                onClick = onToggleScroll,
            )
            TestPanel(
                intervalSeconds = state.intervalSeconds,
                scrollCount = state.scrollCount,
            )
        } else {
            SetupCard(onOpenSettings = onOpenAccessibilitySettings)
        }
    }
}

// ---------------------------------------------------------------------------
// Service status
// ---------------------------------------------------------------------------

@Composable
private fun ServiceStatusCard(
    isEnabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    val color = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) color.primaryContainer else color.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusRow(isEnabled = isEnabled)
            if (!isEnabled) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_open_accessibility))
                }
            }
        }
    }
}

@Composable
private fun StatusRow(isEnabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier
                .height(12.dp)
                .clip(CircleShape),
            color = if (isEnabled) Color(0xFF2E7D32) else Color(0xFFC62828),
        ) {
            Box(modifier = Modifier.height(12.dp).fillMaxWidth(0.04f))
        }
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = stringResource(
                if (isEnabled) R.string.home_service_enabled
                else R.string.home_service_disabled,
            ),
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// Setup (service disabled)
// ---------------------------------------------------------------------------

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_setup_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = stringResource(R.string.home_setup_body))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_open_accessibility))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Usage instructions (service enabled)
// ---------------------------------------------------------------------------

@Composable
private fun UsageInstructionsCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_usage_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.home_usage_step_1))
            Text(stringResource(R.string.home_usage_step_2))
            Text(stringResource(R.string.home_usage_step_3))
            Text(stringResource(R.string.home_usage_step_4))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.home_usage_tip),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Start / stop
// ---------------------------------------------------------------------------

@Composable
private fun ScrollToggleButton(
    isScrolling: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(
                if (isScrolling) R.string.home_stop_scroll
                else R.string.home_start_scroll,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Test panel
// ---------------------------------------------------------------------------

@Composable
private fun TestPanel(
    intervalSeconds: Int,
    scrollCount: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_test_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = stringResource(R.string.home_test_hint, intervalSeconds),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = stringResource(R.string.home_test_counter, scrollCount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TEST_LIST_HEIGHT_DP.dp),
            ) {
                items(items = (1..TEST_ITEM_COUNT).toList(), key = { it }) { index ->
                    TestRow(index = index)
                }
            }
        }
    }
}

@Composable
private fun TestRow(index: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        tonalElevation = 1.dp,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            text = stringResource(R.string.home_test_item, index),
        )
    }
}

private const val TEST_LIST_HEIGHT_DP: Int = 280
private const val TEST_ITEM_COUNT: Int = 60

package com.freelanzer.autoscroller.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freelanzer.autoscroller.R
import com.freelanzer.autoscroller.service.accessibility.AutoScrollService
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // El usuario puede habilitar/deshabilitar el servicio desde Ajustes del sistema;
    // refrescamos al volver a la app.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshServiceStatus()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        SettingsContent(
            state = state,
            innerPadding = innerPadding,
            onOpenAccessibilitySettings = { openAccessibilityDetails(context) },
            onToggleScroll = viewModel::onToggleScroll,
            onIntervalChange = viewModel::onIntervalSecondsChanged,
            onTimeLimitChange = viewModel::onTimeLimitMinutesChanged,
            onAlertsToggle = viewModel::onAlertsEnabledChanged,
            onThreeFingerToggle = viewModel::onThreeFingerEnabledChanged,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    innerPadding: PaddingValues,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleScroll: () -> Unit,
    onIntervalChange: (Float) -> Unit,
    onTimeLimitChange: (Float) -> Unit,
    onAlertsToggle: (Boolean) -> Unit,
    onThreeFingerToggle: (Boolean) -> Unit,
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

        SectionCard(title = stringResource(R.string.settings_section_scroll)) {
            SliderRow(
                label = stringResource(R.string.settings_interval_label, state.intervalSeconds),
                value = state.intervalSeconds.toFloat(),
                range = MIN_INTERVAL_S..MAX_INTERVAL_S,
                steps = (MAX_INTERVAL_S - MIN_INTERVAL_S).toInt() - 1,
                minLabel = stringResource(R.string.settings_interval_min),
                maxLabel = stringResource(R.string.settings_interval_max),
                onValueChange = onIntervalChange,
            )
            ScrollPreview(intervalSeconds = state.intervalSeconds)
        }

        SectionCard(title = stringResource(R.string.settings_section_wellbeing)) {
            SliderRow(
                label = stringResource(R.string.settings_limit_label, state.timeLimitMinutes),
                value = state.timeLimitMinutes.toFloat(),
                range = MIN_LIMIT_MIN..MAX_LIMIT_MIN,
                steps = 0,
                minLabel = stringResource(R.string.settings_limit_min),
                maxLabel = stringResource(R.string.settings_limit_max),
                onValueChange = onTimeLimitChange,
            )
            ToggleRow(
                label = stringResource(R.string.settings_alerts_label),
                checked = state.alertsEnabled,
                onCheckedChange = onAlertsToggle,
            )
        }

        SectionCard(title = stringResource(R.string.settings_section_triggers)) {
            ToggleRow(
                label = stringResource(R.string.settings_three_finger_label),
                help = stringResource(R.string.settings_three_finger_help),
                checked = state.threeFingerEnabled,
                onCheckedChange = onThreeFingerToggle,
            )
            Text(
                text = stringResource(R.string.settings_future_activations),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_section_test)) {
            TestPanel(
                isServiceEnabled = state.isServiceEnabled,
                isScrolling = state.isScrolling,
                scrollCount = state.scrollCount,
                intervalSeconds = state.intervalSeconds,
                onToggleScroll = onToggleScroll,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Service status (top card)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape),
                    color = if (isEnabled) Color(0xFF2E7D32) else Color(0xFFC62828),
                ) { Box(Modifier.size(12.dp)) }
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(
                        if (isEnabled) R.string.home_service_enabled
                        else R.string.home_service_disabled,
                    ),
                    fontWeight = FontWeight.Medium,
                )
            }
            if (!isEnabled) {
                Text(
                    text = stringResource(R.string.home_setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_open_accessibility))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Vista previa animada del scroll
// ---------------------------------------------------------------------------

@Composable
private fun ScrollPreview(intervalSeconds: Int) {
    var counter by remember { mutableIntStateOf(1) }
    LaunchedEffect(intervalSeconds) {
        while (true) {
            delay(intervalSeconds * 1_000L)
            counter++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_preview_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Box(
            modifier = Modifier
                .size(width = PREVIEW_WIDTH_DP.dp, height = PREVIEW_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = counter,
                transitionSpec = {
                    (slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(PREVIEW_SLIDE_DURATION_MS),
                    ) + fadeIn(tween(PREVIEW_SLIDE_DURATION_MS))).togetherWith(
                        slideOutVertically(
                            targetOffsetY = { fullHeight -> -fullHeight },
                            animationSpec = tween(PREVIEW_SLIDE_DURATION_MS),
                        ) + fadeOut(tween(PREVIEW_SLIDE_DURATION_MS)),
                    )
                },
                label = "scroll_preview",
            ) { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Probar (botón manual + contador + lista)
// ---------------------------------------------------------------------------

@Composable
private fun TestPanel(
    isServiceEnabled: Boolean,
    isScrolling: Boolean,
    scrollCount: Int,
    intervalSeconds: Int,
    onToggleScroll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.home_test_hint, intervalSeconds),
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = onToggleScroll,
            enabled = isServiceEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    if (isScrolling) R.string.home_stop_scroll
                    else R.string.home_start_scroll,
                ),
            )
        }
        Text(
            text = stringResource(R.string.home_test_counter, scrollCount),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(TEST_LIST_HEIGHT_DP.dp),
        ) {
            items(items = (1..TEST_ITEM_COUNT).toList(), key = { it }) { index ->
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
        }
    }
}

// ---------------------------------------------------------------------------
// Bloques de UI reutilizables
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    minLabel: String,
    maxLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(text = label)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = minLabel, style = MaterialTheme.typography.bodySmall)
            Text(text = maxLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    help: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label)
            if (help != null) {
                Text(
                    text = help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Mismo deep-link al detalle de AutoScroller en Ajustes de Accesibilidad que usa la EULA.
 * Fallback a la lista general si el OEM no resuelve el detalle.
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

private const val MIN_INTERVAL_S: Float = 1f
private const val MAX_INTERVAL_S: Float = 30f
private const val MIN_LIMIT_MIN: Float = 5f
private const val MAX_LIMIT_MIN: Float = 180f

private const val PREVIEW_WIDTH_DP: Int = 96
private const val PREVIEW_HEIGHT_DP: Int = 120
private const val PREVIEW_SLIDE_DURATION_MS: Int = 350

private const val TEST_LIST_HEIGHT_DP: Int = 200
private const val TEST_ITEM_COUNT: Int = 30

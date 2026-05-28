package com.freelanzer.autoscroller.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freelanzer.autoscroller.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsContent(
            state = state,
            innerPadding = innerPadding,
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
        SectionCard(title = stringResource(R.string.settings_section_scroll)) {
            SliderRow(
                label = stringResource(R.string.settings_interval_label, state.intervalSeconds),
                help = stringResource(R.string.settings_interval_help),
                value = state.intervalSeconds.toFloat(),
                range = MIN_INTERVAL_S..MAX_INTERVAL_S,
                steps = (MAX_INTERVAL_S - MIN_INTERVAL_S).toInt() - 1,
                minLabel = stringResource(R.string.settings_interval_min),
                maxLabel = stringResource(R.string.settings_interval_max),
                onValueChange = onIntervalChange,
            )
        }

        SectionCard(title = stringResource(R.string.settings_section_wellbeing)) {
            SliderRow(
                label = stringResource(R.string.settings_limit_label, state.timeLimitMinutes),
                help = stringResource(R.string.settings_limit_help),
                value = state.timeLimitMinutes.toFloat(),
                range = MIN_LIMIT_MIN..MAX_LIMIT_MIN,
                steps = 0,
                minLabel = stringResource(R.string.settings_limit_min),
                maxLabel = stringResource(R.string.settings_limit_max),
                onValueChange = onTimeLimitChange,
            )
            ToggleRow(
                label = stringResource(R.string.settings_alerts_label),
                help = stringResource(R.string.settings_alerts_help),
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
        }
    }
}

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
    help: String,
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
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = help,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    help: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label)
            Text(
                text = help,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private const val MIN_INTERVAL_S: Float = 1f
private const val MAX_INTERVAL_S: Float = 30f
private const val MIN_LIMIT_MIN: Float = 5f
private const val MAX_LIMIT_MIN: Float = 180f

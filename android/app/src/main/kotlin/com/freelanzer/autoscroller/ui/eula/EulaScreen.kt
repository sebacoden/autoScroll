package com.freelanzer.autoscroller.ui.eula

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.freelanzer.autoscroller.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EulaScreen(
    onAccepted: () -> Unit,
    viewModel: EulaViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.eula_title)) }) },
    ) { innerPadding ->
        EulaContent(
            innerPadding = innerPadding,
            onAccept = { viewModel.acceptEula(onAccepted) },
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

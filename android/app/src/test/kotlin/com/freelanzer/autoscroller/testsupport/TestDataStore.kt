package com.freelanzer.autoscroller.testsupport

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.rules.TemporaryFolder

/**
 * Crea un `DataStore<Preferences>` respaldado por un archivo dentro del [TemporaryFolder]
 * del test. Importante: el archivo NO debe existir todavía — DataStore lo inicializa al
 * primer acceso. Pasar un archivo vacío preexistente da `IOException` (lo interpreta
 * como proto corrupto).
 *
 * El scope usa un dispatcher **real** (`Dispatchers.IO`), no un `TestScope`: los tests que
 * combinan este DataStore bajo `runBlocking` (sin reloj virtual) necesitan que las
 * corutinas internas de DataStore se ejecuten de verdad. Un `TestScope` no se "bombea"
 * fuera de `runTest`, lo que dejaría los flujos sin emitir y colgaría el `runBlocking`.
 */
internal fun createTestPreferencesDataStore(
    tempFolder: TemporaryFolder,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> {
    val dir = tempFolder.newFolder()
    val file = File(dir, "test.preferences_pb")
    return PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { file },
    )
}

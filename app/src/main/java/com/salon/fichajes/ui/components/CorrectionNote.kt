package com.salon.fichajes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.salon.fichajes.core.format.formatTime
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.ClockSource
import java.time.ZoneId

/** Texto de auditoría de un fichaje tocado por la jefa, p. ej. "Modificado por Marta. Antes: 10:15–14:00. Motivo: …". */
fun correctionDescription(entry: ClockEntry, zone: ZoneId, adminName: String?): String? {
    if (!entry.isAdminModified) return null
    val who = adminName ?: "la encargada"
    val reason = entry.correctionReason?.let { " Motivo: $it" }.orEmpty()
    return when (entry.source) {
        ClockSource.ADMIN_ENTRY -> "Añadido por $who.$reason"
        else -> {
            val before = entry.originalClockIn?.let { original ->
                " Antes: ${original.formatTime(zone)}–${entry.originalClockOut?.formatTime(zone) ?: "sin salida"}."
            }.orEmpty()
            "Modificado por $who.$before$reason"
        }
    }
}

@Composable
fun CorrectionNote(entry: ClockEntry, zone: ZoneId, adminName: String?, modifier: Modifier = Modifier) {
    val text = correctionDescription(entry, zone, adminName) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

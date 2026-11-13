package com.salon.fichajes.ui.security

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PinField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Enseña el código de recuperación UNA sola vez. No se puede cerrar sin confirmar que se ha apuntado:
 * es la única forma de recuperar el control si la dueña olvida su contraseña.
 */
@Composable
fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    var written by remember(code) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Tu código de recuperación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Apúntalo en papel y guárdalo en un sitio seguro (no en la tablet). Si olvidas la contraseña maestra, este código es la única forma de crear una nueva.")
                Text(code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Text("No se volverá a mostrar.", color = MaterialTheme.colorScheme.error)
                Row(Modifier.clickable { written = !written }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = written, onCheckedChange = { written = it })
                    Text("Lo he apuntado")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone, enabled = written) { Text("Hecho") } },
    )
}

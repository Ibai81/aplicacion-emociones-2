package com.example.emotionapp.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.emotionapp.data.UiPrefs

@Composable
fun HintTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val context = LocalContext.current
    val showHints by UiPrefs.observeShowHints(context).collectAsState(initial = true)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (showHints) Text(hint) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        modifier = modifier,
        keyboardOptions = keyboardOptions
    )
}

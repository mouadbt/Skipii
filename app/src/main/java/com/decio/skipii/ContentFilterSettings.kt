package com.decio.skipii

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContentFilterSettings(showHeader: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    var term by remember { mutableStateOf("") }
    var scopes by remember { mutableStateOf(setOf(ContentFilter.Scope.Anywhere)) }
    val add = {
        if (term.isNotBlank()) {
            ContentFilter.add(term, scopes)
            term = ""
        }
        keyboard?.hide()
    }
    Column(Modifier.fillMaxWidth()) {
        if (showHeader) {
            Text("Content filters", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("Hide videos or channels containing a word", color = c.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            BasicTextField(
                value = term,
                onValueChange = { term = it },
                singleLine = true,
                textStyle = TextStyle(color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                decorationBox = { inner ->
                    if (term.isEmpty()) Text("Word or phrase", color = c.onSurfaceVariant, fontSize = 14.sp)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ContentFilter.Scope.entries.forEach { scope ->
                val on = scope in scopes
                androidx.compose.material3.Surface(
                    modifier = Modifier.pressable { scopes = if (on) scopes - scope else scopes + scope },
                    shape = RoundedCornerShape(12.dp),
                    color = if (on) c.secondaryContainer else c.surfaceContainerHighest,
                ) {
                    Text(scope.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .pressable(enabled = term.isNotBlank()) { add() }
                .clip(RoundedCornerShape(14.dp))
                .background(if (term.isNotBlank()) c.primary else c.surfaceContainerHighest)
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
            Text(
                "Add filter",
                color = if (term.isNotBlank()) c.onPrimary else c.onSurfaceVariant,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        ContentFilter.rules.forEachIndexed { index, rule ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("\"${rule.term}\"", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(rule.scopes.joinToString { it.label }, color = c.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Text("Remove", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.pressable { ContentFilter.remove(index) })
            }
        }
    }
}
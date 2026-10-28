package com.tal.eyedrops.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CheckChip(on: Boolean, label: String?, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
            .background(if (on) OkSoft else Color.Transparent)
            .border(1.5.dp, if (on) Ok else Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (on) Icon(Icons.Filled.Check, null, tint = Ok, modifier = Modifier.size(20.dp))
        val text = label ?: if (on) "" else "סמן"
        if (text.isNotEmpty()) Text(text, color = if (on) Ok else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Line, RoundedCornerShape(10.dp))) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.background(if (i == selected) Brand else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (i == selected) Color.White else Ink, textAlign = TextAlign.Center)
            }
        }
    }
}

package com.sitehub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sitehub.app.core.HomeData

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onNavigate: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val favs = HomeData.favorites
    val rec = HomeData.recent
    val cs = MaterialTheme.colorScheme

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(cs.primaryContainer, cs.secondaryContainer, cs.surface))
            ).padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp)
        ) {
            Column {
                Text("SiteHub", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                Text("Tu home, como en Firefox: busca o abre un sitio.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp), shadowElevation = 6.dp, tonalElevation = 3.dp,
            color = cs.surface, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = cs.primary,
                    modifier = Modifier.padding(start = 12.dp).size(20.dp))
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Busca en la web o escribe una URL", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (query.isNotBlank()) onNavigate(query) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Limpiar") }
                }
                IconButton(onClick = { if (query.isNotBlank()) onNavigate(query) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ir", tint = cs.primary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                SectionHeader("Recientes", { Icon(Icons.Outlined.History, null, Modifier.size(18.dp), tint = cs.primary) }, rec.size)
                if (rec.isEmpty()) EmptyHint("Tus sitios visitados aparecerán aquí.")
                rec.take(8).forEach { e ->
                    SiteCard(e, cs.primary, { Icon(Icons.Outlined.History, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant) }) { onNavigate(e.url) }
                }
            }
            Column(Modifier.weight(1f)) {
                SectionHeader("Favoritos", { Icon(Icons.Filled.Star, null, Modifier.size(18.dp), tint = cs.tertiary) }, favs.size)
                if (favs.isEmpty()) EmptyHint("Marca sitios con la ★ del navegador.")
                favs.take(8).forEach { e ->
                    SiteCard(e, cs.tertiary, { Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = cs.tertiary) }) { onNavigate(e.url) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(title: String, icon: @Composable () -> Unit, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun SiteCard(e: HomeData.Entry, accent: Color, trailing: @Composable () -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp), color = cs.surfaceVariant, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onClick() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
            Box(Modifier.size(34.dp).background(accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Text(e.title.take(1).uppercase(), color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(HomeData.prettyHost(e.url), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

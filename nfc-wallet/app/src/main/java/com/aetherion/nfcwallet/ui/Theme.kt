package com.aetherion.nfcwallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val WalletDark = darkColorScheme(
    primary = Color(0xFF7CD6FF),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004D6B),
    onPrimaryContainer = Color(0xFFC4E7FF),
    secondary = Color(0xFFB4C9D6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE2E6EB),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE2E6EB),
    surfaceVariant = Color(0xFF1E252E),
    onSurfaceVariant = Color(0xFFB7BEC7),
    surfaceContainerHigh = Color(0xFF1A2028),
    error = Color(0xFFFF8A80),
)

/** Dark only for v1, per the design brief. */
@Composable
fun NfcWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WalletDark, typography = Typography(), content = content)
}

object CardStyle {
    val colors: List<Color> = listOf(
        Color(0xFF2F6FED), Color(0xFF8E44AD), Color(0xFFE67E22), Color(0xFF16A085),
        Color(0xFFE74C3C), Color(0xFF455A64), Color(0xFFD81B60), Color(0xFF7CB342),
    )

    val icons: Map<String, ImageVector> = linkedMapOf(
        "key" to Icons.Filled.Key,
        "card" to Icons.Filled.CreditCard,
        "badge" to Icons.Filled.Badge,
        "home" to Icons.Filled.Home,
        "car" to Icons.Filled.DirectionsCar,
        "work" to Icons.Filled.Work,
        "gym" to Icons.Filled.FitnessCenter,
        "tag" to Icons.Filled.Sell,
    )

    fun color(index: Int): Color = colors[index.coerceIn(0, colors.lastIndex)]
    fun icon(key: String): ImageVector = icons[key] ?: Icons.Filled.Nfc
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CardStyle.colors.forEachIndexed { i, c ->
            Box(
                Modifier
                    .size(36.dp)
                    .background(c, CircleShape)
                    .then(if (i == selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (i == selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CardStyle.icons.forEach { (key, vector) ->
            val on = key == selected
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
                    .clickable { onSelect(key) }
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    vector, key,
                    tint = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

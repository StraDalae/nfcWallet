package com.aetherion.nfcwallet.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aetherion.nfcwallet.data.Capability
import com.aetherion.nfcwallet.data.WalletCard

@Composable
fun NfcPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "alpha",
    )
    Box(modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(170.dp)
                .scale(scale)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Box(
            Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Nfc, null,
                Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun CapabilityChip(capability: Capability, modifier: Modifier = Modifier) {
    val (bg, fg) = when (capability) {
        Capability.VIEW_ONLY -> Color(0x33FFFFFF) to Color.White
        else -> Color.White to Color(0xFF10151B)
    }
    Text(
        capability.label,
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun CardTile(card: WalletCard, onClick: (() -> Unit)? = null) {
    val base = CardStyle.color(card.meta.colorIndex)
    Column(
        Modifier
            .fillMaxWidth()
            .background(base, RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(Color(0x33FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CardStyle.icon(card.meta.iconKey), null, tint = Color.White)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    card.meta.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.meta.family.label,
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                card.meta.summary,
                color = Color(0xCCFFFFFF),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            CapabilityChip(card.meta.capability)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

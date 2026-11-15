package com.salon.fichajes.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salon.fichajes.R
import com.salon.fichajes.ui.theme.BrandGold

/** Monograma del salón (se tiñe con el color del tema para que funcione también en modo oscuro). */
@Composable
fun SalonMonogram(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_brand_monogram_tight),
        contentDescription = null,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
        modifier = modifier.size(size),
    )
}

/**
 * Cabecera con la identidad del salón, como en su logo:
 *   [F]  Tu Salón
 *        PELUQUERÍA · FICHAJES
 */
@Composable
fun SalonHeader(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SalonMonogram(size = if (compact) 44.dp else 58.dp)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.salon_name),
                fontFamily = FontFamily.Serif,
                fontSize = if (compact) 26.sp else 32.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            HorizontalDivider(Modifier.width(if (compact) 150.dp else 190.dp), color = BrandGold, thickness = 1.dp)
            Text(
                stringResource(R.string.salon_tagline),
                fontSize = 11.sp,
                letterSpacing = 2.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

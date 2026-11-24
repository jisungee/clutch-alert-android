package com.clutchalarm.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.domain.model.KboTeams
import com.clutchalarm.app.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color

/** Original club artwork, converted from the supplied SVG files without tinting. */
internal object TeamLogoResources {
    @DrawableRes fun find(teamId: String?): Int? = approved[teamId]
    private val approved = mapOf(
        "LG" to R.drawable.team_logo_lg, "DOOSAN" to R.drawable.team_logo_doosan,
        "KIA" to R.drawable.team_logo_kia, "SAMSUNG" to R.drawable.team_logo_samsung,
        "SSG" to R.drawable.team_logo_ssg, "LOTTE" to R.drawable.team_logo_lotte,
        "HANWHA" to R.drawable.team_logo_hanwha, "NC" to R.drawable.team_logo_nc,
        "KT" to R.drawable.team_logo_kt, "KIWOOM" to R.drawable.team_logo_kiwoom,
    )
}

@Composable
internal fun TeamCrest(teamId: String?, modifier: Modifier = Modifier) {
    val resource = TeamLogoResources.find(teamId)
    if (resource != null) {
        Surface(modifier, color = Color.White, shape = ClutchStyle.control) {
            Image(painterResource(resource), contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
        }
    } else {
        Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                val short = when (teamId) {
                    "DOOSAN" -> "두산"; "SAMSUNG" -> "삼성"; "LOTTE" -> "롯데"
                    "HANWHA" -> "한화"; "KIWOOM" -> "키움"
                    else -> teamId?.takeIf { KboTeams.find(it) != null }
                }
                val labelStyle = when {
                    maxWidth >= 64.dp -> MaterialTheme.typography.titleLarge
                    maxWidth >= 40.dp -> MaterialTheme.typography.labelLarge
                    else -> MaterialTheme.typography.labelSmall
                }
                if (short != null) Text(short, style = labelStyle,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                else BaseballMark(Modifier.padding(8.dp).fillMaxSize(), MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun SelectionBadge(modifier: Modifier = Modifier, teamId: String? = null) {
    val palette = if (teamId == null) LocalTeamPalette.current else teamPalette(teamId)
    val ink = palette.onPrimaryColor
    Surface(modifier.size(24.dp), shape = CircleShape, color = palette.primaryColor) {
        Canvas(Modifier.padding(5.dp)) {
            val stroke = Stroke(2.dp.toPx())
            drawLine(ink, Offset(size.width * .12f, size.height * .5f), Offset(size.width * .4f, size.height * .8f), stroke.width)
            drawLine(ink, Offset(size.width * .4f, size.height * .8f), Offset(size.width * .9f, size.height * .18f), stroke.width)
        }
    }
}

@Composable
internal fun ChevronMark(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(20.dp)) {
        val width = 2.dp.toPx()
        drawLine(color, Offset(size.width * .35f, size.height * .2f), Offset(size.width * .65f, size.height * .5f), width)
        drawLine(color, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .35f, size.height * .8f), width)
    }
}

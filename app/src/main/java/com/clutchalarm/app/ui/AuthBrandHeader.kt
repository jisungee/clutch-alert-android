package com.clutchalarm.app.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.clutchalarm.app.R
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun AuthBrandHeader() {
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(ClutchStyle.red, ClutchStyle.deepRed)))
        .padding(horizontal = ClutchStyle.page, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = Color.White, shape = ClutchStyle.control) {
                Image(painterResource(R.drawable.app_logo), contentDescription = null,
                    modifier = Modifier.width(78.dp).height(52.dp), contentScale = ContentScale.Fit)
            }
            Text("클러치알림", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Text("내 팀의 중요한 순간을 함께하세요", color = Color.White.copy(alpha = .85f),
            style = MaterialTheme.typography.bodySmall)
    }
}

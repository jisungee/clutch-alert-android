package com.clutchalarm.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.R

/** The existing session check, branded without adding a timer or changing authentication. */
@Composable
internal fun BrandStartupScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
            Image(painterResource(R.drawable.splash_logo), contentDescription = "클러치알림",
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(3f), contentScale = ContentScale.Fit)
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("로그인 정보를 확인하고 있어요", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

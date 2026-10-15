package com.duzup.app.feature.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import kotlinx.coroutines.delay

private const val SHOW_MILLIS = 1500L

/**
 * A ~1.5 second brand moment after the system splash: the bell, the wordmark and a one-line tagline.
 * Tap anywhere to skip. It is not shown when reduced motion is on.
 */
@Composable
fun IntroOverlay(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    val brand = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.intro_tagline)

    LaunchedEffect(Unit) {
        delay(SHOW_MILLIS)
        visible = false
        delay(260)
        onDone()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(240)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0B0D))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.intro_skip),
                ) { visible = false }
                .semantics(mergeDescendants = true) { contentDescription = "$brand. $tagline" },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                modifier = Modifier.size(144.dp).offset(y = (-40).dp),
            )
            Column(
                Modifier.offset(y = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    brand,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color(0xFFE6ECF0),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9BA4AB),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

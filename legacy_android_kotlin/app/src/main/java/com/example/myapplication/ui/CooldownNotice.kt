package com.example.myapplication.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R

@Composable
fun CooldownNotice(seconds: Long) {
    if (seconds > 0) {
        // Do not announce every tick over TalkBack; the error region announces throttling once.
        Text(stringResource(R.string.retry_cooldown, seconds), Modifier.testTag("retry_cooldown"))
    }
}

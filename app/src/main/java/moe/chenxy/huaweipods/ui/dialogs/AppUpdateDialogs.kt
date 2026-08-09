package moe.chenxy.huaweipods.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import moe.chenxy.huaweipods.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AvailableUpdateDialog(
    show: Boolean,
    currentVersion: String,
    latestVersion: String,
    releaseNotes: String,
    onLater: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val normalizedReleaseNotes = releaseNotes.trim()
    val displayedReleaseNotes = if (normalizedReleaseNotes.isEmpty()) {
        stringResource(R.string.update_release_notes_empty)
    } else {
        normalizedReleaseNotes
    }

    AppUpdatePlatformDialog(
        show = show,
        title = stringResource(R.string.update_available_title),
        summary = stringResource(
            R.string.update_available_summary,
            currentVersion,
            latestVersion,
        ),
        completed = false,
        onDismissRequest = onLater,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.update_release_notes),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = displayedReleaseNotes,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                text = stringResource(R.string.update_install_restart_hint),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        }
        UpdateDialogActions(
            primaryText = stringResource(R.string.update_go_to_release),
            onLater = onLater,
            onPrimary = onOpenRelease,
        )
    }
}

@Composable
fun UpdatedAppDialog(
    show: Boolean,
    versionName: String,
    onLater: () -> Unit,
    onRestartScope: () -> Unit,
) {
    AppUpdatePlatformDialog(
        show = show,
        title = stringResource(R.string.update_completed_title),
        summary = stringResource(R.string.update_completed_summary, versionName),
        completed = true,
        onDismissRequest = {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.update_restart_recommended),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        }
        UpdateDialogActions(
            primaryText = stringResource(R.string.update_restart_scope),
            onLater = onLater,
            onPrimary = onRestartScope,
        )
    }
}

@Composable
private fun AppUpdatePlatformDialog(
    show: Boolean,
    title: String,
    summary: String,
    completed: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!show) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 430.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    UpdateDialogLogo(completed = completed)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            color = MiuixTheme.colorScheme.onSurface,
                            style = MiuixTheme.textStyles.headline1.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.055f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = summary,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun UpdateDialogLogo(completed: Boolean) {
    Box(modifier = Modifier.size(58.dp)) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MiuixTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        if (completed) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    style = MiuixTheme.textStyles.body2.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun UpdateDialogActions(
    primaryText: String,
    onLater: () -> Unit,
    onPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            text = stringResource(R.string.update_later),
            onClick = onLater,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        )
        TextButton(
            text = primaryText,
            onClick = onPrimary,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

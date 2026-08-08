package moe.chenxy.huaweipods.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
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

    OverlayDialog(
        modifier = responsiveOverlayDialogModifier(),
        title = stringResource(R.string.update_available_title),
        summary = stringResource(
            R.string.update_available_summary,
            currentVersion,
            latestVersion,
        ),
        show = show,
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
    OverlayDialog(
        modifier = responsiveOverlayDialogModifier(),
        title = stringResource(R.string.update_completed_title),
        summary = stringResource(R.string.update_completed_summary, versionName),
        show = show,
        onDismissRequest = {},
    ) {
        Text(
            text = stringResource(R.string.update_restart_recommended),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        UpdateDialogActions(
            primaryText = stringResource(R.string.update_restart_scope),
            onLater = onLater,
            onPrimary = onRestartScope,
        )
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

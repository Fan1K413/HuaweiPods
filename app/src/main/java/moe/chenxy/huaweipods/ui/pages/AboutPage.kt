package moe.chenxy.huaweipods.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

sealed interface UpdateCheckSummary {
    data class UpToDate(val versionName: String) : UpdateCheckSummary

    data class Available(val versionName: String) : UpdateCheckSummary

    data object Failure : UpdateCheckSummary
}

@Composable
fun AboutPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    appVersion: String,
    checkingForUpdates: Boolean,
    updateCheckSummary: UpdateCheckSummary?,
    onCheckForUpdates: () -> Unit,
    onPreviewUpdateDialog: (() -> Unit)? = null,
    onOpenGitHub: () -> Unit,
    onOpenIssues: () -> Unit,
    onCopyQqGroup: () -> Unit,
    qqGroupNumber: String,
    onOpenOnboarding: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                BasicComponent(
                    title = "HuaweiPods",
                    summary = stringResource(R.string.about_version_summary, appVersion),
                )
                BasicComponent(
                    title = stringResource(R.string.based_on),
                    summary = "1812z/OppoPods",
                )
            }
        }
        item {
            Card {
                BasicComponent(
                    title = stringResource(R.string.check_for_updates),
                    summary = when {
                        checkingForUpdates -> stringResource(R.string.checking_for_updates)
                        else -> when (val result = updateCheckSummary) {
                            is UpdateCheckSummary.UpToDate -> stringResource(
                                R.string.already_latest_version_inline,
                                result.versionName,
                            )
                            is UpdateCheckSummary.Available -> stringResource(
                                R.string.update_available_inline,
                                result.versionName,
                            )
                            UpdateCheckSummary.Failure -> stringResource(R.string.update_check_failed_summary)
                            null -> stringResource(R.string.check_for_updates_summary)
                        }
                    },
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    endActions = {
                        AnimatedVisibility(
                            visible = checkingForUpdates,
                            enter = fadeIn() + scaleIn(initialScale = 0.72f),
                            exit = fadeOut() + scaleOut(targetScale = 0.72f),
                        ) {
                            InfiniteProgressIndicator(
                                color = MiuixTheme.colorScheme.primary,
                                size = 22.dp,
                            )
                        }
                    },
                    // A tap during an automatic check upgrades that request to one with visible
                    // feedback without starting a second network call.
                    onClick = onCheckForUpdates,
                )
                if (onPreviewUpdateDialog != null) {
                    BasicComponent(
                        title = stringResource(R.string.preview_update_dialog),
                        summary = stringResource(R.string.preview_update_dialog_summary),
                        onClick = onPreviewUpdateDialog,
                    )
                }
                BasicComponent(
                    title = stringResource(R.string.github_repository),
                    summary = "Nshpiter/HuaweiPods",
                    onClick = onOpenGitHub,
                )
                BasicComponent(
                    title = stringResource(R.string.github_issues),
                    summary = stringResource(R.string.github_issues_summary),
                    onClick = onOpenIssues,
                )
                BasicComponent(
                    title = stringResource(R.string.qq_group),
                    summary = stringResource(R.string.qq_group_summary, qqGroupNumber),
                    onClick = onCopyQqGroup,
                )
            }
        }
        item {
            Card {
                BasicComponent(
                    title = stringResource(R.string.open_onboarding),
                    summary = stringResource(R.string.open_onboarding_summary),
                    onClick = onOpenOnboarding,
                )
            }
        }
    }
}

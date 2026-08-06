package moe.chenxy.huaweipods.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card

@Composable
fun AboutPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    appVersion: String,
    checkingForUpdates: Boolean,
    onCheckForUpdates: () -> Unit,
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
                    summary = stringResource(
                        if (checkingForUpdates) R.string.checking_for_updates else R.string.check_for_updates_summary,
                    ),
                    onClick = onCheckForUpdates,
                )
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

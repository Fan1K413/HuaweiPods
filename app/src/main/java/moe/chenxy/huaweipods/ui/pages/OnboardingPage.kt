package moe.chenxy.huaweipods.ui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.XposedService
import moe.chenxy.huaweipods.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PAGE_COUNT = 3

private val requiredCoreScopes = setOf(
    "com.android.bluetooth",
    "com.xiaomi.bluetooth",
)

@Composable
fun OnboardingPage(
    xposedService: XposedService?,
    isReplay: Boolean = false,
    onFinish: () -> Unit,
    onSkip: () -> Unit = onFinish,
) {
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    val surface = MiuixTheme.colorScheme.surface
    val onSurface = MiuixTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = onSurface,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                text = stringResource(
                    if (isReplay) R.string.onboarding_close else R.string.onboarding_skip,
                ),
                onClick = onSkip,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }

        CapsuleProgress(
            currentPage = currentPage,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )

        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier.weight(1f),
            label = "onboarding_page",
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> EnvironmentPage(xposedService = xposedService)
                else -> ReadyPage()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (currentPage > 0) {
                TextButton(
                    text = stringResource(R.string.onboarding_previous),
                    onClick = { currentPage-- },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
            TextButton(
                text = stringResource(
                    if (currentPage == PAGE_COUNT - 1) {
                        R.string.onboarding_start
                    } else {
                        R.string.onboarding_next
                    },
                ),
                onClick = {
                    if (currentPage == PAGE_COUNT - 1) {
                        onFinish()
                    } else {
                        currentPage++
                    }
                },
                modifier = Modifier
                    .weight(if (currentPage == 0) 1f else 2f)
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun CapsuleProgress(
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val pageState = stringResource(
        R.string.onboarding_page_status,
        currentPage + 1,
        PAGE_COUNT,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = pageState
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGE_COUNT) { index ->
            val indicatorWidth by animateDpAsState(
                targetValue = if (index == currentPage) 30.dp else 10.dp,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "onboarding_indicator_width",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(indicatorWidth)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    OnboardingPageLayout(
        title = stringResource(R.string.onboarding_welcome_title),
        summary = stringResource(R.string.onboarding_welcome_summary),
    ) {
        FloatingEarbudsArtwork()
    }
}

@Composable
private fun FloatingEarbudsArtwork() {
    val transition = rememberInfiniteTransition(label = "onboarding_earbuds")
    val floatOffset by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "earbud_float",
    )
    val glowScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "earbud_glow",
    )
    val density = LocalDensity.current.density
    val primary = MiuixTheme.colorScheme.primary

    Box(
        modifier = Modifier.size(246.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(218.dp)
                .graphicsLayer {
                    scaleX = glowScale
                    scaleY = glowScale
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.24f),
                            primary.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        Image(
            painter = painterResource(R.drawable.img_left),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 76.dp, height = 124.dp)
                .graphicsLayer {
                    translationX = -42f * density
                    translationY = floatOffset * density
                    rotationZ = -4f
                },
        )
        Image(
            painter = painterResource(R.drawable.img_right),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 76.dp, height = 124.dp)
                .graphicsLayer {
                    translationX = 42f * density
                    translationY = -floatOffset * density
                    rotationZ = 4f
                },
        )
    }
}

@Composable
private fun EnvironmentPage(xposedService: XposedService?) {
    var refreshVersion by remember { mutableIntStateOf(0) }
    val serviceConnected = xposedService != null
    val coreScopesReady = remember(xposedService, refreshVersion) {
        runCatching {
            xposedService?.scope?.containsAll(requiredCoreScopes) == true
        }.getOrDefault(false)
    }

    OnboardingPageLayout(
        title = stringResource(R.string.onboarding_environment_title),
        summary = stringResource(R.string.onboarding_environment_summary),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                EnvironmentStatusRow(
                    title = stringResource(R.string.onboarding_environment_lsposed),
                    ready = serviceConnected,
                )
                EnvironmentStatusRow(
                    title = stringResource(R.string.onboarding_environment_scopes),
                    ready = coreScopesReady,
                )
                TextButton(
                    text = stringResource(R.string.onboarding_refresh),
                    onClick = { refreshVersion++ },
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun EnvironmentStatusRow(
    title: String,
    ready: Boolean,
) {
    val statusColor = if (ready) {
        MiuixTheme.colorScheme.primary
    } else {
        Color(0xFFFF9500)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(statusColor, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        Text(
            text = stringResource(
                if (ready) R.string.onboarding_status_ready else R.string.onboarding_status_missing,
            ),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun ReadyPage() {
    OnboardingPageLayout(
        title = stringResource(R.string.onboarding_ready_title),
        summary = stringResource(R.string.onboarding_ready_summary),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                ReadyItem(
                    number = "1",
                    text = stringResource(R.string.onboarding_ready_pair),
                )
                ReadyItem(
                    number = "2",
                    text = stringResource(R.string.onboarding_ready_model),
                )
                ReadyItem(
                    number = "3",
                    text = stringResource(
                        R.string.onboarding_ready_group,
                        stringResource(R.string.qq_group_number),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ReadyItem(
    number: String,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
                .background(
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
    }
}

@Composable
private fun OnboardingPageLayout(
    title: String,
    summary: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.headline1,
            textAlign = TextAlign.Center,
        )
    }
}

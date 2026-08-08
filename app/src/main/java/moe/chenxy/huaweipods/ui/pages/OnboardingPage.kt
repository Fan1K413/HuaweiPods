package moe.chenxy.huaweipods.ui.pages

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.delay
import moe.chenxy.huaweipods.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PAGE_TRANSITION_MS = 360L

private val requiredCoreScopes = setOf(
    "com.android.bluetooth",
    "com.xiaomi.bluetooth",
)

private fun colorWithWhiteTextContrast(source: Color): Color {
    var result = source
    var overlayAlpha = 0f
    while (1.05f / (result.luminance() + 0.05f) < 4.5f && overlayAlpha < 0.64f) {
        overlayAlpha += 0.08f
        result = Color.Black.copy(alpha = overlayAlpha).compositeOver(source)
    }
    return result
}

@Composable
fun OnboardingPage(
    xposedService: XposedService?,
    isReplay: Boolean = false,
    onFinish: () -> Unit,
    onSkip: () -> Unit = onFinish,
) {
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var navigationLocked by remember { mutableStateOf(false) }
    var terminalActionInvoked by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val layoutPolicy = onboardingLayoutPolicy(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    )
    val animatorScale = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
    val motionEnabled = onboardingMotionEnabled(animatorScale)
    val surface = MiuixTheme.colorScheme.surface
    val accent = MiuixTheme.colorScheme.primary
    val actionColor = remember(accent) { colorWithWhiteTextContrast(accent) }
    val successColor = if (surface.luminance() < 0.5f) Color(0xFF63D6A0) else Color(0xFF25865B)

    fun invokeTerminalOnce(action: () -> Unit) {
        if (terminalActionInvoked) return
        terminalActionInvoked = true
        action()
    }

    fun navigate(action: OnboardingNavigationAction) {
        if (navigationLocked || terminalActionInvoked) return
        val result = reduceOnboardingNavigation(currentPage, action)
        if (result.finish) {
            invokeTerminalOnce(onFinish)
            return
        }
        if (result.page == currentPage) return
        navigationLocked = motionEnabled
        currentPage = result.page
    }

    LaunchedEffect(currentPage, navigationLocked, motionEnabled, animatorScale) {
        if (!navigationLocked) return@LaunchedEffect
        val scaledDuration = (PAGE_TRANSITION_MS * animatorScale.coerceIn(0.1f, 10f)).toLong()
        delay(scaledDuration)
        navigationLocked = false
    }

    BackHandler(enabled = currentPage > 0 && !terminalActionInvoked) {
        navigate(OnboardingNavigationAction.PREVIOUS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SetupTopBar(
            isReplay = isReplay,
            enabled = !terminalActionInvoked,
            onSkip = { invokeTerminalOnce(onSkip) },
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            SetupScene(
                currentPage = currentPage,
                xposedService = xposedService,
                accent = accent,
                successColor = successColor,
                motionEnabled = motionEnabled,
                landscape = layoutPolicy.landscape,
                compact = layoutPolicy.compact,
                viewportHeight = maxHeight,
            )
        }

        SetupFooter(
            currentPage = currentPage,
            accent = accent,
            actionColor = actionColor,
            navigationEnabled = !navigationLocked && !terminalActionInvoked,
            motionEnabled = motionEnabled,
            onPrevious = { navigate(OnboardingNavigationAction.PREVIOUS) },
            onNext = { navigate(OnboardingNavigationAction.NEXT) },
        )
    }
}

@Composable
private fun SetupTopBar(
    isReplay: Boolean,
    enabled: Boolean,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            text = stringResource(
                if (isReplay) R.string.onboarding_close else R.string.onboarding_skip,
            ),
            onClick = onSkip,
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun SetupScene(
    currentPage: Int,
    xposedService: XposedService?,
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
    landscape: Boolean,
    compact: Boolean,
    viewportHeight: Dp,
) {
    AnimatedContent(
        targetState = currentPage,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { setupPageTransition(targetState > initialState, motionEnabled) },
        contentAlignment = Alignment.Center,
        label = "setup_page",
    ) { page ->
        when (page) {
            0 -> WelcomeSetupPage(
                motionEnabled = motionEnabled,
                landscape = landscape,
                compact = compact,
                viewportHeight = viewportHeight,
            )

            1 -> EnvironmentSetupPage(
                xposedService = xposedService,
                accent = accent,
                successColor = successColor,
                motionEnabled = motionEnabled,
                landscape = landscape,
                compact = compact,
                viewportHeight = viewportHeight,
            )

            else -> ReadySetupPage(
                accent = accent,
                successColor = successColor,
                motionEnabled = motionEnabled,
                landscape = landscape,
                compact = compact,
                viewportHeight = viewportHeight,
            )
        }
    }
}

private fun setupPageEnter(
    motionEnabled: Boolean,
    delayMillis: Int = 0,
    withScale: Boolean = false,
): EnterTransition {
    if (!motionEnabled) return fadeIn(snap())
    var transition: EnterTransition = fadeIn(
        animationSpec = tween(420, delayMillis = delayMillis),
    ) + slideInVertically(
        animationSpec = tween(560, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        initialOffsetY = { height -> height.coerceAtLeast(48) / 5 },
    )
    if (withScale) {
        transition += scaleIn(
            animationSpec = tween(560, delayMillis = delayMillis, easing = FastOutSlowInEasing),
            initialScale = 0.84f,
        )
    }
    return transition
}

private fun setupPageTransition(forward: Boolean, motionEnabled: Boolean): ContentTransform {
    if (!motionEnabled) return fadeIn(snap()).togetherWith(fadeOut(snap()))
    val direction = if (forward) 1 else -1
    return (
        fadeIn(tween(220)) +
            slideInHorizontally(
                animationSpec = tween(PAGE_TRANSITION_MS.toInt(), easing = FastOutSlowInEasing),
                initialOffsetX = { direction * it / 8 },
            )
        ).togetherWith(
        fadeOut(tween(140)) +
            slideOutHorizontally(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                targetOffsetX = { -direction * it / 10 },
            ),
    )
}

@Composable
private fun WelcomeSetupPage(
    motionEnabled: Boolean,
    landscape: Boolean,
    compact: Boolean,
    viewportHeight: Dp,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val iconSize = if (compact || landscape) 104.dp else 132.dp
    val modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .heightIn(min = viewportHeight)
        .padding(horizontal = if (compact) 24.dp else 36.dp, vertical = 16.dp)

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = setupPageEnter(motionEnabled = motionEnabled, withScale = true),
    ) {
        if (landscape) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HuaweiPodsAppIcon(size = iconSize)
                Spacer(Modifier.width(42.dp))
                WelcomeCopy(
                    modifier = Modifier.widthIn(max = 430.dp),
                    centered = false,
                    compact = true,
                )
            }
        } else {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                HuaweiPodsAppIcon(size = iconSize)
                Spacer(Modifier.height(if (compact) 26.dp else 36.dp))
                WelcomeCopy(
                    modifier = Modifier.widthIn(max = 540.dp),
                    centered = true,
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun WelcomeCopy(
    centered: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.semantics { heading() },
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = if (compact) 38.sp else 44.sp,
            lineHeight = if (compact) 44.sp else 52.sp,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_summary),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            style = MiuixTheme.textStyles.headline1,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun EnvironmentSetupPage(
    xposedService: XposedService?,
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
    landscape: Boolean,
    compact: Boolean,
    viewportHeight: Dp,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .heightIn(min = viewportHeight)
            .padding(
                horizontal = if (compact) 24.dp else 36.dp,
                vertical = if (landscape || compact) 10.dp else 22.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (landscape) 680.dp else 620.dp)
                .fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, withScale = true),
            ) {
                CompactBrandHeader()
            }
            Spacer(Modifier.height(if (compact || landscape) 18.dp else 26.dp))
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, delayMillis = 90),
            ) {
                Column {
                    SetupHeading(
                        eyebrow = stringResource(R.string.onboarding_environment_label),
                        title = stringResource(R.string.onboarding_environment_title),
                        summary = stringResource(R.string.onboarding_environment_summary),
                        accent = accent,
                        compact = compact || landscape,
                    )
                }
            }
            Spacer(Modifier.height(if (compact || landscape) 16.dp else 24.dp))
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, delayMillis = 190),
            ) {
                EnvironmentDetails(
                    xposedService = xposedService,
                    accent = accent,
                    successColor = successColor,
                    motionEnabled = motionEnabled,
                )
            }
        }
    }
}

@Composable
private fun CompactBrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HuaweiPodsAppIcon(size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SetupHeading(
    eyebrow: String,
    title: String,
    summary: String,
    accent: Color,
    compact: Boolean,
) {
    Text(
        text = eyebrow,
        color = accent,
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(7.dp))
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        color = MiuixTheme.colorScheme.onSurface,
        fontSize = if (compact) 29.sp else 34.sp,
        lineHeight = if (compact) 35.sp else 41.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(9.dp))
    Text(
        text = summary,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f),
        style = MiuixTheme.textStyles.headline1,
    )
}

@Composable
private fun EnvironmentDetails(
    xposedService: XposedService?,
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
) {
    var refreshVersion by remember { mutableIntStateOf(0) }
    val serviceConnected = xposedService != null
    val coreScopesReady = remember(xposedService, refreshVersion) {
        runCatching { xposedService?.scope?.containsAll(requiredCoreScopes) == true }
            .getOrDefault(false)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SetupStatusRow(
            title = stringResource(R.string.onboarding_environment_lsposed),
            ready = serviceConnected,
            accent = accent,
            successColor = successColor,
            motionEnabled = motionEnabled,
        )
        SetupDivider()
        SetupStatusRow(
            title = stringResource(R.string.onboarding_environment_scopes),
            ready = coreScopesReady,
            accent = accent,
            successColor = successColor,
            motionEnabled = motionEnabled,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            text = stringResource(R.string.onboarding_refresh),
            onClick = { refreshVersion++ },
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun SetupStatusRow(
    title: String,
    ready: Boolean,
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
) {
    val statusText = stringResource(
        if (ready) R.string.onboarding_status_ready else R.string.onboarding_status_missing,
    )
    val statusColor by animateColorAsState(
        targetValue = if (ready) successColor else Color(0xFFE69A37),
        animationSpec = if (motionEnabled) tween(220) else snap(),
        label = "setup_status_color",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 62.dp)
            .semantics(mergeDescendants = true) { stateDescription = statusText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(statusColor.copy(alpha = 0.14f), CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (ready) "✓" else "!",
                color = statusColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = statusText,
            modifier = Modifier.clearAndSetSemantics { },
            color = if (ready) accent else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ReadySetupPage(
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
    landscape: Boolean,
    compact: Boolean,
    viewportHeight: Dp,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val outerModifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .heightIn(min = viewportHeight)
        .padding(
            horizontal = if (compact) 24.dp else 36.dp,
            vertical = if (landscape || compact) 10.dp else 20.dp,
        )

    if (landscape) {
        Row(
            modifier = outerModifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 220.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = setupPageEnter(motionEnabled = motionEnabled, withScale = true),
                ) {
                    HuaweiPodsAppIcon(size = 88.dp)
                }
                Spacer(Modifier.height(18.dp))
                AnimatedVisibility(
                    visible = visible,
                    enter = setupPageEnter(motionEnabled = motionEnabled, delayMillis = 120),
                ) {
                    CompletionState(successColor = successColor)
                }
            }
            Spacer(Modifier.width(44.dp))
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, delayMillis = 210),
            ) {
                ReadyCopy(
                    accent = accent,
                    compact = true,
                    modifier = Modifier.widthIn(max = 470.dp),
                )
            }
        }
    } else {
        Column(
            modifier = outerModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, withScale = true),
            ) {
                HuaweiPodsAppIcon(size = if (compact) 78.dp else 92.dp)
            }
            Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, delayMillis = 120),
            ) {
                CompletionState(successColor = successColor)
            }
            Spacer(Modifier.height(if (compact) 18.dp else 26.dp))
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, delayMillis = 210),
            ) {
                ReadyCopy(
                    accent = accent,
                    compact = compact,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CompletionState(successColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(successColor, CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✓",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_label),
            color = successColor,
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReadyCopy(
    accent: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.onboarding_ready_title),
            modifier = Modifier.semantics { heading() },
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = if (compact) 29.sp else 34.sp,
            lineHeight = if (compact) 35.sp else 41.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_summary),
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            style = MiuixTheme.textStyles.headline1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
        ReadyDetails(accent = accent)
    }
}

@Composable
private fun ReadyDetails(accent: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ReadyItem("1", stringResource(R.string.onboarding_ready_pair), accent)
        SetupDivider()
        ReadyItem("2", stringResource(R.string.onboarding_ready_model), accent)
        SetupDivider()
        ReadyItem(
            "3",
            stringResource(
                R.string.onboarding_ready_group,
                stringResource(R.string.qq_group_number),
            ),
            accent,
        )
    }
}

@Composable
private fun ReadyItem(
    number: String,
    text: String,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
                .background(accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = accent,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(13.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
    }
}

@Composable
private fun SetupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                MiuixTheme.colorScheme.onSurface
                    .copy(alpha = 0.07f)
                    .compositeOver(MiuixTheme.colorScheme.surface),
            ),
    )
}

@Composable
private fun HuaweiPodsAppIcon(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val launcherBackground = colorResource(android.R.color.system_accent1_300)
    val launcherForeground = colorResource(android.R.color.system_accent1_10)
    val corner = when {
        size >= 120.dp -> 32.dp
        size >= 80.dp -> 24.dp
        else -> 14.dp
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(launcherBackground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.tint(launcherForeground),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SetupFooter(
    currentPage: Int,
    accent: Color,
    actionColor: Color,
    navigationEnabled: Boolean,
    motionEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val pageState = stringResource(
        R.string.onboarding_page_status,
        currentPage + 1,
        ONBOARDING_PAGE_COUNT,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {
                stateDescription = pageState
            },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(ONBOARDING_PAGE_COUNT) { index ->
                val dotSize by animateDpAsState(
                    targetValue = if (index == currentPage) 10.dp else 7.dp,
                    animationSpec = if (motionEnabled) tween(240, easing = FastOutSlowInEasing) else snap(),
                    label = "setup_step_dot",
                )
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .background(
                                if (index == currentPage) {
                                    accent
                                } else {
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                                },
                                CircleShape,
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = stringResource(R.string.onboarding_previous),
                onClick = onPrevious,
                enabled = navigationEnabled && currentPage > 0,
                modifier = Modifier
                    .weight(0.38f)
                    .heightIn(min = 54.dp),
            )
            val primaryEnabled = navigationEnabled
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .heightIn(min = 54.dp)
                    .clip(CircleShape)
                    .background(
                        if (primaryEnabled) {
                            actionColor
                        } else {
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        },
                    )
                    .clickable(enabled = primaryEnabled, role = Role.Button, onClick = onNext)
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentPage == ONBOARDING_PAGE_COUNT - 1,
                    transitionSpec = {
                        if (motionEnabled) {
                            fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                        } else {
                            fadeIn(snap()).togetherWith(fadeOut(snap()))
                        }
                    },
                    label = "setup_primary_label",
                ) { isLastPage ->
                    Text(
                        text = stringResource(
                            if (isLastPage) R.string.onboarding_start else R.string.onboarding_next,
                        ),
                        color = Color.White.copy(alpha = if (primaryEnabled) 1f else 0.52f),
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

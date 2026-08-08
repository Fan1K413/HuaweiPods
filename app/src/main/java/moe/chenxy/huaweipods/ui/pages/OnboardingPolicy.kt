package moe.chenxy.huaweipods.ui.pages

internal const val ONBOARDING_PAGE_COUNT = 3

internal enum class OnboardingNavigationAction {
    PREVIOUS,
    NEXT,
}

internal data class OnboardingNavigationResult(
    val page: Int,
    val finish: Boolean = false,
)

internal fun reduceOnboardingNavigation(
    currentPage: Int,
    action: OnboardingNavigationAction,
): OnboardingNavigationResult {
    val safePage = currentPage.coerceIn(0, ONBOARDING_PAGE_COUNT - 1)
    return when (action) {
        OnboardingNavigationAction.PREVIOUS -> OnboardingNavigationResult(
            page = (safePage - 1).coerceAtLeast(0),
        )

        OnboardingNavigationAction.NEXT -> if (safePage == ONBOARDING_PAGE_COUNT - 1) {
            OnboardingNavigationResult(page = safePage, finish = true)
        } else {
            OnboardingNavigationResult(page = safePage + 1)
        }
    }
}

internal data class OnboardingLayoutPolicy(
    val landscape: Boolean,
    val compact: Boolean,
)

internal fun onboardingLayoutPolicy(
    widthDp: Int,
    heightDp: Int,
): OnboardingLayoutPolicy = OnboardingLayoutPolicy(
    landscape = widthDp > heightDp,
    compact = widthDp < 360 || heightDp < 620,
)

internal fun onboardingMotionEnabled(animatorDurationScale: Float): Boolean =
    animatorDurationScale > 0f

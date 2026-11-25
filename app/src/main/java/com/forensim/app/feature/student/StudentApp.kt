package com.forensim.app.feature.student

import com.forensim.app.core.designsystem.NavMotion
import androidx.navigation.NavDestination.Companion.hierarchy

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.forensim.app.core.designsystem.LocalReducedMotion
import com.forensim.app.core.designsystem.Motion
import com.forensim.app.core.designsystem.WorkspaceDestination
import com.forensim.app.core.designsystem.WorkspaceScaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.forensim.app.core.model.UserAccount
import com.forensim.app.feature.ar.ArHubScreen
import com.forensim.app.feature.ar.EvidenceViewerScreen
import com.forensim.app.feature.lms.LessonScreen
import com.forensim.app.feature.lms.ModuleDetailScreen
import com.forensim.app.feature.lms.ModulesScreen
import com.forensim.app.feature.quiz.QuizScreen
import com.forensim.app.feature.settings.ControlSettingsScreen
import com.forensim.app.feature.settings.SettingsScreen
import com.forensim.app.feature.simulation.ScenarioListScreen
import com.forensim.app.feature.simulation.SimulationSessionScreen
import com.forensim.app.feature.tutor.TutorScreen
import kotlinx.serialization.Serializable

// Typed routes (ui-ux-pro-max jetpack-compose guidance: sealed/typed routes, not strings).
@Serializable data object HomeRoute
@Serializable data object LearnRoute
@Serializable data object SimulateRoute
@Serializable data object ArRoute
@Serializable data class TutorRoute(val moduleTitle: String? = null)
@Serializable data class ModuleRoute(val moduleId: String)
@Serializable data class LessonRoute(val lessonId: String)
@Serializable data class ClassChatRoute(val classId: String, val title: String)
@Serializable data class QuizRoute(val quizId: String)
@Serializable data class SimSessionRoute(val scenarioId: String)
@Serializable data class EvidenceViewerRoute(val type: String)
/** Real AR (camera + ARCore) for one evidence specimen. */
@Serializable data class ArCameraRoute(val type: String)
@Serializable data object ProfileRoute
@Serializable data object ControlsRoute
@Serializable data object SettingsRoute
@Serializable data object AccountRoute

private data class TopLevel(
    @androidx.annotation.StringRes val label: Int,
    val route: Any,
    val selected: ImageVector,
    val unselected: ImageVector,
    val prominent: Boolean = false,
    val matches: (androidx.navigation.NavDestination) -> Boolean,
)

private val topLevel = listOf(
    TopLevel(R.string.nav_home, HomeRoute, Icons.Filled.Home, Icons.Outlined.Home) { it.hasRoute(HomeRoute::class) },
    TopLevel(R.string.nav_classes, LearnRoute, Icons.Filled.School, Icons.Outlined.School) { it.hasRoute(LearnRoute::class) },
    // Centre action: drawn as a raised circle, always filled.
    TopLevel(R.string.nav_simulate, SimulateRoute, Icons.Filled._3dRotation, Icons.Filled._3dRotation, prominent = true) { it.hasRoute(SimulateRoute::class) },
    TopLevel(R.string.nav_ar, ArRoute, Icons.Filled.ViewInAr, Icons.Outlined.ViewInAr) { it.hasRoute(ArRoute::class) },
    TopLevel(R.string.nav_profile, ProfileRoute, Icons.Filled.Person, Icons.Outlined.Person) { it.hasRoute(ProfileRoute::class) },
)

@Composable
fun StudentApp(user: UserAccount) {
    // Brand-new accounts get the first-run setup once before the app opens.
    val context = androidx.compose.ui.platform.LocalContext.current
    val ob = com.forensim.app.feature.onboarding.Onboarding
    var onboarding by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(ob.isPending(context, user.email) && !ob.isDone(context, user.id))
    }
    // Also ask the server how old the account is, so a new account gets onboarding even if the
    // sign-up flag was lost or it was created with Google / Facebook.
    val container = com.forensim.app.ui.LocalAppContainer.current
    androidx.compose.runtime.LaunchedEffect(user.id) {
        if (!onboarding && !ob.isDone(context, user.id)) {
            val created = container.auth.accountCreatedAt()
            if (created != null && java.time.Duration.between(created, java.time.Instant.now()) < ob.NEW_ACCOUNT_WINDOW) onboarding = true
        }
    }
    if (onboarding) {
        com.forensim.app.feature.onboarding.OnboardingFlow(user) { answers ->
            com.forensim.app.feature.onboarding.Onboarding.complete(context, user, answers)
            onboarding = false
        }
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = destination != null &&
        topLevel.any { item -> destination.hierarchy.any(item.matches) }
    val reducedMotion = LocalReducedMotion.current
    WorkspaceScaffold(
        destinations = topLevel.map { item ->
            val selected = destination?.hierarchy?.any(item.matches) == true
            val label = stringResource(item.label)
            WorkspaceDestination(
                label,
                if (selected) item.selected else item.unselected,
                selected,
                compactLabel = if (item.route == SimulateRoute) stringResource(R.string.nav_lab) else label,
                prominent = item.prominent,
            ) {
                nav.navigateTopLevel(item.route)
            }
        },
        showNavigation = showBar,
    ) {
        NavHost(
            nav,
            startDestination = HomeRoute,
            // Shared-axis slide for push/pop, fade-through between tabs (see NavMotion).
            enterTransition = { if (reducedMotion || initialState.destination.hasSurfaceView() || targetState.destination.hasSurfaceView()) EnterTransition.None else NavMotion.enter(topLevel.any { t -> initialState.destination.hierarchy.any(t.matches) } && topLevel.any { t -> targetState.destination.hierarchy.any(t.matches) }) },
            exitTransition = { if (reducedMotion || initialState.destination.hasSurfaceView() || targetState.destination.hasSurfaceView()) ExitTransition.None else NavMotion.exit(topLevel.any { t -> initialState.destination.hierarchy.any(t.matches) } && topLevel.any { t -> targetState.destination.hierarchy.any(t.matches) }) },
            popEnterTransition = { if (reducedMotion || initialState.destination.hasSurfaceView() || targetState.destination.hasSurfaceView()) EnterTransition.None else NavMotion.popEnter(topLevel.any { t -> initialState.destination.hierarchy.any(t.matches) } && topLevel.any { t -> targetState.destination.hierarchy.any(t.matches) }) },
            popExitTransition = { if (reducedMotion || initialState.destination.hasSurfaceView() || targetState.destination.hasSurfaceView()) ExitTransition.None else NavMotion.popExit(topLevel.any { t -> initialState.destination.hierarchy.any(t.matches) } && topLevel.any { t -> targetState.destination.hierarchy.any(t.matches) }) },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    user = user,
                    openModule = { nav.navigate(ModuleRoute(it)) },
                    openLesson = { nav.navigate(LessonRoute(it)) },
                    openQuiz = { nav.navigate(QuizRoute(it)) },
                    openSimulate = { nav.navigateTopLevel(SimulateRoute) },
                    openProfile = { nav.navigateTopLevel(ProfileRoute) },
                    openTutor = { nav.navigate(TutorRoute()) },
                    openAr = { nav.navigateTopLevel(ArRoute) },
                    openClasses = { nav.navigateTopLevel(LearnRoute) },
                )
            }
            composable<LearnRoute> { ModulesScreen(openModule = { nav.navigate(ModuleRoute(it)) }) }
            composable<ModuleRoute> { back ->
                ModuleDetailScreen(
                    moduleId = back.toRoute<ModuleRoute>().moduleId,
                    onBack = { nav.popBackStack() },
                    openLesson = { nav.navigate(LessonRoute(it)) },
                    openQuiz = { nav.navigate(QuizRoute(it)) },
                    openTutor = { title -> nav.navigate(TutorRoute(title)) },
                    openChat = { id, title -> nav.navigate(ClassChatRoute(id, title)) },
                )
            }
            composable<ClassChatRoute> { back ->
                val r = back.toRoute<ClassChatRoute>()
                com.forensim.app.feature.classes.ClassChatScreen(r.classId, r.title, canModerate = false, onBack = { nav.popBackStack() })
            }
            composable<LessonRoute> { back ->
                LessonScreen(
                    lessonId = back.toRoute<LessonRoute>().lessonId,
                    onBack = { nav.popBackStack() },
                    openTutor = { title -> nav.navigate(TutorRoute(title)) },
                )
            }
            composable<QuizRoute> { back -> QuizScreen(back.toRoute<QuizRoute>().quizId, onBack = { nav.popBackStack() }) }
            composable<SimulateRoute> {
                ScenarioListScreen(openScenario = { nav.navigate(SimSessionRoute(it)) }, openControls = { nav.navigate(ControlsRoute) })
            }
            composable<SimSessionRoute> { back ->
                SimulationSessionScreen(back.toRoute<SimSessionRoute>().scenarioId, user, onExit = { nav.popBackStack() },
                    onOpen = { next -> nav.navigate(SimSessionRoute(next)) { popUpTo<SimSessionRoute> { inclusive = true } } })
            }
            composable<ArRoute> { ArHubScreen(openViewer = { nav.navigate(EvidenceViewerRoute(it)) }, openCamera = { nav.navigate(ArCameraRoute(it)) }) }
            composable<EvidenceViewerRoute> { back ->
                val type = back.toRoute<EvidenceViewerRoute>().type
                EvidenceViewerScreen(type, onBack = { nav.popBackStack() }, openAr = { nav.navigate(ArCameraRoute(type)) })
            }
            composable<ArCameraRoute> { back ->
                com.forensim.app.feature.ar.ArCameraScreen(back.toRoute<ArCameraRoute>().type, onBack = { nav.popBackStack() })
            }
            composable<TutorRoute> { back ->
                val title = back.toRoute<TutorRoute>().moduleTitle
                TutorScreen(moduleTitle = title, openLesson = { nav.navigate(LessonRoute(it)) }, onBack = { nav.popBackStack() })
            }
            composable<ProfileRoute> {
                ProfileHubScreen(
                    user = user,
                    openClasses = { nav.navigateTopLevel(LearnRoute) },
                    openSettings = { nav.navigate(SettingsRoute) },
                    openControls = { nav.navigate(ControlsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(user, onBack = { nav.popBackStack() }, openAccount = { nav.navigate(AccountRoute) },
                    openControls = { nav.navigate(ControlsRoute) })
            }
            composable<AccountRoute> { com.forensim.app.feature.settings.AccountScreen(user, onBack = { nav.popBackStack() }) }
            composable<ControlsRoute> { ControlSettingsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun androidx.navigation.NavDestination.hasSurfaceView() =
    hasRoute(SimSessionRoute::class) || hasRoute(EvidenceViewerRoute::class) || hasRoute(ArCameraRoute::class)

fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

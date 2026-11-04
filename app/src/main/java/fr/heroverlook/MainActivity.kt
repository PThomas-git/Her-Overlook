package fr.heroverlook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.heroverlook.ui.AccessScreen
import fr.heroverlook.ui.ChecklistScreen
import fr.heroverlook.ui.DocumentsScreen
import fr.heroverlook.ui.HerTheme
import fr.heroverlook.ui.HomeScreen
import fr.heroverlook.ui.LayoutScreen
import fr.heroverlook.ui.PhotosScreen
import fr.heroverlook.ui.RackScreen
import fr.heroverlook.ui.RacksScreen
import fr.heroverlook.ui.SiteScreen
import fr.heroverlook.ui.SummaryScreen
import fr.heroverlook.ui.UsagersScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HerTheme { AppRoot() }
        }
    }
}

@Composable
fun AppRoot(vm: AppVM = viewModel()) {
    BackHandler(enabled = vm.nav.size > 1) { vm.back() }

    // Sens de la transition : on avance (nouvel écran empilé) ou on revient.
    val depth = vm.nav.size
    var prevDepth by remember { mutableIntStateOf(depth) }
    val forward = depth >= prevDepth
    SideEffect { prevDepth = depth }

    AnimatedContent(
        targetState = vm.nav.last(),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            val inSpec = tween<androidx.compose.ui.unit.IntOffset>(420, easing = FastOutSlowInEasing)
            val outSpec = tween<androidx.compose.ui.unit.IntOffset>(300, easing = FastOutSlowInEasing)
            if (forward) {
                (slideInHorizontally(inSpec) { it / 5 } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(outSpec) { -it / 8 } + fadeOut(tween(220)))
            } else {
                (slideInHorizontally(inSpec) { -it / 8 } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(outSpec) { it / 5 } + fadeOut(tween(220)))
            }
        },
        label = "navigation",
    ) { route -> Route(vm, route) }
}

@Composable
private fun Route(vm: AppVM, route: String) {
    // Routes : home | site/<id> | site/<id>/<écran> | site/<id>/rack/<rackId>
    val parts = route.split("/")
    when {
        parts[0] == "home" -> HomeScreen(vm)
        parts[0] == "site" && parts.size == 2 -> SiteScreen(vm, parts[1])
        parts[0] == "site" && parts.size == 3 -> when (parts[2]) {
            "access" -> AccessScreen(vm, parts[1])
            "checklist" -> ChecklistScreen(vm, parts[1])
            "docs" -> DocumentsScreen(vm, parts[1])
            "layout" -> LayoutScreen(vm, parts[1])
            "racks" -> RacksScreen(vm, parts[1])
            "usagers" -> UsagersScreen(vm, parts[1])
            "photos" -> PhotosScreen(vm, parts[1])
            "summary" -> SummaryScreen(vm, parts[1])
            else -> HomeScreen(vm)
        }
        parts[0] == "site" && parts.size == 4 && parts[2] == "rack" -> RackScreen(vm, parts[1], parts[3])
        else -> HomeScreen(vm)
    }
}

package fr.heroverlook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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

    // Routes : home | site/<id> | site/<id>/<écran> | site/<id>/rack/<rackId>
    val parts = vm.nav.last().split("/")
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

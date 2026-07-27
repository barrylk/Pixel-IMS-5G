package dev.bluehouse.enablevolte

import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.bluehouse.enablevolte.components.OnLifecycleEvent
import dev.bluehouse.enablevolte.components.GlassBackdrop
import dev.bluehouse.enablevolte.pages.Config
import dev.bluehouse.enablevolte.pages.ControlsHub
import dev.bluehouse.enablevolte.pages.Bands
import dev.bluehouse.enablevolte.pages.DumpedConfig
import dev.bluehouse.enablevolte.pages.Editor
import dev.bluehouse.enablevolte.pages.Home
import dev.bluehouse.enablevolte.pages.HowToUse
import dev.bluehouse.enablevolte.pages.FieldTestPage
import dev.bluehouse.enablevolte.pages.MonitoringHub
import dev.bluehouse.enablevolte.pages.About
import dev.bluehouse.enablevolte.ui.theme.EnableVoLTETheme
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.lang.IllegalStateException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
)

val NavDestination.depth: Int get() = this.route?.let { route -> route.count { it == '/' } + 1 } ?: 0

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
        enableEdgeToEdge()

        setContent {
            EnableVoLTETheme {
                GlassBackdrop {
                    PixelIMSApp()
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelIMSApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val carrierModer = CarrierModer(context)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    var subscriptions by rememberSaveable { mutableStateOf(listOf<SubscriptionInfo>()) }
    var showRecoveryDialog by rememberSaveable { mutableStateOf(false) }
    var selectedPrivilegeMode by rememberSaveable {
        mutableStateOf(PrivilegeManager.selectedMode(context)?.name)
    }
    var privilegeError by rememberSaveable { mutableStateOf<String?>(null) }
    var privilegeConnecting by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadApplication() {
        val mode = PrivilegeManager.selectedMode(context) ?: return
        PrivilegeManager.activate(context, mode)
        if (mode == PrivilegeMode.ROOT && !PrivilegeManager.isRootReady()) {
            if (privilegeConnecting) return
            privilegeConnecting = true
            PrivilegeManager.connectRoot(context) { ready, error ->
                context.mainExecutor.execute {
                    privilegeConnecting = false
                    privilegeError = error
                    if (ready) {
                        runCatching {
                            subscriptions = carrierModer.subscriptions
                        }.onFailure { privilegeError = it.message ?: "Unable to read telephony services as root" }
                    }
                }
            }
            return
        }
        if (mode == PrivilegeMode.ROOT) {
            runCatching {
                subscriptions = carrierModer.subscriptions
            }.onFailure { privilegeError = it.message ?: "Unable to read telephony services as root" }
            return
        }
        val shizukuStatus = checkShizukuPermission(0)
        try {
            when (shizukuStatus) {
                ShizukuStatus.GRANTED -> {
                    Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                    subscriptions = carrierModer.subscriptions
                }
                ShizukuStatus.NOT_GRANTED -> {
                    Shizuku.addRequestPermissionResultListener { _, grantResult ->
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                            subscriptions = carrierModer.subscriptions
                        }
                    }
                }
                else -> {
                    subscriptions = listOf()
                }
            }
        } catch (_: IllegalStateException) {
        }
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_CREATE) {
            loadApplication()
        }
    }
    if (selectedPrivilegeMode == null || privilegeError != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.choose_access_mode)) },
            text = {
                Text(
                    privilegeError ?: stringResource(R.string.choose_access_mode_message),
                )
            },
            confirmButton = {
                Button(
                    enabled = !privilegeConnecting,
                    onClick = {
                        privilegeError = null
                        selectedPrivilegeMode = PrivilegeMode.ROOT.name
                        PrivilegeManager.activate(context, PrivilegeMode.ROOT)
                        loadApplication()
                    },
                ) {
                    Text(if (privilegeConnecting) stringResource(R.string.connecting) else stringResource(R.string.use_root))
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !privilegeConnecting,
                    onClick = {
                        privilegeError = null
                        selectedPrivilegeMode = PrivilegeMode.SHIZUKU.name
                        PrivilegeManager.activate(context, PrivilegeMode.SHIZUKU)
                        loadApplication()
                    },
                ) { Text(stringResource(R.string.use_shizuku)) }
            },
        )
    }
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text(stringResource(R.string.restore_reboot_title)) },
            text = { Text(stringResource(R.string.restore_reboot_message)) },
            confirmButton = {
                Button(onClick = {
                    showRecoveryDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) { carrierModer.restoreAllManagedSettingsAndReboot() }
                    }
                }) { Text(stringResource(R.string.restore_and_reboot)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRecoveryDialog = false }) { Text(stringResource(R.string.dismiss)) }
            },
        )
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentBackStackEntry?.destination?.label?.toString() ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    if (currentBackStackEntry?.destination?.depth?.let { it > 1 } == true) {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back",
                            )
                        }
                    }
                },
                actions = {
                    if (subscriptions.isNotEmpty()) {
                        IconButton(
                            onClick = { showRecoveryDialog = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(),
                        ) {
                            Icon(Icons.Filled.PowerSettingsNew, contentDescription = stringResource(R.string.restore_and_reboot))
                        }
                    }
                    if (currentBackStackEntry?.destination?.route != "home/about") {
                        IconButton(
                            onClick = { navController.navigate("home/about") },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(),
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.about))
                        }
                    }
                    if (currentBackStackEntry?.destination?.route == "home") {
                        IconButton(onClick = {
                            loadApplication()
                        }, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh contents",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                ),
            )
        },
        bottomBar = {
            val currentRoute = currentBackStackEntry?.destination?.route
            if (currentRoute in setOf("home", "controls", "monitor", "field-test", "config/{subId}", "bands/{subId}")) {
                NavigationBar(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(32.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.84f),
                    tonalElevation = 8.dp,
                ) {
                    val currentDestination = currentBackStackEntry?.destination
                    val items = arrayListOf(
                        Screen("home", stringResource(R.string.home), Icons.Filled.Home),
                        Screen("controls", stringResource(R.string.controls), Icons.Filled.Tune),
                        Screen("monitor", stringResource(R.string.monitor), Icons.Filled.SignalCellularAlt),
                        Screen("field-test", stringResource(R.string.field_test_short), Icons.Filled.Science),
                    )

                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = {
                                Text(screen.title)
                            },
                            selected = when {
                                screen.route == "controls" ->
                                    currentRoute in setOf("controls", "config/{subId}", "bands/{subId}")
                                else -> currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            },
                            onClick = {
                                navController.navigate(screen.route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large stack of destinations
                                    // on the back stack as users select items
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination when
                                    // reselecting the same item
                                    launchSingleTop = true
                                    // Restore state when reselecting a previously selected item
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController, startDestination = "home", Modifier.padding(innerPadding)) {
            composable("home", context.resources.getString(R.string.home)) {
                Home(navController)
            }
            composable("home/about", context.resources.getString(R.string.about)) {
                About()
            }
            composable("home/how-to", context.resources.getString(R.string.how_to_use)) {
                HowToUse()
            }
            composable("controls", context.resources.getString(R.string.controls)) {
                ControlsHub(subscriptions, navController)
            }
            composable("monitor", context.resources.getString(R.string.network_monitor)) {
                MonitoringHub(subscriptions)
            }
            composable("field-test", context.resources.getString(R.string.field_test)) {
                FieldTestPage(subscriptions)
            }
            composable("config/{subId}", context.resources.getString(R.string.sim_config)) { entry ->
                entry.arguments?.getString("subId")?.toIntOrNull()?.let { Config(navController, it) }
            }
            composable("config/{subId}/dump", context.resources.getString(R.string.config_dump_viewer)) { entry ->
                entry.arguments?.getString("subId")?.toIntOrNull()?.let { DumpedConfig(context, it) }
            }
            composable("config/{subId}/edit", context.resources.getString(R.string.expert_mode)) { entry ->
                entry.arguments?.getString("subId")?.toIntOrNull()?.let { Editor(it) }
            }
            composable("bands/{subId}", context.resources.getString(R.string.bands)) { entry ->
                entry.arguments?.getString("subId")?.toIntOrNull()?.let { Bands(it, navController) }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Preview
@Composable
fun PixelIMSAppPreview() {
    EnableVoLTETheme {
        GlassBackdrop {
            PixelIMSApp()
        }
    }
}

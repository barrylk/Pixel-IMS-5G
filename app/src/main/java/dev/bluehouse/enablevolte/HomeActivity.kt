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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import dev.bluehouse.enablevolte.components.OnLifecycleEvent
import dev.bluehouse.enablevolte.components.GlassBackdrop
import dev.bluehouse.enablevolte.pages.Config
import dev.bluehouse.enablevolte.pages.Bands
import dev.bluehouse.enablevolte.pages.DumpedConfig
import dev.bluehouse.enablevolte.pages.Editor
import dev.bluehouse.enablevolte.pages.Home
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
    val scope = rememberCoroutineScope()
    var navBuilder by remember {
        mutableStateOf<NavGraphBuilder.() -> Unit>({
            composable("home", context.resources.getString(R.string.home)) {
                Home(navController)
            }
            composable("home/about", context.resources.getString(R.string.about)) {
                About()
            }
        })
    }

    fun generateInitialNavBuilder(): (NavGraphBuilder.() -> Unit) =
        {
            composable("home", "Home") {
                Home(navController)
            }
        }

    fun generateNavBuilder(): (NavGraphBuilder.() -> Unit) =
        {
            composable("home", context.resources.getString(R.string.home)) {
                Home(navController)
            }
            composable("home/about", context.resources.getString(R.string.about)) {
                About()
            }
            for (subscription in subscriptions) {
                navigation(startDestination = "config${subscription.subscriptionId}", route = "config${subscription.subscriptionId}root") {
                    composable("config${subscription.subscriptionId}", context.resources.getString(R.string.sim_config)) {
                        Config(navController, subscription.subscriptionId)
                    }
                    composable("config${subscription.subscriptionId}/dump", context.resources.getString(R.string.config_dump_viewer)) {
                        DumpedConfig(context, subscription.subscriptionId)
                    }
                    composable("config${subscription.subscriptionId}/edit", context.resources.getString(R.string.expert_mode)) {
                        Editor(subscription.subscriptionId)
                    }
                }
                composable("bands${subscription.subscriptionId}", context.resources.getString(R.string.bands)) {
                    Bands(subscription.subscriptionId)
                }
            }
        }

    fun loadApplication() {
        val shizukuStatus = checkShizukuPermission(0)
        try {
            when (shizukuStatus) {
                ShizukuStatus.GRANTED -> {
                    Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                    subscriptions = carrierModer.subscriptions
                    navBuilder = generateNavBuilder()
                }
                ShizukuStatus.NOT_GRANTED -> {
                    Shizuku.addRequestPermissionResultListener { _, grantResult ->
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                            subscriptions = carrierModer.subscriptions
                            navBuilder = generateNavBuilder()
                        }
                    }
                }
                else -> {
                    subscriptions = listOf()
                    navBuilder = generateInitialNavBuilder()
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
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.restore_and_reboot))
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
            if (currentBackStackEntry?.destination?.depth?.let { it == 1 } == true) {
                NavigationBar(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(32.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.84f),
                    tonalElevation = 8.dp,
                ) {
                    val currentDestination = currentBackStackEntry?.destination
                    val items =
                        arrayListOf(
                            Screen("home", stringResource(R.string.home), Icons.Filled.Home),
                        )
                    for (subscription in subscriptions) {
                        items.add(
                            Screen("config${subscription.subscriptionId}", subscription.uniqueName, Icons.Filled.Settings),
                        )
                        items.add(
                            Screen(
                                "bands${subscription.subscriptionId}",
                                "${subscription.uniqueName} ${stringResource(R.string.bands)}",
                                Icons.AutoMirrored.Filled.List,
                            ),
                        )
                    }

                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = {
                                Text(screen.title)
                            },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
        NavHost(navController, startDestination = "home", Modifier.padding(innerPadding), builder = navBuilder)
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

package dev.bluehouse.enablevolte

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
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
import dev.bluehouse.enablevolte.components.AppBackdrop
import dev.bluehouse.enablevolte.components.InfoDialog
import dev.bluehouse.enablevolte.components.WhatsNewDialog
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
    companion object {
        const val EXTRA_OPEN_UPDATES = "open_updates"
        const val EXTRA_INSTALL_DOWNLOADED_UPDATE = "install_downloaded_update"
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            UpdateNotificationScheduler.initialize(this)
        }
    private val unknownSourcesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            continueDownloadedUpdateInstall()
        }
    private var navigationRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
        enableEdgeToEdge()

        setContent {
            EnableVoLTETheme {
                AppBackdrop {
                    PixelIMSApp(
                        startDestination = if (intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)) {
                            "home/about"
                        } else {
                            "home"
                        },
                        navigationRequest = navigationRequest,
                        onNavigationRequestHandled = { navigationRequest = null },
                    )
                }
            }
        }
        configureUpdateNotifications()
        handleUpdateIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)) {
            intent.removeExtra(EXTRA_OPEN_UPDATES)
            navigationRequest = "home/about"
        } else {
            handleUpdateIntent(intent)
        }
    }

    private fun configureUpdateNotifications() {
        UpdateNotificationScheduler.initialize(this)
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("github_updater", MODE_PRIVATE)
        val permissionPromptKey = "notification_permission_asked"
        if (prefs.getBoolean(permissionPromptKey, false)) return
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.update_notification_permission_title)
            .setMessage(R.string.update_notification_permission_message)
            .setPositiveButton(R.string.allow_notifications) { _, _ ->
                prefs.edit().putBoolean(permissionPromptKey, true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.not_now) { _, _ ->
                prefs.edit().putBoolean(permissionPromptKey, true).apply()
            }
            .show()
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_INSTALL_DOWNLOADED_UPDATE, false) != true) return
        intent.removeExtra(EXTRA_INSTALL_DOWNLOADED_UPDATE)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(this, R.string.install_permission_needed, Toast.LENGTH_LONG).show()
            unknownSourcesLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
        } else {
            continueDownloadedUpdateInstall()
        }
    }

    private fun continueDownloadedUpdateInstall() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            return
        }
        if (!UpdateManager.installDownloadedUpdate(this)) {
            Toast.makeText(this, R.string.installer_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelIMSApp(
    startDestination: String = "home",
    navigationRequest: String? = null,
    onNavigationRequestHandled: () -> Unit = {},
) {
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
    val osDistribution = remember { OsDistributionDetector.detect(context) }
    var showShizukuRegionalWarning by rememberSaveable { mutableStateOf(false) }
    var whatsNew by remember { mutableStateOf(UpdateManager.changelogToShow(context)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedPrivilegeMode, osDistribution.name) {
        if (selectedPrivilegeMode == PrivilegeMode.SHIZUKU.name) {
            val warningKey = "regional_warning_${Build.VERSION.INCREMENTAL}_${osDistribution.name}"
            val preferences = context.getSharedPreferences("pixel_ims_notices", Context.MODE_PRIVATE)
            if (!preferences.getBoolean(warningKey, false)) {
                showShizukuRegionalWarning = true
            }
        }
    }

    LaunchedEffect(navigationRequest) {
        navigationRequest?.let { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
            onNavigationRequestHandled()
        }
    }

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
    LaunchedEffect(subscriptions, selectedPrivilegeMode) {
        if (
            subscriptions.isNotEmpty() &&
            selectedPrivilegeMode == PrivilegeMode.ROOT.name &&
            PrivilegeManager.isRootReady()
        ) {
            ImsStatusBarController.apply(
                enabled = ImsStatusBarController.isEnabled(context),
                subscriptionIds = subscriptions.map { it.subscriptionId }.toIntArray(),
            )
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
    if (selectedPrivilegeMode != null && privilegeError == null) {
        whatsNew?.let { changelog ->
            WhatsNewDialog(
                changelog = changelog,
                onDismiss = {
                    UpdateManager.markChangelogShown(context, BuildConfig.VERSION_NAME)
                    whatsNew = null
                },
            )
        }
    }
    if (
        whatsNew == null &&
        showShizukuRegionalWarning &&
        selectedPrivilegeMode == PrivilegeMode.SHIZUKU.name
    ) {
        InfoDialog(
            title = stringResource(R.string.shizuku_regional_limit_title),
            message = stringResource(
                R.string.shizuku_regional_limit_message,
                osDistribution.name,
            ),
            confirmLabel = stringResource(R.string.understood),
            onDismiss = {
                val warningKey =
                    "regional_warning_${Build.VERSION.INCREMENTAL}_${osDistribution.name}"
                context.getSharedPreferences("pixel_ims_notices", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(warningKey, true)
                    .apply()
                showShizukuRegionalWarning = false
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
                    val route = currentBackStackEntry?.destination?.route
                    if (route in setOf("home", "controls", "monitor", "field-test")) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.app_signature),
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Text(
                            currentBackStackEntry?.destination?.label?.toString()
                                ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (currentBackStackEntry?.destination?.depth?.let { it > 1 } == true) {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }) {
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
                        ) {
                            Icon(Icons.Filled.PowerSettingsNew, contentDescription = stringResource(R.string.restore_and_reboot))
                        }
                    }
                    if (currentBackStackEntry?.destination?.route != "home/about") {
                        IconButton(
                            onClick = { navController.navigate("home/about") },
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.about))
                        }
                    }
                    if (currentBackStackEntry?.destination?.route == "home") {
                        IconButton(onClick = {
                            loadApplication()
                        }) {
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
                val currentDestination = currentBackStackEntry?.destination
                val items = arrayListOf(
                    Screen("home", stringResource(R.string.home), Icons.Filled.Home),
                    Screen("controls", stringResource(R.string.controls), Icons.Filled.Tune),
                    Screen("monitor", stringResource(R.string.monitor), Icons.Filled.SignalCellularAlt),
                    Screen("field-test", stringResource(R.string.field_test_short), Icons.Filled.Science),
                )
                // Flush to the edge with a hairline above it, rather than a floating
                // rounded pill: the bar is chrome, and should not read as a control.
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEach { screen ->
                            val selected = when {
                                screen.route == "controls" ->
                                    currentRoute in setOf("controls", "config/{subId}", "bands/{subId}")
                                else -> currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            }
                            val tint = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.title,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    screen.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tint,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController, startDestination = startDestination, Modifier.padding(innerPadding)) {
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
        AppBackdrop {
            PixelIMSApp()
        }
    }
}

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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.statusBarsPadding
import dev.bluehouse.enablevolte.components.LocalAppActions
import dev.bluehouse.enablevolte.components.GlassIconButton
import dev.bluehouse.enablevolte.components.AppActions
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.bluehouse.enablevolte.components.AppBackdrop
import dev.bluehouse.enablevolte.components.GlassDepth
import dev.bluehouse.enablevolte.components.InfoDialog
import dev.bluehouse.enablevolte.components.OnLifecycleEvent
import dev.bluehouse.enablevolte.components.WhatsNewDialog
import dev.bluehouse.enablevolte.components.liquidGlass
import dev.bluehouse.enablevolte.components.LocalBottomBarInset
import dev.bluehouse.enablevolte.components.LocalHazeState
import dev.bluehouse.enablevolte.components.appBackdropPaint
import dev.bluehouse.enablevolte.components.floatingGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.bluehouse.enablevolte.pages.About
import dev.bluehouse.enablevolte.pages.Bands
import dev.bluehouse.enablevolte.pages.Config
import dev.bluehouse.enablevolte.pages.ControlsHub
import dev.bluehouse.enablevolte.pages.DumpedConfig
import dev.bluehouse.enablevolte.pages.Editor
import dev.bluehouse.enablevolte.pages.FieldTestPage
import dev.bluehouse.enablevolte.pages.Home
import dev.bluehouse.enablevolte.pages.HowToUse
import dev.bluehouse.enablevolte.pages.MonitoringHub
import dev.bluehouse.enablevolte.pages.NetworkPage
import dev.bluehouse.enablevolte.pages.SetupPage
import dev.bluehouse.enablevolte.ui.theme.EnableVoLTETheme
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import dev.bluehouse.enablevolte.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.lang.IllegalStateException

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
            var themeMode by remember { mutableStateOf(ThemeMode.load(this@HomeActivity)) }
            EnableVoLTETheme(themeMode = themeMode) {
                AppBackdrop {
                    PixelIMSApp(
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            ThemeMode.save(this@HomeActivity, it)
                        },
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
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
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

    // Observable, unlike PrivilegeManager.isRootReady(): the regional patch card
    // on the home screen appears only once root is detected and granted.
    var rootGranted by remember { mutableStateOf(PrivilegeManager.isRootReady()) }
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
                    rootGranted = ready && PrivilegeManager.isRootReady()
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
            rootGranted = true
            runCatching {
                subscriptions = carrierModer.subscriptions
            }.onFailure { privilegeError = it.message ?: "Unable to read telephony services as root" }
            return
        }
        rootGranted = false
        // checkShizukuPermission throws IllegalStateException when the binder dies
        // between its own checks, so it belongs inside the try.
        try {
            when (checkShizukuPermission(0)) {
                ShizukuStatus.GRANTED -> {
                    Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                    subscriptions = carrierModer.subscriptions
                }
                // The grant arrives through shizukuPermissionListener below.
                ShizukuStatus.NOT_GRANTED -> Unit
                else -> {
                    subscriptions = listOf()
                }
            }
        } catch (_: IllegalStateException) {
        }
    }

    // One listener for the life of the screen. It used to be added on every
    // load and every refresh tap, and never removed, so each grant re-read the
    // subscriptions once per stale listener.
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED && PrivilegeManager.activeMode == PrivilegeMode.SHIZUKU) {
                Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                runCatching { subscriptions = carrierModer.subscriptions }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
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
    if (selectedPrivilegeMode == null || privilegeError != null || privilegeConnecting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.choose_access_mode)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (privilegeConnecting) stringResource(R.string.connecting)
                        else privilegeError ?: stringResource(R.string.choose_access_mode_message),
                    )
                    Text(
                        "Pixel IMS 5G ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    if (
        selectedPrivilegeMode != null && privilegeError == null && !privilegeConnecting &&
        (selectedPrivilegeMode != PrivilegeMode.ROOT.name || rootGranted)
    ) {
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
    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Tab pages carry their own large title and round glass buttons, as in the
                // concept; only deeper pages get a bar, with a glass back button.
                val route = currentBackStackEntry?.destination?.route
                if (route !in TAB_ROUTES) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (currentBackStackEntry?.destination?.depth?.let { it > 1 } == true) {
                            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Go back", { navController.popBackStack() })
                        }
                        Text(
                            currentBackStackEntry?.destination?.label?.toString() ?: stringResource(R.string.app_name),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
            bottomBar = {
                val currentRoute = currentBackStackEntry?.destination?.route
                if (currentRoute in BAR_ROUTES) {
                    val currentDestination = currentBackStackEntry?.destination
                    val items = arrayListOf(
                        Screen("home", stringResource(R.string.setup), Icons.Filled.Home),
                        Screen("controls", stringResource(R.string.controls), Icons.Filled.Tune),
                        Screen("network", stringResource(R.string.network), Icons.Filled.SignalCellularAlt),
                        Screen("field-test", stringResource(R.string.field_test_short), Icons.Filled.Science),
                    )
                    val inst = LocalInstrument.current
                    val barShape = RoundedCornerShape(30.dp)
                    val selectedIndex = items.indexOfFirst { screen ->
                        when (screen.route) {
                            "controls" -> currentRoute in setOf("controls", "config/{subId}", "bands/{subId}")
                            else -> currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        }
                    }
                    val lensPosition by animateFloatAsState(
                        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                        label = "nav lens",
                    )
                    // The floating glass tab bar from the concept: the chosen tab sits in a
                    // soft accent pill that slides between tabs.
                    val accent = MaterialTheme.colorScheme.primary
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 6.dp)) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .floatingGlass(barShape)
                                .padding(6.dp),
                        ) {
                            val slot = maxWidth / items.size
                            if (selectedIndex >= 0) {
                                Box(
                                    Modifier
                                        .offset(x = slot * lensPosition)
                                        .width(slot)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(accent.copy(alpha = if (inst.isDark) 0.16f else 0.12f)),
                                )
                            }
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                items.forEachIndexed { index, screen ->
                                    val selected = index == selectedIndex
                                    val tint by animateColorAsState(
                                        if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        label = "nav tint",
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(24.dp))
                                            .selectable(
                                                selected = selected,
                                                role = Role.Tab,
                                                onClick = {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                            ),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(screen.icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                                        Spacer(Modifier.height(2.dp))
                                        Text(screen.title, fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            // Tab pages scroll under the floating bar, which blurs them; other pages keep
            // the plain bottom inset. The backdrop is painted inside the blur source too.
            val barVisible = currentBackStackEntry?.destination?.route in BAR_ROUTES
            val bottomInset = innerPadding.calculateBottomPadding()
            Box(Modifier.fillMaxSize().hazeSource(hazeState).appBackdropPaint()) {
                // Glass inside the blurred content falls back to its tinted body: an effect
                // must not sample the layer it is drawn into.
                val appearanceLabel = stringResource(
                    when (themeMode) {
                        ThemeMode.SYSTEM -> R.string.appearance_system
                        ThemeMode.LIGHT -> R.string.appearance_light
                        ThemeMode.DARK -> R.string.appearance_dark
                    },
                )
                CompositionLocalProvider(
                    LocalBottomBarInset provides if (barVisible) bottomInset else 0.dp,
                    LocalHazeState provides null,
                    LocalAppActions provides AppActions(
                        restoreAvailable = subscriptions.isNotEmpty(),
                        onRestore = { showRecoveryDialog = true },
                        appearanceLabel = appearanceLabel,
                        onCycleAppearance = { onThemeModeChange(themeMode.next()) },
                        onAbout = { navController.navigate("home/about") },
                        onReload = { loadApplication() },
                    ),
                ) {
                    NavHost(
                        navController,
                        startDestination = startDestination,
                        Modifier.padding(top = innerPadding.calculateTopPadding(), bottom = if (barVisible) 0.dp else bottomInset),
                    ) {
                        composable("home", context.resources.getString(R.string.setup)) {
                            SetupPage(subscriptions, navController, rootGranted)
                        }
                        // The old readiness overview is still reachable; Setup answers the
                        // narrower question most people open the app with.
                        composable("home/overview", context.resources.getString(R.string.home)) {
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
                        composable("network", context.resources.getString(R.string.network)) {
                            NetworkPage(subscriptions, navController)
                        }
                        // The deep diagnostics hub keeps its own route: the attach trace,
                        // physical channels and config diff are still reachable from Network.
                        composable("network/diagnostics", context.resources.getString(R.string.network_monitor)) {
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
                            entry.arguments?.getString("subId")?.toIntOrNull()?.let { Bands(it, navController, rootGranted) }
                        }
                    }
                }
            }
        }
    }
}

/** The four tabs: they draw their own title row instead of a top bar. */
private val TAB_ROUTES = setOf("home", "controls", "network", "field-test")

/** Routes that show the floating tab bar. */
private val BAR_ROUTES = setOf("home", "controls", "network", "field-test", "config/{subId}", "bands/{subId}")

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

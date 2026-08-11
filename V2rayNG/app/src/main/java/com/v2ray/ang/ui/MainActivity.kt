package com.v2ray.ang.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.MobileTinaSessionLimiter
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MobileTinaExpiryManager
import com.v2ray.ang.handler.MobileTinaHiddenShareManager
import com.v2ray.ang.handler.MobileTinaResetManager
import com.v2ray.ang.handler.MobileTinaSubscriptionInfo
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.MobileTinaImportNormalizer
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil

class MainActivity : HelperBaseActivity(), com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    val mainViewModel: MainViewModel by viewModels()

    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null
    private val firstRunPrefs by lazy { getSharedPreferences("mobiletina_first_run", MODE_PRIVATE) }

    private var currentMode = MODE_AUTO
    private var lastSubscriptionRefreshAt = 0L
    private var subscriptionRefreshing = false
    private var pendingSmartVpnPermission = false
    private var smartConnecting = false
    private var smartConnectionFailed = false
    private var smartCountdownSeconds = 0
    private var lastConnectedPing: String? = null
    private var smartConnectJob: kotlinx.coroutines.Job? = null
    private var manualConnecting = false
    private var manualPrewarmGuid: String? = null
    private var manualPrewarmJob: kotlinx.coroutines.Job? = null

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val smart = pendingSmartVpnPermission
            pendingSmartVpnPermission = false
            if (result.resultCode == RESULT_OK) {
                startV2Ray(smart)
            } else if (smart) {
                markSmartConnectFailed()
            } else {
                manualConnecting = false
                refreshSelectedServerUi()
            }
        }

    private val requestFirstRunCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            requestFirstRunVpnPermissionOnly()
        }

    private val requestFirstRunVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            markFirstRunPermissionCompleted()
        }

    private val requestActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) restartV2Ray()
            if (SettingsChangeManager.consumeSetupGroupTab()) setupGroupTab()
            setupGroupTab()
            refreshSelectedServerUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, false, getString(R.string.mobiletina_app_name))

        setupModeTabs()
        setupGroupPager()
        setupDrawer()
        setupActions()
        setupViewModel()
        normalizeSubscriptionNames()
        setupGroupTab()
        mainViewModel.reloadServerList()
        ensureSelectedServerForCurrentSubscription()
        refreshSelectedServerUi()

        MobileTinaExpiryManager.recoverPending(this)

        if (firstRunPrefs.getBoolean(FIRST_RUN_COMPLETED, false)) {
            checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) { }
        } else {
            handleFirstRunPermissions()
        }
    }

    private fun setupModeTabs() {
        binding.btnModeManual.setOnClickListener { setMode(MODE_MANUAL) }
        binding.btnModeAuto.setOnClickListener { setMode(MODE_AUTO) }
        binding.modeContainer.setOnModeSwipeListener { direction ->
            // +1 = left-to-right -> Manual, -1 = right-to-left -> Auto.
            if (direction > 0) setMode(MODE_MANUAL) else setMode(MODE_AUTO)
        }
        setMode(MODE_AUTO)
    }

    private fun setMode(mode: Int, updateTab: Boolean = true) {
        currentMode = mode.coerceIn(MODE_AUTO, MODE_MANUAL)
        binding.autoPanel.visibility = if (currentMode == MODE_AUTO) View.VISIBLE else View.GONE
        binding.manualPanel.visibility = if (currentMode == MODE_MANUAL) View.VISIBLE else View.GONE
        updateModeSelector()
        refreshSelectedServerUi()
    }

    private fun updateModeSelector() {
        val manualSelected = currentMode == MODE_MANUAL
        val autoSelected = currentMode == MODE_AUTO
        binding.btnModeManual.isSelected = manualSelected
        binding.btnModeAuto.isSelected = autoSelected

        fun style(button: com.google.android.material.button.MaterialButton, selected: Boolean) {
            button.backgroundTintList = ColorStateList.valueOf(
                if (selected) Color.rgb(86, 86, 91) else Color.rgb(34, 34, 38)
            )
            button.strokeColor = ColorStateList.valueOf(
                if (selected) Color.rgb(106, 106, 112) else Color.rgb(52, 52, 58)
            )
            button.setTextColor(Color.WHITE)
        }
        style(binding.btnModeManual, manualSelected)
        style(binding.btnModeAuto, autoSelected)
        binding.modeIndicatorManual.setBackgroundColor(if (manualSelected) Color.WHITE else Color.TRANSPARENT)
        binding.modeIndicatorAuto.setBackgroundColor(if (autoSelected) Color.WHITE else Color.TRANSPARENT)
    }

    private fun setupGroupPager() {
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.adapter = groupPagerAdapter
        // Horizontal gestures belong to Auto/Manual; subscription changes happen by tapping tabs.
        binding.viewPager.isUserInputEnabled = false
    }

    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    moveTaskToBack(false)
                }
            }
        })
    }

    private fun setupActions() {
        binding.fabAuto.setOnClickListener { smartConnectAndStart() }
        binding.btnSmartConnect.setOnClickListener { smartConnectAndStart() }
        binding.fab.setOnClickListener { handleManualFabAction() }
        binding.tvAutoPing.setOnClickListener { handlePingClick() }
        binding.manualSelectedRow.setOnClickListener { handlePingClick() }
    }

    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { content ->
            if (mainViewModel.isRunning.value == true && !smartConnecting) {
                lastConnectedPing = Regex("(\\d+)").find(content.orEmpty())?.groupValues?.getOrNull(1)
            }
            refreshSelectedServerUi()
        }
        mainViewModel.updateListAction.observe(this) {
            ensureSelectedServerForCurrentSubscription()
            refreshSelectedServerUi()
        }
        mainViewModel.realPingFinishedAction.observe(this) {
            refreshSelectedServerUi()
            refreshSubscriptionCard()
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            if (isRunning == true) {
                manualConnecting = false
                smartConnecting = false
                smartConnectionFailed = false
                smartCountdownSeconds = 0
                MobileTinaSessionLimiter.schedule(this)
            } else {
                MobileTinaSessionLimiter.cancel(this)
                lastConnectedPing = null
            }
            refreshSelectedServerUi()
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupPagerAdapter.update(groups)
        tabMediator?.detach()

        if (groups.isEmpty()) {
            binding.tabGroup.visibility = View.GONE
            refreshSelectedServerUi()
            return
        }
        binding.tabGroup.visibility = View.VISIBLE

        tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
            val group = groupPagerAdapter.groups[position]
            val count = MmkvManager.decodeServerList(group.id).size
            val textView = TextView(this).apply {
                text = "${group.remarks} ($count)"
                setPadding(22, 14, 22, 14)
                maxLines = 1
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorTextPrimary))
            }
            installSecretHold(textView, group.id)
            tab.customView = textView
            tab.tag = group.id
        }.also { it.attach() }

        val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }.let { if (it >= 0) it else 0 }
        val targetGroup = groups[targetIndex]
        if (mainViewModel.subscriptionId != targetGroup.id) {
            mainViewModel.subscriptionIdChanged(targetGroup.id)
        }
        binding.viewPager.setCurrentItem(targetIndex, false)
        ensureSelectedServerForCurrentSubscription()
        refreshSubscriptionCard()
    }

    private fun ensureSelectedServerForCurrentSubscription() {
        val subId = mainViewModel.subscriptionId
        if (subId.isBlank()) return
        val guids = MmkvManager.decodeServerList(subId)
        if (guids.isEmpty()) return
        val selected = MmkvManager.getSelectServer()
        if (selected.isNullOrBlank() || selected !in guids) {
            MmkvManager.setSelectServer(guids.first())
        }
    }

    private fun installSecretHold(view: View, subscriptionId: String) {
        val handler = Handler(Looper.getMainLooper())
        var revealed = false
        val reveal = Runnable {
            revealed = true
            showSubscriptionSecret(subscriptionId)
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    revealed = false
                    handler.postDelayed(reveal, SUBSCRIPTION_REVEAL_HOLD_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(reveal)
            }
            revealed
        }
    }

    private fun showSubscriptionSecret(subscriptionId: String) {
        val subscription = MmkvManager.decodeSubscription(subscriptionId) ?: return
        if (subscription.url.isBlank()) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 12, 40, 12)
        }
        QRCodeDecoder.createQRCode(subscription.url)?.let { bitmap ->
            container.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(-1, 520)
            })
        }
        container.addView(Button(this).apply {
            setText(R.string.mobiletina_copy_subscription_link)
            setOnClickListener { Utils.setClipboard(this@MainActivity, subscription.url) }
        })
        container.addView(Button(this).apply {
            setText(R.string.mobiletina_copy_all_configs)
            setOnClickListener { MobileTinaHiddenShareManager.copyAllConfigs(this@MainActivity, subscriptionId) }
        })

        AlertDialog.Builder(this)
            .setTitle(subscription.remarks.ifBlank { getString(R.string.mobiletina_subscription_secret_title) })
            .setView(container)
            .setNegativeButton(R.string.mobiletina_close, null)
            .show()
    }

    private fun handleManualFabAction() {
        if (mainViewModel.isRunning.value == true) {
            manualConnecting = false
            V2RayServiceManager.stopVService(this)
            return
        }
        if (smartConnecting || manualConnecting) return

        manualConnecting = true
        lastConnectedPing = null
        refreshSelectedServerUi()
        requestVpnPermissionAndStart(false)
    }

    private fun smartConnectAndStart() {
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
            clearSmartConnectState()
            return
        }
        if (smartConnecting) {
            cancelSmartConnect()
            return
        }

        smartConnecting = true
        lastConnectedPing = null
        smartConnectionFailed = false
        smartCountdownSeconds = 0
        refreshSelectedServerUi()

        smartConnectJob?.cancel()
        smartConnectJob = lifecycleScope.launch {
            // The complete Smart Connect selection phase is capped at six seconds.
            // A subscription refresh may get a brief grace period, but it no longer adds an
            // independent 8-second wait before the Real Ping window starts.
            val smartDeadline = SystemClock.elapsedRealtime() + SMART_CONNECT_TIMEOUT_MS
            val refreshGrace = (smartDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L).coerceAtMost(750L)
            if (refreshGrace > 0L) {
                withTimeoutOrNull(refreshGrace) {
                    while (subscriptionRefreshing && isActive) delay(50L)
                }
            }

            val groups = mainViewModel.getSubscriptions(this@MainActivity)
            if (mainViewModel.subscriptionId.isBlank()) {
                val first = groups.firstOrNull()
                if (first != null) mainViewModel.subscriptionIdChanged(first.id)
            } else {
                mainViewModel.reloadServerList()
            }
            ensureSelectedServerForCurrentSubscription()
            delay(100L)

            val serverGuids = mainViewModel.currentServerGuids()
            if (serverGuids.isEmpty()) {
                markSmartConnectFailed()
                toast(R.string.title_file_chooser)
                return@launch
            }

            if (serverGuids.size == 1) {
                MmkvManager.setSelectServer(serverGuids.first())
                refreshSelectedServerUi()
                requestVpnPermissionAndStart(true)
                return@launch
            }

            val generation = mainViewModel.realPingGeneration
            val remainingForPing = (smartDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (remainingForPing <= 0L) {
                markSmartConnectFailed()
                return@launch
            }
            smartCountdownSeconds = ceil(remainingForPing / 1000.0).toInt().coerceIn(1, SMART_CONNECT_TIMEOUT_SECONDS)
            refreshSelectedServerUi()

            val countdownJob = launch {
                while (isActive) {
                    val remaining = smartDeadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) break
                    smartCountdownSeconds = ceil(remaining / 1000.0).toInt().coerceAtLeast(1)
                    refreshSelectedServerUi()
                    delay(100L)
                }
            }

            // IMPORTANT: Smart Connect uses the native v2rayNG 2.2.6 Real Ping service.
            mainViewModel.testAllRealPing()
            val finished = withTimeoutOrNull(remainingForPing) {
                while (mainViewModel.realPingGeneration == generation) delay(40L)
                true
            } ?: false

            countdownJob.cancel()
            smartCountdownSeconds = 0
            if (!finished) mainViewModel.cancelRealPing()

            val best = serverGuids.mapNotNull { guid ->
                val ping = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
                if (ping > 0L) guid to ping else null
            }.minByOrNull { it.second }

            if (best == null) {
                markSmartConnectFailed()
                toast(R.string.mobiletina_no_working_server)
                return@launch
            }

            MmkvManager.setSelectServer(best.first)
            mainViewModel.sortByTestResults()
            mainViewModel.reloadServerList()
            refreshSelectedServerUi()
            requestVpnPermissionAndStart(true)
        }
    }

    private fun cancelSmartConnect() {
    smartConnectJob?.cancel()
    smartConnectJob = null
    mainViewModel.cancelRealPing()
    pendingSmartVpnPermission = false
    smartConnecting = false
    smartConnectionFailed = false
    smartCountdownSeconds = 0
    // Covers the tiny window where the service start was already dispatched but
    // the UI has not received the running broadcast yet.
    V2RayServiceManager.stopVService(this)
    refreshSelectedServerUi()
}

    private fun requestVpnPermissionAndStart(isSmartConnect: Boolean) {
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray(isSmartConnect)
            } else {
                pendingSmartVpnPermission = isSmartConnect
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray(isSmartConnect)
        }
    }

    private fun startV2Ray(isSmartConnect: Boolean = false) {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            if (isSmartConnect) {
                markSmartConnectFailed()
            } else {
                manualConnecting = false
                refreshSelectedServerUi()
            }
            toast(R.string.title_file_chooser)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) { }
        }
        V2RayServiceManager.startVService(this)
        if (isSmartConnect) {
            lifecycleScope.launch {
                delay(6_000L)
                if (smartConnecting && mainViewModel.isRunning.value != true) markSmartConnectFailed()
            }
        } else {
            lifecycleScope.launch {
                delay(6_000L)
                if (manualConnecting && mainViewModel.isRunning.value != true) {
                    manualConnecting = false
                    refreshSelectedServerUi()
                }
            }
        }
    }

    fun restartV2Ray() {
        lastConnectedPing = null
        if (mainViewModel.isRunning.value == true) V2RayServiceManager.stopVService(this)
        lifecycleScope.launch {
            delay(500L)
            startV2Ray(false)
        }
    }

    private fun handlePingClick() {
        val guid = MmkvManager.getSelectServer().orEmpty()
        if (guid.isBlank() || smartConnecting) return
        val running = mainViewModel.isRunning.value == true
        if (currentMode == MODE_MANUAL) {
            binding.tvManualPing.setText(R.string.mobiletina_testing)
        } else {
            binding.tvAutoPing.setText(R.string.mobiletina_testing)
        }
        if (running) {
            lastConnectedPing = null
            mainViewModel.testCurrentServerRealPing()
        } else {
            mainViewModel.testServerRealPing(guid)
        }
    }

    fun refreshSelectedServerUi() {
        val selectedGuid = MmkvManager.getSelectServer().orEmpty()
        val profile = selectedGuid.takeIf { it.isNotBlank() }?.let(MmkvManager::decodeServerConfig)
        val ping = selectedGuid.takeIf { it.isNotBlank() }
            ?.let { MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis } ?: 0L
        val running = mainViewModel.isRunning.value == true

        if (!running && currentMode == MODE_MANUAL && selectedGuid.isNotBlank()) {
            prewarmManualConnection(selectedGuid)
        }

        binding.tvAutoServer.text = profile?.remarks.orEmpty()
        binding.tvManualSelected.text = profile?.remarks.orEmpty()
        binding.tvManualPing.text = when {
            smartConnecting && smartCountdownSeconds > 0 ->
                getString(R.string.mobiletina_smart_countdown_short, smartCountdownSeconds)
            smartConnecting -> getString(R.string.mobiletina_testing)
            running && !lastConnectedPing.isNullOrBlank() -> lastConnectedPing
            else -> pingLabel(ping)
        }
        binding.manualSelectedRow.visibility = if (profile != null) View.VISIBLE else View.INVISIBLE

        val autoArtwork: Int
        val status: String
        when {
            running -> {
                autoArtwork = R.drawable.mt_auto_blue
                status = getString(R.string.mobiletina_status_connected)
            }
            smartConnecting -> {
                autoArtwork = R.drawable.mt_auto_yellow
                status = getString(R.string.mobiletina_status_connecting)
            }
            smartConnectionFailed -> {
                autoArtwork = R.drawable.mt_auto_red
                status = getString(R.string.mobiletina_status_failed)
            }
            else -> {
                autoArtwork = R.drawable.mt_auto_white
                status = getString(R.string.mobiletina_status_disconnected)
            }
        }

        binding.fabAuto.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        binding.fabAuto.imageTintList = null
        binding.fabAuto.setImageResource(autoArtwork)
        binding.tvAutoStatus.text = status
        val showAutoDetails = running || smartConnecting
        binding.tvAutoPing.visibility = if (showAutoDetails) View.VISIBLE else View.GONE
        binding.tvAutoServer.visibility = if (showAutoDetails) View.VISIBLE else View.GONE
        binding.tvAutoServer.text = if (showAutoDetails) profile?.remarks.orEmpty() else ""
        binding.tvAutoPing.text = when {
            smartConnecting && smartCountdownSeconds > 0 ->
                getString(R.string.mobiletina_smart_countdown_format, smartCountdownSeconds)
            smartConnecting -> getString(R.string.mobiletina_testing)
            running && !lastConnectedPing.isNullOrBlank() ->
                getString(R.string.mobiletina_ping_format, lastConnectedPing)
            running && ping > 0L ->
                getString(R.string.mobiletina_ping_format, ping.toString())
            running && ping < 0L -> getString(R.string.mobiletina_ping_inactive)
            running -> getString(R.string.mobiletina_tap_for_ping)
            else -> ""
        }

        binding.fab.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        binding.fab.imageTintList = null
        // User-supplied artwork: VPN off = stop, VPN on = fab.
        binding.fab.setImageResource(
            if (running) R.drawable.mt_manual_fab else R.drawable.mt_manual_stop
        )
        refreshSubscriptionCard()
    }


    private fun prewarmManualConnection(guid: String) {
        if (guid.isBlank() || guid == manualPrewarmGuid) return
        manualPrewarmGuid = guid
        manualPrewarmJob?.cancel()
        manualPrewarmJob = lifecycleScope.launch(Dispatchers.IO) {
            // Move one-time initialization/config work out of the tap-to-connect critical path.
            runCatching { V2RayServiceManager.isRunning() }
            runCatching { com.v2ray.ang.service.TProxyService.preloadNative() }
            // Warm-up only. The real config is rebuilt again at connect time,
            // so configuration and settings freshness are preserved.
            runCatching {
                com.v2ray.ang.handler.V2rayConfigManager.getV2rayConfig(
                    applicationContext,
                    guid
                )
            }
        }
    }

    private fun pingLabel(ping: Long): String = when {
        ping > 0L -> ping.toString()
        ping < 0L -> getString(R.string.mobiletina_ping_inactive)
        else -> getString(R.string.mobiletina_ping_unknown)
    }

    private fun refreshSubscriptionCard() {
        val item = MmkvManager.decodeSubscription(mainViewModel.subscriptionId)
        if (item == null) {
            binding.subscriptionCard.visibility = View.GONE
            return
        }

        val total = (item.trafficTotalBytes ?: 0L).coerceAtLeast(0L)
        val used = ((item.trafficUploadBytes ?: 0L) + (item.trafficDownloadBytes ?: 0L)).coerceAtLeast(0L)
        val expire = (item.expireEpochSeconds ?: 0L).coerceAtLeast(0L)
        if (total <= 0L && expire <= 0L) {
            binding.subscriptionCard.visibility = View.GONE
            return
        }

        binding.subscriptionCard.visibility = View.VISIBLE
        binding.tvSubscriptionName.text = item.remarks
        if (total > 0L) {
            val progress = ((used.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            binding.subscriptionProgress.progress = progress
            binding.subscriptionProgress.visibility = View.VISIBLE
            binding.tvSubscriptionUsage.text = getString(
                R.string.mobiletina_subscription_usage_compact,
                formatBytes(used), formatBytes(total)
            )
            binding.tvSubscriptionUsage.visibility = View.VISIBLE
        } else {
            binding.subscriptionProgress.visibility = View.GONE
            binding.tvSubscriptionUsage.visibility = View.INVISIBLE
        }

        if (expire > 0L) {
            val days = ceil(((expire * 1000L - System.currentTimeMillis()).coerceAtLeast(0L)) / 86_400_000.0).toLong()
            binding.tvSubscriptionDays.text = getString(R.string.mobiletina_subscription_days_remaining, days)
            binding.tvSubscriptionDays.visibility = View.VISIBLE
        } else {
            binding.tvSubscriptionDays.visibility = View.INVISIBLE
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = -1
        do {
            value /= 1024.0
            i++
        } while (value >= 1024.0 && i < units.lastIndex)
        return String.format(java.util.Locale.US, "%.1f %s", value, units[i])
    }

    private fun clearSmartConnectState() {
        smartConnecting = false
        smartConnectionFailed = false
        smartCountdownSeconds = 0
        refreshSelectedServerUi()
    }

    private fun markSmartConnectFailed() {
        smartConnecting = false
        smartConnectionFailed = true
        smartCountdownSeconds = 0
        refreshSelectedServerUi()
    }

    override fun onResume() {
        super.onResume()
        MobileTinaExpiryManager.recoverPending(this)
        setupGroupTab()
        ensureSelectedServerForCurrentSubscription()
        refreshSelectedServerUi()
        updateSubscriptionOnResume()
    }

    /** Network subscription refreshes belong to the resumed/visible activity lifecycle. */
    private fun updateSubscriptionOnResume() {
        if (!hasInternetConnection()) {
            toast(R.string.mobiletina_enable_internet)
            return
        }
        if (!firstRunPrefs.getBoolean(FIRST_RUN_COMPLETED, false)) return

        val now = SystemClock.elapsedRealtime()
        if (!subscriptionRefreshing && now - lastSubscriptionRefreshAt >= SUBSCRIPTION_REFRESH_GUARD_MS) {
            lastSubscriptionRefreshAt = now
            refreshSubscriptionsSilently()
        }
    }

    private fun refreshSubscriptionsSilently() {
        if (subscriptionRefreshing) return
        subscriptionRefreshing = true
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.updateEverySubscription()
            MobileTinaSubscriptionInfo.refreshAll()
            withContext(Dispatchers.Main) {
                normalizeSubscriptionNames()
                setupGroupTab()
                mainViewModel.reloadServerList()
                ensureSelectedServerForCurrentSubscription()
                subscriptionRefreshing = false
                binding.progressBar.visibility = View.INVISIBLE
                refreshSelectedServerUi()
            }
        }
    }

    fun importConfigViaSub(showResultToast: Boolean = true): Boolean {
        if (subscriptionRefreshing) return false
        subscriptionRefreshing = true
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateEverySubscription()
            MobileTinaSubscriptionInfo.refreshAll()
            withContext(Dispatchers.Main) {
                if (showResultToast) {
                    if (result.successCount + result.failureCount + result.skipCount == 0) {
                        toast(R.string.title_update_subscription_no_subscription)
                    } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                        toast(getString(R.string.title_update_config_count, result.configCount))
                    } else {
                        toast(getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                }
                normalizeSubscriptionNames()
                setupGroupTab()
                mainViewModel.reloadServerList()
                ensureSelectedServerForCurrentSubscription()
                subscriptionRefreshing = false
                binding.progressBar.visibility = View.INVISIBLE
                refreshSelectedServerUi()
            }
        }
        return true
    }

    private fun normalizeSubscriptionNames() {
        MmkvManager.decodeSubscriptions().forEach { cache ->
            if (cache.guid == AppConfig.DEFAULT_SUBSCRIPTION_ID) return@forEach
            val remarks = cache.subscription.remarks.trim()
            if (remarks.isBlank() || remarks.equals("import sub", ignoreCase = true)) {
                cache.subscription.remarks = DEFAULT_SUBSCRIPTION_NAME
                MmkvManager.encodeSubscription(cache.guid, cache.subscription)
            }
        }
    }

    private fun hasInternetConnection(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun handleFirstRunPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            requestFirstRunVpnPermissionOnly()
        } else {
            requestFirstRunCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun requestFirstRunVpnPermissionOnly() {
        if (!SettingsManager.isVpnMode()) {
            markFirstRunPermissionCompleted()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) markFirstRunPermissionCompleted() else requestFirstRunVpnPermission.launch(intent)
    }

    private fun markFirstRunPermissionCompleted() {
        firstRunPrefs.edit().putBoolean(FIRST_RUN_COMPLETED, true).apply()
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) { }
        // Subscription updating is intentionally deferred to onResume.
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode()
            true
        }
        R.id.import_clipboard -> {
            importClipboard()
            true
        }
        R.id.import_local -> {
            importConfigLocal()
            true
        }
        R.id.import_manually_policy_group -> {
            importManually(EConfigType.POLICYGROUP.value)
            true
        }
        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }
        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }
        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }
        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }
        R.id.import_manually_http -> {
            importManually(EConfigType.HTTP.value)
            true
        }
        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }
        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value)
            true
        }
        R.id.import_manually_hysteria2 -> {
            importManually(EConfigType.HYSTERIA2.value)
            true
        }
        R.id.service_restart -> {
            restartV2Ray()
            true
        }
        R.id.mobiletina_locate_selected -> {
            locateSelectedServer()
            true
        }
        R.id.ping_all -> {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllTcping()
            true
        }
        R.id.real_ping_all -> {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllRealPing()
            true
        }
        R.id.sort_by_test_results -> {
            sortByTestResults()
            true
        }
        R.id.sub_update -> {
            importConfigViaSub()
            true
        }
        R.id.del_all_config -> {
            delAllConfig()
            true
        }
        R.id.del_duplicate_config -> {
            delDuplicateConfig()
            true
        }
        R.id.del_invalid_config -> {
            delInvalidConfig()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType: Int) {
        val intent = if (createConfigType == EConfigType.POLICYGROUP.value) {
            Intent()
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerGroupActivity::class.java)
        } else {
            Intent()
                .putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerActivity::class.java)
        }
        requestActivityLauncher.launch(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (!scanResult.isNullOrBlank()) importBatchConfig(scanResult)
        }
    }

    private fun importClipboard(): Boolean {
        return try {
            importBatchConfig(Utils.getClipboard(this))
            true
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            toastError(R.string.toast_failure)
            false
        }
    }

    private fun importConfigLocal(): Boolean {
        return try {
            showFileChooser()
            true
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import config from local file", e)
            toastError(R.string.toast_failure)
            false
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri != null) readContentFromUri(uri)
        }
    }

    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                importBatchConfig(input?.bufferedReader()?.readText())
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to read config file", e)
            toastError(R.string.toast_failure)
        }
    }

    private fun importBatchConfig(raw: String?) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val normalized = MobileTinaImportNormalizer.normalize(raw)
                MobileTinaExpiryManager.scheduleFromImportedText(this@MainActivity, normalized)
                val (count, countSub) = AngConfigManager.importBatchConfig(normalized, mainViewModel.subscriptionId, true)
                if (countSub > 0) MobileTinaSubscriptionInfo.refreshAll()
                withContext(Dispatchers.Main) {
                    normalizeSubscriptionNames()
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                        }
                        countSub > 0 -> {
                            setupGroupTab()
                            // A QR subscription should be usable immediately without restarting the app.
                            if (hasInternetConnection()) refreshSubscriptionsSilently()
                        }
                        else -> toastError(R.string.toast_failure)
                    }
                    binding.progressBar.visibility = View.INVISIBLE
                    ensureSelectedServerForCurrentSubscription()
                    refreshSelectedServerUi()
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to import batch config", e)
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    binding.progressBar.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun locateSelectedServer() {
        if (currentMode != MODE_MANUAL) setMode(MODE_MANUAL)
        val selected = MmkvManager.getSelectServer().orEmpty()
        if (selected.isBlank()) {
            toast(R.string.title_file_chooser)
            return
        }
        val subId = MmkvManager.decodeServerConfig(selected)?.subscriptionId.orEmpty()
        val groups = mainViewModel.getSubscriptions(this)
        val index = groups.indexOfFirst { it.id == subId }
        if (index >= 0) {
            mainViewModel.subscriptionIdChanged(groups[index].id)
            binding.viewPager.setCurrentItem(index, false)
        }
        binding.viewPager.postDelayed({
            supportFragmentManager.fragments
                .filterIsInstance<GroupServerFragment>()
                .firstOrNull { it.isVisible }
                ?.scrollToSelectedServer()
        }, 180L)
    }

    private fun delAllConfig() {
        AlertDialog.Builder(this)
            .setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeAllServer()
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        setupGroupTab()
                        toast(getString(R.string.title_del_config_count, ret))
                        binding.progressBar.visibility = View.INVISIBLE
                        refreshSelectedServerUi()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun delDuplicateConfig() {
        AlertDialog.Builder(this)
            .setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeDuplicateServer()
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_duplicate_config_count, ret))
                        binding.progressBar.visibility = View.INVISIBLE
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun delInvalidConfig() {
        AlertDialog.Builder(this)
            .setMessage(R.string.del_invalid_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeInvalidServer()
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_config_count, ret))
                        binding.progressBar.visibility = View.INVISIBLE
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sortByTestResults() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            withContext(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                binding.progressBar.visibility = View.INVISIBLE
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mobiletina_store_about -> startActivity(Intent(this, MobileTinaStoreAboutActivity::class.java))
            R.id.per_app_proxy_settings -> requestActivityLauncher.launch(Intent(this, PerAppProxyActivity::class.java))
            R.id.routing_setting -> requestActivityLauncher.launch(Intent(this, RoutingSettingActivity::class.java))
            R.id.user_asset_setting -> requestActivityLauncher.launch(Intent(this, UserAssetActivity::class.java))
            R.id.settings -> requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java))
            R.id.promotion -> Utils.openUri(this, "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}")
            R.id.logcat -> startActivity(Intent(this, LogcatActivity::class.java))
            R.id.check_for_update -> startActivity(Intent(this, CheckUpdateActivity::class.java))
            R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
            R.id.mobiletina_reset -> confirmResetVpn()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun confirmResetVpn() {
        AlertDialog.Builder(this)
            .setTitle(R.string.mobiletina_reset_title)
            .setMessage(R.string.mobiletina_reset_message)
            .setPositiveButton(R.string.mobiletina_reset_confirm) { _, _ ->
                MobileTinaResetManager.reset(this)
                clearSmartConnectState()
                setupGroupTab()
                mainViewModel.reloadServerList()
                refreshSelectedServerUi()
                toast(R.string.mobiletina_reset_done)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        tabMediator?.detach()
        super.onDestroy()
    }

    companion object {
        private const val MODE_AUTO = 0
        private const val MODE_MANUAL = 1
        private const val FIRST_RUN_COMPLETED = "permissions_completed"
        private const val SUBSCRIPTION_REFRESH_GUARD_MS = 30_000L
        private const val SUBSCRIPTION_REVEAL_HOLD_MS = 10_000L
        private const val SMART_CONNECT_TIMEOUT_SECONDS = 6
        private const val SMART_CONNECT_TIMEOUT_MS = 6_000L
        private const val DEFAULT_SUBSCRIPTION_NAME = "instagram : mobile.tina"
    }
}

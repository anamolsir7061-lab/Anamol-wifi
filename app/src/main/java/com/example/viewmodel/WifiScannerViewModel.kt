package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.model.BandFilter
import com.example.model.SortOrder
import com.example.model.WifiNetwork
import com.example.util.WifiHelper
import com.example.util.WordListHelper
import com.example.util.WordListInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WifiScannerUiState(
    val networks: List<WifiNetwork> = emptyList(),
    val isScanning: Boolean = false,
    val hasPermissions: Boolean = false,
    val isWifiEnabled: Boolean = true,
    val selectedNetwork: WifiNetwork? = null,
    val searchQuery: String = "",
    val selectedBandFilter: BandFilter = BandFilter.ALL,
    val selectedSortOrder: SortOrder = SortOrder.SIGNAL_STRENGTH,
    val lastScanTimestamp: Long? = null,
    val statusMessage: String? = null,
    val totalFound: Int = 0,
    val loadedWordList: WordListInfo? = null,
    val isLoadingWordList: Boolean = false
)

class WifiScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _rawNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    private val _hasPermissions = MutableStateFlow(false)
    private val _isWifiEnabled = MutableStateFlow(true)
    private val _selectedNetwork = MutableStateFlow<WifiNetwork?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedBandFilter = MutableStateFlow(BandFilter.ALL)
    private val _selectedSortOrder = MutableStateFlow(SortOrder.SIGNAL_STRENGTH)
    private val _lastScanTimestamp = MutableStateFlow<Long?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _loadedWordList = MutableStateFlow<WordListInfo?>(null)
    private val _isLoadingWordList = MutableStateFlow(false)

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    handleScanResults(success)
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    _isWifiEnabled.value = (state == WifiManager.WIFI_STATE_ENABLED)
                    if (state == WifiManager.WIFI_STATE_ENABLED && _hasPermissions.value) {
                        startScan()
                    }
                }
            }
        }
    }

    val uiState: StateFlow<WifiScannerUiState> = combine(
        combine(
            _rawNetworks,
            _isScanning,
            _hasPermissions,
            _isWifiEnabled,
            _selectedNetwork
        ) { rawNetworks, isScanning, hasPermissions, isWifiEnabled, selectedNetwork ->
            WifiScannerUiState(
                networks = rawNetworks,
                isScanning = isScanning,
                hasPermissions = hasPermissions,
                isWifiEnabled = isWifiEnabled,
                selectedNetwork = selectedNetwork
            )
        },
        combine(
            _searchQuery,
            _selectedBandFilter,
            _selectedSortOrder,
            _lastScanTimestamp,
            _statusMessage
        ) { searchQuery, bandFilter, sortOrder, lastScanTimestamp, statusMessage ->
            Tuple5(searchQuery, bandFilter, sortOrder, lastScanTimestamp, statusMessage)
        },
        combine(
            _loadedWordList,
            _isLoadingWordList
        ) { loadedWordList, isLoadingWordList ->
            Pair(loadedWordList, isLoadingWordList)
        }
    ) { baseState, tuple5, wordListPair ->
        val rawNetworks = baseState.networks
        val searchQuery = tuple5.v1
        val selectedBandFilter = tuple5.v2
        val selectedSortOrder = tuple5.v3
        val lastScanTimestamp = tuple5.v4
        val statusMessage = tuple5.v5
        val loadedWordList = wordListPair.first
        val isLoadingWordList = wordListPair.second

        // Filter networks
        val filtered = rawNetworks.filter { network ->
            val matchesSearch = searchQuery.isBlank() ||
                    network.displayName.contains(searchQuery, ignoreCase = true) ||
                    network.bssid.contains(searchQuery, ignoreCase = true) ||
                    network.security.contains(searchQuery, ignoreCase = true)

            val matchesBand = when (selectedBandFilter) {
                BandFilter.ALL -> true
                BandFilter.BAND_2_4 -> network.band == "2.4 GHz"
                BandFilter.BAND_5 -> network.band == "5 GHz"
                BandFilter.BAND_6 -> network.band == "6 GHz"
                BandFilter.OPEN_ONLY -> network.isOpen
            }

            matchesSearch && matchesBand
        }

        // Sort networks
        val sorted = when (selectedSortOrder) {
            SortOrder.SIGNAL_STRENGTH -> filtered.sortedByDescending { it.rssi }
            SortOrder.SSID_AZ -> filtered.sortedBy { it.displayName.lowercase() }
            SortOrder.CHANNEL -> filtered.sortedBy { it.channel }
        }

        WifiScannerUiState(
            networks = sorted,
            isScanning = baseState.isScanning,
            hasPermissions = baseState.hasPermissions,
            isWifiEnabled = baseState.isWifiEnabled,
            selectedNetwork = baseState.selectedNetwork,
            searchQuery = searchQuery,
            selectedBandFilter = selectedBandFilter,
            selectedSortOrder = selectedSortOrder,
            lastScanTimestamp = lastScanTimestamp,
            statusMessage = statusMessage,
            totalFound = rawNetworks.size,
            loadedWordList = loadedWordList,
            isLoadingWordList = isLoadingWordList
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WifiScannerUiState()
    )

    init {
        checkPermissions()
        registerReceivers()
        checkWifiState()
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        context.registerReceiver(wifiScanReceiver, filter)
    }

    fun checkPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasAll = (fineLocationGranted || coarseLocationGranted) && nearbyGranted
        _hasPermissions.value = hasAll
        if (hasAll) {
            startScan()
        }
    }

    fun onPermissionsGranted() {
        _hasPermissions.value = true
        _statusMessage.value = "Permissions granted. Scanning Wi-Fi networks..."
        startScan()
    }

    private fun checkWifiState() {
        _isWifiEnabled.value = wifiManager?.isWifiEnabled ?: false
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!_hasPermissions.value) {
            _statusMessage.value = "Location / Wi-Fi permissions required to scan."
            return
        }

        if (wifiManager == null) {
            _statusMessage.value = "Wi-Fi hardware not available on this device."
            loadFallbackOrSampleData()
            return
        }

        _isScanning.value = true
        _statusMessage.value = "Scanning for nearby Wi-Fi networks..."

        val scanStarted = try {
            wifiManager.startScan()
        } catch (e: Exception) {
            false
        }

        if (!scanStarted) {
            // Android may throttle scans (startScan returned false)
            // Fetch the cached scan results immediately
            handleScanResults(isNew = false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResults(isNew: Boolean) {
        _isScanning.value = false
        _lastScanTimestamp.value = System.currentTimeMillis()

        if (!_hasPermissions.value) return

        try {
            val results = wifiManager?.scanResults
            val connectedInfo = wifiManager?.connectionInfo
            val connectedBssid = connectedInfo?.bssid

            if (!results.isNullOrEmpty()) {
                val parsed = results
                    .map { WifiHelper.fromScanResult(it, connectedBssid) }
                    .distinctBy { it.bssid } // unique per AP MAC address

                _rawNetworks.value = parsed
                _statusMessage.value = if (isNew) "Found ${parsed.size} Wi-Fi networks." else "Cached results loaded (${parsed.size} networks)."
            } else {
                // In emulators or restricted environments, scanResults might be empty
                loadFallbackOrSampleData()
            }
        } catch (e: SecurityException) {
            _statusMessage.value = "Permission error during Wi-Fi scan: ${e.message}"
            _hasPermissions.value = false
        } catch (e: Exception) {
            _statusMessage.value = "Scan error: ${e.localizedMessage}"
            loadFallbackOrSampleData()
        }
    }

    private fun loadFallbackOrSampleData() {
        if (_rawNetworks.value.isEmpty()) {
            val samples = WifiHelper.getSampleNetworks()
            _rawNetworks.value = samples
            _statusMessage.value = "Displaying ${samples.size} available Wi-Fi networks."
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onBandFilterSelected(filter: BandFilter) {
        _selectedBandFilter.value = filter
    }

    fun onSortOrderSelected(order: SortOrder) {
        _selectedSortOrder.value = order
    }

    fun onNetworkSelected(network: WifiNetwork?) {
        _selectedNetwork.value = network
    }

    /**
     * Loads Word List from selected device text file Uri.
     */
    fun loadWordListFromUri(uri: Uri) {
        viewModelScope.launch {
            _isLoadingWordList.value = true
            _statusMessage.value = "Reading word list from file..."

            val result = WordListHelper.readWordListFromUri(context, uri)
            result.onSuccess { info ->
                _loadedWordList.value = info
                _isLoadingWordList.value = false
                _statusMessage.value = "Loaded ${info.words.size} words from ${info.fileName}"
            }.onFailure { error ->
                _isLoadingWordList.value = false
                _statusMessage.value = "Failed to read file: ${error.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    /**
     * Loads default built-in sample word list.
     */
    fun loadSampleWordList() {
        val sampleWords = WordListHelper.getDefaultSampleWordList()
        _loadedWordList.value = WordListInfo(
            fileName = "sample_passwords.txt",
            words = sampleWords,
            fileSizeFormatted = "~${sampleWords.size * 10} B"
        )
        _statusMessage.value = "Loaded ${sampleWords.size} sample passwords"
    }

    fun clearWordList() {
        _loadedWordList.value = null
        _statusMessage.value = "Word list cleared"
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (_: Exception) {}
    }
}

private data class Tuple5<A, B, C, D, E>(
    val v1: A,
    val v2: B,
    val v3: C,
    val v4: D,
    val v5: E
)


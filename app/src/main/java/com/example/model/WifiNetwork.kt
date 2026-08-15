package com.example.model

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Data model representing a scanned Wi-Fi network with its technical parameters.
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val level: Int, // 0 to 4 signal bars
    val frequency: Int, // in MHz
    val channel: Int,
    val band: String, // "2.4 GHz", "5 GHz", "6 GHz"
    val security: String, // "WPA3", "WPA2-PSK", "WPA-PSK", "WEP", "OPEN", etc.
    val capabilities: String,
    val channelWidth: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false
) {
    val displayName: String
        get() = if (ssid.isBlank() || ssid == "<unknown ssid>") "Hidden Network ($bssid)" else ssid

    val isOpen: Boolean
        get() = security.equals("OPEN", ignoreCase = true) || !capabilities.contains("WPA") && !capabilities.contains("WEP")

    val signalPercentage: Int
        get() {
            // Convert dBm to percentage (-100 dBm to -50 dBm typical range)
            return when {
                rssi <= -100 -> 0
                rssi >= -50 -> 100
                else -> (2 * (rssi + 100)).coerceIn(0, 100)
            }
        }

    val estimatedDistanceMeters: Double
        get() {
            // Free-space path loss formula approximation
            val exp = (27.55 - (20 * log10(frequency.toDouble())) + abs(rssi)) / 20.0
            return 10.0.pow(exp)
        }
}

enum class BandFilter(val title: String) {
    ALL("All Bands"),
    BAND_2_4("2.4 GHz"),
    BAND_5("5 GHz"),
    BAND_6("6 GHz"),
    OPEN_ONLY("Open Only")
}

enum class SortOrder(val title: String) {
    SIGNAL_STRENGTH("Signal (Strongest)"),
    SSID_AZ("Name (A to Z)"),
    CHANNEL("Channel")
}

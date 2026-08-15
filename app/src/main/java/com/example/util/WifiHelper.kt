package com.example.util

import android.net.wifi.ScanResult
import android.os.Build
import com.example.model.WifiNetwork

object WifiHelper {

    /**
     * Converts frequency in MHz to Wi-Fi channel number.
     */
    fun frequencyToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2412) / 5 + 1
            freq in 5170..5825 -> (freq - 5170) / 5 + 34
            freq in 5925..7125 -> (freq - 5925) / 5 + 1 // Wi-Fi 6E / 7
            else -> 0
        }
    }

    /**
     * Identifies frequency band.
     */
    fun frequencyToBand(freq: Int): String {
        return when {
            freq in 2400..2499 -> "2.4 GHz"
            freq in 5000..5899 -> "5 GHz"
            freq >= 5900 -> "6 GHz"
            else -> "Unknown Band"
        }
    }

    /**
     * Extracts security type summary from ScanResult capabilities.
     */
    fun parseSecurity(capabilities: String): String {
        return when {
            capabilities.contains("SAE") || capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") && capabilities.contains("WPA-PSK") -> "WPA/WPA2-PSK"
            capabilities.contains("WPA2") -> "WPA2-PSK"
            capabilities.contains("WPA") -> "WPA-PSK"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("EAP") -> "WPA-Enterprise"
            capabilities.contains("OWE") -> "Enhanced Open (OWE)"
            capabilities.isBlank() || capabilities == "[ESS]" -> "OPEN"
            else -> "OPEN"
        }
    }

    /**
     * Extracts channel width description from ScanResult.
     */
    fun parseChannelWidth(scanResult: ScanResult): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (scanResult.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
                ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
                ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
                ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
                else -> "20 MHz"
            }
        } else {
            "20 MHz"
        }
    }

    /**
     * Calculates signal quality level (0 to 4).
     */
    fun calculateSignalLevel(rssi: Int): Int {
        return when {
            rssi >= -55 -> 4
            rssi >= -65 -> 3
            rssi >= -75 -> 2
            rssi >= -85 -> 1
            else -> 0
        }
    }

    /**
     * Converts a raw ScanResult into our unified WifiNetwork model.
     */
    fun fromScanResult(scanResult: ScanResult, connectedBssid: String? = null): WifiNetwork {
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            scanResult.wifiSsid?.toString()?.removePrefix("\"")?.removeSuffix("\"") ?: scanResult.SSID
        } else {
            scanResult.SSID
        }

        val cleanSsid = if (ssid.isNullOrBlank()) "" else ssid
        val bssid = scanResult.BSSID ?: "00:00:00:00:00:00"
        val channel = frequencyToChannel(scanResult.frequency)
        val band = frequencyToBand(scanResult.frequency)
        val security = parseSecurity(scanResult.capabilities)
        val channelWidth = parseChannelWidth(scanResult)
        val level = calculateSignalLevel(scanResult.level)

        return WifiNetwork(
            ssid = cleanSsid,
            bssid = bssid,
            rssi = scanResult.level,
            level = level,
            frequency = scanResult.frequency,
            channel = channel,
            band = band,
            security = security,
            capabilities = scanResult.capabilities ?: "",
            channelWidth = channelWidth,
            isConnected = connectedBssid != null && connectedBssid.equals(bssid, ignoreCase = true)
        )
    }

    /**
     * Provides sample Wi-Fi networks when running in environments without physical Wi-Fi hardware
     * (such as cloud emulator), allowing full UI verification.
     */
    fun getSampleNetworks(): List<WifiNetwork> {
        return listOf(
            WifiNetwork(
                ssid = "Home_Office_5G",
                bssid = "C4:EA:1D:88:42:1A",
                rssi = -48,
                level = 4,
                frequency = 5180,
                channel = 36,
                band = "5 GHz",
                security = "WPA3",
                capabilities = "[WPA3-SAE-CCMP][RSN-SAE-CCMP][ESS]",
                channelWidth = "80 MHz",
                isConnected = true
            ),
            WifiNetwork(
                ssid = "FiberNet_Guest_HighSpeed",
                bssid = "00:1A:2B:3C:4D:5E",
                rssi = -62,
                level = 3,
                frequency = 2437,
                channel = 6,
                band = "2.4 GHz",
                security = "WPA2-PSK",
                capabilities = "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]",
                channelWidth = "20 MHz"
            ),
            WifiNetwork(
                ssid = "Cafe_Public_Free_WiFi",
                bssid = "74:83:C2:11:99:FF",
                rssi = -68,
                level = 3,
                frequency = 5240,
                channel = 48,
                band = "5 GHz",
                security = "OPEN",
                capabilities = "[ESS]",
                channelWidth = "40 MHz"
            ),
            WifiNetwork(
                ssid = "SmartHome_IoT_Hub",
                bssid = "D8:0D:17:AB:33:10",
                rssi = -74,
                level = 2,
                frequency = 2412,
                channel = 1,
                band = "2.4 GHz",
                security = "WPA2-PSK",
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                channelWidth = "20 MHz"
            ),
            WifiNetwork(
                ssid = "TechCorp_Enterprise_Secure",
                bssid = "A0:B1:C2:D3:E4:F5",
                rssi = -54,
                level = 4,
                frequency = 5745,
                channel = 149,
                band = "5 GHz",
                security = "WPA-Enterprise",
                capabilities = "[WPA2-EAP-CCMP][RSN-EAP-CCMP][ESS]",
                channelWidth = "80 MHz"
            ),
            WifiNetwork(
                ssid = "Ultra_WiFi_6E_Turbo",
                bssid = "2C:30:33:AA:9F:8B",
                rssi = -58,
                level = 4,
                frequency = 6125,
                channel = 37,
                band = "6 GHz",
                security = "WPA3",
                capabilities = "[WPA3-SAE-CCMP][ESS]",
                channelWidth = "160 MHz"
            ),
            WifiNetwork(
                ssid = "", // Hidden Network
                bssid = "E4:8D:8C:91:02:44",
                rssi = -86,
                level = 1,
                frequency = 2462,
                channel = 11,
                band = "2.4 GHz",
                security = "WPA2-PSK",
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                channelWidth = "20 MHz"
            )
        )
    }
}

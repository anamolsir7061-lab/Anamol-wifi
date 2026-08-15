package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.WifiNetwork
import com.example.ui.components.WifiItemCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun wifi_item_screenshot() {
    val sampleNetwork = WifiNetwork(
      ssid = "Office_5G",
      bssid = "00:11:22:33:44:55",
      rssi = -52,
      level = 4,
      frequency = 5180,
      channel = 36,
      band = "5 GHz",
      security = "WPA3",
      capabilities = "[WPA3-SAE-CCMP][ESS]",
      channelWidth = "80 MHz",
      isConnected = true
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        WifiItemCard(network = sampleNetwork, onClick = {})
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

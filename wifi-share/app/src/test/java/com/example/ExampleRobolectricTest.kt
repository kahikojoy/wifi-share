package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("WiFi Share", appName)
    }

    @Test
    fun `welcome to dashboard and primary navigation tabs work cleanly`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("welcome_continue_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("toggle_share_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_wifi_info").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wifi_refresh_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_devices").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("refresh_clients_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_compatibility").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("refresh_compat_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_wifi_only_safety").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_hotspot_config_button"))
        composeTestRule.onNodeWithTag("settings_hotspot_config_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("hotspot_config_list")
            .performScrollToNode(hasTestTag("save_hotspot_config_button"))
        composeTestRule.onNodeWithTag("save_hotspot_config_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("topbar_qr_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("topbar_troubleshoot_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("run_diagnostics_button").assertIsDisplayed()
    }
}

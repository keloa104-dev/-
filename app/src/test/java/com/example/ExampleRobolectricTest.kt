package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Smart Puck", appName)
  }

  @Test
  fun `verify axis lock preferences default and update`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.PuckPreferences(context)
    
    // Default mapping: Horizontal = Brightness, Vertical = Volume (swapAxes = false)
    assertEquals(false, prefs.swapAxes)
    assertEquals(4, prefs.axisLockThresholdPx)

    // Updating swapAxes and threshold
    prefs.swapAxes = true
    assertEquals(true, prefs.swapAxes)
    assertEquals(true, prefs.swapAxesState.value)

    prefs.axisLockThresholdPx = 8
    assertEquals(8, prefs.axisLockThresholdPx)
    assertEquals(8, prefs.axisLockThresholdPxState.value)
  }
}

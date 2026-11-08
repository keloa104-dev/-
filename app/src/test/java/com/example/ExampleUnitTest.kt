package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSensitivityThresholdScaling() {
    val maxAbsX = 400f
    val minThresholdPx = 28f * 2.5f // ~70px
    val maxThresholdPx = (maxAbsX * 0.70f).coerceAtLeast(120f * 2.5f) // 300px

    // Low sensitivity (10%): requires larger drag to switch sides
    val sLow = 10
    val factorLow = (sLow - 10f) / 90f
    val thresholdLow = maxThresholdPx - factorLow * (maxThresholdPx - minThresholdPx)
    assertEquals(maxThresholdPx, thresholdLow, 0.01f)

    // High sensitivity (100%): requires minimal drag to switch sides
    val sHigh = 100
    val factorHigh = (sHigh - 10f) / 90f
    val thresholdHigh = maxThresholdPx - factorHigh * (maxThresholdPx - minThresholdPx)
    assertEquals(minThresholdPx, thresholdHigh, 0.01f)

    assertTrue(thresholdHigh < thresholdLow)
  }
}

package com.example

import com.example.data.model.CircumstanceContext
import com.example.data.model.TripDirection
import com.example.domain.kalman.AdaptiveRobustKalmanEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRobustKalmanEngineTest {

    private val engine = AdaptiveRobustKalmanEngine()

    @Test
    fun `same normal day and hour across distant months keep high similarity`() {
        val jan = CircumstanceContext(
            direction = TripDirection.IDA,
            minuteOfDay = 8 * 60,
            dayOfWeek = 1, // Lunes
            month = 1      // Enero
        )
        val jul = jan.copy(month = 7) // Julio (6 meses después)

        val sim = engine.compute4DSimilarity(jan, jul).similarity
        assertTrue("Expected high similarity across months, got $sim", sim > 0.70)
    }

    @Test
    fun `protest day barely affects normal day and vice versa`() {
        val normal = CircumstanceContext(
            direction = TripDirection.IDA,
            minuteOfDay = 8 * 60,
            dayOfWeek = 1,
            month = 5
        )
        val protest = normal.copy(hasProtest = true)

        val sim = engine.compute4DSimilarity(protest, normal).similarity
        assertTrue("Expected very low similarity between normal and protest, got $sim", sim < 0.15)
    }

    @Test
    fun `rainy day strongly affects rainy day but barely normal day`() {
        val rainyA = CircumstanceContext(
            direction = TripDirection.IDA,
            hasRain = true,
            minuteOfDay = 8 * 60,
            dayOfWeek = 2,
            month = 4
        )
        val rainyB = rainyA.copy(dayOfWeek = 3)
        val normal = rainyA.copy(hasRain = false)

        val simRainToRain = engine.compute4DSimilarity(rainyA, rainyB).similarity
        val simNormalToRain = engine.compute4DSimilarity(normal, rainyA).similarity

        assertTrue("Rain-to-rain ($simRainToRain) should be much higher than normal-to-rain ($simNormalToRain)", simRainToRain > 0.85 && simNormalToRain < 0.25)
    }

    @Test
    fun `monday similarity decreases from tuesday down to sunday`() {
        val monday = CircumstanceContext(TripDirection.IDA, minuteOfDay = 540, dayOfWeek = 1, month = 6)
        val simTue = engine.compute4DSimilarity(monday, monday.copy(dayOfWeek = 2)).similarity
        val simWed = engine.compute4DSimilarity(monday, monday.copy(dayOfWeek = 3)).similarity
        val simThu = engine.compute4DSimilarity(monday, monday.copy(dayOfWeek = 4)).similarity
        val simFri = engine.compute4DSimilarity(monday, monday.copy(dayOfWeek = 5)).similarity
        val simSat = engine.compute4DSimilarity(monday, monday.copy(dayOfWeek = 6)).similarity
        val simSun = engine.compute4DSimilarity(monday, monday.copy(dayOfWeek = 7)).similarity

        assertTrue(simTue > simWed)
        assertTrue(simWed > simThu)
        assertTrue(simThu > simFri)
        assertTrue(simFri > simSat)
        assertTrue(simSat > simSun)
    }

    @Test
    fun `dynamic threshold starts at zero and rises with data volume`() {
        assertEquals(0.0, engine.computeDynamicThreshold(0), 1e-6)
        assertEquals(0.0, engine.computeDynamicThreshold(3), 1e-6)
        val t10 = engine.computeDynamicThreshold(10)
        val t30 = engine.computeDynamicThreshold(30)
        assertTrue("Expected t10 ($t10) > 0 and t30 ($t30) > t10", t10 > 0.15 && t30 > t10)
    }

    @Test
    fun `huber m-estimator resists extreme outlier spike`() {
        val baseCtx = CircumstanceContext(TripDirection.IDA, minuteOfDay = 480, dayOfWeek = 1, month = 3)
        // 5 normal trips ~1800s (30 min), plus 1 extreme unflagged outlier 5400s (90 min)
        val observations = listOf(
            Triple(1L, 1800L, baseCtx),
            Triple(2L, 1780L, baseCtx),
            Triple(3L, 1820L, baseCtx),
            Triple(4L, 5400L, baseCtx), // anomaly!
            Triple(5L, 1810L, baseCtx),
            Triple(6L, 1795L, baseCtx)
        )
        val timestamps = observations.associate { it.first to (it.first * 86400_000L) }
        val pred = engine.predictFromObservations(observations, baseCtx, timestamps)

        assertTrue(pred.hasData)
        // Prediction should stay close to 1800s (~30 min), not pulled up to 2400s+
        assertTrue(
            "Expected robust prediction near 1800s, got ${pred.predictedDurationSec}s",
            pred.predictedDurationSec in 1700L..1950L
        )
        val outlierDiag = pred.diagnostics.first { it.tripId == 4L }
        assertTrue("Outlier should be downweighted by Huber M-estimator (weight=${outlierDiag.huberWeight})", outlierDiag.huberWeight < 0.5)
    }
}

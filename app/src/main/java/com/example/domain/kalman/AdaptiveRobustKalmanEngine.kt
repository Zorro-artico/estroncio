package com.example.domain.kalman

import com.example.data.model.CircumstanceContext
import com.example.data.model.SegmentRecordEntity
import com.example.data.model.SegmentTemplateEntity
import com.example.data.model.TripDirection
import com.example.data.model.TripRecordEntity
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class CircumstanceWeights(
    val extraordinaryWeight: Double = 4.0,
    val hourWeight: Double = 2.0,
    val dayOfWeekWeight: Double = 1.0,
    val monthWeight: Double = 0.25,
    val kernelSigma: Double = 0.28,
    val huberThresholdC: Double = 1.345,
    val maxPruningThreshold: Double = 0.62
)

data class SimilarityBreakdown(
    val dExtraordinary: Double,
    val dHour: Double,
    val dDayOfWeek: Double,
    val dMonth: Double,
    val distance4D: Double,
    val similarity: Double // 0.0 .. 1.0
)

data class DiagnosticObservation(
    val tripId: Long,
    val timestampMs: Long,
    val durationSec: Long,
    val context: CircumstanceContext,
    val similarity: Double,
    val distance4D: Double,
    val huberWeight: Double,
    val wasPruned: Boolean
)

data class KalmanPrediction(
    val hasData: Boolean,
    val predictedDurationSec: Long,
    val uncertaintySec: Long,
    val thresholdUsed: Double,
    val totalHistoryCount: Int,
    val retainedCount: Int,
    val ignoredCount: Int,
    val usedFallback: Boolean,
    val usedCrossDirectionFallback: Boolean,
    val diagnostics: List<DiagnosticObservation>
)

data class SegmentPrediction(
    val orderIndex: Int,
    val name: String,
    val prediction: KalmanPrediction,
    val cumulativeEtaMs: Long?
)

data class RoutePredictionSummary(
    val totalPrediction: KalmanPrediction,
    val segmentPredictions: List<SegmentPrediction>,
    val estimatedArrivalMs: Long?
)

class AdaptiveRobustKalmanEngine(
    private val weights: CircumstanceWeights = CircumstanceWeights()
) {

    /**
     * Calcula la distancia en el espacio de 4 dimensiones:
     * 1. Circunstancias extraordinarias (máxima jerarquía)
     * 2. Hora del día
     * 3. Día de la semana (Lunes cercano a Martes, lejano a Sábado y aún más a Domingo)
     * 4. Mes del año (peso mínimo)
     */
    fun compute4DSimilarity(
        historical: CircumstanceContext,
        target: CircumstanceContext
    ): SimilarityBreakdown {
        val dExt = computeExtraordinaryDistance(historical, target)
        val dHour = computeHourDistance(historical.minuteOfDay, target.minuteOfDay)
        val dDow = computeDayOfWeekDistance(historical.dayOfWeek, target.dayOfWeek)
        val dMonth = computeMonthDistance(historical.month, target.month)

        val wSum = weights.extraordinaryWeight +
            weights.hourWeight +
            weights.dayOfWeekWeight +
            weights.monthWeight

        val weightedSq = (
            weights.extraordinaryWeight * dExt.pow(2) +
                weights.hourWeight * dHour.pow(2) +
                weights.dayOfWeekWeight * dDow.pow(2) +
                weights.monthWeight * dMonth.pow(2)
            ) / wSum

        val distance4D = sqrt(weightedSq).coerceIn(0.0, 1.0)
        val similarity = exp(-(distance4D.pow(2)) / (2.0 * weights.kernelSigma.pow(2)))
            .coerceIn(0.0, 1.0)

        return SimilarityBreakdown(
            dExtraordinary = dExt,
            dHour = dHour,
            dDayOfWeek = dDow,
            dMonth = dMonth,
            distance4D = distance4D,
            similarity = similarity
        )
    }

    internal fun computeExtraordinaryDistance(
        a: CircumstanceContext,
        b: CircumstanceContext
    ): Double {
        val normCustomA = a.customIncident.trim().lowercase()
        val normCustomB = b.customIncident.trim().lowercase()

        if (!a.hasAnyExtraordinary && !b.hasAnyExtraordinary) return 0.0

        // Un día con manifestación o accidente casi no afecta a un día normal (y viceversa)
        if (a.hasAnyExtraordinary != b.hasAnyExtraordinary) {
            val active = if (a.hasAnyExtraordinary) a else b
            var maxSeverity = 0.0
            if (active.hasProtest) maxSeverity = max(maxSeverity, 0.96)
            if (active.hasAccident) maxSeverity = max(maxSeverity, 0.90)
            if (active.customIncident.isNotBlank()) maxSeverity = max(maxSeverity, 0.86)
            if (active.isVacation) maxSeverity = max(maxSeverity, 0.82)
            if (active.hasRain) maxSeverity = max(maxSeverity, 0.78)
            return maxSeverity
        }

        // Ambos tienen al menos una circunstancia extraordinaria: comparar similitud de conjuntos
        val tagsA = mutableSetOf<String>()
        val tagsB = mutableSetOf<String>()
        if (a.hasRain) tagsA.add("RAIN")
        if (b.hasRain) tagsB.add("RAIN")
        if (a.hasProtest) tagsA.add("PROTEST")
        if (b.hasProtest) tagsB.add("PROTEST")
        if (a.hasAccident) tagsA.add("ACCIDENT")
        if (b.hasAccident) tagsB.add("ACCIDENT")
        if (a.isVacation) tagsA.add("VACATION")
        if (b.isVacation) tagsB.add("VACATION")

        if (normCustomA.isNotEmpty() || normCustomB.isNotEmpty()) {
            if (normCustomA == normCustomB) {
                tagsA.add("CUSTOM_SAME")
                tagsB.add("CUSTOM_SAME")
            } else {
                if (normCustomA.isNotEmpty()) tagsA.add("CUSTOM_A")
                if (normCustomB.isNotEmpty()) tagsB.add("CUSTOM_B")
            }
        }

        val union = (tagsA union tagsB).size
        if (union == 0) return 0.0
        val intersection = (tagsA intersect tagsB).size
        val jaccardDist = 1.0 - (intersection.toDouble() / union.toDouble())
        return (jaccardDist * 0.90).coerceIn(0.0, 1.0)
    }

    internal fun computeHourDistance(minuteA: Int, minuteB: Int): Double {
        val diff = abs(minuteA - minuteB)
        val circular = min(diff, 1440 - diff)
        // 4 horas (240 min) satura la distancia horaria
        return (circular / 240.0).coerceIn(0.0, 1.0)
    }

    internal fun computeDayOfWeekDistance(dowA: Int, dowB: Int): Double {
        if (dowA == dowB) return 0.0
        val a = dowA.coerceIn(1, 7)
        val b = dowB.coerceIn(1, 7)
        val lo = min(a, b)
        val hi = max(a, b)

        // Ambos entre semana (1=Lunes .. 5=Viernes): Lunes-Martes pequeño, crece hasta Viernes
        if (hi <= 5) {
            return ((hi - lo) * 0.195).coerceIn(0.0, 0.78)
        }
        // Ambos fin de semana (6=Sábado, 7=Domingo): cercanos entre sí
        if (lo >= 6) {
            return 0.22
        }
        // Uno entre semana (1..5) y uno fin de semana (6 o 7)
        // Para Lunes(1): Sábado=0.92, Domingo=1.00 (se reduce desde Martes hasta Domingo)
        return if (hi == 6) {
            (0.92 - (lo - 1) * 0.045).coerceIn(0.72, 0.92)
        } else {
            (1.00 - (lo - 1) * 0.025).coerceIn(0.88, 1.00)
        }
    }

    internal fun computeMonthDistance(monthA: Int, monthB: Int): Double {
        val diff = abs(monthA - monthB)
        val circular = min(diff, 12 - diff)
        return (circular / 6.0).coerceIn(0.0, 1.0)
    }

    /**
     * Umbral dinámico de poda: inicia en 0% cuando hay pocos datos (<= 3)
     * y crece progresivamente conforme aumenta el historial.
     */
    fun computeDynamicThreshold(sampleCount: Int): Double {
        if (sampleCount <= 3) return 0.0
        return (weights.maxPruningThreshold * (1.0 - exp(-(sampleCount - 3) / 14.0)))
            .coerceIn(0.0, weights.maxPruningThreshold)
    }

    fun predictFromObservations(
        observations: List<Triple<Long, Long, CircumstanceContext>>, // (tripId, durationSec, ctx) + sorted chronologically
        targetContext: CircumstanceContext,
        timestampByTripId: Map<Long, Long> = emptyMap(),
        directionPenalty: Boolean = false
    ): KalmanPrediction {
        if (observations.isEmpty()) {
            return KalmanPrediction(
                hasData = false,
                predictedDurationSec = 0L,
                uncertaintySec = 0L,
                thresholdUsed = 0.0,
                totalHistoryCount = 0,
                retainedCount = 0,
                ignoredCount = 0,
                usedFallback = false,
                usedCrossDirectionFallback = directionPenalty,
                diagnostics = emptyList()
            )
        }

        val threshold = computeDynamicThreshold(observations.size)
        val scored = observations.map { (tripId, durSec, ctx) ->
            val breakdown = compute4DSimilarity(ctx, targetContext)
            val adjSim = if (directionPenalty) breakdown.similarity * 0.45 else breakdown.similarity
            Triple(tripId, durSec, ctx) to breakdown.copy(similarity = adjSim)
        }

        val aboveThreshold = scored.filter { it.second.similarity >= threshold }
        val usedFallback = aboveThreshold.isEmpty() || directionPenalty

        val retainedPairs = if (aboveThreshold.isNotEmpty()) {
            aboveThreshold
        } else {
            // Si ningún registro supera el umbral (ej. primer día de lluvia),
            // conservamos los 2 más parecidos para no quedar sin referencia.
            scored.sortedByDescending { it.second.similarity }.take(2)
        }

        val retainedIds = retainedPairs.map { it.first.first }.toSet()

        // Filtro de Kalman Adaptativo Robusto (M-Estimador robusto) sobre Modelo de Nivel Local
        val sortedRetained = retainedPairs.sortedBy { timestampByTripId[it.first.first] ?: it.first.first }
        val sortedDurations = sortedRetained.map { it.first.second.toDouble() }.sorted()
        val medianDur = if (sortedDurations.size % 2 == 1) {
            sortedDurations[sortedDurations.size / 2]
        } else {
            (sortedDurations[sortedDurations.size / 2 - 1] + sortedDurations[sortedDurations.size / 2]) / 2.0
        }.coerceAtLeast(30.0)

        var mu = sortedRetained.first().first.second.toDouble()
        var p = max(625.0, (0.12 * medianDur).pow(2))
        var q = max(144.0, (0.03 * medianDur).pow(2))
        var r = max(256.0, (0.08 * medianDur).pow(2))
        val alpha = 0.22
        val minR = 64.0
        val maxR = (0.50 * medianDur).pow(2) + 2500.0
        val minQ = 25.0
        val maxQ = (0.30 * medianDur).pow(2) + 900.0

        val huberWeightsByTripId = mutableMapOf<Long, Double>()
        huberWeightsByTripId[sortedRetained.first().first.first] = 1.0

        for (i in 1 until sortedRetained.size) {
            val (obsTriple, breakdown) = sortedRetained[i]
            val tripId = obsTriple.first
            val y = obsTriple.second.toDouble()
            val w = max(0.005, breakdown.similarity.pow(2))

            // 1. Predicción (Modelo de Nivel Local)
            val muPred = mu
            val pPred = p + q

            // 2. Innovación ponderada por similitud 4D
            val rEff = r / w
            val innovation = y - muPred
            val innovVar = max(1.0, pPred + rEff)
            val stdResidual = abs(innovation) / sqrt(innovVar)

            // 3. M-Estimador robusto (Huber para leves, decaimiento cuadrático para atípicos extremos)
            val psiM = when {
                stdResidual <= weights.huberThresholdC -> 1.0
                stdResidual <= weights.huberThresholdC * 2.5 -> weights.huberThresholdC / stdResidual
                else -> (weights.huberThresholdC / stdResidual).pow(2)
            }
            huberWeightsByTripId[tripId] = psiM

            val rRobust = rEff / max(psiM, 0.001)

            // 4. Ganancia de Kalman y actualización de estado
            val gain = pPred / (pPred + rRobust)
            mu = muPred + gain * innovation
            p = (1.0 - gain) * pPred

            // 5. Adaptación en línea de R y Q acotada por M-Estimador
            val boundedInnov = psiM * innovation
            val rCandidate = max(minR, boundedInnov.pow(2))
            r = ((1.0 - alpha) * r + alpha * rCandidate).coerceIn(minR, maxR)
            val qCandidate = (gain * innovation).pow(2)
            q = ((1.0 - alpha) * q + alpha * qCandidate).coerceIn(minQ, maxQ)
        }

        val diagnostics = scored.map { (obsTriple, breakdown) ->
            val tripId = obsTriple.first
            val isRetained = tripId in retainedIds
            DiagnosticObservation(
                tripId = tripId,
                timestampMs = timestampByTripId[tripId] ?: 0L,
                durationSec = obsTriple.second,
                context = obsTriple.third,
                similarity = breakdown.similarity,
                distance4D = breakdown.distance4D,
                huberWeight = huberWeightsByTripId[tripId] ?: 0.0,
                wasPruned = !isRetained
            )
        }.sortedByDescending { it.timestampMs }

        return KalmanPrediction(
            hasData = true,
            predictedDurationSec = mu.toLong().coerceAtLeast(1L),
            uncertaintySec = sqrt(p + q).toLong().coerceAtLeast(15L),
            thresholdUsed = threshold,
            totalHistoryCount = observations.size,
            retainedCount = retainedIds.size,
            ignoredCount = observations.size - retainedIds.size,
            usedFallback = usedFallback,
            usedCrossDirectionFallback = directionPenalty,
            diagnostics = diagnostics
        )
    }

    fun predictRoute(
        segmentTemplates: List<SegmentTemplateEntity>,
        allTripsForRoute: List<TripRecordEntity>,
        allSegmentsForRoute: List<SegmentRecordEntity>,
        targetContext: CircumstanceContext,
        departureTimeMs: Long = System.currentTimeMillis()
    ): RoutePredictionSummary {
        val matchingDirectionTrips = allTripsForRoute.filter {
            TripDirection.fromString(it.direction) == targetContext.direction
        }
        val useCrossDirection = matchingDirectionTrips.isEmpty() && allTripsForRoute.isNotEmpty()
        val sourceTrips = if (useCrossDirection) allTripsForRoute else matchingDirectionTrips

        val timestampMap = sourceTrips.associate { it.id to it.departureTimestampMs }
        val tripMap = sourceTrips.associateBy { it.id }

        val totalObs = sourceTrips.map {
            Triple(it.id, it.totalDurationSec, it.toContext())
        }
        val totalPrediction = predictFromObservations(
            observations = totalObs,
            targetContext = targetContext,
            timestampByTripId = timestampMap,
            directionPenalty = useCrossDirection
        )

        val segmentsByOrder = allSegmentsForRoute
            .filter { it.tripId in tripMap }
            .groupBy { it.segmentOrder }

        val maxOrder = max(
            segmentTemplates.size,
            (segmentsByOrder.keys.maxOrNull()?.plus(1)) ?: 0
        )

        var cumulativeSec = 0L
        val segmentPredictions = (0 until maxOrder).map { order ->
            val templateName = segmentTemplates.firstOrNull { it.orderIndex == order }?.name
                ?: segmentsByOrder[order]?.lastOrNull()?.segmentName
                ?: "Segmento ${order + 1}"

            val segRecords = segmentsByOrder[order].orEmpty()
            val segObs = segRecords.mapNotNull { rec ->
                val trip = tripMap[rec.tripId] ?: return@mapNotNull null
                Triple(rec.tripId, rec.durationSec, trip.toContext())
            }
            val segPred = predictFromObservations(
                observations = segObs,
                targetContext = targetContext,
                timestampByTripId = timestampMap,
                directionPenalty = useCrossDirection
            )
            val etaMs = if (segPred.hasData) {
                cumulativeSec += segPred.predictedDurationSec
                departureTimeMs + cumulativeSec * 1000L
            } else null

            SegmentPrediction(
                orderIndex = order,
                name = templateName,
                prediction = segPred,
                cumulativeEtaMs = etaMs
            )
        }

        val finalEtaMs = if (totalPrediction.hasData) {
            departureTimeMs + totalPrediction.predictedDurationSec * 1000L
        } else null

        return RoutePredictionSummary(
            totalPrediction = totalPrediction,
            segmentPredictions = segmentPredictions,
            estimatedArrivalMs = finalEtaMs
        )
    }
}

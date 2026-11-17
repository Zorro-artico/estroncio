package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AstrolabioDatabase
import com.example.data.local.AstrolabioRepository
import com.example.data.model.ActiveSegmentMarkEntity
import com.example.data.model.ActiveTripEntity
import com.example.data.model.CircumstanceContext
import com.example.data.model.RouteEntity
import com.example.data.model.SegmentRecordEntity
import com.example.data.model.SegmentTemplateEntity
import com.example.data.model.TripDirection
import com.example.data.model.TripRecordEntity
import com.example.domain.kalman.AdaptiveRobustKalmanEngine
import com.example.domain.kalman.RoutePredictionSummary
import com.example.tracking.TripTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripSetupConfig(
    val direction: TripDirection = TripDirection.IDA,
    val hasRain: Boolean = false,
    val hasProtest: Boolean = false,
    val hasAccident: Boolean = false,
    val isVacation: Boolean = false,
    val customIncident: String = ""
)

data class RouteCardUiModel(
    val route: RouteEntity,
    val segmentCount: Int,
    val tripCount: Int,
    val quickPrediction: RoutePredictionSummary
)

data class SimulationParams(
    val useCurrentTime: Boolean = true,
    val direction: TripDirection = TripDirection.IDA,
    val hasRain: Boolean = false,
    val hasProtest: Boolean = false,
    val hasAccident: Boolean = false,
    val isVacation: Boolean = false,
    val customIncident: String = "",
    val customHour: Int = 8,
    val customMinute: Int = 0,
    val customDayOfWeek: Int = 1,
    val customMonth: Int = 1
) {
    fun toCircumstanceContext(nowMs: Long = System.currentTimeMillis()): CircumstanceContext {
        val base = CircumstanceContext.now(
            direction = direction,
            hasRain = hasRain,
            hasProtest = hasProtest,
            hasAccident = hasAccident,
            isVacation = isVacation,
            customIncident = customIncident,
            timestampMs = nowMs
        )
        return if (useCurrentTime) {
            base
        } else {
            base.copy(
                minuteOfDay = (customHour.coerceIn(0, 23) * 60 + customMinute.coerceIn(0, 59)),
                dayOfWeek = customDayOfWeek.coerceIn(1, 7),
                month = customMonth.coerceIn(1, 12)
            )
        }
    }
}

data class RouteDetailUiState(
    val route: RouteEntity? = null,
    val segmentTemplates: List<SegmentTemplateEntity> = emptyList(),
    val trips: List<TripRecordEntity> = emptyList(),
    val simulation: SimulationParams = SimulationParams(),
    val contextUsed: CircumstanceContext = CircumstanceContext.now(),
    val prediction: RoutePredictionSummary? = null
)

data class ActiveTripUiState(
    val activeTrip: ActiveTripEntity,
    val completedMarks: List<ActiveSegmentMarkEntity>,
    val templates: List<SegmentTemplateEntity>,
    val prediction: RoutePredictionSummary,
    val elapsedSec: Long,
    val currentSegmentElapsedSec: Long,
    val dynamicEtaMs: Long?
)

class AstrolabioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AstrolabioRepository by lazy {
        AstrolabioRepository(AstrolabioDatabase.getInstance(application).astrolabioDao())
    }
    private val engine = AdaptiveRobustKalmanEngine()

    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    private val simulationParams = MutableStateFlow(SimulationParams())

    val routesUiState: StateFlow<List<RouteCardUiModel>> = combine(
        repository.observeRoutesByFrequency(),
        repository.observeAllSegmentTemplates(),
        repository.observeAllTripRecords(),
        repository.observeAllSegmentRecords()
    ) { routes, allTemplates, allTrips, allSegRecords ->
        val templatesByRoute = allTemplates.groupBy { it.routeId }
        val tripsByRoute = allTrips.groupBy { it.routeId }
        val segsByRoute = allSegRecords.groupBy { it.routeId }
        val nowMs = System.currentTimeMillis()
        val nowCtx = CircumstanceContext.now(direction = TripDirection.IDA, timestampMs = nowMs)

        routes.map { route ->
            val templates = templatesByRoute[route.id].orEmpty()
            val trips = tripsByRoute[route.id].orEmpty()
            val segs = segsByRoute[route.id].orEmpty()
            val pred = engine.predictRoute(
                segmentTemplates = templates,
                allTripsForRoute = trips,
                allSegmentsForRoute = segs,
                targetContext = nowCtx,
                departureTimeMs = nowMs
            )
            RouteCardUiModel(
                route = route,
                segmentCount = maxOf(templates.size, pred.segmentPredictions.size),
                tripCount = trips.size,
                quickPrediction = pred
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val activeTripUiState: StateFlow<ActiveTripUiState?> = combine(
        repository.observeActiveTrip(),
        repository.observeActiveSegmentMarks(),
        repository.observeAllSegmentTemplates(),
        repository.observeAllTripRecords(),
        repository.observeAllSegmentRecords(),
        tickerFlow
    ) { values ->
        val active = values[0] as ActiveTripEntity? ?: return@combine null
        @Suppress("UNCHECKED_CAST")
        val marks = values[1] as List<ActiveSegmentMarkEntity>
        @Suppress("UNCHECKED_CAST")
        val allTemplates = values[2] as List<SegmentTemplateEntity>
        @Suppress("UNCHECKED_CAST")
        val allTrips = values[3] as List<TripRecordEntity>
        @Suppress("UNCHECKED_CAST")
        val allSegRecords = values[4] as List<SegmentRecordEntity>
        val nowMs = values[5] as Long

        val routeTemplates = allTemplates.filter { it.routeId == active.routeId }
        val routeTrips = allTrips.filter { it.routeId == active.routeId }
        val routeSegs = allSegRecords.filter { it.routeId == active.routeId }

        val pred = engine.predictRoute(
            segmentTemplates = routeTemplates,
            allTripsForRoute = routeTrips,
            allSegmentsForRoute = routeSegs,
            targetContext = active.toContext(),
            departureTimeMs = active.departureTimestampMs
        )

        val elapsedSec = ((nowMs - active.departureTimestampMs) / 1000L).coerceAtLeast(0L)
        val segElapsedSec = ((nowMs - active.lastMarkTimestampMs) / 1000L).coerceAtLeast(0L)

        val completedSec = marks.sumOf { it.durationSec }
        val remainingPredSec = pred.segmentPredictions
            .filter { it.orderIndex >= active.currentSegmentIndex && it.prediction.hasData }
            .sumOf { it.prediction.predictedDurationSec }

        val dynamicEtaMs = when {
            remainingPredSec > 0L -> {
                val baseTotal = maxOf(
                    completedSec + remainingPredSec,
                    elapsedSec + 30L
                )
                active.departureTimestampMs + baseTotal * 1000L
            }
            pred.totalPrediction.hasData -> {
                val baseTotal = maxOf(pred.totalPrediction.predictedDurationSec, elapsedSec + 30L)
                active.departureTimestampMs + baseTotal * 1000L
            }
            else -> null
        }

        ActiveTripUiState(
            activeTrip = active,
            completedMarks = marks,
            templates = routeTemplates,
            prediction = pred,
            elapsedSec = elapsedSec,
            currentSegmentElapsedSec = segElapsedSec,
            dynamicEtaMs = dynamicEtaMs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    fun observeRouteDetail(routeId: Long): Flow<RouteDetailUiState> {
        return combine(
            repository.observeRoute(routeId),
            repository.observeSegmentTemplatesForRoute(routeId),
            repository.observeTripsForRoute(routeId),
            repository.observeSegmentRecordsForRoute(routeId),
            simulationParams
        ) { route, templates, trips, segRecords, sim ->
            val nowMs = System.currentTimeMillis()
            val ctx = sim.toCircumstanceContext(nowMs)
            val pred = engine.predictRoute(
                segmentTemplates = templates,
                allTripsForRoute = trips,
                allSegmentsForRoute = segRecords,
                targetContext = ctx,
                departureTimeMs = nowMs
            )
            RouteDetailUiState(
                route = route,
                segmentTemplates = templates,
                trips = trips.sortedByDescending { it.departureTimestampMs },
                simulation = sim,
                contextUsed = ctx,
                prediction = pred
            )
        }
    }

    fun resetSimulationToNow() {
        val nowCtx = CircumstanceContext.now()
        simulationParams.value = SimulationParams(
            useCurrentTime = true,
            customHour = nowCtx.minuteOfDay / 60,
            customMinute = nowCtx.minuteOfDay % 60,
            customDayOfWeek = nowCtx.dayOfWeek,
            customMonth = nowCtx.month
        )
    }

    fun updateSimulation(transform: (SimulationParams) -> SimulationParams) {
        simulationParams.value = transform(simulationParams.value)
    }

    fun createRouteAndStartTrip(
        routeName: String,
        initialSegments: List<String>,
        setup: TripSetupConfig,
        onStarted: () -> Unit
    ) {
        val cleanName = routeName.trim()
        if (cleanName.isEmpty()) return

        viewModelScope.launch {
            val routeId = repository.createRoute(cleanName, initialSegments)
            launchTripInternal(routeId, setup)
            onStarted()
        }
    }

    fun startTripForRoute(
        routeId: Long,
        setup: TripSetupConfig,
        onStarted: () -> Unit
    ) {
        viewModelScope.launch {
            launchTripInternal(routeId, setup)
            onStarted()
        }
    }

    private suspend fun launchTripInternal(routeId: Long, setup: TripSetupConfig) {
        val route = repository.getRouteById(routeId) ?: return
        val templates = repository.getSegmentTemplatesForRoute(routeId)
        val trips = repository.getTripsForRoute(routeId)
        val segRecords = repository.getSegmentRecordsForRoute(routeId)
        val nowMs = System.currentTimeMillis()

        val ctx = CircumstanceContext.now(
            direction = setup.direction,
            hasRain = setup.hasRain,
            hasProtest = setup.hasProtest,
            hasAccident = setup.hasAccident,
            isVacation = setup.isVacation,
            customIncident = setup.customIncident,
            timestampMs = nowMs
        )

        val summary = engine.predictRoute(
            segmentTemplates = templates,
            allTripsForRoute = trips,
            allSegmentsForRoute = segRecords,
            targetContext = ctx,
            departureTimeMs = nowMs
        )

        val predictedSec = if (summary.totalPrediction.hasData) {
            summary.totalPrediction.predictedDurationSec
        } else 0L
        val predictedArrivalMs = summary.estimatedArrivalMs ?: 0L

        repository.startActiveTrip(
            ActiveTripEntity(
                id = 1,
                routeId = route.id,
                routeName = route.name,
                direction = setup.direction.name,
                departureTimestampMs = nowMs,
                lastMarkTimestampMs = nowMs,
                currentSegmentIndex = 0,
                predictedTotalSec = predictedSec,
                predictedArrivalTimestampMs = predictedArrivalMs,
                hasRain = setup.hasRain,
                hasProtest = setup.hasProtest,
                hasAccident = setup.hasAccident,
                isVacation = setup.isVacation,
                customIncident = setup.customIncident.trim()
            )
        )
        TripTrackingService.startOrUpdate(getApplication())
    }

    fun markSegmentPartition(customSegmentName: String? = null) {
        viewModelScope.launch {
            val active = repository.getActiveTrip() ?: return@launch
            val now = System.currentTimeMillis()
            val templates = repository.getSegmentTemplatesForRoute(active.routeId)
            val trips = repository.getTripsForRoute(active.routeId)
            val segRecords = repository.getSegmentRecordsForRoute(active.routeId)
            val marks = repository.getActiveSegmentMarks()

            val summary = engine.predictRoute(
                segmentTemplates = templates,
                allTripsForRoute = trips,
                allSegmentsForRoute = segRecords,
                targetContext = active.toContext(),
                departureTimeMs = active.departureTimestampMs
            )

            val nextOrder = active.currentSegmentIndex + 1
            val completedSecSoFar = marks.sumOf { it.durationSec } +
                ((now - active.lastMarkTimestampMs) / 1000L).coerceAtLeast(1L)
            val remainingPredSec = summary.segmentPredictions
                .filter { it.orderIndex >= nextOrder && it.prediction.hasData }
                .sumOf { it.prediction.predictedDurationSec }

            val updatedTotalSec = if (remainingPredSec > 0L) {
                completedSecSoFar + remainingPredSec
            } else {
                active.predictedTotalSec
            }
            val updatedArrivalMs = if (updatedTotalSec > 0L) {
                active.departureTimestampMs + updatedTotalSec * 1000L
            } else 0L

            repository.markActiveSegment(
                nowMs = now,
                customSegmentName = customSegmentName,
                updatedPredictedTotalSec = updatedTotalSec,
                updatedPredictedArrivalMs = updatedArrivalMs
            )
            TripTrackingService.startOrUpdate(getApplication())
        }
    }

    fun updateActiveTripConditions(
        hasRain: Boolean,
        hasProtest: Boolean,
        hasAccident: Boolean,
        isVacation: Boolean,
        customIncident: String
    ) {
        viewModelScope.launch {
            val active = repository.getActiveTrip() ?: return@launch
            val updated = active.copy(
                hasRain = hasRain,
                hasProtest = hasProtest,
                hasAccident = hasAccident,
                isVacation = isVacation,
                customIncident = customIncident.trim()
            )
            val templates = repository.getSegmentTemplatesForRoute(active.routeId)
            val trips = repository.getTripsForRoute(active.routeId)
            val segRecords = repository.getSegmentRecordsForRoute(active.routeId)
            val summary = engine.predictRoute(
                segmentTemplates = templates,
                allTripsForRoute = trips,
                allSegmentsForRoute = segRecords,
                targetContext = updated.toContext(),
                departureTimeMs = updated.departureTimestampMs
            )
            val predSec = if (summary.totalPrediction.hasData) summary.totalPrediction.predictedDurationSec else 0L
            val predArr = summary.estimatedArrivalMs ?: 0L

            repository.updateActiveTrip(
                updated.copy(
                    predictedTotalSec = predSec,
                    predictedArrivalTimestampMs = predArr
                )
            )
            TripTrackingService.startOrUpdate(getApplication())
        }
    }

    fun completeTrip(finalSegmentName: String? = null, onCompleted: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.completeActiveTrip(now, finalSegmentName)
            TripTrackingService.stop(getApplication())
            onCompleted()
        }
    }

    fun cancelActiveTrip(onCancelled: () -> Unit) {
        viewModelScope.launch {
            repository.cancelActiveTrip()
            TripTrackingService.stop(getApplication())
            onCancelled()
        }
    }

    fun updateSegmentTemplates(routeId: Long, names: List<String>) {
        viewModelScope.launch {
            repository.updateSegmentTemplates(routeId, names)
        }
    }

    fun renameRoute(routeId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameRoute(routeId, newName)
        }
    }

    fun deleteRoute(routeId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteRoute(routeId)
            onDeleted()
        }
    }

    fun deleteTripRecord(tripId: Long) {
        viewModelScope.launch {
            repository.deleteTripRecord(tripId)
        }
    }
}

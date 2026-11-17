package com.example.data.local

import com.example.data.model.ActiveSegmentMarkEntity
import com.example.data.model.ActiveTripEntity
import com.example.data.model.RouteEntity
import com.example.data.model.SegmentRecordEntity
import com.example.data.model.SegmentTemplateEntity
import com.example.data.model.TripRecordEntity
import kotlinx.coroutines.flow.Flow

class AstrolabioRepository(private val dao: AstrolabioDao) {

    fun observeRoutesByFrequency(): Flow<List<RouteEntity>> = dao.observeRoutesByFrequency()

    fun observeRoute(routeId: Long): Flow<RouteEntity?> = dao.observeRoute(routeId)

    fun observeAllSegmentTemplates(): Flow<List<SegmentTemplateEntity>> =
        dao.observeAllSegmentTemplates()

    fun observeSegmentTemplatesForRoute(routeId: Long): Flow<List<SegmentTemplateEntity>> =
        dao.observeSegmentTemplatesForRoute(routeId)

    fun observeAllTripRecords(): Flow<List<TripRecordEntity>> = dao.observeAllTripRecords()

    fun observeTripsForRoute(routeId: Long): Flow<List<TripRecordEntity>> =
        dao.observeTripsForRoute(routeId)

    fun observeAllSegmentRecords(): Flow<List<SegmentRecordEntity>> =
        dao.observeAllSegmentRecords()

    fun observeSegmentRecordsForRoute(routeId: Long): Flow<List<SegmentRecordEntity>> =
        dao.observeSegmentRecordsForRoute(routeId)

    fun observeActiveTrip(): Flow<ActiveTripEntity?> = dao.observeActiveTrip()

    fun observeActiveSegmentMarks(): Flow<List<ActiveSegmentMarkEntity>> =
        dao.observeActiveSegmentMarks()

    suspend fun createRoute(name: String, initialSegments: List<String> = emptyList()): Long {
        val now = System.currentTimeMillis()
        val routeId = dao.insertRoute(
            RouteEntity(
                name = name.trim(),
                usageCount = 0,
                createdAtMs = now,
                lastUsedAtMs = now
            )
        )
        val cleanSegments = initialSegments.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanSegments.isNotEmpty()) {
            dao.insertSegmentTemplates(
                cleanSegments.mapIndexed { idx, segName ->
                    SegmentTemplateEntity(routeId = routeId, orderIndex = idx, name = segName)
                }
            )
        }
        return routeId
    }

    suspend fun renameRoute(routeId: Long, newName: String) {
        val route = dao.getRouteById(routeId) ?: return
        dao.updateRoute(route.copy(name = newName.trim()))
    }

    suspend fun deleteRoute(routeId: Long) = dao.deleteRouteById(routeId)

    suspend fun deleteTripRecord(tripId: Long) = dao.deleteTripRecordById(tripId)

    suspend fun updateSegmentTemplates(routeId: Long, segmentNames: List<String>) {
        dao.deleteSegmentTemplatesForRoute(routeId)
        val clean = segmentNames.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isNotEmpty()) {
            dao.insertSegmentTemplates(
                clean.mapIndexed { idx, name ->
                    SegmentTemplateEntity(routeId = routeId, orderIndex = idx, name = name)
                }
            )
        }
    }

    suspend fun getRouteById(routeId: Long): RouteEntity? = dao.getRouteById(routeId)

    suspend fun getSegmentTemplatesForRoute(routeId: Long): List<SegmentTemplateEntity> =
        dao.getSegmentTemplatesForRoute(routeId)

    suspend fun getTripsForRoute(routeId: Long): List<TripRecordEntity> =
        dao.getTripsForRoute(routeId)

    suspend fun getSegmentRecordsForRoute(routeId: Long): List<SegmentRecordEntity> =
        dao.getSegmentRecordsForRoute(routeId)

    suspend fun getActiveTrip(): ActiveTripEntity? = dao.getActiveTrip()

    suspend fun getActiveSegmentMarks(): List<ActiveSegmentMarkEntity> =
        dao.getActiveSegmentMarks()

    suspend fun startActiveTrip(activeTrip: ActiveTripEntity) =
        dao.startNewActiveTrip(activeTrip)

    suspend fun updateActiveTrip(activeTrip: ActiveTripEntity) =
        dao.upsertActiveTrip(activeTrip)

    suspend fun markActiveSegment(
        nowMs: Long,
        customSegmentName: String? = null,
        updatedPredictedTotalSec: Long? = null,
        updatedPredictedArrivalMs: Long? = null
    ): ActiveTripEntity? {
        val active = dao.getActiveTrip() ?: return null
        val templates = dao.getSegmentTemplatesForRoute(active.routeId)
        val order = active.currentSegmentIndex
        val durationSec = ((nowMs - active.lastMarkTimestampMs) / 1000L).coerceAtLeast(1L)
        val resolvedName = customSegmentName?.trim()?.takeIf { it.isNotEmpty() }
            ?: templates.getOrNull(order)?.name
            ?: "Segmento ${order + 1}"

        dao.insertActiveSegmentMark(
            ActiveSegmentMarkEntity(
                segmentOrder = order,
                segmentName = resolvedName,
                durationSec = durationSec,
                markedAtMs = nowMs
            )
        )

        val updated = active.copy(
            lastMarkTimestampMs = nowMs,
            currentSegmentIndex = order + 1,
            predictedTotalSec = updatedPredictedTotalSec ?: active.predictedTotalSec,
            predictedArrivalTimestampMs = updatedPredictedArrivalMs ?: active.predictedArrivalTimestampMs
        )
        dao.upsertActiveTrip(updated)
        return updated
    }

    suspend fun completeActiveTrip(nowMs: Long, finalSegmentName: String? = null): Long? =
        dao.completeActiveTrip(nowMs, finalSegmentName)

    suspend fun cancelActiveTrip() = dao.cancelActiveTrip()
}

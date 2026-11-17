package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.ActiveSegmentMarkEntity
import com.example.data.model.ActiveTripEntity
import com.example.data.model.CircumstanceContext
import com.example.data.model.RouteEntity
import com.example.data.model.SegmentRecordEntity
import com.example.data.model.SegmentTemplateEntity
import com.example.data.model.TripRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AstrolabioDao {

    @Query("SELECT * FROM routes ORDER BY usageCount DESC, lastUsedAtMs DESC, createdAtMs DESC")
    fun observeRoutesByFrequency(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    fun observeRoute(routeId: Long): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: Long): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity): Long

    @Update
    suspend fun updateRoute(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRouteById(routeId: Long)

    @Query("UPDATE routes SET usageCount = usageCount + 1, lastUsedAtMs = :usedAtMs WHERE id = :routeId")
    suspend fun incrementRouteUsage(routeId: Long, usedAtMs: Long)

    @Query("SELECT * FROM segment_templates ORDER BY routeId ASC, orderIndex ASC")
    fun observeAllSegmentTemplates(): Flow<List<SegmentTemplateEntity>>

    @Query("SELECT * FROM segment_templates WHERE routeId = :routeId ORDER BY orderIndex ASC")
    fun observeSegmentTemplatesForRoute(routeId: Long): Flow<List<SegmentTemplateEntity>>

    @Query("SELECT * FROM segment_templates WHERE routeId = :routeId ORDER BY orderIndex ASC")
    suspend fun getSegmentTemplatesForRoute(routeId: Long): List<SegmentTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegmentTemplates(templates: List<SegmentTemplateEntity>)

    @Query("DELETE FROM segment_templates WHERE routeId = :routeId")
    suspend fun deleteSegmentTemplatesForRoute(routeId: Long)

    @Query("SELECT * FROM trip_records ORDER BY departureTimestampMs ASC")
    fun observeAllTripRecords(): Flow<List<TripRecordEntity>>

    @Query("SELECT * FROM trip_records WHERE routeId = :routeId ORDER BY departureTimestampMs ASC")
    fun observeTripsForRoute(routeId: Long): Flow<List<TripRecordEntity>>

    @Query("SELECT * FROM trip_records WHERE routeId = :routeId ORDER BY departureTimestampMs ASC")
    suspend fun getTripsForRoute(routeId: Long): List<TripRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripRecord(record: TripRecordEntity): Long

    @Query("DELETE FROM trip_records WHERE id = :tripId")
    suspend fun deleteTripRecordById(tripId: Long)

    @Query("SELECT * FROM segment_records WHERE routeId = :routeId ORDER BY tripId ASC, segmentOrder ASC")
    fun observeSegmentRecordsForRoute(routeId: Long): Flow<List<SegmentRecordEntity>>

    @Query("SELECT * FROM segment_records WHERE routeId = :routeId ORDER BY tripId ASC, segmentOrder ASC")
    suspend fun getSegmentRecordsForRoute(routeId: Long): List<SegmentRecordEntity>

    @Query("SELECT * FROM segment_records ORDER BY tripId ASC, segmentOrder ASC")
    fun observeAllSegmentRecords(): Flow<List<SegmentRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegmentRecords(records: List<SegmentRecordEntity>)

    @Query("SELECT * FROM active_trip WHERE id = 1")
    fun observeActiveTrip(): Flow<ActiveTripEntity?>

    @Query("SELECT * FROM active_trip WHERE id = 1")
    suspend fun getActiveTrip(): ActiveTripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActiveTrip(activeTrip: ActiveTripEntity)

    @Query("DELETE FROM active_trip")
    suspend fun clearActiveTrip()

    @Query("SELECT * FROM active_segment_marks ORDER BY segmentOrder ASC")
    fun observeActiveSegmentMarks(): Flow<List<ActiveSegmentMarkEntity>>

    @Query("SELECT * FROM active_segment_marks ORDER BY segmentOrder ASC")
    suspend fun getActiveSegmentMarks(): List<ActiveSegmentMarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveSegmentMark(mark: ActiveSegmentMarkEntity): Long

    @Query("DELETE FROM active_segment_marks")
    suspend fun clearActiveSegmentMarks()

    @Transaction
    suspend fun startNewActiveTrip(activeTrip: ActiveTripEntity) {
        clearActiveSegmentMarks()
        upsertActiveTrip(activeTrip)
    }

    @Transaction
    suspend fun cancelActiveTrip() {
        clearActiveSegmentMarks()
        clearActiveTrip()
    }

    @Transaction
    suspend fun completeActiveTrip(nowMs: Long, finalSegmentName: String? = null): Long? {
        val active = getActiveTrip() ?: return null
        val marks = getActiveSegmentMarks()
        val templates = getSegmentTemplatesForRoute(active.routeId)

        val totalSec = ((nowMs - active.departureTimestampMs) / 1000L).coerceAtLeast(1L)
        val lastLegSec = ((nowMs - active.lastMarkTimestampMs) / 1000L).coerceAtLeast(1L)

        val ctx = CircumstanceContext.now(
            direction = com.example.data.model.TripDirection.fromString(active.direction),
            hasRain = active.hasRain,
            hasProtest = active.hasProtest,
            hasAccident = active.hasAccident,
            isVacation = active.isVacation,
            customIncident = active.customIncident,
            timestampMs = active.departureTimestampMs
        )

        val tripId = insertTripRecord(
            TripRecordEntity(
                routeId = active.routeId,
                direction = active.direction,
                departureTimestampMs = active.departureTimestampMs,
                arrivalTimestampMs = nowMs,
                totalDurationSec = totalSec,
                minuteOfDay = ctx.minuteOfDay,
                dayOfWeek = ctx.dayOfWeek,
                month = ctx.month,
                hasRain = active.hasRain,
                hasProtest = active.hasProtest,
                hasAccident = active.hasAccident,
                isVacation = active.isVacation,
                customIncident = active.customIncident
            )
        )

        val allSegmentEntries = mutableListOf<SegmentRecordEntity>()
        marks.forEach { mark ->
            allSegmentEntries.add(
                SegmentRecordEntity(
                    tripId = tripId,
                    routeId = active.routeId,
                    segmentOrder = mark.segmentOrder,
                    segmentName = mark.segmentName,
                    durationSec = mark.durationSec
                )
            )
        }

        val finalOrder = marks.size
        val resolvedFinalName = finalSegmentName?.takeIf { it.isNotBlank() }
            ?: templates.getOrNull(finalOrder)?.name
            ?: if (finalOrder == 0) "Tramo completo" else "Tramo final (${finalOrder + 1})"

        allSegmentEntries.add(
            SegmentRecordEntity(
                tripId = tripId,
                routeId = active.routeId,
                segmentOrder = finalOrder,
                segmentName = resolvedFinalName,
                durationSec = lastLegSec
            )
        )
        insertSegmentRecords(allSegmentEntries)

        // Ensure route templates reflect any newly created partitions
        if (allSegmentEntries.size > templates.size) {
            val mergedTemplates = allSegmentEntries.mapIndexed { idx, seg ->
                SegmentTemplateEntity(
                    routeId = active.routeId,
                    orderIndex = idx,
                    name = templates.getOrNull(idx)?.name ?: seg.segmentName
                )
            }
            deleteSegmentTemplatesForRoute(active.routeId)
            insertSegmentTemplates(mergedTemplates)
        }

        incrementRouteUsage(active.routeId, nowMs)
        clearActiveSegmentMarks()
        clearActiveTrip()
        return tripId
    }
}

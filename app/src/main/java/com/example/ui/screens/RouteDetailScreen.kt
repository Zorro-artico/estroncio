package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.kalman.DiagnosticObservation
import com.example.domain.kalman.SegmentPrediction
import com.example.domain.kalman.TimeFormatters
import com.example.ui.RouteDetailUiState
import com.example.ui.SimulationParams
import com.example.ui.TripSetupConfig
import com.example.ui.components.AstronomicalDivider
import com.example.ui.components.CelestialMeridianBackdrop
import com.example.ui.components.CircumstanceChipsRow
import com.example.ui.components.DestinationEtaHeroCard
import com.example.ui.components.DirectionSegmentedSelector
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouteDetailScreen(
    uiState: RouteDetailUiState,
    onBack: () -> Unit,
    onUpdateSimulation: ((SimulationParams) -> SimulationParams) -> Unit,
    onStartTripWithCurrentParams: (TripSetupConfig) -> Unit,
    onUpdateSegmentTemplates: (List<String>) -> Unit,
    onDeleteTripRecord: (Long) -> Unit,
    onDeleteRoute: () -> Unit
) {
    val route = uiState.route
    val prediction = uiState.prediction
    var showSimulatorDetails by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showEditSegmentsDialog by remember { mutableStateOf(false) }
    var showDeleteRouteDialog by remember { mutableStateOf(false) }
    var segmentsDraft by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = route?.name ?: "Carta de ruta",
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (route != null) {
                            Text(
                                text = "${route.usageCount} viajes completados · Predicción sin iniciar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_from_detail_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            segmentsDraft = uiState.segmentTemplates.joinToString(", ") { it.name }
                            showEditSegmentsDialog = true
                        },
                        modifier = Modifier.testTag("edit_segments_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Editar segmentos"
                        )
                    }
                    IconButton(
                        onClick = { showDeleteRouteDialog = true },
                        modifier = Modifier.testTag("delete_route_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Eliminar ruta"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            onStartTripWithCurrentParams(
                                TripSetupConfig(
                                    direction = uiState.simulation.direction,
                                    hasRain = uiState.simulation.hasRain,
                                    hasProtest = uiState.simulation.hasProtest,
                                    hasAccident = uiState.simulation.hasAccident,
                                    isVacation = uiState.simulation.isVacation,
                                    customIncident = uiState.simulation.customIncident
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .height(52.dp)
                            .testTag("detail_start_trip_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Iniciar viaje con estas condiciones")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            CelestialMeridianBackdrop()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Large Hero Box for Destination Arrival Time
                if (prediction != null) {
                    item {
                        DestinationEtaHeroCard(
                            title = "Llegada estimada al destino",
                            direction = uiState.simulation.direction,
                            prediction = prediction.totalPrediction,
                            estimatedArrivalMs = prediction.estimatedArrivalMs
                        )
                    }
                }

                // 2. Circumstances & 4D Simulator Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Circunstancias en espacio 4D",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            DirectionSegmentedSelector(
                                selected = uiState.simulation.direction,
                                onSelect = { dir ->
                                    onUpdateSimulation { it.copy(direction = dir) }
                                }
                            )

                            CircumstanceChipsRow(
                                hasRain = uiState.simulation.hasRain,
                                hasProtest = uiState.simulation.hasProtest,
                                hasAccident = uiState.simulation.hasAccident,
                                isVacation = uiState.simulation.isVacation,
                                customIncident = uiState.simulation.customIncident,
                                onToggleRain = { onUpdateSimulation { it.copy(hasRain = !it.hasRain) } },
                                onToggleProtest = { onUpdateSimulation { it.copy(hasProtest = !it.hasProtest) } },
                                onToggleAccident = { onUpdateSimulation { it.copy(hasAccident = !it.hasAccident) } },
                                onToggleVacation = { onUpdateSimulation { it.copy(isVacation = !it.isVacation) } },
                                onCustomIncidentChange = { txt ->
                                    onUpdateSimulation { it.copy(customIncident = txt) }
                                }
                            )

                            OutlinedButton(
                                onClick = { showSimulatorDetails = !showSimulatorDetails },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("toggle_time_simulator_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val simLabel = if (uiState.simulation.useCurrentTime) {
                                    "Simular hora, día de semana o mes"
                                } else {
                                    val dow = TimeFormatters.dayOfWeekLabel(uiState.simulation.customDayOfWeek)
                                    val mon = TimeFormatters.monthLabel(uiState.simulation.customMonth)
                                    val hr = String.format("%02d:%02d", uiState.simulation.customHour, uiState.simulation.customMinute)
                                    "Simulando: $dow · $hr · $mon"
                                }
                                Text(simLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            AnimatedVisibility(visible = showSimulatorDetails) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Usar reloj actual del sistema",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Switch(
                                            checked = uiState.simulation.useCurrentTime,
                                            onCheckedChange = { useNow ->
                                                onUpdateSimulation { it.copy(useCurrentTime = useNow) }
                                            },
                                            modifier = Modifier.testTag("switch_use_current_time")
                                        )
                                    }

                                    if (!uiState.simulation.useCurrentTime) {
                                        // Hour selector
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Hora de salida: ${String.format("%02d:%02d", uiState.simulation.customHour, uiState.simulation.customMinute)}",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        onUpdateSimulation {
                                                            it.copy(customHour = (it.customHour + 23) % 24)
                                                        }
                                                    }
                                                ) { Text("-1h") }
                                                OutlinedButton(
                                                    onClick = {
                                                        onUpdateSimulation {
                                                            it.copy(customHour = (it.customHour + 1) % 24)
                                                        }
                                                    }
                                                ) { Text("+1h") }
                                            }
                                        }

                                        // Day of week chips (L..D)
                                        Text(
                                            text = "Día de la semana",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(1 to "Lun", 2 to "Mar", 3 to "Mié", 4 to "Jue", 5 to "Vie", 6 to "Sáb", 7 to "Dom")
                                                .forEach { (dow, label) ->
                                                    FilterChip(
                                                        selected = uiState.simulation.customDayOfWeek == dow,
                                                        onClick = {
                                                            onUpdateSimulation { it.copy(customDayOfWeek = dow) }
                                                        },
                                                        label = { Text(label) }
                                                    )
                                                }
                                        }

                                        // Month stepper
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Mes: ${TimeFormatters.monthLabel(uiState.simulation.customMonth)}",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        onUpdateSimulation {
                                                            val prev = if (it.customMonth <= 1) 12 else it.customMonth - 1
                                                            it.copy(customMonth = prev)
                                                        }
                                                    }
                                                ) { Text("Ant.") }
                                                OutlinedButton(
                                                    onClick = {
                                                        onUpdateSimulation {
                                                            val next = if (it.customMonth >= 12) 1 else it.customMonth + 1
                                                            it.copy(customMonth = next)
                                                        }
                                                    }
                                                ) { Text("Sig.") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Segment Predictions List
                item {
                    AstronomicalDivider()
                }
                item {
                    Text(
                        text = "Segmentos de la ruta",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                val segmentList = prediction?.segmentPredictions.orEmpty()
                if (segmentList.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "Aún no hay particiones registradas. Puedes añadir nombres de segmentos arriba con el ícono de lápiz, o marcar particiones durante tu primer viaje.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(segmentList, key = { it.orderIndex }) { seg ->
                        SegmentPredictionCard(segment = seg)
                    }
                }

                // 4. Ephemeris Log & 4D Diagnostics (History + Weights)
                if (prediction != null && prediction.totalPrediction.diagnostics.isNotEmpty()) {
                    item {
                        Surface(
                            onClick = { showDiagnostics = !showDiagnostics },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toggle_diagnostics_button"),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Efemérides registradas (${prediction.totalPrediction.diagnostics.size})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Similitud 4D, poda dinámica y pesos M-Estimador",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (showDiagnostics) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = "Mostrar u ocultar historial"
                                )
                            }
                        }
                    }

                    if (showDiagnostics) {
                        items(
                            items = prediction.totalPrediction.diagnostics,
                            key = { it.tripId }
                        ) { diag ->
                            DiagnosticTripRow(
                                diag = diag,
                                onDelete = { onDeleteTripRecord(diag.tripId) }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showEditSegmentsDialog) {
        AlertDialog(
            onDismissRequest = { showEditSegmentsDialog = false },
            title = { Text("Segmentos de la ruta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Escribe los nombres de los tramos separados por coma.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = segmentsDraft,
                        onValueChange = { segmentsDraft = it },
                        label = { Text("Segmentos") },
                        placeholder = { Text("Ej. Puente Norte, Avenida, Destino") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val list = segmentsDraft.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onUpdateSegmentTemplates(list)
                        showEditSegmentsDialog = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showEditSegmentsDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteRouteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteRouteDialog = false },
            title = { Text("Eliminar ruta") },
            text = { Text("Se borrará la ruta y todo su historial del Filtro de Kalman en este dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteRouteDialog = false
                        onDeleteRoute()
                    }
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRouteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun SegmentPredictionCard(segment: SegmentPrediction) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("segment_prediction_${segment.orderIndex}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${segment.orderIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column {
                    Text(
                        text = segment.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val info = if (segment.prediction.hasData) {
                        "Tramo: ${TimeFormatters.formatDurationHuman(segment.prediction.predictedDurationSec)}"
                    } else {
                        "Sin historial aún para este tramo"
                    }
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = segment.cumulativeEtaMs?.let { TimeFormatters.formatClock(it) } ?: "--:--",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "llegada est.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticTripRow(
    diag: DiagnosticObservation,
    onDelete: () -> Unit
) {
    val simPct = (diag.similarity * 100.0).roundToInt()
    val huberPct = (diag.huberWeight * 100.0).roundToInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (diag.wasPruned) {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            if (diag.wasPruned) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${TimeFormatters.formatDateTime(diag.timestampMs)} · ${TimeFormatters.formatDurationHuman(diag.durationSec)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = diag.context.summaryLabels().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val status = if (diag.wasPruned) {
                    "Similitud 4D: $simPct% · Ignorado por umbral"
                } else {
                    "Similitud 4D: $simPct% · Peso Huber: $huberPct%"
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (diag.wasPruned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Eliminar registro",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

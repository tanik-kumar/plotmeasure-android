package com.tanik.biharmapmeasure.plotmeasure.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tanik.biharmapmeasure.plotmeasure.model.PdfProject
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings
import com.tanik.biharmapmeasure.plotmeasure.model.SegmentMeasurement
import com.tanik.biharmapmeasure.plotmeasure.ui.PanelTab
import com.tanik.biharmapmeasure.plotmeasure.ui.PlotMeasureUiState
import com.tanik.biharmapmeasure.plotmeasure.ui.formatAreaBreakdown
import com.tanik.biharmapmeasure.plotmeasure.ui.formatCoordinate
import com.tanik.biharmapmeasure.plotmeasure.ui.formatDistance
import com.tanik.biharmapmeasure.plotmeasure.ui.formatMapUnits
import com.tanik.biharmapmeasure.plotmeasure.ui.formatTimestamp

@Composable
fun PlotSidePanel(
    modifier: Modifier = Modifier,
    uiState: PlotMeasureUiState,
    onSelectTab: (PanelTab) -> Unit,
    onSelectPlot: (String) -> Unit,
    onCreatePlot: () -> Unit,
    onDeletePlot: () -> Unit,
    onSelectPoint: (String?) -> Unit,
    onDeleteSelectedPoint: () -> Unit,
    onNudgeSelectedPoint: (Double, Double) -> Unit,
    onLoadProject: (String) -> Unit,
    onUpdateAreaUnitSettings: (Double, Double, Double) -> Unit,
    onUpdateViewerSettings: (Boolean, Boolean) -> Unit,
    onUpdateLayerVisibility: (com.tanik.biharmapmeasure.plotmeasure.model.LayerVisibility) -> Unit,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
        ) {
            PanelTabRow(selectedTab = uiState.panelTab, onSelectTab = onSelectTab)
            when (uiState.panelTab) {
                PanelTab.RESULTS -> ResultsTab(uiState)
                PanelTab.POINTS -> PointsTab(uiState, onSelectPoint, onDeleteSelectedPoint, onNudgeSelectedPoint)
                PanelTab.SEGMENTS -> SegmentsTab(uiState.plotComputation?.segments.orEmpty())
                PanelTab.SETTINGS ->
                    SettingsTab(
                        settings = uiState.settings,
                        onUpdateAreaUnitSettings = onUpdateAreaUnitSettings,
                        onUpdateViewerSettings = onUpdateViewerSettings,
                        onUpdateLayerVisibility = onUpdateLayerVisibility,
                    )
                PanelTab.PROJECTS ->
                    ProjectsTab(
                        uiState = uiState,
                        onSelectPlot = onSelectPlot,
                        onCreatePlot = onCreatePlot,
                        onDeletePlot = onDeletePlot,
                        onLoadProject = onLoadProject,
                    )
            }
        }
    }
}

@Composable
private fun PanelTabRow(
    selectedTab: PanelTab,
    onSelectTab: (PanelTab) -> Unit,
) {
    val tabs =
        listOf(
            PanelTab.RESULTS to "Results",
            PanelTab.POINTS to "Points",
            PanelTab.SEGMENTS to "Segments",
            PanelTab.SETTINGS to "Settings",
            PanelTab.PROJECTS to "Projects",
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { (tab, label) ->
            FilterChip(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ResultsTab(uiState: PlotMeasureUiState) {
    val pageState = uiState.currentPageState
    val plot = uiState.activePlot
    val computation = uiState.plotComputation
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(
            title = plot?.name ?: "No active plot",
            subtitle = pageState?.calibration?.name ?: "No calibration on this page",
            lines =
                listOf(
                    "Page" to ((uiState.project?.selectedPageIndex ?: 0) + 1).toString(),
                    "Mode" to (plot?.mode?.name ?: uiState.currentMode.name),
                    "Points" to (plot?.points?.size ?: 0).toString(),
                    "Perimeter" to formatDistance(computation?.perimeterMeters),
                ),
        )
        if (uiState.warningMessage != null) {
            WarningCard(uiState.warningMessage)
        }
        if (computation?.area != null) {
            SummaryCard(
                title = "Area conversions",
                subtitle = "Live values from the current polygon",
                lines = formatAreaBreakdown(computation.area),
            )
        } else {
            SummaryCard(
                title = "Live measurement",
                subtitle = "Area needs a valid calibrated polygon.",
                lines =
                    listOf(
                        "Map perimeter" to formatMapUnits(computation?.perimeterPageUnits),
                        "Map area" to formatMapUnits(computation?.areaPageUnits),
                    ),
            )
        }
        if (computation?.centroid != null) {
            SummaryCard(
                title = "Centroid",
                subtitle = "Stored in PDF page coordinates",
                lines =
                    listOf(
                        "X" to formatCoordinate(computation.centroid.x),
                        "Y" to formatCoordinate(computation.centroid.y),
                    ),
            )
        }
    }
}

@Composable
private fun PointsTab(
    uiState: PlotMeasureUiState,
    onSelectPoint: (String?) -> Unit,
    onDeleteSelectedPoint: () -> Unit,
    onNudgeSelectedPoint: (Double, Double) -> Unit,
) {
    val plot = uiState.activePlot
    val nudgeStep = uiState.pointNudgeStepPageUnits
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Boundary points", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDeleteSelectedPoint, enabled = uiState.selectedPointId != null) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete selected point")
            }
        }
        SummaryCard(
            title = "Fine move",
            subtitle = "Move the selected point exactly",
            lines = listOf("Step" to "${formatCoordinate(nudgeStep)} page units (2 px)"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { onNudgeSelectedPoint(0.0, -nudgeStep) },
                enabled = uiState.selectedPointId != null,
                label = { Text("Up") },
            )
            AssistChip(
                onClick = { onNudgeSelectedPoint(0.0, nudgeStep) },
                enabled = uiState.selectedPointId != null,
                label = { Text("Down") },
            )
            AssistChip(
                onClick = { onNudgeSelectedPoint(-nudgeStep, 0.0) },
                enabled = uiState.selectedPointId != null,
                label = { Text("Left") },
            )
            AssistChip(
                onClick = { onNudgeSelectedPoint(nudgeStep, 0.0) },
                enabled = uiState.selectedPointId != null,
                label = { Text("Right") },
            )
        }
        if (plot == null || plot.points.isEmpty()) {
            Text("Tap on the PDF to add points. Switch to Edit mode to drag or insert points.")
        } else {
            plot.points.forEachIndexed { index, point ->
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPoint(point.id) },
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (uiState.selectedPointId == point.id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                        ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("P${index + 1}", fontWeight = FontWeight.Bold)
                        Text("X: ${formatCoordinate(point.x)}")
                        Text("Y: ${formatCoordinate(point.y)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentsTab(segments: List<SegmentMeasurement>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Segment lengths", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (segments.isEmpty()) {
            Text("Add at least two points to see segment lengths.")
        } else {
            segments.forEach { segment ->
                SummaryCard(
                    title = "Segment ${segment.index}",
                    subtitle = "${segment.startPointLabel} to ${segment.endPointLabel}",
                    lines =
                        listOf(
                            "Distance" to formatDistance(segment.lengthMeters),
                            "Map length" to formatMapUnits(segment.lengthPageUnits),
                        ),
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    settings: PlotMeasureSettings,
    onUpdateAreaUnitSettings: (Double, Double, Double) -> Unit,
    onUpdateViewerSettings: (Boolean, Boolean) -> Unit,
    onUpdateLayerVisibility: (com.tanik.biharmapmeasure.plotmeasure.model.LayerVisibility) -> Unit,
) {
    var bigha by remember(settings.squareFeetPerBigha) { mutableStateOf(settings.squareFeetPerBigha.toString()) }
    var kattha by remember(settings.squareFeetPerKattha) { mutableStateOf(settings.squareFeetPerKattha.toString()) }
    var dhur by remember(settings.squareFeetPerDhur) { mutableStateOf(settings.squareFeetPerDhur.toString()) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Land unit settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = bigha,
            onValueChange = { value ->
                bigha = value
                val parsedBigha = value.toDoubleOrNull()
                val parsedKattha = kattha.toDoubleOrNull()
                val parsedDhur = dhur.toDoubleOrNull()
                if (parsedBigha != null && parsedKattha != null && parsedDhur != null) {
                    onUpdateAreaUnitSettings(parsedBigha, parsedKattha, parsedDhur)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Square feet per bigha") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = kattha,
            onValueChange = { value ->
                kattha = value
                val parsedBigha = bigha.toDoubleOrNull()
                val parsedKattha = value.toDoubleOrNull()
                val parsedDhur = dhur.toDoubleOrNull()
                if (parsedBigha != null && parsedKattha != null && parsedDhur != null) {
                    onUpdateAreaUnitSettings(parsedBigha, parsedKattha, parsedDhur)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Square feet per kattha") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = dhur,
            onValueChange = { value ->
                dhur = value
                val parsedBigha = bigha.toDoubleOrNull()
                val parsedKattha = kattha.toDoubleOrNull()
                val parsedDhur = value.toDoubleOrNull()
                if (parsedBigha != null && parsedKattha != null && parsedDhur != null) {
                    onUpdateAreaUnitSettings(parsedBigha, parsedKattha, parsedDhur)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Square feet per dhur") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        Text("Viewer helpers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ToggleChipRow(
            title = "Snap to edge",
            selected = settings.snapToEdgeEnabled,
            onToggle = { onUpdateViewerSettings(!settings.snapToEdgeEnabled, settings.showLoupe) },
        )
        ToggleChipRow(
            title = "Show loupe",
            selected = settings.showLoupe,
            onToggle = { onUpdateViewerSettings(settings.snapToEdgeEnabled, !settings.showLoupe) },
        )

        Text("Visible layers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ToggleChipRow(
            title = "Points",
            selected = settings.layerVisibility.showPoints,
            onToggle = { onUpdateLayerVisibility(settings.layerVisibility.copy(showPoints = !settings.layerVisibility.showPoints)) },
        )
        ToggleChipRow(
            title = "Labels",
            selected = settings.layerVisibility.showLabels,
            onToggle = { onUpdateLayerVisibility(settings.layerVisibility.copy(showLabels = !settings.layerVisibility.showLabels)) },
        )
        ToggleChipRow(
            title = "Polygon fill",
            selected = settings.layerVisibility.showPolygonFill,
            onToggle = { onUpdateLayerVisibility(settings.layerVisibility.copy(showPolygonFill = !settings.layerVisibility.showPolygonFill)) },
        )
        ToggleChipRow(
            title = "Segments",
            selected = settings.layerVisibility.showSegments,
            onToggle = { onUpdateLayerVisibility(settings.layerVisibility.copy(showSegments = !settings.layerVisibility.showSegments)) },
        )
        ToggleChipRow(
            title = "Centroid",
            selected = settings.layerVisibility.showCentroid,
            onToggle = { onUpdateLayerVisibility(settings.layerVisibility.copy(showCentroid = !settings.layerVisibility.showCentroid)) },
        )
    }
}

@Composable
private fun ProjectsTab(
    uiState: PlotMeasureUiState,
    onSelectPlot: (String) -> Unit,
    onCreatePlot: () -> Unit,
    onDeletePlot: () -> Unit,
    onLoadProject: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Current page plots", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = onCreatePlot) {
                    Icon(Icons.Outlined.Add, contentDescription = "New plot")
                }
                IconButton(onClick = onDeletePlot, enabled = uiState.activePlot != null) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete plot")
                }
            }
        }

        if (uiState.currentPageState?.plots.isNullOrEmpty()) {
            Text("No saved plot on this page yet.")
        } else {
            uiState.currentPageState?.plots.orEmpty().forEach { plot ->
                SummaryCard(
                    title = plot.name,
                    subtitle = "${plot.mode.name} • ${plot.points.size} points",
                    lines =
                        listOf(
                            "Closed" to plot.isClosed.toString(),
                            "Updated" to formatTimestamp(plot.updatedAt),
                        ),
                    onClick = { onSelectPlot(plot.id) },
                )
            }
        }

        Text("Saved projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (uiState.projectSummaries.isEmpty()) {
            Text("Import a PDF to start your first project.")
        } else {
            uiState.projectSummaries.forEach { summary ->
                SummaryCard(
                    title = summary.name,
                    subtitle = summary.pdfName,
                    lines = listOf("Updated" to formatTimestamp(summary.updatedAt)),
                    onClick = { onLoadProject(summary.id) },
                )
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    subtitle: String,
    lines: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .let {
                    if (onClick != null) {
                        it.clickable(onClick = onClick)
                    } else {
                        it
                    }
                },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            lines.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label)
                    Text(value, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ToggleChipRow(
    title: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        AssistChip(
            onClick = onToggle,
            label = { Text(if (selected) "On" else "Off") },
        )
    }
}

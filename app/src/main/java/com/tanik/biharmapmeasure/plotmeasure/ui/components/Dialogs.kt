package com.tanik.biharmapmeasure.plotmeasure.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tanik.biharmapmeasure.plotmeasure.core.geometry.GeometryEngine
import com.tanik.biharmapmeasure.plotmeasure.model.LinearUnit
import com.tanik.biharmapmeasure.plotmeasure.model.ManualReferenceType
import com.tanik.biharmapmeasure.plotmeasure.model.PdfProject
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings
import com.tanik.biharmapmeasure.plotmeasure.ui.formatCoordinate

@Composable
fun PagePickerDialog(
    pageCount: Int,
    selectedPageIndex: Int,
    onDismiss: () -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose PDF page") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(pageCount) { index ->
                    SummaryCard(
                        title = "Page ${index + 1}",
                        subtitle = if (index == selectedPageIndex) "Current page" else "Tap to open",
                        lines = emptyList(),
                        onClick = { onSelectPage(index) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
fun CalibrationDialog(
    project: PdfProject,
    capturePoints: List<com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPoint>,
    selectedCaptureIndex: Int,
    capturePointsCount: Int,
    capturePointsDistance: Double?,
    nudgeStepPageUnits: Double,
    onDismiss: () -> Unit,
    onStartManualCapture: () -> Unit,
    onResumeManualCapture: () -> Unit,
    onSelectCapturePoint: (Int) -> Unit,
    onNudgeCapturePoint: (Int, Double, Double) -> Unit,
    onApplyManual: (String, Double, LinearUnit, ManualReferenceType, Boolean) -> Unit,
    onApplyRatio: (String, Double, Boolean) -> Unit,
    onApplyTextScale: (String, Double, LinearUnit, Double, LinearUnit, Boolean) -> Unit,
    onApplyPreset: (String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var manualName by remember { mutableStateOf("Page ${project.selectedPageIndex + 1} calibration") }
    var manualDistance by remember { mutableStateOf("") }
    var manualUnit by remember { mutableStateOf(LinearUnit.METER) }
    var referenceType by remember { mutableStateOf(ManualReferenceType.KNOWN_LINE) }
    var manualSavePreset by remember { mutableStateOf(false) }

    var ratioName by remember { mutableStateOf("1:N ratio") }
    var ratioDenominator by remember { mutableStateOf("") }
    var ratioSavePreset by remember { mutableStateOf(false) }

    var textName by remember { mutableStateOf("Text scale") }
    var mapDistance by remember { mutableStateOf("16") }
    var mapUnit by remember { mutableStateOf(LinearUnit.INCH) }
    var groundDistance by remember { mutableStateOf("1") }
    var groundUnit by remember { mutableStateOf(LinearUnit.MILE) }
    var textSavePreset by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calibration") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Manual", "Ratio", "Text", "Presets").forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            label = { Text(label) },
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Calibration name") },
                        )
                        Text(
                            text =
                                if (capturePointsDistance != null) {
                                    "Selected map distance: ${formatCoordinate(capturePointsDistance)} page units"
                                } else {
                                    "Select two points on a known line or scale bar."
                                },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (capturePoints.isNotEmpty()) {
                            val safeSelectedCaptureIndex = selectedCaptureIndex.coerceIn(0, capturePoints.lastIndex)
                            ChoiceSelector(
                                title = "Selected point",
                                selected = "C${safeSelectedCaptureIndex + 1}",
                                options =
                                    capturePoints.mapIndexed { index, _ ->
                                        "C${index + 1}" to { onSelectCapturePoint(index) }
                                    },
                            )
                            SummaryCard(
                                title = "Calibration point move",
                                subtitle = "Adjust the selected calibration point exactly",
                                lines =
                                    listOf(
                                        "Step" to "${formatCoordinate(nudgeStepPageUnits)} page units (2 px)",
                                        "X" to formatCoordinate(capturePoints[safeSelectedCaptureIndex].x),
                                        "Y" to formatCoordinate(capturePoints[safeSelectedCaptureIndex].y),
                                    ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(onClick = { onNudgeCapturePoint(safeSelectedCaptureIndex, 0.0, -nudgeStepPageUnits) }) {
                                    Text("Up")
                                }
                                TextButton(onClick = { onNudgeCapturePoint(safeSelectedCaptureIndex, 0.0, nudgeStepPageUnits) }) {
                                    Text("Down")
                                }
                                TextButton(onClick = { onNudgeCapturePoint(safeSelectedCaptureIndex, -nudgeStepPageUnits, 0.0) }) {
                                    Text("Left")
                                }
                                TextButton(onClick = { onNudgeCapturePoint(safeSelectedCaptureIndex, nudgeStepPageUnits, 0.0) }) {
                                    Text("Right")
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                when {
                                    capturePointsCount in 1 until 2 -> onResumeManualCapture()
                                    else -> onStartManualCapture()
                                }
                            },
                        ) {
                            Text(
                                when {
                                    capturePointsCount >= 2 -> "Capture again"
                                    capturePointsCount == 1 -> "Continue point capture"
                                    else -> "Pick two points on map"
                                }
                            )
                        }
                        OutlinedTextField(
                            value = manualDistance,
                            onValueChange = { manualDistance = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Real distance") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        UnitSelector(
                            title = "Ground unit",
                            units = LinearUnit.defaultGroundUnits(),
                            selectedUnit = manualUnit,
                            onSelect = { manualUnit = it },
                        )
                        ChoiceSelector(
                            title = "Reference type",
                            selected = if (referenceType == ManualReferenceType.KNOWN_LINE) "Known line" else "Scale bar",
                            options =
                                listOf(
                                    "Known line" to { referenceType = ManualReferenceType.KNOWN_LINE },
                                    "Scale bar" to { referenceType = ManualReferenceType.SCALE_BAR },
                                ),
                        )
                        ChoiceSelector(
                            title = "Save as preset",
                            selected = if (manualSavePreset) "Yes" else "No",
                            options =
                                listOf(
                                    "No" to { manualSavePreset = false },
                                    "Yes" to { manualSavePreset = true },
                                ),
                        )
                        TextButton(
                            onClick = {
                                manualDistance.toDoubleOrNull()?.let {
                                    onApplyManual(manualName, it, manualUnit, referenceType, manualSavePreset)
                                }
                            },
                            enabled = capturePointsDistance != null,
                        ) {
                            Text("Apply manual calibration")
                        }
                    }

                    1 -> {
                        OutlinedTextField(
                            value = ratioName,
                            onValueChange = { ratioName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Calibration name") },
                        )
                        OutlinedTextField(
                            value = ratioDenominator,
                            onValueChange = { ratioDenominator = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Scale denominator in 1:N") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        ChoiceSelector(
                            title = "Save as preset",
                            selected = if (ratioSavePreset) "Yes" else "No",
                            options =
                                listOf(
                                    "No" to { ratioSavePreset = false },
                                    "Yes" to { ratioSavePreset = true },
                                ),
                        )
                        TextButton(
                            onClick = {
                                ratioDenominator.toDoubleOrNull()?.let {
                                    onApplyRatio(ratioName, it, ratioSavePreset)
                                }
                            },
                        ) {
                            Text("Apply ratio calibration")
                        }
                    }

                    2 -> {
                        OutlinedTextField(
                            value = textName,
                            onValueChange = { textName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Calibration name") },
                        )
                        OutlinedTextField(
                            value = mapDistance,
                            onValueChange = { mapDistance = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Map distance value") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        UnitSelector(
                            title = "Map unit",
                            units = LinearUnit.defaultMapUnits(),
                            selectedUnit = mapUnit,
                            onSelect = { mapUnit = it },
                        )
                        OutlinedTextField(
                            value = groundDistance,
                            onValueChange = { groundDistance = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ground distance value") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        UnitSelector(
                            title = "Ground unit",
                            units = LinearUnit.defaultGroundUnits(),
                            selectedUnit = groundUnit,
                            onSelect = { groundUnit = it },
                        )
                        ChoiceSelector(
                            title = "Save as preset",
                            selected = if (textSavePreset) "Yes" else "No",
                            options =
                                listOf(
                                    "No" to { textSavePreset = false },
                                    "Yes" to { textSavePreset = true },
                                ),
                        )
                        TextButton(
                            onClick = {
                                val mapValue = mapDistance.toDoubleOrNull()
                                val realValue = groundDistance.toDoubleOrNull()
                                if (mapValue != null && realValue != null) {
                                    onApplyTextScale(textName, mapValue, mapUnit, realValue, groundUnit, textSavePreset)
                                }
                            },
                        ) {
                            Text("Apply text scale")
                        }
                    }

                    else -> {
                        if (project.calibrationPresets.isEmpty()) {
                            Text("No saved presets in this project yet.")
                        } else {
                            project.calibrationPresets.forEach { preset ->
                                SummaryCard(
                                    title = preset.name,
                                    subtitle = preset.method.name,
                                    lines = listOf("Meters / page unit" to preset.metersPerPageUnit.toString()),
                                    onClick = { onApplyPreset(preset.id) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Show map")
            }
        },
    )
}

@Composable
private fun UnitSelector(
    title: String,
    units: List<LinearUnit>,
    selectedUnit: LinearUnit,
    onSelect: (LinearUnit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            units.forEach { unit ->
                FilterChip(
                    selected = unit == selectedUnit,
                    onClick = { onSelect(unit) },
                    label = { Text(unit.label) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceSelector(
    title: String,
    selected: String,
    options: List<Pair<String, () -> Unit>>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (label, action) ->
                FilterChip(
                    selected = label == selected,
                    onClick = action,
                    label = { Text(label) },
                )
            }
        }
    }
}

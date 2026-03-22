package com.tanik.biharmapmeasure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tanik.biharmapmeasure.plotmeasure.ui.PlotMeasureApp
import com.tanik.biharmapmeasure.plotmeasure.ui.PlotMeasureViewModel
import com.tanik.biharmapmeasure.plotmeasure.ui.theme.PlotMeasureTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<PlotMeasureViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlotMeasureTheme {
                PlotMeasureApp(viewModel = viewModel)
            }
        }
    }
}

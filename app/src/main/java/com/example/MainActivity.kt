package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.CalorieLensTheme
import com.example.ui.viewmodel.CalorieLensViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CalorieLensViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalorieLensTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(
    viewModel: CalorieLensViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyMeals by viewModel.historyMeals.collectAsStateWithLifecycle()
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Back handling
    BackHandler(enabled = uiState.activeTab != 0 || uiState.hasResults) {
        if (uiState.activeTab != 0) {
            viewModel.setActiveTab(0)
        } else if (uiState.hasResults) {
            viewModel.resetScanner()
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                0 -> {
                    ScanScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        historyCount = historyMeals.size,
                        onOpenSettings = { showSettingsDialog = true }
                    )
                }
                1 -> {
                    HistoryScreen(
                        meals = historyMeals,
                        onSelectMeal = { meal -> viewModel.viewHistoryDetail(meal) },
                        onDeleteMeal = { mealId -> viewModel.deleteHistoryMeal(mealId) },
                        onGoToScanner = { viewModel.setActiveTab(0) }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            apiKeyRepo = viewModel.apiKeyRepo,
            onDismiss = { showSettingsDialog = false },
            onSaved = { /* updated */ }
        )
    }
}

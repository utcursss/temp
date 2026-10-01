package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.MealEntity
import com.example.data.model.AdjustedFoodItem
import com.example.data.model.FoodAnalysisResponse
import com.example.data.model.FoodItem
import com.example.data.remote.GeminiService
import com.example.data.repository.ApiKeyRepository
import com.example.data.repository.MealRepository
import com.example.utils.ImageCompressor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SampleFoodPreset(
    val title: String,
    val description: String,
    val drawableResId: Int,
    val defaultContext: String,
)

data class UiState(
    val selectedBitmap: Bitmap? = null,
    val selectedBase64: String? = null,
    val selectedThumbnailBase64: String? = null,
    val userContextText: String = "",
    val isLoading: Boolean = false,
    val loadingStep: String = "",
    val errorMessage: String? = null,
    val isFoodDetected: Boolean = true,
    val adjustedItems: List<AdjustedFoodItem> = emptyList(),
    val healthNotes: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val editingItem: AdjustedFoodItem? = null,
    val isReestimatingItem: Boolean = false,
    val isSavedToHistory: Boolean = false,
    val activeTab: Int = 0, // 0 = Scanner, 1 = History
    val viewingHistoryDetail: MealEntity? = null,
) {
    val hasResults: Boolean get() = adjustedItems.isNotEmpty()

    val totalCalories: Int
        get() = adjustedItems.sumOf { it.calories }

    val totalMinCalories: Int
        get() = adjustedItems.sumOf { it.minCalories }

    val totalMaxCalories: Int
        get() = adjustedItems.sumOf { it.maxCalories }

    val totalProtein: Double
        get() = ((adjustedItems.sumOf { it.protein } * 10).toInt()) / 10.0

    val totalCarbs: Double
        get() = ((adjustedItems.sumOf { it.carbs } * 10).toInt()) / 10.0

    val totalFat: Double
        get() = ((adjustedItems.sumOf { it.fat } * 10).toInt()) / 10.0

    val totalFiber: Double
        get() = ((adjustedItems.sumOf { it.fiber } * 10).toInt()) / 10.0

    val totalSugar: Double
        get() = ((adjustedItems.sumOf { it.sugar } * 10).toInt()) / 10.0

    val overallConfidence: Double
        get() = if (adjustedItems.isEmpty()) 0.0 else adjustedItems.map { it.item.confidence }.average()
}

class CalorieLensViewModel(application: Application) : AndroidViewModel(application) {

    private val geminiService = GeminiService()
    val apiKeyRepo = ApiKeyRepository(application)
    private val database = AppDatabase.getDatabase(application)
    val mealRepo = MealRepository(database.mealDao())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val historyMeals: StateFlow<List<MealEntity>> = mealRepo.getAllMeals()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val samplePresets = listOf(
        SampleFoodPreset(
            title = "🍛 Royal Indian Thali",
            description = "Dal makhani, paneer tikka, basmati rice & naan",
            drawableResId = R.drawable.sample_indian_thali_1790833056924,
            defaultContext = "Full traditional restaurant thali with ghee naan and dal"
        ),
        SampleFoodPreset(
            title = "🥞 Masala Dosa Platter",
            description = "Crispy dosa with potato masala, sambar & chutneys",
            drawableResId = R.drawable.sample_masala_dosa_1790833070026,
            defaultContext = "South Indian restaurant style with coconut and tomato chutneys"
        ),
        SampleFoodPreset(
            title = "🥗 Mediterranean Bowl",
            description = "Quinoa, avocado, feta, chickpeas & olive oil",
            drawableResId = R.drawable.sample_avocado_salad_1790833085178,
            defaultContext = "Dressed with extra virgin olive oil and lemon"
        )
    )

    fun setActiveTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex, viewingHistoryDetail = null) }
    }

    fun setUserContext(text: String) {
        _uiState.update { it.copy(userContextText = text) }
    }

    fun setImageUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = ImageCompressor.compressUri(context, uri, maxDimension = 1024)
            if (result != null) {
                _uiState.update {
                    it.copy(
                        selectedBitmap = result.bitmap,
                        selectedBase64 = result.base64Data,
                        selectedThumbnailBase64 = result.thumbnailBase64,
                        errorMessage = null,
                        adjustedItems = emptyList(),
                        isSavedToHistory = false
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to load selected image. Please try another.") }
            }
        }
    }

    fun setImageBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val result = ImageCompressor.compressBitmap(bitmap, maxDimension = 1024)
            _uiState.update {
                it.copy(
                    selectedBitmap = result.bitmap,
                    selectedBase64 = result.base64Data,
                    selectedThumbnailBase64 = result.thumbnailBase64,
                    errorMessage = null,
                    adjustedItems = emptyList(),
                    isSavedToHistory = false
                )
            }
        }
    }

    fun loadSamplePreset(preset: SampleFoodPreset, context: Context) {
        viewModelScope.launch {
            val result = ImageCompressor.compressResource(context, preset.drawableResId, maxDimension = 1024)
            if (result != null) {
                _uiState.update {
                    it.copy(
                        selectedBitmap = result.bitmap,
                        selectedBase64 = result.base64Data,
                        selectedThumbnailBase64 = result.thumbnailBase64,
                        userContextText = preset.defaultContext,
                        errorMessage = null,
                        adjustedItems = emptyList(),
                        isSavedToHistory = false
                    )
                }
            }
        }
    }

    fun analyzeImage() {
        val base64 = _uiState.value.selectedBase64
        if (base64.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Please upload or capture a food photo first.") }
            return
        }

        val apiKey = apiKeyRepo.getActiveApiKey()
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Gemini API Key is missing. Please add your key in the Settings dialog to start analyzing."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingStep = "Scanning image & detecting dishes...",
                    errorMessage = null
                )
            }

            // Simulate progress step updates while network executes
            val progressJob = launch {
                delay(1200)
                _uiState.update { it.copy(loadingStep = "Identifying regional ingredients & allergens...") }
                delay(1500)
                _uiState.update { it.copy(loadingStep = "Calculating portion densities & calories...") }
            }

            val result = geminiService.analyzeFood(
                imageBase64 = base64,
                userContext = _uiState.value.userContextText,
                apiKey = apiKey,
                modelName = apiKeyRepo.getSelectedModel()
            )

            progressJob.cancel()

            result.onSuccess { response ->
                if (!response.isFood || response.items.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isFoodDetected = false,
                            adjustedItems = emptyList(),
                            healthNotes = response.healthNotes.ifEmpty {
                                listOf("No recognizable food or beverage was detected in this photo.")
                            },
                            assumptions = response.assumptions.ifEmpty {
                                listOf("Try taking a clearer, well-lit photo of your meal.")
                            }
                        )
                    }
                } else {
                    val adjusted = response.items.map { item ->
                        AdjustedFoodItem(item = item, customName = item.name, multiplier = 1.0f)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isFoodDetected = true,
                            adjustedItems = adjusted,
                            healthNotes = response.healthNotes,
                            assumptions = response.assumptions,
                            errorMessage = null,
                            isSavedToHistory = false
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to analyze food image. Please check your connection or API key."
                    )
                }
            }
        }
    }

    fun updatePortionMultiplier(itemId: String, multiplier: Float) {
        val clamped = multiplier.coerceIn(0.5f, 3.0f)
        _uiState.update { state ->
            val updated = state.adjustedItems.map {
                if (it.id == itemId) it.copy(multiplier = clamped) else it
            }
            state.copy(adjustedItems = updated, isSavedToHistory = false)
        }
    }

    fun startEditItem(item: AdjustedFoodItem) {
        _uiState.update { it.copy(editingItem = item) }
    }

    fun dismissEditItem() {
        _uiState.update { it.copy(editingItem = null) }
    }

    fun applyItemNameEdit(itemId: String, newName: String) {
        _uiState.update { state ->
            val updated = state.adjustedItems.map {
                if (it.id == itemId) it.copy(customName = newName.trim()) else it
            }
            state.copy(adjustedItems = updated, editingItem = null, isSavedToHistory = false)
        }
    }

    fun reestimateEditedItem(itemId: String, newName: String, portionNote: String) {
        val apiKey = apiKeyRepo.getActiveApiKey()
        if (apiKey.isBlank()) {
            applyItemNameEdit(itemId, newName)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isReestimatingItem = true) }
            val result = geminiService.reestimateSingleItem(
                foodName = newName,
                portionContext = portionNote,
                apiKey = apiKey,
                modelName = apiKeyRepo.getSelectedModel()
            )
            _uiState.update { it.copy(isReestimatingItem = false) }

            result.onSuccess { newItem ->
                _uiState.update { state ->
                    val updated = state.adjustedItems.map {
                        if (it.id == itemId) {
                            it.copy(item = newItem, customName = newItem.name)
                        } else it
                    }
                    state.copy(adjustedItems = updated, editingItem = null, isSavedToHistory = false)
                }
            }.onFailure {
                // If re-query fails, at least apply name change
                applyItemNameEdit(itemId, newName)
            }
        }
    }

    fun removeItem(itemId: String) {
        _uiState.update { state ->
            val updated = state.adjustedItems.filterNot { it.id == itemId }
            state.copy(adjustedItems = updated, isSavedToHistory = false)
        }
    }

    fun saveCurrentMealToHistory() {
        val state = _uiState.value
        if (state.adjustedItems.isEmpty() || state.isSavedToHistory) return

        viewModelScope.launch {
            val title = if (state.adjustedItems.size == 1) {
                state.adjustedItems.first().customName
            } else {
                "${state.adjustedItems.first().customName} & ${state.adjustedItems.size - 1} more"
            }

            mealRepo.saveMeal(
                title = title,
                items = state.adjustedItems,
                totalCalories = state.totalCalories,
                totalProtein = state.totalProtein,
                totalCarbs = state.totalCarbs,
                totalFat = state.totalFat,
                healthNotes = state.healthNotes,
                assumptions = state.assumptions,
                thumbnailBase64 = state.selectedThumbnailBase64
            )
            _uiState.update { it.copy(isSavedToHistory = true) }
        }
    }

    fun deleteHistoryMeal(mealId: Long) {
        viewModelScope.launch {
            mealRepo.deleteMeal(mealId)
            if (_uiState.value.viewingHistoryDetail?.id == mealId) {
                _uiState.update { it.copy(viewingHistoryDetail = null) }
            }
        }
    }

    fun viewHistoryDetail(meal: MealEntity) {
        val items = mealRepo.parseItems(meal.itemsJson)
        val adjusted = items.map { item ->
            AdjustedFoodItem(item = item, customName = item.name, multiplier = 1.0f)
        }
        val notes = mealRepo.parseStringList(meal.healthNotesJson)
        val assumptions = mealRepo.parseStringList(meal.assumptionsJson)
        val bitmap = meal.thumbnailBase64?.let { ImageCompressor.decodeBase64ToBitmap(it) }

        _uiState.update {
            it.copy(
                activeTab = 0,
                selectedBitmap = bitmap ?: it.selectedBitmap,
                adjustedItems = adjusted,
                healthNotes = notes,
                assumptions = assumptions,
                isFoodDetected = true,
                isSavedToHistory = true,
                errorMessage = null,
                viewingHistoryDetail = meal
            )
        }
    }

    fun resetScanner() {
        _uiState.update {
            UiState(activeTab = 0)
        }
    }
}

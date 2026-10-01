package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class CalorieRange(
    @Json(name = "min") val min: Double = 0.0,
    @Json(name = "max") val max: Double = 0.0,
) {
    val minCalories: Int get() = min.roundToInt()
    val maxCalories: Int get() = max.roundToInt()
}

@JsonClass(generateAdapter = true)
data class Macros(
    @Json(name = "protein") val protein: Double = 0.0,
    @Json(name = "carbs") val carbs: Double = 0.0,
    @Json(name = "fat") val fat: Double = 0.0,
    @Json(name = "fiber") val fiber: Double = 0.0,
    @Json(name = "sugar") val sugar: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class FoodItem(
    @Json(name = "name") val name: String = "",
    @Json(name = "cuisine") val cuisine: String = "Universal",
    @Json(name = "estimated_portion") val estimatedPortion: String = "1 serving",
    @Json(name = "portion_grams") val portionGrams: Double = 100.0,
    @Json(name = "calories_kcal") val caloriesKcal: Double = 0.0,
    @Json(name = "calorie_range_kcal") val calorieRangeKcal: CalorieRange = CalorieRange(),
    @Json(name = "macros_g") val macrosG: Macros = Macros(),
    @Json(name = "key_micronutrients") val keyMicronutrients: List<String> = emptyList(),
    @Json(name = "common_allergens") val commonAllergens: List<String> = emptyList(),
    @Json(name = "diet_tags") val dietTags: List<String> = emptyList(),
    @Json(name = "confidence") val confidence: Double = 0.85,
) {
    val calories: Int get() = caloriesKcal.roundToInt()
}

@JsonClass(generateAdapter = true)
data class MealTotal(
    @Json(name = "calories_kcal") val caloriesKcal: Double = 0.0,
    @Json(name = "protein") val protein: Double = 0.0,
    @Json(name = "carbs") val carbs: Double = 0.0,
    @Json(name = "fat") val fat: Double = 0.0,
) {
    val calories: Int get() = caloriesKcal.roundToInt()
}

@JsonClass(generateAdapter = true)
data class FoodAnalysisResponse(
    @Json(name = "is_food") val isFood: Boolean = true,
    @Json(name = "items") val items: List<FoodItem> = emptyList(),
    @Json(name = "meal_total") val mealTotal: MealTotal = MealTotal(),
    @Json(name = "health_notes") val healthNotes: List<String> = emptyList(),
    @Json(name = "assumptions") val assumptions: List<String> = emptyList(),
)

data class AdjustedFoodItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val item: FoodItem,
    val customName: String = item.name,
    val multiplier: Float = 1.0f,
) {
    val calories: Int
        get() = (item.caloriesKcal * multiplier).roundToInt()
    val portionGrams: Int
        get() = (item.portionGrams * multiplier).roundToInt()
    val protein: Double
        get() = ((item.macrosG.protein * multiplier * 10.0).roundToInt()) / 10.0
    val carbs: Double
        get() = ((item.macrosG.carbs * multiplier * 10.0).roundToInt()) / 10.0
    val fat: Double
        get() = ((item.macrosG.fat * multiplier * 10.0).roundToInt()) / 10.0
    val fiber: Double
        get() = ((item.macrosG.fiber * multiplier * 10.0).roundToInt()) / 10.0
    val sugar: Double
        get() = ((item.macrosG.sugar * multiplier * 10.0).roundToInt()) / 10.0
    val minCalories: Int
        get() = (item.calorieRangeKcal.min * multiplier).roundToInt()
    val maxCalories: Int
        get() = (item.calorieRangeKcal.max * multiplier).roundToInt()
}

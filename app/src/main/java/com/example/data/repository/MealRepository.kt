package com.example.data.repository

import com.example.data.local.MealDao
import com.example.data.local.MealEntity
import com.example.data.model.AdjustedFoodItem
import com.example.data.model.FoodItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

class MealRepository(private val mealDao: MealDao) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val foodItemListType = Types.newParameterizedType(List::class.java, FoodItem::class.java)
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)

    private val foodItemListAdapter = moshi.adapter<List<FoodItem>>(foodItemListType)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    fun getAllMeals(): Flow<List<MealEntity>> = mealDao.getAllMeals()

    suspend fun saveMeal(
        title: String,
        items: List<AdjustedFoodItem>,
        totalCalories: Int,
        totalProtein: Double,
        totalCarbs: Double,
        totalFat: Double,
        healthNotes: List<String>,
        assumptions: List<String>,
        thumbnailBase64: String?,
    ): Long {
        val convertedFoodItems = items.map { adjusted ->
            adjusted.item.copy(
                name = adjusted.customName,
                portionGrams = adjusted.portionGrams.toDouble(),
                caloriesKcal = adjusted.calories.toDouble(),
                macrosG = adjusted.item.macrosG.copy(
                    protein = adjusted.protein,
                    carbs = adjusted.carbs,
                    fat = adjusted.fat,
                    fiber = adjusted.fiber,
                    sugar = adjusted.sugar,
                )
            )
        }

        val itemsJson = foodItemListAdapter.toJson(convertedFoodItems)
        val notesJson = stringListAdapter.toJson(healthNotes)
        val assumptionsJson = stringListAdapter.toJson(assumptions)

        val entity = MealEntity(
            mealTitle = title,
            thumbnailBase64 = thumbnailBase64,
            totalCalories = totalCalories,
            totalProtein = totalProtein,
            totalCarbs = totalCarbs,
            totalFat = totalFat,
            itemsJson = itemsJson,
            healthNotesJson = notesJson,
            assumptionsJson = assumptionsJson,
        )
        return mealDao.insertMeal(entity)
    }

    suspend fun deleteMeal(id: Long) {
        mealDao.deleteMealById(id)
    }

    fun parseItems(itemsJson: String): List<FoodItem> {
        return try {
            foodItemListAdapter.fromJson(itemsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseStringList(json: String): List<String> {
        return try {
            stringListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

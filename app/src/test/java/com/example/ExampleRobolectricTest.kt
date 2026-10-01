package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AdjustedFoodItem
import com.example.data.model.CalorieRange
import com.example.data.model.FoodItem
import com.example.data.model.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CalorieLens", appName)
    }

    @Test
    fun `test portion multiplier scaling`() {
        val baseItem = FoodItem(
            name = "Masala Dosa",
            cuisine = "South Indian",
            estimatedPortion = "1 medium dosa",
            portionGrams = 200.0,
            caloriesKcal = 350.0,
            calorieRangeKcal = CalorieRange(320.0, 390.0),
            macrosG = Macros(protein = 8.0, carbs = 54.0, fat = 12.0, fiber = 4.0, sugar = 2.0),
            confidence = 0.92
        )

        // 1.0x baseline
        val adjusted1x = AdjustedFoodItem(item = baseItem, multiplier = 1.0f)
        assertEquals(350, adjusted1x.calories)
        assertEquals(200, adjusted1x.portionGrams)
        assertEquals(8.0, adjusted1x.protein, 0.01)
        assertEquals(54.0, adjusted1x.carbs, 0.01)
        assertEquals(12.0, adjusted1x.fat, 0.01)

        // 1.5x portion scaling
        val adjusted1_5x = AdjustedFoodItem(item = baseItem, multiplier = 1.5f)
        assertEquals(525, adjusted1_5x.calories)
        assertEquals(300, adjusted1_5x.portionGrams)
        assertEquals(12.0, adjusted1_5x.protein, 0.01)
        assertEquals(81.0, adjusted1_5x.carbs, 0.01)
        assertEquals(18.0, adjusted1_5x.fat, 0.01)

        // 0.5x half portion
        val adjustedHalf = AdjustedFoodItem(item = baseItem, multiplier = 0.5f)
        assertEquals(175, adjustedHalf.calories)
        assertEquals(100, adjustedHalf.portionGrams)
        assertEquals(4.0, adjustedHalf.protein, 0.01)
    }
}

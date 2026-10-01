package com.example.data.remote

import android.util.Log
import com.example.data.model.CalorieRange
import com.example.data.model.FoodAnalysisResponse
import com.example.data.model.FoodItem
import com.example.data.model.Macros
import com.example.data.model.MealTotal
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(FoodAnalysisResponse::class.java)
    private val itemAdapter = moshi.adapter(FoodItem::class.java)

    companion object {
        private const val TAG = "GeminiService"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    }

    suspend fun analyzeFood(
        imageBase64: String,
        userContext: String,
        apiKey: String,
        modelName: String = "gemini-2.5-flash",
    ): Result<FoodAnalysisResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please set GEMINI_API_KEY in the Secrets panel or app Settings.")
            )
        }

        try {
            val endpoint = "$BASE_URL$modelName:generateContent?key=$apiKey"

            val promptText = buildString {
                append("Analyze this food photograph and estimate complete nutritional information. ")
                if (userContext.isNotBlank()) {
                    append("User notes/context: \"$userContext\". ")
                }
                append("Detect all individual food items on the plate or thali. ")
                append("Accurately identify Indian and world regional dishes (e.g. thali, poha, misal pav, dosa, biryani, curries, breads, bowls, etc.). ")
                append("If the image does not show any food or drink, set is_food to false with empty items. ")
                append("Return strictly valid JSON adhering to the specified schema.")
            }

            val requestJson = JSONObject().apply {
                // Contents
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptText)
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", imageBase64)
                                })
                            })
                        })
                    })
                })

                // Generation Config with JSON Schema
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                    put("responseSchema", buildResponseSchema())
                })

                // System Instruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are CalorieLens, an authoritative food recognition and clinical nutritional estimation engine. Your task is to accurately calculate calories, macros (protein, carbs, fat, fiber, sugar in grams), portion sizes, diet tags, and allergens. Ensure realistic portion estimation for all cuisines including Indian, Asian, Mediterranean, and Western foods.")
                        })
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e(TAG, "API call failed with code ${response.code}: $responseBody")
                val errorMsg = parseErrorMessage(responseBody)
                return@withContext Result.failure(Exception("Gemini API error ($errorMsg)"))
            }

            val textContent = extractTextContent(responseBody)
            if (textContent.isBlank()) {
                return@withContext Result.failure(Exception("Empty response received from Gemini"))
            }

            val parsedResponse = parseAnalysisResponse(textContent)
            Result.success(parsedResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing food", e)
            Result.failure(e)
        }
    }

    suspend fun reestimateSingleItem(
        foodName: String,
        portionContext: String,
        apiKey: String,
        modelName: String = "gemini-2.5-flash",
    ): Result<FoodItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        try {
            val endpoint = "$BASE_URL$modelName:generateContent?key=$apiKey"

            val prompt = buildString {
                append("Provide accurate nutritional data for the food item: \"$foodName\". ")
                if (portionContext.isNotBlank()) {
                    append("Portion description: \"$portionContext\". ")
                } else {
                    append("Standard restaurant/home serving. ")
                }
                append("Return strictly JSON conforming to the FoodItem schema.")
            }

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                    put("responseSchema", buildSingleItemSchema())
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API error: ${parseErrorMessage(responseBody)}"))
            }

            val textContent = extractTextContent(responseBody)
            val item = parseSingleItem(textContent)
            Result.success(item)
        } catch (e: Exception) {
            Log.e(TAG, "Error re-estimating item", e)
            Result.failure(e)
        }
    }

    private fun extractTextContent(apiResponseJson: String): String {
        return try {
            val root = JSONObject(apiResponseJson)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            if (parts.length() == 0) return ""
            parts.getJSONObject(0).optString("text", "")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text content", e)
            ""
        }
    }

    private fun parseAnalysisResponse(jsonText: String): FoodAnalysisResponse {
        val cleanJson = cleanJsonString(jsonText)
        return try {
            responseAdapter.fromJson(cleanJson) ?: manualParseResponse(cleanJson)
        } catch (e: Exception) {
            Log.w(TAG, "Moshi parse failed, falling back to manual parse: ${e.message}")
            manualParseResponse(cleanJson)
        }
    }

    private fun manualParseResponse(cleanJson: String): FoodAnalysisResponse {
        val root = JSONObject(cleanJson)
        val isFood = root.optBoolean("is_food", true)
        val itemsList = mutableListOf<FoodItem>()

        val itemsArr = root.optJSONArray("items")
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val itemObj = itemsArr.optJSONObject(i) ?: continue
                itemsList.add(parseItemObject(itemObj))
            }
        }

        val mealTotalObj = root.optJSONObject("meal_total")
        val mealTotal = if (mealTotalObj != null) {
            MealTotal(
                caloriesKcal = mealTotalObj.optDouble("calories_kcal", 0.0),
                protein = mealTotalObj.optDouble("protein", 0.0),
                carbs = mealTotalObj.optDouble("carbs", 0.0),
                fat = mealTotalObj.optDouble("fat", 0.0)
            )
        } else {
            MealTotal(
                caloriesKcal = itemsList.sumOf { it.caloriesKcal },
                protein = itemsList.sumOf { it.macrosG.protein },
                carbs = itemsList.sumOf { it.macrosG.carbs },
                fat = itemsList.sumOf { it.macrosG.fat }
            )
        }

        val healthNotes = parseJsonStringArray(root.optJSONArray("health_notes"))
        val assumptions = parseJsonStringArray(root.optJSONArray("assumptions"))

        return FoodAnalysisResponse(
            isFood = isFood,
            items = itemsList,
            mealTotal = mealTotal,
            healthNotes = healthNotes,
            assumptions = assumptions
        )
    }

    private fun parseSingleItem(jsonText: String): FoodItem {
        val clean = cleanJsonString(jsonText)
        return try {
            itemAdapter.fromJson(clean) ?: parseItemObject(JSONObject(clean))
        } catch (e: Exception) {
            parseItemObject(JSONObject(clean))
        }
    }

    private fun parseItemObject(itemObj: JSONObject): FoodItem {
        val rangeObj = itemObj.optJSONObject("calorie_range_kcal")
        val range = if (rangeObj != null) {
            CalorieRange(
                min = rangeObj.optDouble("min", 0.0),
                max = rangeObj.optDouble("max", 0.0)
            )
        } else {
            val cal = itemObj.optDouble("calories_kcal", 0.0)
            CalorieRange(cal * 0.9, cal * 1.1)
        }

        val macrosObj = itemObj.optJSONObject("macros_g")
        val macros = if (macrosObj != null) {
            Macros(
                protein = macrosObj.optDouble("protein", 0.0),
                carbs = macrosObj.optDouble("carbs", 0.0),
                fat = macrosObj.optDouble("fat", 0.0),
                fiber = macrosObj.optDouble("fiber", 0.0),
                sugar = macrosObj.optDouble("sugar", 0.0)
            )
        } else {
            Macros()
        }

        return FoodItem(
            name = itemObj.optString("name", "Food Item"),
            cuisine = itemObj.optString("cuisine", "General"),
            estimatedPortion = itemObj.optString("estimated_portion", "1 portion"),
            portionGrams = itemObj.optDouble("portion_grams", 100.0),
            caloriesKcal = itemObj.optDouble("calories_kcal", 0.0),
            calorieRangeKcal = range,
            macrosG = macros,
            keyMicronutrients = parseJsonStringArray(itemObj.optJSONArray("key_micronutrients")),
            commonAllergens = parseJsonStringArray(itemObj.optJSONArray("common_allergens")),
            dietTags = parseJsonStringArray(itemObj.optJSONArray("diet_tags")),
            confidence = itemObj.optDouble("confidence", 0.85)
        )
    }

    private fun parseJsonStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) list.add(s)
        }
        return list
    }

    private fun cleanJsonString(input: String): String {
        var trimmed = input.trim()
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json").trim()
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```").trim()
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```").trim()
        }
        return trimmed
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val root = JSONObject(errorBody)
            val error = root.optJSONObject("error")
            error?.optString("message", "Unknown error") ?: "Error response"
        } catch (e: Exception) {
            "Network request failed"
        }
    }

    private fun buildResponseSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("is_food", JSONObject().apply {
                    put("type", "BOOLEAN")
                    put("description", "Whether food or beverage is detected in the image")
                })
                put("items", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", buildSingleItemSchema())
                })
                put("meal_total", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("calories_kcal", JSONObject().apply { put("type", "NUMBER") })
                        put("protein", JSONObject().apply { put("type", "NUMBER") })
                        put("carbs", JSONObject().apply { put("type", "NUMBER") })
                        put("fat", JSONObject().apply { put("type", "NUMBER") })
                    })
                    put("required", JSONArray().apply {
                        put("calories_kcal")
                        put("protein")
                        put("carbs")
                        put("fat")
                    })
                })
                put("health_notes", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply { put("type", "STRING") })
                })
                put("assumptions", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply { put("type", "STRING") })
                })
            })
            put("required", JSONArray().apply {
                put("is_food")
                put("items")
                put("meal_total")
                put("health_notes")
                put("assumptions")
            })
        }
    }

    private fun buildSingleItemSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("name", JSONObject().apply { put("type", "STRING") })
                put("cuisine", JSONObject().apply { put("type", "STRING") })
                put("estimated_portion", JSONObject().apply { put("type", "STRING") })
                put("portion_grams", JSONObject().apply { put("type", "NUMBER") })
                put("calories_kcal", JSONObject().apply { put("type", "NUMBER") })
                put("calorie_range_kcal", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("min", JSONObject().apply { put("type", "NUMBER") })
                        put("max", JSONObject().apply { put("type", "NUMBER") })
                    })
                    put("required", JSONArray().apply { put("min"); put("max") })
                })
                put("macros_g", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("protein", JSONObject().apply { put("type", "NUMBER") })
                        put("carbs", JSONObject().apply { put("type", "NUMBER") })
                        put("fat", JSONObject().apply { put("type", "NUMBER") })
                        put("fiber", JSONObject().apply { put("type", "NUMBER") })
                        put("sugar", JSONObject().apply { put("type", "NUMBER") })
                    })
                    put("required", JSONArray().apply {
                        put("protein"); put("carbs"); put("fat"); put("fiber"); put("sugar")
                    })
                })
                put("key_micronutrients", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply { put("type", "STRING") })
                })
                put("common_allergens", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply { put("type", "STRING") })
                })
                put("diet_tags", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply { put("type", "STRING") })
                })
                put("confidence", JSONObject().apply { put("type", "NUMBER") })
            })
            put("required", JSONArray().apply {
                put("name")
                put("cuisine")
                put("estimated_portion")
                put("portion_grams")
                put("calories_kcal")
                put("calorie_range_kcal")
                put("macros_g")
                put("key_micronutrients")
                put("common_allergens")
                put("diet_tags")
                put("confidence")
            })
        }
    }
}

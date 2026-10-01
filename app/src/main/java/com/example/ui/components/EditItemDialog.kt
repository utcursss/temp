package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AdjustedFoodItem

@Composable
fun EditItemDialog(
    item: AdjustedFoodItem,
    isReestimating: Boolean,
    onDismiss: () -> Unit,
    onSaveNameOnly: (newName: String) -> Unit,
    onReestimateWithAi: (newName: String, portionNote: String) -> Unit,
) {
    var nameInput by remember { mutableStateOf(item.customName) }
    var portionInput by remember { mutableStateOf(item.item.estimatedPortion) }

    AlertDialog(
        onDismissRequest = { if (!isReestimating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit Item & Nutrition",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "If this dish was misidentified or has a specific recipe, rename it below to recalculate with AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Food / Dish Name") },
                    placeholder = { Text("e.g. Paneer Butter Masala") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_food_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = portionInput,
                    onValueChange = { portionInput = it },
                    label = { Text("Portion / Cooking Notes (Optional)") },
                    placeholder = { Text("e.g. 1 bowl (~200g), cooked with olive oil") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_portion_context_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                if (isReestimating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Querying Gemini for updated nutrition...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameInput.isNotBlank()) {
                        onReestimateWithAi(nameInput, portionInput)
                    }
                },
                enabled = !isReestimating && nameInput.isNotBlank(),
                modifier = Modifier.testTag("requery_nutrition_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Re-query AI")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isReestimating,
                    modifier = Modifier.testTag("cancel_edit_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            onSaveNameOnly(nameInput)
                        }
                    },
                    enabled = !isReestimating && nameInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Rename Only")
                }
            }
        }
    )
}

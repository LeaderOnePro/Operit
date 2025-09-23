package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.components.CustomScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageStatisticsScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    
    // State to hold token data for all provider models
    val providerModelTokenUsage = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    // State to hold custom pricing for each model
    val modelPricing = remember { mutableStateMapOf<String, Pair<Double, Double>>() }
    var showPricingDialog by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    
    // Collect tokens for ALL provider models from ApiPreferences
    LaunchedEffect(Unit) {
        scope.launch {
            apiPreferences.allProviderModelTokensFlow.collect { tokensMap ->
                providerModelTokenUsage.clear()
                providerModelTokenUsage.putAll(tokensMap)
                
                // Initialize pricing for new models with default values
                tokensMap.keys.forEach { model ->
                    if (!modelPricing.containsKey(model)) {
                        // Default pricing in RMB (Claude-3.5-Sonnet pricing as default)
                        modelPricing[model] = Pair(21.6, 108.0) // ¥21.6/1M input, ¥108/1M output
                    }
                }
            }
        }
    }
    
    // Load custom pricing from preferences
    LaunchedEffect(Unit) {
        scope.launch {
            providerModelTokenUsage.keys.forEach { model ->
                val inputPrice = apiPreferences.getModelInputPrice(model)
                val outputPrice = apiPreferences.getModelOutputPrice(model)
                if (inputPrice > 0.0 || outputPrice > 0.0) {
                    modelPricing[model] = Pair(inputPrice, outputPrice)
                }
            }
        }
    }

    // Calculate costs for each provider model using custom pricing
    val providerModelCosts = providerModelTokenUsage.mapValues { (model, tokens) ->
        val pricing = modelPricing[model] ?: Pair(21.6, 108.0)
        val inputCost = tokens.first.toDouble() * pricing.first / 1_000_000
        val outputCost = tokens.second.toDouble() * pricing.second / 1_000_000
        inputCost + outputCost
    }

    val totalInputTokens = providerModelTokenUsage.values.sumOf { it.first }
    val totalOutputTokens = providerModelTokenUsage.values.sumOf { it.second }
    val totalCost = providerModelCosts.values.sum()
    val totalTokens = totalInputTokens + totalOutputTokens

    CustomScaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showResetDialog = true },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = stringResource(id = R.string.settings_reset_all_counts)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_usage_summary),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.settings_total_tokens),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$totalTokens",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(id = R.string.settings_total_cost),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "¥${String.format("%.2f", totalCost)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_model_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.settings_click_to_edit_pricing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Model details
            val sortedProviderModels = providerModelTokenUsage.entries.sortedBy { it.key }
            
            if (sortedProviderModels.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(id = R.string.settings_no_token_records),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(sortedProviderModels) { (providerModel, tokens) ->
                    val (input, output) = tokens
                    val cost = providerModelCosts[providerModel] ?: 0.0
                    val pricing = modelPricing[providerModel] ?: Pair(21.6, 108.0)
                    
                    TokenUsageModelCard(
                        modelName = providerModel,
                        inputTokens = input,
                        outputTokens = output,
                        cost = cost,
                        inputPrice = pricing.first,
                        outputPrice = pricing.second,
                        onClick = {
                            selectedModel = providerModel
                            showPricingDialog = true
                        }
                    )
                }
            }
        }
    }

    // Pricing Dialog
    if (showPricingDialog && selectedModel.isNotEmpty()) {
        val currentPricing = modelPricing[selectedModel] ?: Pair(21.6, 108.0)
        var inputPrice by remember { mutableStateOf(currentPricing.first.toString()) }
        var outputPrice by remember { mutableStateOf(currentPricing.second.toString()) }
        
        AlertDialog(
            onDismissRequest = { showPricingDialog = false },
            title = {
                Text(text = stringResource(id = R.string.settings_edit_model_pricing, selectedModel))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(id = R.string.settings_pricing_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = inputPrice,
                        onValueChange = { inputPrice = it },
                        label = { Text(stringResource(id = R.string.settings_input_price_per_million)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = outputPrice,
                        onValueChange = { outputPrice = it },
                        label = { Text(stringResource(id = R.string.settings_output_price_per_million)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val inputPriceDouble = inputPrice.toDoubleOrNull() ?: 21.6
                        val outputPriceDouble = outputPrice.toDoubleOrNull() ?: 108.0
                        
                        modelPricing[selectedModel] = Pair(inputPriceDouble, outputPriceDouble)
                        
                        scope.launch {
                            apiPreferences.setModelInputPrice(selectedModel, inputPriceDouble)
                            apiPreferences.setModelOutputPrice(selectedModel, outputPriceDouble)
                        }
                        
                        showPricingDialog = false
                    }
                ) {
                    Text(stringResource(id = R.string.settings_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPricingDialog = false }
                ) {
                    Text(stringResource(id = R.string.settings_cancel))
                }
            }
        )
    }

    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(text = stringResource(id = R.string.settings_reset_confirmation))
            },
            text = {
                Text(text = stringResource(id = R.string.settings_reset_warning))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            apiPreferences.resetAllProviderModelTokenCounts()
                        }
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(id = R.string.settings_reset))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text(stringResource(id = R.string.settings_cancel))
                }
            }
        )
    }
}

@Composable
private fun TokenUsageModelCard(
    modelName: String,
    inputTokens: Int,
    outputTokens: Int,
    cost: Double,
    inputPrice: Double,
    outputPrice: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(id = R.string.settings_edit_pricing),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.settings_input_tokens),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$inputTokens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(id = R.string.settings_price_format, inputPrice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(id = R.string.settings_output_tokens),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$outputTokens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(id = R.string.settings_price_format, outputPrice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(id = R.string.settings_total_cost),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "¥${String.format("%.2f", cost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
} 
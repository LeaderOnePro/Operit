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
import com.ai.assistance.operit.util.TokenCacheManager

private const val DEFAULT_INPUT_PRICE = 2.0
private const val DEFAULT_OUTPUT_PRICE = 3.0
private const val DEFAULT_CACHED_INPUT_PRICE = 0.2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageStatisticsScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    
    // State to hold token data for all provider models
    val providerModelTokenUsage = remember { mutableStateMapOf<String, Triple<Int, Int, Int>>() }
    // State to hold custom pricing for each model (input, output, cached input)
    val modelPricing = remember { mutableStateMapOf<String, Triple<Double, Double, Double>>() }
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
                        // Default pricing in RMB
                        modelPricing[model] = Triple(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE, DEFAULT_CACHED_INPUT_PRICE) // ¥2/1M input, ¥3/1M output, ¥0.2/1M cached input
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
                val cachedInputPrice = apiPreferences.getModelCachedInputPrice(model)
                if (inputPrice > 0.0 || outputPrice > 0.0 || cachedInputPrice > 0.0) {
                    modelPricing[model] = Triple(inputPrice, outputPrice, cachedInputPrice)
                }
            }
        }
    }

    // Calculate costs for each provider model using custom pricing
    val providerModelCosts = providerModelTokenUsage.mapValues { (model, tokens) ->
        val pricing = modelPricing[model] ?: Triple(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE, DEFAULT_CACHED_INPUT_PRICE)
        // tokens.first = total input, tokens.second = output, tokens.third = cached input
        val nonCachedInput = tokens.first - tokens.third
        (nonCachedInput / 1_000_000.0 * pricing.first) + (tokens.second / 1_000_000.0 * pricing.second) + (tokens.third / 1_000_000.0 * pricing.third)
    }

    val totalInputTokens = providerModelTokenUsage.values.sumOf { it.first }
    val totalOutputTokens = providerModelTokenUsage.values.sumOf { it.second }
    val totalCachedInputTokens = providerModelTokenUsage.values.sumOf { it.third }
    val totalTokens = totalInputTokens + totalOutputTokens

    // Calculate total cost across all models
    val totalCost = providerModelCosts.values.sum()

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
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Token breakdown by type
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.settings_input_tokens),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$totalInputTokens",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.settings_output_tokens),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$totalOutputTokens",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            if (totalCachedInputTokens > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_cached_tokens_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "$totalCachedInputTokens",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
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
                    val (input, output, cached) = tokens
                    val cost = providerModelCosts[providerModel] ?: 0.0
                    val pricing = modelPricing[providerModel] ?: Triple(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE, DEFAULT_CACHED_INPUT_PRICE)
                    
                    TokenUsageModelCard(
                        modelName = providerModel,
                        inputTokens = input,
                        cachedInputTokens = cached,
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
        val currentPricing = modelPricing[selectedModel] ?: Triple(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE, DEFAULT_CACHED_INPUT_PRICE)
        var inputPrice by remember { mutableStateOf(currentPricing.first.toString()) }
        var outputPrice by remember { mutableStateOf(currentPricing.second.toString()) }
        var cachedInputPrice by remember { mutableStateOf(currentPricing.third.toString()) }
        
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
                        value = cachedInputPrice,
                        onValueChange = { cachedInputPrice = it },
                        label = { Text(stringResource(id = R.string.settings_cached_input_price_per_million)) },
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
                        val inputPriceDouble = inputPrice.toDoubleOrNull() ?: DEFAULT_INPUT_PRICE
                        val outputPriceDouble = outputPrice.toDoubleOrNull() ?: DEFAULT_OUTPUT_PRICE
                        val cachedInputPriceDouble = cachedInputPrice.toDoubleOrNull() ?: DEFAULT_CACHED_INPUT_PRICE
                        
                        modelPricing[selectedModel] = Triple(inputPriceDouble, outputPriceDouble, cachedInputPriceDouble)
                        
                        scope.launch {
                            apiPreferences.setModelInputPrice(selectedModel, inputPriceDouble)
                            apiPreferences.setModelOutputPrice(selectedModel, outputPriceDouble)
                            apiPreferences.setModelCachedInputPrice(selectedModel, cachedInputPriceDouble)
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
    cachedInputTokens: Int,
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
                    if (cachedInputTokens > 0) {
                        Text(
                            text = stringResource(R.string.settings_cached_tokens, cachedInputTokens),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
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
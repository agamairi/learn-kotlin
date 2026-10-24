package dev.agam.learn.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agam.learn.ai.AiProvider
import dev.agam.learn.ai.AiProviderConfig
import dev.agam.learn.di.LocalAppContainer

/**
 * UI state for the Settings screen.
 */
data class SettingsUiState(
    val dailyGoalXp: Int = 50,
    val aiProvider: AiProvider = AiProvider.ANTHROPIC,
    val aiModel: String = "claude-haiku-5-5",
    val apiKey: String = "",
    val isTestingAiConnection: Boolean = false,
    val aiConnectionTestResult: ConnectionTestResult? = null,
    val keystoreErrorMessage: String? = null,
    val compilerServerUrl: String = "http://10.0.2.2:8080",
    val compilerToken: String = "",
    val isInterviewModeDefault: Boolean = false,
    val isSavedMessageVisible: Boolean = false
)

/**
 * Result of the AI tutor API connection test.
 */
data class ConnectionTestResult(
    val isSuccess: Boolean,
    val message: String
)

/**
 * Recommended models mapped by AI provider.
 */
val PROVIDER_MODELS: Map<AiProvider, List<String>> = mapOf(
    AiProvider.ANTHROPIC to listOf("claude-haiku-5-5", "claude-opus-5-5"),
    AiProvider.OPENAI to listOf("gpt-6-luna", "gpt-6-astra"),
    AiProvider.GEMINI to listOf("gemini-3.5-flash-lite", "gemini-4-argon"),
    AiProvider.OPENROUTER to listOf(AiProviderConfig.OPENROUTER_DEFAULT_MODEL)
)

/**
 * Daily XP goal presets.
 */
val DAILY_GOAL_PRESETS: List<Pair<Int, String>> = listOf(
    30 to "Casual (30 XP)",
    50 to "Regular (50 XP • Default)",
    75 to "Serious (75 XP)",
    100 to "Intense (100 XP)"
)

/**
 * Stateless settings composable screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = SettingsViewModel.provideFactory(LocalAppContainer.current)
    )
) {
    val state by viewModel.uiState.collectAsState()
    SettingsScreen(
        state = state,
        onDailyGoalSelected = viewModel::onDailyGoalSelected,
        onAiProviderSelected = viewModel::onAiProviderSelected,
        onAiModelSelected = viewModel::onAiModelSelected,
        onApiKeyChange = viewModel::onApiKeyChange,
        onTestAiConnection = viewModel::testAiConnection,
        onRemoveApiKey = viewModel::removeApiKey,
        onSaveAiSettings = viewModel::saveAiSettings,
        onCompilerUrlChange = viewModel::onCompilerUrlChange,
        onCompilerTokenChange = viewModel::onCompilerTokenChange,
        onSaveCompilerSettings = viewModel::saveCompilerSettings,
        onToggleInterviewModeDefault = viewModel::toggleInterviewModeDefault,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onDailyGoalSelected: (Int) -> Unit,
    onAiProviderSelected: (AiProvider) -> Unit,
    onAiModelSelected: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onTestAiConnection: () -> Unit,
    onRemoveApiKey: () -> Unit,
    onSaveAiSettings: () -> Unit,
    onCompilerUrlChange: (String) -> Unit,
    onCompilerTokenChange: (String) -> Unit,
    onSaveCompilerSettings: () -> Unit,
    onToggleInterviewModeDefault: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isTokenVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Preferences, AI Tutor configuration, and compiler runtime",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Daily Goal Picker Section
            SettingsSectionCard(title = "🎯 Daily Goal") {
                Text(
                    text = "Choose your target daily practice commitment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                DAILY_GOAL_PRESETS.forEach { (xp, label) ->
                    val isSelected = state.dailyGoalXp == xp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(vertical = 3.dp)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onDailyGoalSelected(xp) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onDailyGoalSelected(xp) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. AI Tutor Section
            SettingsSectionCard(title = "🤖 AI Tutor (Bring Your Own Key)") {
                Text(
                    text = "Configure your preferred AI provider to unlock interactive mistake explanations, progressive hints, and code reviews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Provider Choice
                Text(
                    text = "Provider",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        val isSelected = state.aiProvider == provider
                        FilterChip(
                            selected = isSelected,
                            onClick = { onAiProviderSelected(provider) },
                            label = {
                                Text(
                                    text = when (provider) {
                                        AiProvider.ANTHROPIC -> "Anthropic"
                                        AiProvider.OPENAI -> "OpenAI"
                                        AiProvider.GEMINI -> "Google Gemini"
                                        AiProvider.OPENROUTER -> "OpenRouter"
                                    }
                                )
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Model Choice
                Text(
                    text = "Model",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                val availableModels = PROVIDER_MODELS[state.aiProvider] ?: emptyList()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableModels.forEach { modelName ->
                        val isSelected = state.aiModel == modelName
                        ElevatedFilterChip(
                            selected = isSelected,
                            onClick = { onAiModelSelected(modelName) },
                            label = {
                                Text(
                                    text = modelName,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                if (state.aiProvider == AiProvider.OPENROUTER) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.aiModel,
                        onValueChange = onAiModelSelected,
                        label = { Text("OpenRouter model") },
                        placeholder = { Text("openrouter/auto") },
                        singleLine = true,
                        supportingText = {
                            Text("Use a model slug from OpenRouter, or leave blank for automatic routing.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // API Key Field with Show/Hide
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    placeholder = { Text("Paste your API key here") },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(
                            onClick = { isApiKeyVisible = !isApiKeyVisible },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Text(
                                if (isApiKeyVisible) "Hide" else "Show",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action buttons: Test Connection, Remove Key, Save Key
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTestAiConnection,
                        enabled = state.apiKey.isNotBlank() && !state.isTestingAiConnection && !state.isInterviewModeDefault,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isTestingAiConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Text("Test connection")
                        }
                    }

                    OutlinedButton(
                        onClick = onRemoveApiKey,
                        enabled = state.apiKey.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Remove key")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onSaveAiSettings,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save AI Settings")
                }

                // Keystore error feedback
                if (state.keystoreErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE), RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "✗ ${state.keystoreErrorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }

                // Connection test feedback result
                if (state.aiConnectionTestResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val isSuccess = state.aiConnectionTestResult.isSuccess
                    val resultBg = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    val resultColor = if (isSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(resultBg, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (isSuccess) "✓ ${state.aiConnectionTestResult.message}" else "✗ ${state.aiConnectionTestResult.message}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = resultColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Privacy Note text from PLAN.md 7b
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔒", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.aiProvider == AiProvider.OPENROUTER) {
                                "Your answers and code are sent to OpenRouter and its selected model providers, using your key and your account."
                            } else {
                                "Your answers and code are sent to the provider you chose, using your key and your account."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Compiler Server URL & Token Fields
            SettingsSectionCard(title = "⚙ Compiler Server") {
                Text(
                    text = "Endpoint for self-hosted kotlin-compiler-server runtime execution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.compilerServerUrl,
                    onValueChange = onCompilerUrlChange,
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.compilerToken,
                    onValueChange = onCompilerTokenChange,
                    label = { Text("Bearer Token (optional)") },
                    singleLine = true,
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(
                            onClick = { isTokenVisible = !isTokenVisible },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Text(
                                if (isTokenVisible) "Hide" else "Show",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onSaveCompilerSettings,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Compiler Settings")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Interview Mode Default Section
            SettingsSectionCard(title = "⏱ Interview Mode Default") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start problems in Interview Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Disables and hides all AI tutor controls during problem sessions to simulate realistic screen conditions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = state.isInterviewModeDefault,
                        onCheckedChange = onToggleInterviewModeDefault
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            state = SettingsUiState(
                dailyGoalXp = 50,
                aiProvider = AiProvider.ANTHROPIC,
                aiModel = "claude-haiku-5-5",
                apiKey = "sk-ant-test-key",
                compilerServerUrl = "http://10.0.2.2:8080",
                isInterviewModeDefault = false
            ),
            onDailyGoalSelected = {},
            onAiProviderSelected = {},
            onAiModelSelected = {},
            onApiKeyChange = {},
            onTestAiConnection = {},
            onRemoveApiKey = {},
            onSaveAiSettings = {},
            onCompilerUrlChange = {},
            onCompilerTokenChange = {},
            onSaveCompilerSettings = {},
            onToggleInterviewModeDefault = {}
        )
    }
}

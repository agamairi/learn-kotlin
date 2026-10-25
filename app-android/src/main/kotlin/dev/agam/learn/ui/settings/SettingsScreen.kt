package dev.agam.learn.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
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
import dev.agam.learn.ui.components.BrandArtwork
import dev.agam.learn.ui.components.BrandAsset
import dev.agam.learn.ui.theme.extendedColors

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
    val soundEffectsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reducedMotionEnabled: Boolean = false,
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
        onSoundEffectsChanged = viewModel::setSoundEffectsEnabled,
        onHapticsChanged = viewModel::setHapticsEnabled,
        onReducedMotionChanged = viewModel::setReducedMotionEnabled,
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
    onSoundEffectsChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onReducedMotionChanged: (Boolean) -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )

            CompanionIntro()

            SettingsGroup(title = "Learning experience") {
                PreferenceSwitchRow(
                    title = "Sound effects",
                    description = "Soft cues for answers and milestones.",
                    checked = state.soundEffectsEnabled,
                    onCheckedChange = onSoundEffectsChanged
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                PreferenceSwitchRow(
                    title = "Haptics",
                    description = "A light confirmation and a stronger mistake cue.",
                    checked = state.hapticsEnabled,
                    onCheckedChange = onHapticsChanged
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                PreferenceSwitchRow(
                    title = "Reduce motion",
                    description = "Calmer animation; your device setting is respected too.",
                    checked = state.reducedMotionEnabled,
                    onCheckedChange = onReducedMotionChanged
                )
            }

            SettingsGroup(
                title = "Daily practice goal",
                trailing = {
                    Text(
                        text = "${state.dailyGoalXp} XP daily",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            ) {
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DailyGoalChoice(
                            xp = 30,
                            label = "Casual",
                            selected = state.dailyGoalXp == 30,
                            onClick = { onDailyGoalSelected(30) },
                            modifier = Modifier.weight(1f)
                        )
                        DailyGoalChoice(
                            xp = 50,
                            label = "Recommended",
                            selected = state.dailyGoalXp == 50,
                            onClick = { onDailyGoalSelected(50) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DailyGoalChoice(
                            xp = 75,
                            label = "Focused",
                            selected = state.dailyGoalXp == 75,
                            onClick = { onDailyGoalSelected(75) },
                            modifier = Modifier.weight(1f)
                        )
                        DailyGoalChoice(
                            xp = 100,
                            label = "Intense",
                            selected = state.dailyGoalXp == 100,
                            onClick = { onDailyGoalSelected(100) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                Text(
                    text = "A steady daily target helps you build a regular practice habit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsGroup(
                title = "AI tutor",
                trailing = {
                    Text(
                        text = "Optional",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            ) {
                Text(
                    text = "Get explanations, hints, and code reviews with a provider you choose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsFieldLabel("Provider")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = state.aiProvider == provider,
                            onClick = { onAiProviderSelected(provider) },
                            label = { Text(providerLabel(provider)) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }

                SettingsFieldLabel("Model")
                val availableModels = PROVIDER_MODELS[state.aiProvider].orEmpty()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableModels.forEach { modelName ->
                        FilterChip(
                            selected = state.aiModel == modelName,
                            onClick = { onAiModelSelected(modelName) },
                            label = {
                                Text(
                                    text = modelName,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }

                if (state.aiProvider == AiProvider.OPENROUTER) {
                    OutlinedTextField(
                        value = state.aiModel,
                        onValueChange = onAiModelSelected,
                        label = { Text("OpenRouter model") },
                        placeholder = { Text("openrouter/auto") },
                        singleLine = true,
                        supportingText = {
                            Text("Enter a model slug, or leave blank for automatic routing.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API key") },
                    placeholder = { Text("Paste your API key") },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(
                            onClick = { isApiKeyVisible = !isApiKeyVisible },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Text(if (isApiKeyVisible) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTestAiConnection,
                        enabled = state.apiKey.isNotBlank() && !state.isTestingAiConnection && !state.isInterviewModeDefault,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isTestingAiConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing…")
                        } else {
                            Text("Test connection")
                        }
                    }
                    OutlinedButton(
                        onClick = onRemoveApiKey,
                        enabled = state.apiKey.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove key")
                    }
                    Button(
                        onClick = onSaveAiSettings,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save AI settings")
                    }
                }

                if (state.isSavedMessageVisible) {
                    StatusMessage(text = "AI settings saved.", success = true)
                }
                state.keystoreErrorMessage?.let { message ->
                    StatusMessage(text = message, success = false)
                }
                state.aiConnectionTestResult?.let { result ->
                    StatusMessage(text = result.message, success = result.isSuccess)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                Text(
                    text = if (state.aiProvider == AiProvider.OPENROUTER) {
                        "Your API key is encrypted on this device. AI features send relevant prompts and code to OpenRouter and the selected model provider using your account."
                    } else {
                        "Your API key is encrypted on this device. AI features send relevant prompts and code to ${providerLabel(state.aiProvider)} using your account."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsGroup(title = "Kotlin compiler server") {
                Text(
                    text = "Choose the server used to run compiler and code exercises.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = state.compilerServerUrl,
                    onValueChange = onCompilerUrlChange,
                    label = { Text("Server URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.compilerToken,
                    onValueChange = onCompilerTokenChange,
                    label = { Text("Bearer token (optional)") },
                    placeholder = { Text("Optional bearer token") },
                    singleLine = true,
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(
                            onClick = { isTokenVisible = !isTokenVisible },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Text(if (isTokenVisible) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = onSaveCompilerSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Save compiler settings")
                }
            }

            SettingsGroup(title = "Practice preferences") {
                PreferenceSwitchRow(
                    title = "Interview mode default",
                    description = "New problem sessions start with AI tutor controls hidden.",
                    checked = state.isInterviewModeDefault,
                    onCheckedChange = onToggleInterviewModeDefault
                )
            }
        }
    }
}

@Composable
private fun CompanionIntro() {
    SettingsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Box(contentAlignment = Alignment.Center) {
                    BrandArtwork(
                        asset = BrandAsset.MascotThink,
                        modifier = Modifier.size(48.dp),
                        contentDescription = "Koda"
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Make practice work for you",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Set your daily rhythm, feedback, and optional tools.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
            trailing?.invoke()
        }
        SettingsPanel(content = content)
    }
}

@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun DailyGoalChoice(
    xp: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 76.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$xp XP",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun SettingsFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StatusMessage(text: String, success: Boolean) {
    val background = if (success) MaterialTheme.extendedColors.successContainer else MaterialTheme.colorScheme.errorContainer
    val foreground = if (success) MaterialTheme.extendedColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .semantics { contentDescription = text }
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = foreground)
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = "$title. $description"
            }
        )
    }
}

private fun providerLabel(provider: AiProvider): String = when (provider) {
    AiProvider.ANTHROPIC -> "Anthropic"
    AiProvider.OPENAI -> "OpenAI"
    AiProvider.GEMINI -> "Google Gemini"
    AiProvider.OPENROUTER -> "OpenRouter"
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
                isInterviewModeDefault = false,
                soundEffectsEnabled = true,
                hapticsEnabled = true,
                reducedMotionEnabled = false
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
            onToggleInterviewModeDefault = {},
            onSoundEffectsChanged = {},
            onHapticsChanged = {},
            onReducedMotionChanged = {}
        )
    }
}

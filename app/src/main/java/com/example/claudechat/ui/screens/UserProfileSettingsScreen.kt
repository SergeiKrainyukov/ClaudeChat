package com.example.claudechat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.claudechat.model.UserProfile
import com.example.claudechat.viewmodel.UserProfileViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SaveStatus {
    IDLE, SAVING, SAVED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileSettingsScreen(
    viewModel: UserProfileViewModel,
    onBack: () -> Unit
) {
    val currentProfile by viewModel.userProfile.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val saveSuccess by viewModel.saveSuccess.observeAsState(false)

    // Состояния для полей
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }
    var hobbiesText by remember { mutableStateOf("") }
    var habitsText by remember { mutableStateOf("") }
    var goalsText by remember { mutableStateOf("") }
    var languageLevel by remember { mutableStateOf("") }
    var dietaryRestrictionsText by remember { mutableStateOf("") }
    var medicalInfo by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf("") }

    // Для автосохранения с задержкой
    val coroutineScope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var isInitialLoad by remember { mutableStateOf(true) }
    var lastSaveTime by remember { mutableStateOf(0L) }

    // Определяем статус сохранения
    val saveStatus = when {
        isLoading -> SaveStatus.SAVING
        saveSuccess && (System.currentTimeMillis() - lastSaveTime) < 3000 -> SaveStatus.SAVED
        else -> SaveStatus.IDLE
    }

    // Функция автосохранения с задержкой 1 секунда
    fun autoSave() {
        if (isInitialLoad) return

        saveJob?.cancel()
        saveJob = coroutineScope.launch {
            delay(1000) // Задержка 1 секунда перед сохранением
            val profile = UserProfile(
                name = name.takeIf { it.isNotBlank() },
                age = age.toIntOrNull(),
                occupation = occupation.takeIf { it.isNotBlank() },
                hobbies = hobbiesText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                habits = habitsText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                goals = goalsText.split(";").map { it.trim() }.filter { it.isNotBlank() },
                languageLevel = languageLevel.takeIf { it.isNotBlank() },
                dietaryRestrictions = dietaryRestrictionsText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                medicalInfo = medicalInfo.takeIf { it.isNotBlank() },
                timezone = timezone.takeIf { it.isNotBlank() }
            )
            viewModel.saveProfile(profile)
            lastSaveTime = System.currentTimeMillis()
        }
    }

    // Загружаем текущий профиль в поля
    LaunchedEffect(currentProfile) {
        currentProfile?.let { profile ->
            isInitialLoad = true
            name = profile.name ?: ""
            age = profile.age?.toString() ?: ""
            occupation = profile.occupation ?: ""
            hobbiesText = profile.hobbies.joinToString(", ")
            habitsText = profile.habits.joinToString(", ")
            goalsText = profile.goals.joinToString("; ")
            languageLevel = profile.languageLevel ?: ""
            dietaryRestrictionsText = profile.dietaryRestrictions.joinToString(", ")
            medicalInfo = profile.medicalInfo ?: ""
            timezone = profile.timezone ?: ""
            // Небольшая задержка перед включением автосохранения
            delay(500)
            isInitialLoad = false
        }
    }

    // Показываем сообщение при успешном сохранении
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            viewModel.resetSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Персональная информация") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteProfile() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить профиль",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = "Эта информация будет использоваться для персонализации ответов вашего личного ассистента",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Индикатор автосохранения - всегда виден
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = when (saveStatus) {
                        SaveStatus.SAVING -> MaterialTheme.colorScheme.primaryContainer
                        SaveStatus.SAVED -> MaterialTheme.colorScheme.tertiaryContainer
                        SaveStatus.IDLE -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        when (saveStatus) {
                            SaveStatus.SAVING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Сохранение...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            SaveStatus.SAVED -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Все изменения сохранены",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            SaveStatus.IDLE -> {
                                Icon(
                                    imageVector = Icons.Default.Create,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Автосохранение включено",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Основная информация
            Text(
                text = "Основная информация",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    autoSave()
                },
                label = { Text("Имя") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = age,
                onValueChange = {
                    age = it
                    autoSave()
                },
                label = { Text("Возраст") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = occupation,
                onValueChange = {
                    occupation = it
                    autoSave()
                },
                label = { Text("Профессия/Род деятельности") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            // Интересы и привычки
            Text(
                text = "Интересы и привычки",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = hobbiesText,
                onValueChange = {
                    hobbiesText = it
                    autoSave()
                },
                label = { Text("Хобби (через запятую)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                minLines = 2
            )

            OutlinedTextField(
                value = habitsText,
                onValueChange = {
                    habitsText = it
                    autoSave()
                },
                label = { Text("Привычки (через запятую)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                minLines = 2
            )

            OutlinedTextField(
                value = goalsText,
                onValueChange = {
                    goalsText = it
                    autoSave()
                },
                label = { Text("Цели (через точку с запятой)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                minLines = 2
            )

            // Дополнительная информация
            Text(
                text = "Дополнительная информация",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = languageLevel,
                onValueChange = {
                    languageLevel = it
                    autoSave()
                },
                label = { Text("Уровень владения языками") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = dietaryRestrictionsText,
                onValueChange = {
                    dietaryRestrictionsText = it
                    autoSave()
                },
                label = { Text("Пищевые ограничения (через запятую)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                minLines = 2
            )

            OutlinedTextField(
                value = medicalInfo,
                onValueChange = {
                    medicalInfo = it
                    autoSave()
                },
                label = { Text("Медицинская информация") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                minLines = 2
            )

            OutlinedTextField(
                value = timezone,
                onValueChange = {
                    timezone = it
                    autoSave()
                },
                label = { Text("Часовой пояс") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                singleLine = true
            )
        }
    }
}

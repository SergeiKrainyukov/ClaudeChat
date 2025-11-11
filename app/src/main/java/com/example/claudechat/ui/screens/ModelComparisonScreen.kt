package com.example.claudechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.claudechat.viewmodel.ModelComparisonViewModel
import com.example.claudechat.viewmodel.ModelResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelComparisonScreen(
    onBackClick: () -> Unit,
    viewModel: ModelComparisonViewModel = viewModel()
) {
    val state by viewModel.comparisonState.observeAsState()
    var promptText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сравнение моделей") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Информация о моделях
            Text(
                text = "Сравнение двух моделей Hugging Face",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val models = viewModel.getModels()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Модель 1: ${models.first}", style = MaterialTheme.typography.bodyMedium)
                    Text("Модель 2: ${models.second}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Поле ввода запроса
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                label = { Text("Введите запрос") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !state?.isLoading!!
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка сравнения
            Button(
                onClick = {
                    if (promptText.isNotBlank()) {
                        viewModel.compareModels(promptText)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = promptText.isNotBlank() && !state?.isLoading!!
            ) {
                Text(if (state?.isLoading == true) "Загрузка..." else "Сравнить модели")
            }

            // Индикатор загрузки
            if (state?.isLoading == true) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Результаты
            state?.model1Response?.let { response1 ->
                Spacer(modifier = Modifier.height(24.dp))
                ModelResponseCard(
                    response = response1,
                    title = "Модель 1: ${models.first}"
                )
            }

            state?.model2Response?.let { response2 ->
                Spacer(modifier = Modifier.height(16.dp))
                ModelResponseCard(
                    response = response2,
                    title = "Модель 2: ${models.second}"
                )
            }

            // Сравнение времени ответа
            if (state?.model1Response != null && state?.model2Response != null) {
                Spacer(modifier = Modifier.height(24.dp))
                ComparisonSummary(
                    response1 = state!!.model1Response!!,
                    response2 = state!!.model2Response!!,
                    model1Name = models.first,
                    model2Name = models.second
                )
            }
        }
    }
}

@Composable
fun ModelResponseCard(response: ModelResponse, title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (response.error != null)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (response.error != null) {
                Text(
                    text = "Ошибка: ${response.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = response.response,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Метрики
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MetricRow("Время ответа:", "${response.responseTime} мс")
                MetricRow("Токены (prompt):", response.promptTokens.toString())
                MetricRow("Токены (completion):", response.completionTokens.toString())
                MetricRow("Всего токенов:", response.totalTokens.toString())
                if (response.estimatedCost > 0) {
                    MetricRow(
                        "Стоимость:",
                        "$${String.format("%.6f", response.estimatedCost)}",
                        highlight = true
                    )
                } else {
                    MetricRow("Стоимость:", "Бесплатно", highlight = true)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ComparisonSummary(
    response1: ModelResponse,
    response2: ModelResponse,
    model1Name: String,
    model2Name: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Сравнение",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Сравнение по времени
            val fasterModel = if (response1.responseTime < response2.responseTime) {
                model1Name
            } else {
                model2Name
            }
            val timeDiff = kotlin.math.abs(response1.responseTime - response2.responseTime)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Быстрее:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = fasterModel,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Разница:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$timeDiff мс",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Сравнительная таблица метрик
            ComparisonTable(
                metric = "Длина ответа",
                value1 = "${response1.response.length} символов",
                value2 = "${response2.response.length} символов",
                model1Name = model1Name,
                model2Name = model2Name
            )

            Spacer(modifier = Modifier.height(8.dp))

            ComparisonTable(
                metric = "Токены",
                value1 = "${response1.totalTokens}",
                value2 = "${response2.totalTokens}",
                model1Name = model1Name,
                model2Name = model2Name
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Сравнение стоимости
            val cost1 = if (response1.estimatedCost > 0)
                "$${String.format("%.6f", response1.estimatedCost)}"
            else
                "Бесплатно"
            val cost2 = if (response2.estimatedCost > 0)
                "$${String.format("%.6f", response2.estimatedCost)}"
            else
                "Бесплатно"

            ComparisonTable(
                metric = "Стоимость",
                value1 = cost1,
                value2 = cost2,
                model1Name = model1Name,
                model2Name = model2Name,
                highlight = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Вывод победителя
            DetermineWinner(response1, response2, model1Name, model2Name)
        }
    }
}

@Composable
private fun ComparisonTable(
    metric: String,
    value1: String,
    value2: String,
    model1Name: String,
    model2Name: String,
    highlight: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = metric,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model1Name.take(20),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value1,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = model2Name.take(20),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value2,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DetermineWinner(
    response1: ModelResponse,
    response2: ModelResponse,
    model1Name: String,
    model2Name: String
) {
    // Определяем победителя по разным критериям
    val fasterModel = if (response1.responseTime < response2.responseTime) model1Name else model2Name
    val cheaperModel = when {
        response1.estimatedCost == 0.0 && response2.estimatedCost == 0.0 -> "Обе бесплатны"
        response1.estimatedCost == 0.0 -> model1Name
        response2.estimatedCost == 0.0 -> model2Name
        response1.estimatedCost < response2.estimatedCost -> model1Name
        else -> model2Name
    }
    val moreTokens = if (response1.totalTokens > response2.totalTokens) model1Name else model2Name

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Итоги:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("⚡ Быстрее: $fasterModel", style = MaterialTheme.typography.bodySmall)
            Text("💰 Выгоднее: $cheaperModel", style = MaterialTheme.typography.bodySmall)
            Text("📝 Подробнее: $moreTokens", style = MaterialTheme.typography.bodySmall)
        }
    }
}
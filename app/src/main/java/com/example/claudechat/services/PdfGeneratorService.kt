package com.example.claudechat.services

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/**
 * Сервис для генерации PDF документов из текста
 */
class PdfGeneratorService(private val context: Context) {

    companion object {
        private const val PAGE_WIDTH = 595 // A4 width in points (210mm)
        private const val PAGE_HEIGHT = 842 // A4 height in points (297mm)
        private const val MARGIN = 50f
        private const val LINE_SPACING = 1.2f
    }

    /**
     * Генерирует PDF файл из текста
     * @param content Текстовое содержимое для PDF
     * @param fileName Имя файла (без расширения)
     * @return Путь к созданному PDF файлу
     */
    fun generatePdf(content: String, fileName: String): Result<String> {
        return try {
            val pdfDocument = PdfDocument()

            // Создаем текстовые стили
            val titlePaint = createTitlePaint()
            val bodyPaint = createBodyPaint()
            val headerPaint = createHeaderPaint()

            // Разбиваем контент на секции
            val lines = content.lines()
            var currentY = MARGIN
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // Рисуем заголовок документа
            canvas.drawText("План реализации задач", MARGIN, currentY, titlePaint)
            currentY += 40f

            // Добавляем дату
            val dateText = "Создано: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
            canvas.drawText(dateText, MARGIN, currentY, bodyPaint)
            currentY += 30f

            // Рисуем контент
            for (line in lines) {
                // Проверяем, не нужна ли новая страница
                if (currentY > PAGE_HEIGHT - MARGIN) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN
                }

                // Выбираем стиль в зависимости от содержимого строки
                val paint = when {
                    line.startsWith("#") -> headerPaint
                    line.trim().isEmpty() -> {
                        currentY += 10f
                        continue
                    }
                    else -> bodyPaint
                }

                // Убираем markdown символы
                val cleanLine = line.replace(Regex("^#+\\s*"), "")

                // Разбиваем длинные строки
                val wrappedLines = wrapText(cleanLine, PAGE_WIDTH - 2 * MARGIN, paint)
                for (wrappedLine in wrappedLines) {
                    if (currentY > PAGE_HEIGHT - MARGIN) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = MARGIN
                    }

                    canvas.drawText(wrappedLine, MARGIN, currentY, paint)
                    currentY += paint.textSize * LINE_SPACING
                }

                // Дополнительный отступ после заголовков
                if (line.startsWith("#")) {
                    currentY += 5f
                }
            }

            pdfDocument.finishPage(page)

            // Сохраняем PDF в файл
            val outputDir = File(context.filesDir, "task_plans")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val outputFile = File(outputDir, "$fileName.pdf")
            val outputStream = FileOutputStream(outputFile)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()

            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Разбивает текст на строки с учетом ширины страницы
     */
    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)

            if (width > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine)
                currentLine = word
            } else {
                currentLine = testLine
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }

    /**
     * Создает стиль для заголовка документа
     */
    private fun createTitlePaint(): TextPaint {
        return TextPaint().apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }
    }

    /**
     * Создает стиль для заголовков секций
     */
    private fun createHeaderPaint(): TextPaint {
        return TextPaint().apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }
    }

    /**
     * Создает стиль для основного текста
     */
    private fun createBodyPaint(): TextPaint {
        return TextPaint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }
    }
}

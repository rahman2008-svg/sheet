package com.example

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ExportHelper {

    // ─── CSV Export ───────────────────────────────────────────────

    fun exportToCSV(sheet: Sheet): String {
        var maxRow = 1
        var maxCol = 1

        for (coord in sheet.cells.keys) {
            val parsed = FormulaEvaluator.parseCoordinate(coord) ?: continue
            if (parsed.first > maxRow) maxRow = parsed.first
            if (parsed.second > maxCol) maxCol = parsed.second
        }

        val sb = StringBuilder()
        for (r in 1..maxRow) {
            val rowValues = mutableListOf<String>()
            for (c in 1..maxCol) {
                val coord = "${FormulaEvaluator.getColName(c)}$r"
                val value = FormulaEvaluator.evaluateCell(coord, sheet)
                val escaped = if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                    "\"" + value.replace("\"", "\"\"") + "\""
                } else value
                rowValues.add(escaped)
            }
            sb.append(rowValues.joinToString(","))
            sb.append("\n")
        }
        return sb.toString()
    }

    // ─── CSV → Downloads ──────────────────────────────────────────

    fun saveCSVToDownloads(context: Context, sheet: Sheet, fileName: String): Boolean {
        return try {
            val csvContent = exportToCSV(sheet)
            val outputStream = getOutputStreamForDownloads(context, "$fileName.csv", "text/csv")
                ?: return false
            outputStream.use { it.write(csvContent.toByteArray()) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ─── CSV Import ───────────────────────────────────────────────

    fun importFromCSV(csvContent: String, sheetName: String): Sheet {
        val cells = mutableMapOf<String, CellData>()
        val lines = csvContent.split("\n")

        var row = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val tokens = splitCSVLine(trimmed)
            for (col in tokens.indices) {
                val coord = "${FormulaEvaluator.getColName(col + 1)}$row"
                val rawVal = tokens[col].trim()
                if (rawVal.isNotEmpty()) {
                    cells[coord] = CellData(value = rawVal)
                }
            }
            row++
        }
        return Sheet(name = sheetName, cells = cells)
    }

    private fun splitCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val currentToken = StringBuilder()

        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        currentToken.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(currentToken.toString())
                    currentToken.setLength(0)
                }
                else -> currentToken.append(c)
            }
            i++
        }
        result.add(currentToken.toString())
        return result
    }

    // ─── PDF Export → Downloads ───────────────────────────────────

    fun exportToPDFToDownloads(
        context: Context,
        sheet: Sheet,
        fileName: String,
        title: String = "SheetFlow Spreadsheet Report"
    ): Boolean {
        return try {
            val outputStream = getOutputStreamForDownloads(context, "$fileName.pdf", "application/pdf")
                ?: return false
            writePDF(sheet, outputStream, title)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ─── PDF Export → Internal Storage (app-specific) ────────────

    fun exportToPDF(
        sheet: Sheet,
        file: File,
        title: String = "SheetFlow Spreadsheet Report"
    ): Boolean {
        return try {
            writePDF(sheet, FileOutputStream(file), title)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ─── PDF Core Writer ──────────────────────────────────────────

    private fun writePDF(sheet: Sheet, outputStream: OutputStream, title: String): Boolean {
        return try {
            val pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                color = 0xFF0D9488.toInt()
                textSize = 18f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val subtitlePaint = Paint().apply {
                color = 0xFF555555.toInt()
                textSize = 10f
                isAntiAlias = true
            }
            val gridPaint = Paint().apply {
                color = 0xFFCCCCCC.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
            }
            val textPaint = Paint().apply {
                color = 0xFF000000.toInt()
                textSize = 9f
                isAntiAlias = true
            }
            val boldTextPaint = Paint().apply {
                color = 0xFF000000.toInt()
                textSize = 9f
                isFakeBoldText = true
                isAntiAlias = true
            }

            canvas.drawText(title, 30f, 40f, titlePaint)
            canvas.drawText(
                "Generated by SheetFlow — NexVora Lab's Ofc (Prince AR Abdur Rahman)",
                30f, 58f, subtitlePaint
            )

            var maxRow = 1
            var maxCol = 1
            for (coord in sheet.cells.keys) {
                val parsed = FormulaEvaluator.parseCoordinate(coord) ?: continue
                if (parsed.first > maxRow) maxRow = parsed.first
                if (parsed.second > maxCol) maxCol = parsed.second
            }

            val rowsToPrint = minOf(maxRow, 25)
            val colsToPrint = minOf(maxCol, 6)

            val startX = 30f
            val startY = 80f
            val colWidth = 80f
            val rowHeight = 24f

            // Header background
            val headerBgPaint = Paint().apply {
                color = 0xFFF0FDFA.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(
                startX, startY,
                startX + (colsToPrint + 1) * colWidth,
                startY + rowHeight,
                headerBgPaint
            )

            // Column headers
            boldTextPaint.color = 0xFF0D9488.toInt()
            canvas.drawText("No.", startX + 10f, startY + 16f, boldTextPaint)
            for (c in 1..colsToPrint) {
                canvas.drawText(
                    FormulaEvaluator.getColName(c),
                    startX + c * colWidth + 10f,
                    startY + 16f,
                    boldTextPaint
                )
            }

            // Rows
            for (r in 1..rowsToPrint) {
                val y = startY + r * rowHeight

                boldTextPaint.color = 0xFF555555.toInt()
                canvas.drawText(r.toString(), startX + 10f, y + 16f, boldTextPaint)

                for (c in 1..colsToPrint) {
                    val coord = "${FormulaEvaluator.getColName(c)}$r"
                    val evaluatedValue = FormulaEvaluator.evaluateCell(coord, sheet)
                    val cell = sheet.cells[coord]

                    // Cell background
                    if (cell?.backgroundColor != null && cell.backgroundColor != 0xFFFFFFFFL) {
                        val cellBgPaint = Paint().apply {
                            color = cell.backgroundColor.toInt()
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(
                            startX + c * colWidth, y,
                            startX + (c + 1) * colWidth, y + rowHeight,
                            cellBgPaint
                        )
                    }

                    val drawPaint = if (cell?.bold == true) boldTextPaint else textPaint
                    drawPaint.color = cell?.textColor?.toInt() ?: 0xFF000000.toInt()

                    val displayText = if (evaluatedValue.length > 15)
                        evaluatedValue.substring(0, 12) + "..."
                    else evaluatedValue
                    canvas.drawText(displayText, startX + c * colWidth + 8f, y + 16f, drawPaint)
                }
            }

            // Grid lines
            for (r in 0..rowsToPrint) {
                val y = startY + r * rowHeight
                canvas.drawLine(startX, y, startX + (colsToPrint + 1) * colWidth, y, gridPaint)
            }
            canvas.drawLine(
                startX, startY + (rowsToPrint + 1) * rowHeight,
                startX + (colsToPrint + 1) * colWidth, startY + (rowsToPrint + 1) * rowHeight,
                gridPaint
            )
            for (c in 0..(colsToPrint + 1)) {
                val x = startX + c * colWidth
                canvas.drawLine(x, startY, x, startY + (rowsToPrint + 1) * rowHeight, gridPaint)
            }

            pdfDoc.finishPage(page)
            outputStream.use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ─── Helper: OutputStream for Downloads (Android 10+ safe) ───

    private fun getOutputStreamForDownloads(
        context: Context,
        fileName: String,
        mimeType: String
    ): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return null
            context.contentResolver.openOutputStream(uri)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            downloadsDir.mkdirs()
            FileOutputStream(File(downloadsDir, fileName))
        }
    }
}

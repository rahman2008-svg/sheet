package com.example

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

object SheetFileManager {

    fun workbookToJson(workbook: Workbook): String {
        val root = JSONObject()
        root.put("id", workbook.id)
        root.put("title", workbook.title)
        root.put("activeSheetIndex", workbook.activeSheetIndex)
        root.put("createdAt", workbook.createdAt)
        root.put("updatedAt", workbook.updatedAt)

        val sheetsArray = JSONArray()
        for (sheet in workbook.sheets) {
            val sheetObj = JSONObject()
            sheetObj.put("id", sheet.id)
            sheetObj.put("name", sheet.name)
            sheetObj.put("isProtected", sheet.isProtected)

            // cells
            val cellsObj = JSONObject()
            for ((coord, cell) in sheet.cells) {
                val cellObj = JSONObject()
                cellObj.put("value", cell.value)
                cellObj.put("bold", cell.bold)
                cellObj.put("italic", cell.italic)
                cellObj.put("fontSize", cell.fontSize)
                cellObj.put("textColor", cell.textColor)
                cellObj.put("backgroundColor", cell.backgroundColor)
                cellObj.put("alignment", cell.alignment.name)
                
                cell.conditionalRule?.let { rule ->
                    val ruleObj = JSONObject()
                    ruleObj.put("operator", rule.operator)
                    ruleObj.put("value", rule.value)
                    ruleObj.put("textColor", rule.textColor)
                    ruleObj.put("backgroundColor", rule.backgroundColor)
                    cellObj.put("conditionalRule", ruleObj)
                }
                cellsObj.put(coord, cellObj)
            }
            sheetObj.put("cells", cellsObj)

            // colWidths
            val colWidthsObj = JSONObject()
            for ((col, width) in sheet.colWidths) {
                colWidthsObj.put(col.toString(), width.toDouble())
            }
            sheetObj.put("colWidths", colWidthsObj)

            // rowHeights
            val rowHeightsObj = JSONObject()
            for ((row, height) in sheet.rowHeights) {
                rowHeightsObj.put(row.toString(), height.toDouble())
            }
            sheetObj.put("rowHeights", rowHeightsObj)

            // lockedCells
            val lockedArray = JSONArray()
            for (coord in sheet.lockedCells) {
                lockedArray.put(coord)
            }
            sheetObj.put("lockedCells", lockedArray)

            // mergedRegions
            val mergedArray = JSONArray()
            for (region in sheet.mergedRegions) {
                mergedArray.put(region)
            }
            sheetObj.put("mergedRegions", mergedArray)

            sheetsArray.put(sheetObj)
        }
        root.put("sheets", sheetsArray)

        return root.toString()
    }

    fun jsonToWorkbook(jsonStr: String): Workbook {
        val root = JSONObject(jsonStr)
        val id = root.optString("id", java.util.UUID.randomUUID().toString())
        val title = root.optString("title", "Untitled Sheet")
        val activeSheetIndex = root.optInt("activeSheetIndex", 0)
        val createdAt = root.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())

        val sheetsList = mutableListOf<Sheet>()
        val sheetsArray = root.optJSONArray("sheets")
        if (sheetsArray != null) {
            for (i in 0 until sheetsArray.length()) {
                val sheetObj = sheetsArray.getJSONObject(i)
                val sId = sheetObj.optString("id", java.util.UUID.randomUUID().toString())
                val sName = sheetObj.optString("name", "Sheet${i + 1}")
                val isProtected = sheetObj.optBoolean("isProtected", false)

                // cells
                val cellsMap = mutableMapOf<String, CellData>()
                val cellsObj = sheetObj.optJSONObject("cells")
                if (cellsObj != null) {
                    val keys = cellsObj.keys()
                    while (keys.hasNext()) {
                        val coord = keys.next()
                        val cellObj = cellsObj.getJSONObject(coord)
                        
                        var rule: ConditionalRule? = null
                        val ruleObj = cellObj.optJSONObject("conditionalRule")
                        if (ruleObj != null) {
                            rule = ConditionalRule(
                                operator = ruleObj.optString("operator", "GREATER_THAN"),
                                value = ruleObj.optString("value", ""),
                                textColor = ruleObj.optLong("textColor", 0xFF000000L),
                                backgroundColor = ruleObj.optLong("backgroundColor", 0xFFFFFFFFL)
                            )
                        }

                        val cell = CellData(
                            value = cellObj.optString("value", ""),
                            bold = cellObj.optBoolean("bold", false),
                            italic = cellObj.optBoolean("italic", false),
                            fontSize = cellObj.optDouble("fontSize", 14.0).toFloat(),
                            textColor = cellObj.optLong("textColor", 0xFF000000L),
                            backgroundColor = cellObj.optLong("backgroundColor", 0xFFFFFFFFL),
                            alignment = try {
                                CellAlignment.valueOf(cellObj.optString("alignment", "LEFT"))
                            } catch (e: Exception) {
                                CellAlignment.LEFT
                            },
                            conditionalRule = rule
                        )
                        cellsMap[coord] = cell
                    }
                }

                // colWidths
                val colWidthsMap = mutableMapOf<Int, Float>()
                val colWidthsObj = sheetObj.optJSONObject("colWidths")
                if (colWidthsObj != null) {
                    val keys = colWidthsObj.keys()
                    while (keys.hasNext()) {
                        val colStr = keys.next()
                        colWidthsMap[colStr.toInt()] = colWidthsObj.getDouble(colStr).toFloat()
                    }
                }

                // rowHeights
                val rowHeightsMap = mutableMapOf<Int, Float>()
                val rowHeightsObj = sheetObj.optJSONObject("rowHeights")
                if (rowHeightsObj != null) {
                    val keys = rowHeightsObj.keys()
                    while (keys.hasNext()) {
                        val rowStr = keys.next()
                        rowHeightsMap[rowStr.toInt()] = rowHeightsObj.getDouble(rowStr).toFloat()
                    }
                }

                // lockedCells
                val lockedSet = mutableSetOf<String>()
                val lockedArray = sheetObj.optJSONArray("lockedCells")
                if (lockedArray != null) {
                    for (j in 0 until lockedArray.length()) {
                        lockedSet.add(lockedArray.getString(j))
                    }
                }

                // mergedRegions
                val mergedList = mutableListOf<String>()
                val mergedArray = sheetObj.optJSONArray("mergedRegions")
                if (mergedArray != null) {
                    for (j in 0 until mergedArray.length()) {
                        mergedList.add(mergedArray.getString(j))
                    }
                }

                sheetsList.add(
                    Sheet(
                        id = sId,
                        name = sName,
                        cells = cellsMap,
                        colWidths = colWidthsMap,
                        rowHeights = rowHeightsMap,
                        isProtected = isProtected,
                        lockedCells = lockedSet,
                        mergedRegions = mergedList
                    )
                )
            }
        }

        if (sheetsList.isEmpty()) {
            sheetsList.add(Sheet(name = "Sheet1"))
        }

        return Workbook(
            id = id,
            title = title,
            sheets = sheetsList,
            activeSheetIndex = activeSheetIndex,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun saveWorkbook(context: Context, workbook: Workbook): Boolean {
        return try {
            val updatedWorkbook = workbook.copy(updatedAt = System.currentTimeMillis())
            val jsonStr = workbookToJson(updatedWorkbook)
            val file = File(context.filesDir, "sheet_${updatedWorkbook.id}.json")
            file.writeText(jsonStr)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun loadWorkbook(context: Context, id: String): Workbook? {
        return try {
            val file = File(context.filesDir, "sheet_${id}.json")
            if (file.exists()) {
                jsonToWorkbook(file.readText())
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listSavedWorkbooks(context: Context): List<Workbook> {
        val list = mutableListOf<Workbook>()
        try {
            val files = context.filesDir.listFiles { _, name -> name.startsWith("sheet_") && name.endsWith(".json") }
            if (files != null) {
                for (file in files) {
                    try {
                        val wb = jsonToWorkbook(file.readText())
                        list.add(wb)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.updatedAt }
    }

    fun deleteWorkbook(context: Context, id: String): Boolean {
        return try {
            val file = File(context.filesDir, "sheet_${id}.json")
            if (file.exists()) {
                file.delete()
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

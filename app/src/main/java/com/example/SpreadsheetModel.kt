package com.example

import java.util.UUID

enum class CellAlignment {
    LEFT, CENTER, RIGHT
}

data class ConditionalRule(
    val operator: String, // "GREATER_THAN", "LESS_THAN", "EQUALS", "CONTAINS"
    val value: String,
    val textColor: Long,
    val backgroundColor: Long
)

data class CellData(
    val value: String = "", // e.g. "=SUM(A1:B2)" or "100" or "Invoice"
    val bold: Boolean = false,
    val italic: Boolean = false,
    val fontSize: Float = 14f,
    val textColor: Long = 0xFF000000L,
    val backgroundColor: Long = 0xFFFFFFFFL,
    val alignment: CellAlignment = CellAlignment.LEFT,
    val conditionalRule: ConditionalRule? = null
)

data class Sheet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cells: Map<String, CellData> = emptyMap(), // coordinate e.g. "A1", "B2"
    val colWidths: Map<Int, Float> = emptyMap(), // col index to width in Dp
    val rowHeights: Map<Int, Float> = emptyMap(), // row index to height in Dp
    val isProtected: Boolean = false,
    val lockedCells: Set<String> = emptySet(), // locked coordinate keys
    val mergedRegions: List<String> = emptyList() // format "A1:B2"
)

data class Workbook(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val sheets: List<Sheet> = listOf(Sheet(name = "Sheet1")),
    val activeSheetIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

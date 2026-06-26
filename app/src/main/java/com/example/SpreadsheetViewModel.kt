package com.example

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class SpreadsheetViewModel : ViewModel() {

    private val _activeWorkbook = MutableStateFlow<Workbook>(TemplateGenerator.createBlankWorkbook())
    val activeWorkbook: StateFlow<Workbook> = _activeWorkbook.asStateFlow()

    private val _savedWorkbooks = MutableStateFlow<List<Workbook>>(emptyList())
    val savedWorkbooks: StateFlow<List<Workbook>> = _savedWorkbooks.asStateFlow()

    private val _selectedCell = MutableStateFlow<String>("A1")
    val selectedCell: StateFlow<String> = _selectedCell.asStateFlow()

    private val _editingValue = MutableStateFlow<String>("")
    val editingValue: StateFlow<String> = _editingValue.asStateFlow()

    private val _currentScreen = MutableStateFlow<String>("DASHBOARD") // "DASHBOARD", "EDITOR"
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow<String>("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _chartRange = MutableStateFlow<String>("A5:B9") // default range for student marks
    val chartRange: StateFlow<String> = _chartRange.asStateFlow()

    private val _chartType = MutableStateFlow<String>("BAR") // "BAR", "PIE", "LINE"
    val chartType: StateFlow<String> = _chartType.asStateFlow()

    // Viewport Scroll indices
    private val _viewportStartRow = MutableStateFlow<Int>(1)
    val viewportStartRow: StateFlow<Int> = _viewportStartRow.asStateFlow()

    private val _viewportStartCol = MutableStateFlow<Int>(1)
    val viewportStartCol: StateFlow<Int> = _viewportStartCol.asStateFlow()

    // Undo / Redo lists
    private val undoStack = java.util.Stack<String>() // Store serialized workbook state to save memory
    private val redoStack = java.util.Stack<String>()

    init {
        // Initial blank load
    }

    fun loadAllWorkbooks(context: Context) {
        viewModelScope.launch {
            val list = SheetFileManager.listSavedWorkbooks(context)
            _savedWorkbooks.value = list
        }
    }

    private fun saveCurrentStateToUndo() {
        val serialized = SheetFileManager.workbookToJson(_activeWorkbook.value)
        undoStack.push(serialized)
        redoStack.clear()
    }

    fun undo() {
        if (!undoStack.isEmpty()) {
            val currentSerialized = SheetFileManager.workbookToJson(_activeWorkbook.value)
            redoStack.push(currentSerialized)
            val previousStateStr = undoStack.pop()
            _activeWorkbook.value = SheetFileManager.jsonToWorkbook(previousStateStr)
            
            // Sync selected cell editing text
            val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
            val cell = activeSheet.cells[_selectedCell.value]
            _editingValue.value = cell?.value ?: ""
        }
    }

    fun redo() {
        if (!redoStack.isEmpty()) {
            val currentSerialized = SheetFileManager.workbookToJson(_activeWorkbook.value)
            undoStack.push(currentSerialized)
            val nextStateStr = redoStack.pop()
            _activeWorkbook.value = SheetFileManager.jsonToWorkbook(nextStateStr)
            
            // Sync selected cell editing text
            val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
            val cell = activeSheet.cells[_selectedCell.value]
            _editingValue.value = cell?.value ?: ""
        }
    }

    fun canUndo(): Boolean = !undoStack.isEmpty()
    fun canRedo(): Boolean = !redoStack.isEmpty()

    fun navigateToDashboard(context: Context) {
        saveActiveWorkbookDirect(context)
        loadAllWorkbooks(context)
        _currentScreen.value = "DASHBOARD"
    }

    fun createWorkbook(title: String, templateType: String, context: Context) {
        val workbook = when (templateType) {
            "BLANK" -> TemplateGenerator.createBlankWorkbook(title)
            "BUDGET" -> TemplateGenerator.createBudgetTracker()
            "ATTENDANCE" -> TemplateGenerator.createAttendanceManager()
            "RESULTS" -> TemplateGenerator.createStudentResultCalculator()
            "INVOICE" -> TemplateGenerator.createBusinessInvoice()
            "EXPENSES" -> TemplateGenerator.createExpenseTracker()
            else -> TemplateGenerator.createBlankWorkbook(title)
        }
        
        // Ensure exact template name is used if custom title is given
        val finalWb = if (title.trim().isNotEmpty() && title != "Untitled Spreadsheet") {
            workbook.copy(title = title)
        } else {
            workbook
        }
        
        _activeWorkbook.value = finalWb
        _selectedCell.value = "A1"
        val activeSheet = finalWb.sheets[finalWb.activeSheetIndex]
        _editingValue.value = activeSheet.cells["A1"]?.value ?: ""
        
        // Reset viewport scroll offsets
        _viewportStartRow.value = 1
        _viewportStartCol.value = 1
        
        undoStack.clear()
        redoStack.clear()
        
        _currentScreen.value = "EDITOR"
        saveActiveWorkbookDirect(context)
    }

    fun loadWorkbook(wbId: String, context: Context) {
        val loaded = SheetFileManager.loadWorkbook(context, wbId)
        if (loaded != null) {
            _activeWorkbook.value = loaded
            _selectedCell.value = "A1"
            val activeSheet = loaded.sheets[loaded.activeSheetIndex]
            _editingValue.value = activeSheet.cells["A1"]?.value ?: ""
            _viewportStartRow.value = 1
            _viewportStartCol.value = 1
            undoStack.clear()
            redoStack.clear()
            _currentScreen.value = "EDITOR"
        }
    }

    fun deleteWorkbook(wbId: String, context: Context) {
        SheetFileManager.deleteWorkbook(context, wbId)
        loadAllWorkbooks(context)
    }

    fun saveActiveWorkbookDirect(context: Context) {
        SheetFileManager.saveWorkbook(context, _activeWorkbook.value)
    }

    fun setViewportStart(row: Int, col: Int) {
        _viewportStartRow.value = maxOf(1, minOf(row, 1048576))
        _viewportStartCol.value = maxOf(1, minOf(col, 16384))
    }

    fun scrollViewport(deltaRows: Int, deltaCols: Int) {
        setViewportStart(_viewportStartRow.value + deltaRows, _viewportStartCol.value + deltaCols)
    }

    fun selectCell(coord: String) {
        val upper = coord.uppercase(Locale.ROOT).trim()
        if (FormulaEvaluator.parseCoordinate(upper) != null) {
            _selectedCell.value = upper
            val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
            _editingValue.value = activeSheet.cells[upper]?.value ?: ""
        }
    }

    fun updateSelectedCellValue(newValue: String, context: Context) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        
        val cellsMap = sheet.cells.toMutableMap()
        val coord = _selectedCell.value
        val currentCell = cellsMap[coord] ?: CellData()
        
        cellsMap[coord] = currentCell.copy(value = newValue)
        
        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(cells = cellsMap)
        
        _activeWorkbook.value = currentWb.copy(sheets = updatedSheets, updatedAt = System.currentTimeMillis())
        _editingValue.value = newValue
        saveActiveWorkbookDirect(context)
    }

    fun applySelectedCellFormatting(
        bold: Boolean? = null,
        italic: Boolean? = null,
        fontSize: Float? = null,
        textColor: Long? = null,
        backgroundColor: Long? = null,
        alignment: CellAlignment? = null,
        context: Context
    ) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        
        val cellsMap = sheet.cells.toMutableMap()
        val coord = _selectedCell.value
        val currentCell = cellsMap[coord] ?: CellData()
        
        cellsMap[coord] = currentCell.copy(
            bold = bold ?: currentCell.bold,
            italic = italic ?: currentCell.italic,
            fontSize = fontSize ?: currentCell.fontSize,
            textColor = textColor ?: currentCell.textColor,
            backgroundColor = backgroundColor ?: currentCell.backgroundColor,
            alignment = alignment ?: currentCell.alignment
        )
        
        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(cells = cellsMap)
        
        _activeWorkbook.value = currentWb.copy(sheets = updatedSheets, updatedAt = System.currentTimeMillis())
        saveActiveWorkbookDirect(context)
    }

    fun applyConditionalRule(operator: String, value: String, textColor: Long, backgroundColor: Long, context: Context) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        
        val cellsMap = sheet.cells.toMutableMap()
        val coord = _selectedCell.value
        val currentCell = cellsMap[coord] ?: CellData()
        
        val rule = ConditionalRule(operator, value, textColor, backgroundColor)
        cellsMap[coord] = currentCell.copy(conditionalRule = rule)
        
        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(cells = cellsMap)
        
        _activeWorkbook.value = currentWb.copy(sheets = updatedSheets, updatedAt = System.currentTimeMillis())
        saveActiveWorkbookDirect(context)
    }

    fun clearConditionalRule(context: Context) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        
        val cellsMap = sheet.cells.toMutableMap()
        val coord = _selectedCell.value
        val currentCell = cellsMap[coord] ?: CellData()
        
        cellsMap[coord] = currentCell.copy(conditionalRule = null)
        
        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(cells = cellsMap)
        
        _activeWorkbook.value = currentWb.copy(sheets = updatedSheets, updatedAt = System.currentTimeMillis())
        saveActiveWorkbookDirect(context)
    }

    fun copyCell(coord: String): CellData? {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        return activeSheet.cells[coord]
    }

    fun pasteCell(coord: String, cellData: CellData, context: Context) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        
        val cellsMap = sheet.cells.toMutableMap()
        cellsMap[coord] = cellData.copy()
        
        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(cells = cellsMap)
        
        _activeWorkbook.value = currentWb.copy(sheets = updatedSheets, updatedAt = System.currentTimeMillis())
        if (coord == _selectedCell.value) {
            _editingValue.value = cellData.value
        }
        saveActiveWorkbookDirect(context)
    }

    fun smartAutofillDown(context: Context) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        
        val coord = _selectedCell.value
        val parsed = FormulaEvaluator.parseCoordinate(coord) ?: return
        val row = parsed.first
        val col = parsed.second
        val colLetter = FormulaEvaluator.getColName(col)
        
        val sourceCell = sheet.cells[coord] ?: return
        val sourceVal = sourceCell.value
        
        val updatedCellsMap = sheet.cells.toMutableMap()
        
        // If numeric, check if previous row is also numeric for pattern increment
        val prevCoord = "$colLetter${row - 1}"
        val prevCell = sheet.cells[prevCoord]
        val sourceDouble = sourceVal.toDoubleOrNull()
        val prevDouble = prevCell?.value?.toDoubleOrNull()
        
        if (sourceDouble != null && prevDouble != null) {
            val step = sourceDouble - prevDouble
            // Smart auto increment for the next 5 rows
            for (i in 1..5) {
                val targetRow = row + i
                val targetCoord = "$colLetter$targetRow"
                val nextVal = sourceDouble + (step * i)
                val formattedVal = if (nextVal % 1.0 == 0.0) nextVal.toInt().toString() else nextVal.toString()
                
                val currentTargetCell = updatedCellsMap[targetCoord] ?: CellData()
                updatedCellsMap[targetCoord] = currentTargetCell.copy(value = formattedVal)
            }
        } else if (sourceDouble != null) {
            // No previous number, increment by 1 for the next 5 rows
            for (i in 1..5) {
                val targetRow = row + i
                val targetCoord = "$colLetter$targetRow"
                val nextVal = sourceDouble + i
                val formattedVal = if (nextVal % 1.0 == 0.0) nextVal.toInt().toString() else nextVal.toString()
                
                val currentTargetCell = updatedCellsMap[targetCoord] ?: CellData()
                updatedCellsMap[targetCoord] = currentTargetCell.copy(value = formattedVal)
            }
        } else {
            // String literal, just duplicate it downwards
            for (i in 1..5) {
                val targetRow = row + i
                val targetCoord = "$colLetter$targetRow"
                
                val currentTargetCell = updatedCellsMap[targetCoord] ?: CellData()
                updatedCellsMap[targetCoord] = currentTargetCell.copy(value = sourceVal)
            }
        }
        
        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(cells = updatedCellsMap)
        _activeWorkbook.value = currentWb.copy(sheets = updatedSheets, updatedAt = System.currentTimeMillis())
        saveActiveWorkbookDirect(context)
    }

    // MULTI-SHEET CONTROLS
    fun addNewSheet(context: Context) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val newSheetNum = currentWb.sheets.size + 1
        val newSheet = Sheet(name = "Sheet$newSheetNum")
        
        val sheetsList = currentWb.sheets.toMutableList()
        sheetsList.add(newSheet)
        
        _activeWorkbook.value = currentWb.copy(
            sheets = sheetsList,
            activeSheetIndex = sheetsList.size - 1,
            updatedAt = System.currentTimeMillis()
        )
        _selectedCell.value = "A1"
        _editingValue.value = ""
        saveActiveWorkbookDirect(context)
    }

    fun deleteActiveSheet(context: Context) {
        val currentWb = _activeWorkbook.value
        if (currentWb.sheets.size <= 1) return // Keep at least one sheet
        
        saveCurrentStateToUndo()
        val indexToDelete = currentWb.activeSheetIndex
        val sheetsList = currentWb.sheets.toMutableList()
        sheetsList.removeAt(indexToDelete)
        
        val newActiveIndex = maxOf(0, indexToDelete - 1)
        _activeWorkbook.value = currentWb.copy(
            sheets = sheetsList,
            activeSheetIndex = newActiveIndex,
            updatedAt = System.currentTimeMillis()
        )
        _selectedCell.value = "A1"
        val activeSheet = sheetsList[newActiveIndex]
        _editingValue.value = activeSheet.cells["A1"]?.value ?: ""
        saveActiveWorkbookDirect(context)
    }

    fun renameActiveSheet(newName: String, context: Context) {
        if (newName.trim().isEmpty()) return
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val activeIdx = currentWb.activeSheetIndex
        val sheetsList = currentWb.sheets.toMutableList()
        sheetsList[activeIdx] = sheetsList[activeIdx].copy(name = newName.trim())
        
        _activeWorkbook.value = currentWb.copy(sheets = sheetsList, updatedAt = System.currentTimeMillis())
        saveActiveWorkbookDirect(context)
    }

    fun selectSheetIndex(idx: Int) {
        val currentWb = _activeWorkbook.value
        if (idx in currentWb.sheets.indices) {
            _activeWorkbook.value = currentWb.copy(activeSheetIndex = idx)
            _selectedCell.value = "A1"
            val activeSheet = currentWb.sheets[idx]
            _editingValue.value = activeSheet.cells["A1"]?.value ?: ""
        }
    }

    // Protection Engine
    fun toggleSheetProtection(context: Context) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        
        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(isProtected = !sheet.isProtected)
        
        _activeWorkbook.value = currentWb.copy(sheets = updatedSheets, updatedAt = System.currentTimeMillis())
        saveActiveWorkbookDirect(context)
    }

    fun toggleCellLock(coord: String, context: Context) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        
        val lockedSet = sheet.lockedCells.toMutableSet()
        if (lockedSet.contains(coord)) {
            lockedSet.remove(coord)
        } else {
            lockedSet.add(coord)
        }
        
        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(lockedCells = lockedSet)
        
        _activeWorkbook.value = currentWb.copy(sheets = updatedSheets, updatedAt = System.currentTimeMillis())
        saveActiveWorkbookDirect(context)
    }

    // Chart Data Parsing
    fun setChartSettings(range: String, type: String) {
        _chartRange.value = range.uppercase(Locale.ROOT).trim()
        _chartType.value = type
    }

    fun extractChartData(): Pair<List<String>, List<Double>> {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        val rangeStr = _chartRange.value
        val parts = rangeStr.split(":")
        if (parts.size != 2) return Pair(emptyList(), emptyList())
        
        val start = FormulaEvaluator.parseCoordinate(parts[0]) ?: return Pair(emptyList(), emptyList())
        val end = FormulaEvaluator.parseCoordinate(parts[1]) ?: return Pair(emptyList(), emptyList())
        
        val startRow = minOf(start.first, end.first)
        val endRow = maxOf(start.first, end.first)
        val startCol = minOf(start.second, end.second)
        val endCol = maxOf(start.second, end.second)
        
        // We assume 2 columns: column 1 = labels, column 2 = values
        if (endCol - startCol < 1) {
            // Fallback: If only 1 column, use row number or simple alphabet labels
            val labels = mutableListOf<String>()
            val values = mutableListOf<Double>()
            for (r in startRow..endRow) {
                val coord = "${FormulaEvaluator.getColName(startCol)}$r"
                val evalVal = FormulaEvaluator.evaluateCell(coord, activeSheet)
                val doubleVal = evalVal.toDoubleOrNull() ?: 0.0
                labels.add(coord)
                values.add(doubleVal)
            }
            return Pair(labels, values)
        }
        
        val labels = mutableListOf<String>()
        val values = mutableListOf<Double>()
        
        for (r in startRow..endRow) {
            val labelCoord = "${FormulaEvaluator.getColName(startCol)}$r"
            val valCoord = "${FormulaEvaluator.getColName(startCol + 1)}$r"
            
            val labelText = FormulaEvaluator.evaluateCell(labelCoord, activeSheet).ifEmpty { labelCoord }
            val evalVal = FormulaEvaluator.evaluateCell(valCoord, activeSheet)
            val doubleVal = evalVal.toDoubleOrNull() ?: 0.0
            
            labels.add(labelText)
            values.add(doubleVal)
        }
        
        return Pair(labels, values)
    }

    // CSV & PDF exports
    fun exportToCSVString(): String {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        return ExportHelper.exportToCSV(activeSheet)
    }

    fun importFromCSVString(csvContent: String, sheetName: String, context: Context) {
        saveCurrentStateToUndo()
        val importedSheet = ExportHelper.importFromCSV(csvContent, sheetName)
        val currentWb = _activeWorkbook.value
        
        val sheetsList = currentWb.sheets.toMutableList()
        sheetsList.add(importedSheet)
        
        _activeWorkbook.value = currentWb.copy(
            sheets = sheetsList,
            activeSheetIndex = sheetsList.size - 1,
            updatedAt = System.currentTimeMillis()
        )
        _selectedCell.value = "A1"
        _editingValue.value = importedSheet.cells["A1"]?.value ?: ""
        saveActiveWorkbookDirect(context)
    }

    fun exportToPDFFile(file: File): Boolean {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        return ExportHelper.exportToPDF(activeSheet, file, _activeWorkbook.value.title)
    }

    fun searchMatches(query: String): List<String> {
        _searchQuery.value = query
        if (query.trim().isEmpty()) return emptyList()
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        
        val matches = mutableListOf<String>()
        for ((coord, cell) in activeSheet.cells) {
            val evaluated = FormulaEvaluator.evaluateCell(coord, activeSheet)
            if (cell.value.contains(query, ignoreCase = true) || evaluated.contains(query, ignoreCase = true)) {
                matches.add(coord)
            }
        }
        return matches
    }
}

package com.example

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class SpreadsheetViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _activeWorkbook = MutableStateFlow<Workbook>(TemplateGenerator.createBlankWorkbook())
    val activeWorkbook: StateFlow<Workbook> = _activeWorkbook.asStateFlow()

    private val _savedWorkbooks = MutableStateFlow<List<Workbook>>(emptyList())
    val savedWorkbooks: StateFlow<List<Workbook>> = _savedWorkbooks.asStateFlow()

    private val _selectedCell = MutableStateFlow("A1")
    val selectedCell: StateFlow<String> = _selectedCell.asStateFlow()

    private val _editingValue = MutableStateFlow("")
    val editingValue: StateFlow<String> = _editingValue.asStateFlow()

    private val _currentScreen = MutableStateFlow("DASHBOARD")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _chartRange = MutableStateFlow("A5:B9")
    val chartRange: StateFlow<String> = _chartRange.asStateFlow()

    private val _chartType = MutableStateFlow("BAR")
    val chartType: StateFlow<String> = _chartType.asStateFlow()

    private val _viewportStartRow = MutableStateFlow(1)
    val viewportStartRow: StateFlow<Int> = _viewportStartRow.asStateFlow()

    private val _viewportStartCol = MutableStateFlow(1)
    val viewportStartCol: StateFlow<Int> = _viewportStartCol.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private val maxUndoSize = 30

    init {
        loadAllWorkbooks()
    }

    // ─── Workbook Loading ─────────────────────────────────────────

    fun loadAllWorkbooks() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = SheetFileManager.listSavedWorkbooks(context)
            withContext(Dispatchers.Main) {
                _savedWorkbooks.value = list
            }
        }
    }

    // ─── Undo / Redo ──────────────────────────────────────────────

    private fun saveCurrentStateToUndo() {
        val serialized = SheetFileManager.workbookToJson(_activeWorkbook.value)
        undoStack.addLast(serialized)
        if (undoStack.size > maxUndoSize) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val currentSerialized = SheetFileManager.workbookToJson(_activeWorkbook.value)
        redoStack.addLast(currentSerialized)
        val previousStateStr = undoStack.removeLast()
        _activeWorkbook.value = SheetFileManager.jsonToWorkbook(previousStateStr)
        syncEditingValue()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val currentSerialized = SheetFileManager.workbookToJson(_activeWorkbook.value)
        undoStack.addLast(currentSerialized)
        val nextStateStr = redoStack.removeLast()
        _activeWorkbook.value = SheetFileManager.jsonToWorkbook(nextStateStr)
        syncEditingValue()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    private fun syncEditingValue() {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        _editingValue.value = activeSheet.cells[_selectedCell.value]?.value ?: ""
    }

    // ─── Navigation ───────────────────────────────────────────────

    fun navigateToDashboard() {
        saveActiveWorkbook()
        loadAllWorkbooks()
        _currentScreen.value = "DASHBOARD"
    }

    // ─── Create / Load / Delete Workbook ──────────────────────────

    fun createWorkbook(title: String, templateType: String) {
        val workbook = when (templateType) {
            "BLANK"      -> TemplateGenerator.createBlankWorkbook(title)
            "BUDGET"     -> TemplateGenerator.createBudgetTracker()
            "ATTENDANCE" -> TemplateGenerator.createAttendanceManager()
            "RESULTS"    -> TemplateGenerator.createStudentResultCalculator()
            "INVOICE"    -> TemplateGenerator.createBusinessInvoice()
            "EXPENSES"   -> TemplateGenerator.createExpenseTracker()
            else         -> TemplateGenerator.createBlankWorkbook(title)
        }

        val finalWb = if (title.trim().isNotEmpty() && title != "Untitled Spreadsheet") {
            workbook.copy(title = title)
        } else workbook

        _activeWorkbook.value = finalWb
        _selectedCell.value = "A1"
        _editingValue.value = finalWb.sheets[finalWb.activeSheetIndex].cells["A1"]?.value ?: ""
        _viewportStartRow.value = 1
        _viewportStartCol.value = 1
        undoStack.clear()
        redoStack.clear()
        _currentScreen.value = "EDITOR"
        saveActiveWorkbook()
    }

    fun loadWorkbook(wbId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = SheetFileManager.loadWorkbook(context, wbId)
            withContext(Dispatchers.Main) {
                if (loaded != null) {
                    _activeWorkbook.value = loaded
                    _selectedCell.value = "A1"
                    _editingValue.value = loaded.sheets[loaded.activeSheetIndex].cells["A1"]?.value ?: ""
                    _viewportStartRow.value = 1
                    _viewportStartCol.value = 1
                    undoStack.clear()
                    redoStack.clear()
                    _currentScreen.value = "EDITOR"
                } else {
                    Toast.makeText(context, "Failed to load workbook", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteWorkbook(wbId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SheetFileManager.deleteWorkbook(context, wbId)
            withContext(Dispatchers.Main) {
                loadAllWorkbooks()
            }
        }
    }

    fun saveActiveWorkbook() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            SheetFileManager.saveWorkbook(context, _activeWorkbook.value)
            withContext(Dispatchers.Main) {
                _isSaving.value = false
            }
        }
    }

    // ─── Viewport ─────────────────────────────────────────────────

    fun setViewportStart(row: Int, col: Int) {
        _viewportStartRow.value = maxOf(1, minOf(row, 1_048_576))
        _viewportStartCol.value = maxOf(1, minOf(col, 16_384))
    }

    fun scrollViewport(deltaRows: Int, deltaCols: Int) {
        setViewportStart(
            _viewportStartRow.value + deltaRows,
            _viewportStartCol.value + deltaCols
        )
    }

    // ─── Cell Selection ───────────────────────────────────────────

    fun selectCell(coord: String) {
        val upper = coord.uppercase(Locale.ROOT).trim()
        if (FormulaEvaluator.parseCoordinate(upper) != null) {
            _selectedCell.value = upper
            val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
            _editingValue.value = activeSheet.cells[upper]?.value ?: ""
        }
    }

    // ─── Cell Value Update ────────────────────────────────────────

    fun updateSelectedCellValue(newValue: String) {
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

        _activeWorkbook.value = currentWb.copy(
            sheets = updatedSheets,
            updatedAt = System.currentTimeMillis()
        )
        _editingValue.value = newValue
        saveActiveWorkbook()
    }

    // ─── Cell Formatting ──────────────────────────────────────────

    fun applySelectedCellFormatting(
        bold: Boolean? = null,
        italic: Boolean? = null,
        fontSize: Float? = null,
        textColor: Long? = null,
        backgroundColor: Long? = null,
        alignment: CellAlignment? = null
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

        _activeWorkbook.value = currentWb.copy(
            sheets = updatedSheets,
            updatedAt = System.currentTimeMillis()
        )
        saveActiveWorkbook()
    }

    // ─── Conditional Formatting ───────────────────────────────────

    fun applyConditionalRule(
        operator: String,
        value: String,
        textColor: Long,
        backgroundColor: Long
    ) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]

        val cellsMap = sheet.cells.toMutableMap()
        val coord = _selectedCell.value
        val currentCell = cellsMap[coord] ?: CellData()
        cellsMap[coord] = currentCell.copy(
            conditionalRule = ConditionalRule(operator, value, textColor, backgroundColor)
        )

        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(cells = cellsMap)
        _activeWorkbook.value = currentWb.copy(
            sheets = updatedSheets,
            updatedAt = System.currentTimeMillis()
        )
        saveActiveWorkbook()
    }

    fun clearConditionalRule() {
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
        _activeWorkbook.value = currentWb.copy(
            sheets = updatedSheets,
            updatedAt = System.currentTimeMillis()
        )
        saveActiveWorkbook()
    }

    // ─── Copy / Paste ─────────────────────────────────────────────

    fun copyCell(coord: String): CellData? {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        return activeSheet.cells[coord]
    }

    fun pasteCell(coord: String, cellData: CellData) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]

        val cellsMap = sheet.cells.toMutableMap()
        cellsMap[coord] = cellData.copy()

        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(cells = cellsMap)
        _activeWorkbook.value = currentWb.copy(
            sheets = updatedSheets,
            updatedAt = System.currentTimeMillis()
        )
        if (coord == _selectedCell.value) _editingValue.value = cellData.value
        saveActiveWorkbook()
    }

    // ─── Smart Autofill ───────────────────────────────────────────

    fun smartAutofillDown() {
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

        val prevCoord = "$colLetter${row - 1}"
        val prevDouble = sheet.cells[prevCoord]?.value?.toDoubleOrNull()
        val sourceDouble = sourceVal.toDoubleOrNull()

        for (i in 1..5) {
            val targetCoord = "$colLetter${row + i}"
            val currentTargetCell = updatedCellsMap[targetCoord] ?: CellData()

            val newValue = if (sourceDouble != null) {
                val step = if (prevDouble != null) sourceDouble - prevDouble else 1.0
                val nextVal = sourceDouble + (step * i)
                if (nextVal % 1.0 == 0.0) nextVal.toInt().toString() else nextVal.toString()
            } else {
                sourceVal
            }
            updatedCellsMap[targetCoord] = currentTargetCell.copy(value = newValue)
        }

        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(cells = updatedCellsMap)
        _activeWorkbook.value = currentWb.copy(
            sheets = updatedSheets,
            updatedAt = System.currentTimeMillis()
        )
        saveActiveWorkbook()
    }

    // ─── Multi-Sheet Controls ─────────────────────────────────────

    fun addNewSheet() {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val newSheet = Sheet(name = "Sheet${currentWb.sheets.size + 1}")
        val sheetsList = currentWb.sheets.toMutableList().also { it.add(newSheet) }

        _activeWorkbook.value = currentWb.copy(
            sheets = sheetsList,
            activeSheetIndex = sheetsList.size - 1,
            updatedAt = System.currentTimeMillis()
        )
        _selectedCell.value = "A1"
        _editingValue.value = ""
        saveActiveWorkbook()
    }

    fun deleteActiveSheet() {
        val currentWb = _activeWorkbook.value
        if (currentWb.sheets.size <= 1) return

        saveCurrentStateToUndo()
        val indexToDelete = currentWb.activeSheetIndex
        val sheetsList = currentWb.sheets.toMutableList().also { it.removeAt(indexToDelete) }
        val newActiveIndex = maxOf(0, indexToDelete - 1)

        _activeWorkbook.value = currentWb.copy(
            sheets = sheetsList,
            activeSheetIndex = newActiveIndex,
            updatedAt = System.currentTimeMillis()
        )
        _selectedCell.value = "A1"
        _editingValue.value = sheetsList[newActiveIndex].cells["A1"]?.value ?: ""
        saveActiveWorkbook()
    }

    fun renameActiveSheet(newName: String) {
        if (newName.trim().isEmpty()) return
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val activeIdx = currentWb.activeSheetIndex
        val sheetsList = currentWb.sheets.toMutableList()
        sheetsList[activeIdx] = sheetsList[activeIdx].copy(name = newName.trim())

        _activeWorkbook.value = currentWb.copy(
            sheets = sheetsList,
            updatedAt = System.currentTimeMillis()
        )
        saveActiveWorkbook()
    }

    fun selectSheetIndex(idx: Int) {
        val currentWb = _activeWorkbook.value
        if (idx !in currentWb.sheets.indices) return
        _activeWorkbook.value = currentWb.copy(activeSheetIndex = idx)
        _selectedCell.value = "A1"
        _editingValue.value = currentWb.sheets[idx].cells["A1"]?.value ?: ""
    }

    // ─── Sheet Protection ─────────────────────────────────────────

    fun toggleSheetProtection() {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(isProtected = !sheet.isProtected)

        _activeWorkbook.value = currentWb.copy(
            sheets = updatedSheets,
            updatedAt = System.currentTimeMillis()
        )
        saveActiveWorkbook()
    }

    fun toggleCellLock(coord: String) {
        saveCurrentStateToUndo()
        val currentWb = _activeWorkbook.value
        val sheetIndex = currentWb.activeSheetIndex
        val sheet = currentWb.sheets[sheetIndex]
        val lockedSet = sheet.lockedCells.toMutableSet()

        if (coord in lockedSet) lockedSet.remove(coord) else lockedSet.add(coord)

        val updatedSheets = currentWb.sheets.toMutableList()
        updatedSheets[sheetIndex] = sheet.copy(lockedCells = lockedSet)
        _activeWorkbook.value = currentWb.copy(
            sheets = updatedSheets,
            updatedAt = System.currentTimeMillis()
        )
        saveActiveWorkbook()
    }

    // ─── Chart ───────────────────────────────────────────────────

    fun setChartSettings(range: String, type: String) {
        _chartRange.value = range.uppercase(Locale.ROOT).trim()
        _chartType.value = type
    }

    fun extractChartData(): Pair<List<String>, List<Double>> {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        val parts = _chartRange.value.split(":")
        if (parts.size != 2) return Pair(emptyList(), emptyList())

        val start = FormulaEvaluator.parseCoordinate(parts[0]) ?: return Pair(emptyList(), emptyList())
        val end = FormulaEvaluator.parseCoordinate(parts[1]) ?: return Pair(emptyList(), emptyList())

        val startRow = minOf(start.first, end.first)
        val endRow = maxOf(start.first, end.first)
        val startCol = minOf(start.second, end.second)
        val endCol = maxOf(start.second, end.second)

        val labels = mutableListOf<String>()
        val values = mutableListOf<Double>()

        if (endCol - startCol < 1) {
            for (r in startRow..endRow) {
                val coord = "${FormulaEvaluator.getColName(startCol)}$r"
                labels.add(coord)
                values.add(FormulaEvaluator.evaluateCell(coord, activeSheet).toDoubleOrNull() ?: 0.0)
            }
        } else {
            for (r in startRow..endRow) {
                val labelCoord = "${FormulaEvaluator.getColName(startCol)}$r"
                val valCoord = "${FormulaEvaluator.getColName(startCol + 1)}$r"
                labels.add(FormulaEvaluator.evaluateCell(labelCoord, activeSheet).ifEmpty { labelCoord })
                values.add(FormulaEvaluator.evaluateCell(valCoord, activeSheet).toDoubleOrNull() ?: 0.0)
            }
        }
        return Pair(labels, values)
    }

    // ─── CSV / PDF Export ─────────────────────────────────────────

    fun exportToCSVString(): String {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        return ExportHelper.exportToCSV(activeSheet)
    }

    fun saveCSVToDownloads(fileName: String): Boolean {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        return ExportHelper.saveCSVToDownloads(context, activeSheet, fileName)
    }

    fun importFromCSVString(csvContent: String, sheetName: String) {
        saveCurrentStateToUndo()
        val importedSheet = ExportHelper.importFromCSV(csvContent, sheetName)
        val currentWb = _activeWorkbook.value
        val sheetsList = currentWb.sheets.toMutableList().also { it.add(importedSheet) }

        _activeWorkbook.value = currentWb.copy(
            sheets = sheetsList,
            activeSheetIndex = sheetsList.size - 1,
            updatedAt = System.currentTimeMillis()
        )
        _selectedCell.value = "A1"
        _editingValue.value = importedSheet.cells["A1"]?.value ?: ""
        saveActiveWorkbook()
    }

    fun exportToPDFFile(file: File): Boolean {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        return ExportHelper.exportToPDF(activeSheet, file, _activeWorkbook.value.title)
    }

    fun exportPDFToDownloads(fileName: String): Boolean {
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]
        return ExportHelper.exportToPDFToDownloads(context, activeSheet, fileName, _activeWorkbook.value.title)
    }

    // ─── Search ───────────────────────────────────────────────────

    fun searchMatches(query: String): List<String> {
        _searchQuery.value = query
        if (query.trim().isEmpty()) return emptyList()
        val activeSheet = _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]

        return activeSheet.cells.entries
            .filter { (coord, cell) ->
                val evaluated = FormulaEvaluator.evaluateCell(coord, activeSheet)
                cell.value.contains(query, ignoreCase = true) ||
                        evaluated.contains(query, ignoreCase = true)
            }
            .map { it.key }
    }
}

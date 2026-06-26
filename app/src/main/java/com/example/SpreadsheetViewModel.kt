package com.example

import android.app.Application
import android.content.Context
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

    private val ctx = application.applicationContext

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

    init { loadAllWorkbooks() }

    // ─── Internal Helpers ─────────────────────────────────────────

    private fun activeSheet() =
        _activeWorkbook.value.sheets[_activeWorkbook.value.activeSheetIndex]

    private fun syncEditingValue() {
        _editingValue.value = activeSheet().cells[_selectedCell.value]?.value ?: ""
    }

    private fun pushUndo() {
        undoStack.addLast(SheetFileManager.workbookToJson(_activeWorkbook.value))
        if (undoStack.size > maxUndoSize) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun updateSheet(sheetIndex: Int, updatedSheet: Sheet) {
        val wb = _activeWorkbook.value
        val sheets = wb.sheets.toMutableList()
        sheets[sheetIndex] = updatedSheet
        _activeWorkbook.value = wb.copy(sheets = sheets, updatedAt = System.currentTimeMillis())
    }

    // ─── Undo / Redo ──────────────────────────────────────────────

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(SheetFileManager.workbookToJson(_activeWorkbook.value))
        _activeWorkbook.value = SheetFileManager.jsonToWorkbook(undoStack.removeLast())
        syncEditingValue()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(SheetFileManager.workbookToJson(_activeWorkbook.value))
        _activeWorkbook.value = SheetFileManager.jsonToWorkbook(redoStack.removeLast())
        syncEditingValue()
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    // ─── Navigation ───────────────────────────────────────────────

    fun navigateToDashboard(context: Context? = null) {
        saveActiveWorkbook()
        loadAllWorkbooks()
        _currentScreen.value = "DASHBOARD"
    }

    // ─── Workbook CRUD ────────────────────────────────────────────

    fun loadAllWorkbooks(context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = SheetFileManager.listSavedWorkbooks(ctx)
            withContext(Dispatchers.Main) { _savedWorkbooks.value = list }
        }
    }

    fun createWorkbook(title: String, templateType: String, context: Context? = null) {
        val wb = when (templateType) {
            "BUDGET"     -> TemplateGenerator.createBudgetTracker()
            "ATTENDANCE" -> TemplateGenerator.createAttendanceManager()
            "RESULTS"    -> TemplateGenerator.createStudentResultCalculator()
            "INVOICE"    -> TemplateGenerator.createBusinessInvoice()
            "EXPENSES"   -> TemplateGenerator.createExpenseTracker()
            else         -> TemplateGenerator.createBlankWorkbook(title)
        }.let {
            if (title.trim().isNotEmpty() && title != "Untitled Spreadsheet")
                it.copy(title = title) else it
        }

        _activeWorkbook.value = wb
        _selectedCell.value = "A1"
        _editingValue.value = wb.sheets[wb.activeSheetIndex].cells["A1"]?.value ?: ""
        _viewportStartRow.value = 1
        _viewportStartCol.value = 1
        undoStack.clear()
        redoStack.clear()
        _currentScreen.value = "EDITOR"
        saveActiveWorkbook()
    }

    fun loadWorkbook(wbId: String, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = SheetFileManager.loadWorkbook(ctx, wbId)
            withContext(Dispatchers.Main) {
                if (loaded != null) {
                    _activeWorkbook.value = loaded
                    _selectedCell.value = "A1"
                    _editingValue.value =
                        loaded.sheets[loaded.activeSheetIndex].cells["A1"]?.value ?: ""
                    _viewportStartRow.value = 1
                    _viewportStartCol.value = 1
                    undoStack.clear()
                    redoStack.clear()
                    _currentScreen.value = "EDITOR"
                } else {
                    Toast.makeText(ctx, "Failed to load workbook", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteWorkbook(wbId: String, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            SheetFileManager.deleteWorkbook(ctx, wbId)
            withContext(Dispatchers.Main) { loadAllWorkbooks() }
        }
    }

    fun saveActiveWorkbook() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            SheetFileManager.saveWorkbook(ctx, _activeWorkbook.value)
            withContext(Dispatchers.Main) { _isSaving.value = false }
        }
    }

    // পুরনো UI call এর জন্য alias
    fun saveActiveWorkbookDirect(context: Context? = null) = saveActiveWorkbook()

    // ─── Viewport ─────────────────────────────────────────────────

    fun setViewportStart(row: Int, col: Int) {
        _viewportStartRow.value = maxOf(1, minOf(row, 1_048_576))
        _viewportStartCol.value = maxOf(1, minOf(col, 16_384))
    }

    fun scrollViewport(dr: Int, dc: Int) =
        setViewportStart(_viewportStartRow.value + dr, _viewportStartCol.value + dc)

    // ─── Cell Operations ──────────────────────────────────────────

    fun selectCell(coord: String) {
        val upper = coord.uppercase(Locale.ROOT).trim()
        if (FormulaEvaluator.parseCoordinate(upper) != null) {
            _selectedCell.value = upper
            _editingValue.value = activeSheet().cells[upper]?.value ?: ""
        }
    }

    fun updateSelectedCellValue(newValue: String, context: Context? = null) {
        pushUndo()
        val wb = _activeWorkbook.value
        val idx = wb.activeSheetIndex
        val sheet = wb.sheets[idx]
        val cells = sheet.cells.toMutableMap()
        val coord = _selectedCell.value
        cells[coord] = (cells[coord] ?: CellData()).copy(value = newValue)
        updateSheet(idx, sheet.copy(cells = cells))
        _editingValue.value = newValue
        saveActiveWorkbook()
    }

    fun applySelectedCellFormatting(
        bold: Boolean? = null,
        italic: Boolean? = null,
        fontSize: Float? = null,
        textColor: Long? = null,
        backgroundColor: Long? = null,
        alignment: CellAlignment? = null,
        context: Context? = null
    ) {
        pushUndo()
        val wb = _activeWorkbook.value
        val idx = wb.activeSheetIndex
        val sheet = wb.sheets[idx]
        val cells = sheet.cells.toMutableMap()
        val coord = _selectedCell.value
        val cur = cells[coord] ?: CellData()
        cells[coord] = cur.copy(
            bold = bold ?: cur.bold,
            italic = italic ?: cur.italic,
            fontSize = fontSize ?: cur.fontSize,
            textColor = textColor ?: cur.textColor,
            backgroundColor = backgroundColor ?: cur.backgroundColor,
            alignment = alignment ?: cur.alignment
        )
        updateSheet(idx, sheet.copy(cells = cells))
        saveActiveWorkbook()
    }

    fun applyConditionalRule(
        operator: String,
        value: String,
        textColor: Long,
        backgroundColor: Long,
        context: Context? = null
    ) {
        pushUndo()
        val wb = _activeWorkbook.value; val idx = wb.activeSheetIndex
        val sheet = wb.sheets[idx]; val cells = sheet.cells.toMutableMap()
        val coord = _selectedCell.value; val cur = cells[coord] ?: CellData()
        cells[coord] = cur.copy(
            conditionalRule = ConditionalRule(operator, value, textColor, backgroundColor)
        )
        updateSheet(idx, sheet.copy(cells = cells))
        saveActiveWorkbook()
    }

    fun clearConditionalRule(context: Context? = null) {
        pushUndo()
        val wb = _activeWorkbook.value; val idx = wb.activeSheetIndex
        val sheet = wb.sheets[idx]; val cells = sheet.cells.toMutableMap()
        val coord = _selectedCell.value; val cur = cells[coord] ?: CellData()
        cells[coord] = cur.copy(conditionalRule = null)
        updateSheet(idx, sheet.copy(cells = cells))
        saveActiveWorkbook()
    }

    fun copyCell(coord: String): CellData? = activeSheet().cells[coord]

    fun pasteCell(coord: String, cellData: CellData, context: Context? = null) {
        pushUndo()
        val wb = _activeWorkbook.value; val idx = wb.activeSheetIndex
        val sheet = wb.sheets[idx]; val cells = sheet.cells.toMutableMap()
        cells[coord] = cellData.copy()
        updateSheet(idx, sheet.copy(cells = cells))
        if (coord == _selectedCell.value) _editingValue.value = cellData.value
        saveActiveWorkbook()
    }

    fun smartAutofillDown(context: Context? = null) {
        pushUndo()
        val wb = _activeWorkbook.value; val idx = wb.activeSheetIndex
        val sheet = wb.sheets[idx]
        val coord = _selectedCell.value
        val parsed = FormulaEvaluator.parseCoordinate(coord) ?: return
        val row = parsed.first; val col = parsed.second
        val colLetter = FormulaEvaluator.getColName(col)
        val sourceVal = sheet.cells[coord]?.value ?: return
        val sourceDouble = sourceVal.toDoubleOrNull()
        val prevDouble = sheet.cells["$colLetter${row - 1}"]?.value?.toDoubleOrNull()
        val cells = sheet.cells.toMutableMap()

        for (i in 1..5) {
            val target = "$colLetter${row + i}"
            val newVal = if (sourceDouble != null) {
                val step = if (prevDouble != null) sourceDouble - prevDouble else 1.0
                val next = sourceDouble + step * i
                if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
            } else sourceVal
            cells[target] = (cells[target] ?: CellData()).copy(value = newVal)
        }
        updateSheet(idx, sheet.copy(cells = cells))
        saveActiveWorkbook()
    }

    // ─── Multi-Sheet ──────────────────────────────────────────────

    fun addNewSheet(context: Context? = null) {
        pushUndo()
        val wb = _activeWorkbook.value
        val sheets = wb.sheets.toMutableList()
            .also { it.add(Sheet(name = "Sheet${wb.sheets.size + 1}")) }
        _activeWorkbook.value = wb.copy(
            sheets = sheets,
            activeSheetIndex = sheets.size - 1,
            updatedAt = System.currentTimeMillis()
        )
        _selectedCell.value = "A1"; _editingValue.value = ""
        saveActiveWorkbook()
    }

    fun deleteActiveSheet(context: Context? = null) {
        val wb = _activeWorkbook.value
        if (wb.sheets.size <= 1) return
        pushUndo()
        val idx = wb.activeSheetIndex
        val sheets = wb.sheets.toMutableList().also { it.removeAt(idx) }
        val newIdx = maxOf(0, idx - 1)
        _activeWorkbook.value = wb.copy(
            sheets = sheets,
            activeSheetIndex = newIdx,
            updatedAt = System.currentTimeMillis()
        )
        _selectedCell.value = "A1"
        _editingValue.value = sheets[newIdx].cells["A1"]?.value ?: ""
        saveActiveWorkbook()
    }

    fun renameActiveSheet(newName: String, context: Context? = null) {
        if (newName.isBlank()) return
        pushUndo()
        val wb = _activeWorkbook.value; val idx = wb.activeSheetIndex
        val sheets = wb.sheets.toMutableList()
        sheets[idx] = sheets[idx].copy(name = newName.trim())
        _activeWorkbook.value = wb.copy(sheets = sheets, updatedAt = System.currentTimeMillis())
        saveActiveWorkbook()
    }

    fun selectSheetIndex(idx: Int) {
        val wb = _activeWorkbook.value
        if (idx !in wb.sheets.indices) return
        _activeWorkbook.value = wb.copy(activeSheetIndex = idx)
        _selectedCell.value = "A1"
        _editingValue.value = wb.sheets[idx].cells["A1"]?.value ?: ""
    }

    // ─── Protection ───────────────────────────────────────────────

    fun toggleSheetProtection(context: Context? = null) {
        pushUndo()
        val wb = _activeWorkbook.value; val idx = wb.activeSheetIndex
        val sheet = wb.sheets[idx]
        updateSheet(idx, sheet.copy(isProtected = !sheet.isProtected))
        saveActiveWorkbook()
    }

    fun toggleCellLock(coord: String, context: Context? = null) {
        pushUndo()
        val wb = _activeWorkbook.value; val idx = wb.activeSheetIndex
        val sheet = wb.sheets[idx]
        val locked = sheet.lockedCells.toMutableSet()
        if (coord in locked) locked.remove(coord) else locked.add(coord)
        updateSheet(idx, sheet.copy(lockedCells = locked))
        saveActiveWorkbook()
    }

    // ─── Chart ────────────────────────────────────────────────────

    fun setChartSettings(range: String, type: String) {
        _chartRange.value = range.uppercase(Locale.ROOT).trim()
        _chartType.value = type
    }

    fun extractChartData(): Pair<List<String>, List<Double>> {
        val sheet = activeSheet()
        val parts = _chartRange.value.split(":")
        if (parts.size != 2) return Pair(emptyList(), emptyList())
        val start = FormulaEvaluator.parseCoordinate(parts[0])
            ?: return Pair(emptyList(), emptyList())
        val end = FormulaEvaluator.parseCoordinate(parts[1])
            ?: return Pair(emptyList(), emptyList())
        val r1 = minOf(start.first, end.first); val r2 = maxOf(start.first, end.first)
        val c1 = minOf(start.second, end.second); val c2 = maxOf(start.second, end.second)
        val labels = mutableListOf<String>(); val values = mutableListOf<Double>()
        for (r in r1..r2) {
            val lc = "${FormulaEvaluator.getColName(c1)}$r"
            val vc = "${FormulaEvaluator.getColName(if (c2 > c1) c1 + 1 else c1)}$r"
            labels.add(FormulaEvaluator.evaluateCell(lc, sheet).ifEmpty { lc })
            values.add(FormulaEvaluator.evaluateCell(vc, sheet).toDoubleOrNull() ?: 0.0)
        }
        return Pair(labels, values)
    }

    // ─── Export / Import ──────────────────────────────────────────

    fun exportToCSVString(): String = ExportHelper.exportToCSV(activeSheet())

    fun saveCSVToDownloads(fileName: String): Boolean =
        ExportHelper.saveCSVToDownloads(ctx, activeSheet(), fileName)

    fun exportToPDFFile(file: File): Boolean =
        ExportHelper.exportToPDF(activeSheet(), file, _activeWorkbook.value.title)

    fun exportPDFToDownloads(fileName: String): Boolean =
        ExportHelper.exportToPDFToDownloads(ctx, activeSheet(), fileName, _activeWorkbook.value.title)

    fun importFromCSVString(csvContent: String, sheetName: String, context: Context? = null) {
        pushUndo()
        val imported = ExportHelper.importFromCSV(csvContent, sheetName)
        val wb = _activeWorkbook.value
        val sheets = wb.sheets.toMutableList().also { it.add(imported) }
        _activeWorkbook.value = wb.copy(
            sheets = sheets,
            activeSheetIndex = sheets.size - 1,
            updatedAt = System.currentTimeMillis()
        )
        _selectedCell.value = "A1"
        _editingValue.value = imported.cells["A1"]?.value ?: ""
        saveActiveWorkbook()
    }

    // ─── Search ───────────────────────────────────────────────────

    fun searchMatches(query: String): List<String> {
        _searchQuery.value = query
        if (query.isBlank()) return emptyList()
        val sheet = activeSheet()
        return sheet.cells.entries
            .filter { (coord, cell) ->
                val eval = FormulaEvaluator.evaluateCell(coord, sheet)
                cell.value.contains(query, ignoreCase = true) ||
                        eval.contains(query, ignoreCase = true)
            }
            .map { it.key }
    }
}

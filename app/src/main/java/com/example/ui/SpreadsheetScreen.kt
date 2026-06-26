package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.CellAlignment
import com.example.CellData
import com.example.FormulaEvaluator
import com.example.Sheet
import com.example.SpreadsheetViewModel
import java.io.File
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpreadsheetScreen(
    viewModel: SpreadsheetViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeWorkbook by viewModel.activeWorkbook.collectAsState()
    val selectedCell by viewModel.selectedCell.collectAsState()
    val editingValue by viewModel.editingValue.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val chartRange by viewModel.chartRange.collectAsState()
    val chartType by viewModel.chartType.collectAsState()

    val startRow by viewModel.viewportStartRow.collectAsState()
    val startCol by viewModel.viewportStartCol.collectAsState()

    val sheetIndex = activeWorkbook.activeSheetIndex
    val activeSheet = activeWorkbook.sheets[sheetIndex]

    // Local dialog controls
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpTarget by remember { mutableStateOf("") }
    var showRenameSheetDialog by remember { mutableStateOf(false) }
    var sheetNewName by remember { mutableStateOf("") }
    var showConditionalDialog by remember { mutableStateOf(false) }
    var showChartDialog by remember { mutableStateOf(false) }
    var clipboardCell by remember { mutableStateOf<CellData?>(null) }

    // Navigation and Grid dimensions
    val numVisibleRows = 16
    val numVisibleCols = 5

    // Colors list for formatting pickers
    val textColors = listOf(
        Pair("Dark", 0xFF000000L),
        Pair("Teal", 0xFF0D9488L),
        Pair("Red", 0xFFC62828L),
        Pair("Blue", 0xFF1565C0L),
        Pair("Orange", 0xFFE65100L)
    )
    val bgColors = listOf(
        Pair("White", 0xFFFFFFFFL),
        Pair("Light Green", 0xFFE8F5E9L),
        Pair("Light Red", 0xFFFFEBEEL),
        Pair("Light Blue", 0xFFE1F5FEL),
        Pair("Light Orange", 0xFFFFF3E0L),
        Pair("Light Yellow", 0xFFFFFDE7L)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeWorkbook.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D9488),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Offline Spreadsheet Mode",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToDashboard(context) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Undo/Redo
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (viewModel.canUndo()) Color(0xFF0D9488) else Color.Gray.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (viewModel.canRedo()) Color(0xFF0D9488) else Color.Gray.copy(alpha = 0.5f)
                        )
                    }
                    
                    // Share / Export PDF
                    IconButton(onClick = {
                        val pdfFile = File(context.filesDir, "${activeWorkbook.title}.pdf")
                        val success = viewModel.exportToPDFFile(pdfFile)
                        if (success) {
                            Toast.makeText(context, "PDF saved: ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to export PDF", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = Color(0xFF0D9488))
                    }

                    // CSV Export/Import
                    var showCsvMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showCsvMenu = true }) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "CSV Tools", tint = Color(0xFF0D9488))
                    }
                    DropdownMenu(expanded = showCsvMenu, onDismissRequest = { showCsvMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export active sheet to CSV") },
                            onClick = {
                                showCsvMenu = false
                                val csv = viewModel.exportToCSVString()
                                val file = File(context.filesDir, "${activeSheet.name}.csv")
                                file.writeText(csv)
                                Toast.makeText(context, "CSV saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import test dummy CSV") },
                            onClick = {
                                showCsvMenu = false
                                val mockCsv = """
                                    Product,Sales,Cost,Profit
                                    Apples,150,90,60
                                    Bananas,220,110,110
                                    Grapes,310,180,130
                                    Orange,120,70,50
                                """.trimIndent()
                                viewModel.importFromCSVString(mockCsv, "CSV Imported", context)
                                Toast.makeText(context, "CSV Imported into a new sheet", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    
                    // Save Button
                    IconButton(onClick = {
                        viewModel.saveActiveWorkbookDirect(context)
                        Toast.makeText(context, "Spreadsheet auto-saved to internal storage", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save Sheet", tint = Color(0xFF2DD4BF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // SEARCH & CELL NAVIGATION ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Jump to coordinate
                Button(
                    onClick = {
                        jumpTarget = selectedCell
                        showJumpDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0FDFA), contentColor = Color(0xFF0D9488)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Go To: $selectedCell", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                // Search Bar
                var tempSearchQuery by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = tempSearchQuery,
                    onValueChange = {
                        tempSearchQuery = it
                        viewModel.searchMatches(it)
                    },
                    placeholder = { Text("Find cells...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (tempSearchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                tempSearchQuery = ""
                                viewModel.searchMatches("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0D9488),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )
            }

            // FORMULA BAR
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF0FDFA), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "fx",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D9488),
                            fontSize = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Input for formula or value
                    TextField(
                        value = editingValue,
                        onValueChange = { viewModel.updateSelectedCellValue(it, context) },
                        placeholder = { Text("Enter text, number, or formula (=SUM...)", fontSize = 12.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Rule-based formula suggestion bar
            AnimatedVisibility(
                visible = editingValue.startsWith("="),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0FDFA))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SUM(", "AVERAGE(", "MIN(", "MAX(", "COUNT(").forEach { funcName ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                                .clickable {
                                    viewModel.updateSelectedCellValue("=$funcName", context)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "=$funcName",
                                color = Color(0xFF0D9488),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // DYNAMIC CELL STYLING CONTROLS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Formatting States for selected cell
                val currentCell = activeSheet.cells[selectedCell] ?: CellData()
                
                // Bold
                IconToggleButton(
                    checked = currentCell.bold,
                    onCheckedChange = { viewModel.applySelectedCellFormatting(bold = it, context = context) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.FormatBold,
                        contentDescription = "Bold",
                        tint = if (currentCell.bold) Color(0xFF0D9488) else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Italic
                IconToggleButton(
                    checked = currentCell.italic,
                    onCheckedChange = { viewModel.applySelectedCellFormatting(italic = it, context = context) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.FormatItalic,
                        contentDescription = "Italic",
                        tint = if (currentCell.italic) Color(0xFF0D9488) else MaterialTheme.colorScheme.onSurface
                    )
                }

                // FontSize
                IconButton(
                    onClick = { viewModel.applySelectedCellFormatting(fontSize = maxOf(8f, currentCell.fontSize - 1f), context = context) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${currentCell.fontSize.toInt()}pt",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { viewModel.applySelectedCellFormatting(fontSize = minOf(24f, currentCell.fontSize + 1f), context = context) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase Font Size", modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))
                
                // Alignment
                Row {
                    IconButton(
                        onClick = { viewModel.applySelectedCellFormatting(alignment = CellAlignment.LEFT, context = context) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.FormatAlignLeft,
                            contentDescription = "Left Align",
                            tint = if (currentCell.alignment == CellAlignment.LEFT) Color(0xFF0D9488) else Color.Gray
                        )
                    }
                    IconButton(
                        onClick = { viewModel.applySelectedCellFormatting(alignment = CellAlignment.CENTER, context = context) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.FormatAlignCenter,
                            contentDescription = "Center Align",
                            tint = if (currentCell.alignment == CellAlignment.CENTER) Color(0xFF0D9488) else Color.Gray
                        )
                    }
                    IconButton(
                        onClick = { viewModel.applySelectedCellFormatting(alignment = CellAlignment.RIGHT, context = context) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.FormatAlignRight,
                            contentDescription = "Right Align",
                            tint = if (currentCell.alignment == CellAlignment.RIGHT) Color(0xFF0D9488) else Color.Gray
                        )
                    }
                }

                // Cell protection / Lock
                val cellIsLocked = activeSheet.lockedCells.contains(selectedCell)
                IconButton(
                    onClick = { viewModel.toggleCellLock(selectedCell, context) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (cellIsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock cell",
                        tint = if (cellIsLocked) Color(0xFFC62828) else Color.Gray
                    )
                }

                // Copy/Paste
                IconButton(
                    onClick = {
                        clipboardCell = viewModel.copyCell(selectedCell)
                        Toast.makeText(context, "Copied cell $selectedCell", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Cell", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        clipboardCell?.let {
                            viewModel.pasteCell(selectedCell, it, context)
                            Toast.makeText(context, "Pasted into $selectedCell", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = clipboardCell != null,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = "Paste Cell",
                        modifier = Modifier.size(18.dp),
                        tint = if (clipboardCell != null) Color(0xFF0D9488) else Color.Gray.copy(alpha = 0.5f)
                    )
                }

                // Autofill Series Button
                IconButton(
                    onClick = {
                        viewModel.smartAutofillDown(context)
                        Toast.makeText(context, "Auto-filled series down 5 rows", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.AutoMode, contentDescription = "Smart Fill Series", tint = Color(0xFF1565C0))
                }

                // Conditional format trigger
                IconButton(
                    onClick = { showConditionalDialog = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Colorize, contentDescription = "Conditional Formatting", tint = Color(0xFF7B1FA2))
                }
            }

            // VIRTUALIZED SPREADSHEET 2D VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(4.dp)
                    .border(0.5.dp, Color(0xFFE2E8F0)) // Slate 200 border
            ) {
                Column {
                    // Column Headers (Top Row: e.g. A, B, C...)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9)) // Slate 100
                    ) {
                        // Corner empty box
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(26.dp)
                                .border(0.5.dp, Color(0xFFE2E8F0)), // Slate 200
                            contentAlignment = Alignment.Center
                        ) {
                            Text("", fontSize = 10.sp)
                        }
                        
                        // Visible column names
                        for (c in 0 until numVisibleCols) {
                            val colIndex = startCol + c
                            val colName = FormulaEvaluator.getColName(colIndex)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .border(0.5.dp, Color(0xFFE2E8F0)), // Slate 200
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = colName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    // Grid Data (Rows)
                    for (r in 0 until numVisibleRows) {
                        val rowIndex = startRow + r
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Row Header (Leftmost index)
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(32.dp)
                                    .background(Color(0xFFF1F5F9)) // Slate 100
                                    .border(0.5.dp, Color(0xFFE2E8F0)), // Slate 200
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rowIndex.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray
                                )
                            }

                            // Active visible row cells
                            for (c in 0 until numVisibleCols) {
                                val colIndex = startCol + c
                                val colName = FormulaEvaluator.getColName(colIndex)
                                val coord = "$colName$rowIndex"
                                
                                val cell = activeSheet.cells[coord] ?: CellData()
                                val evaluatedValue = FormulaEvaluator.evaluateCell(coord, activeSheet)
                                val isSelected = coord == selectedCell

                                // Check conditional rules
                                var finalBg = Color(cell.backgroundColor)
                                var finalTxt = Color(cell.textColor)
                                if (cell.conditionalRule != null) {
                                    val rule = cell.conditionalRule
                                    val matches = when (rule.operator) {
                                        "GREATER_THAN" -> (evaluatedValue.toDoubleOrNull() ?: 0.0) > (rule.value.toDoubleOrNull() ?: 0.0)
                                        "LESS_THAN" -> (evaluatedValue.toDoubleOrNull() ?: 0.0) < (rule.value.toDoubleOrNull() ?: 0.0)
                                        "EQUALS" -> evaluatedValue.equals(rule.value, ignoreCase = true)
                                        "CONTAINS" -> evaluatedValue.contains(rule.value, ignoreCase = true)
                                        else -> false
                                    }
                                    if (matches) {
                                        finalBg = Color(rule.backgroundColor)
                                        finalTxt = Color(rule.textColor)
                                    }
                                }

                                // Check Search Highlight
                                val isSearchMatch = searchQuery.isNotEmpty() && (
                                    cell.value.contains(searchQuery, ignoreCase = true) ||
                                    evaluatedValue.contains(searchQuery, ignoreCase = true)
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .background(finalBg)
                                        .border(
                                            width = if (isSelected) 2.5.dp else if (isSearchMatch) 2.dp else 0.5.dp,
                                            color = if (isSelected) Color(0xFF0D9488) else if (isSearchMatch) Color.Yellow else Color(0xFFE2E8F0)
                                        )
                                        .clickable {
                                            // Handle cell selection
                                            viewModel.selectCell(coord)
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = evaluatedValue,
                                        fontSize = cell.fontSize.sp,
                                        fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (cell.italic) FontStyle.Italic else FontStyle.Normal,
                                        color = finalTxt,
                                        textAlign = when (cell.alignment) {
                                            CellAlignment.LEFT -> TextAlign.Start
                                            CellAlignment.CENTER -> TextAlign.Center
                                            CellAlignment.RIGHT -> TextAlign.End
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // NAVIGATION & SCROLL CONTROLLERS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation Arrow Pads (Left aligned)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.scrollViewport(0, -1) },
                        enabled = startCol > 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Scroll Left", modifier = Modifier.size(20.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { viewModel.scrollViewport(-1, 0) },
                            enabled = startRow > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll Up", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { viewModel.scrollViewport(1, 0) },
                            enabled = startRow < 1048576 - numVisibleRows,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll Down", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = { viewModel.scrollViewport(0, 1) },
                        enabled = startCol < 16384 - numVisibleCols,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Scroll Right", modifier = Modifier.size(20.dp))
                    }
                }

                // Info stats of viewport
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Viewing Rows: $startRow-${startRow + numVisibleRows - 1}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Columns: ${FormulaEvaluator.getColName(startCol)}-${FormulaEvaluator.getColName(startCol + numVisibleCols - 1)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Advanced Panel Buttons: Custom Charts & Locked Status (Right aligned)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Analytics chart trigger
                    IconButton(
                        onClick = { showChartDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFF0FDFA), CircleShape) // Light Teal 50
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = "View Analytics Charts", tint = Color(0xFF0D9488))
                    }
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // Locked sheet protection status icon
                    IconButton(
                        onClick = { viewModel.toggleSheetProtection(context) },
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (activeSheet.isProtected) Color(0xFFFFEBEE) else Color(0xFFECEFF1),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (activeSheet.isProtected) Icons.Default.VerifiedUser else Icons.Default.Security,
                            contentDescription = "Protect Sheet",
                            tint = if (activeSheet.isProtected) Color(0xFFC62828) else Color.DarkGray
                        )
                    }
                }
            }

            // MULTI-SHEET BOTTOM NAVIGATION TABS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5F9)) // Slate 100
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeWorkbook.sheets.forEachIndexed { index, sheet ->
                        val isSheetActive = index == activeWorkbook.activeSheetIndex
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSheetActive) Color(0xFF0D9488) else Color.LightGray.copy(alpha = 0.5f))
                                .combinedClickable(
                                    onClick = { viewModel.selectSheetIndex(index) },
                                    onLongClick = {
                                        sheetNewName = sheet.name
                                        showRenameSheetDialog = true
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = sheet.name,
                                fontSize = 11.sp,
                                fontWeight = if (isSheetActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSheetActive) Color.White else Color.Black
                            )
                        }
                    }
                }

                // Add Sheet
                IconButton(
                    onClick = { viewModel.addNewSheet(context) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.AddBox, contentDescription = "Add New Sheet", tint = Color(0xFF0D9488))
                }

                // Delete Active Sheet
                IconButton(
                    onClick = { viewModel.deleteActiveSheet(context) },
                    enabled = activeWorkbook.sheets.size > 1,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Delete Active Sheet",
                        tint = if (activeWorkbook.sheets.size > 1) Color(0xFFC62828) else Color.Gray.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    // Go To (Jump Cell) Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Go To Cell Coordinate") },
            text = {
                Column {
                    Text(
                        text = "Enter a cell coordinate to scroll the viewport directly to (e.g., A500, XFD1048576):",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = jumpTarget,
                        onValueChange = { jumpTarget = it.uppercase() },
                        placeholder = { Text("e.g. C100") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0D9488),
                            focusedLabelColor = Color(0xFF0D9488)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = FormulaEvaluator.parseCoordinate(jumpTarget)
                        if (parsed != null) {
                            showJumpDialog = false
                            // Center or start viewport at target
                            viewModel.setViewportStart(parsed.first, parsed.second)
                            viewModel.selectCell(jumpTarget)
                        } else {
                            Toast.makeText(context, "Invalid Coordinate format", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Sheet Dialog
    if (showRenameSheetDialog) {
        AlertDialog(
            onDismissRequest = { showRenameSheetDialog = false },
            title = { Text("Rename Sheet") },
            text = {
                OutlinedTextField(
                    value = sheetNewName,
                    onValueChange = { sheetNewName = it },
                    label = { Text("Sheet Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0D9488)),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRenameSheetDialog = false
                        viewModel.renameActiveSheet(sheetNewName, context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameSheetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Conditional Formatting Dialog
    if (showConditionalDialog) {
        var op by remember { mutableStateOf("GREATER_THAN") }
        var ruleVal by remember { mutableStateOf("") }
        var textColChoice by remember { mutableStateOf(0xFFC62828L) }
        var bgColChoice by remember { mutableStateOf(0xFFFFEBEEL) }

        AlertDialog(
            onDismissRequest = { showConditionalDialog = false },
            title = { Text("Conditional Formatting") },
            text = {
                Column {
                    Text("Set highlighting rule for cell $selectedCell:", fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Operator:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("GREATER_THAN", "LESS_THAN", "EQUALS", "CONTAINS").forEach { o ->
                            val isSelected = op == o
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF0D9488) else Color.LightGray.copy(alpha = 0.5f))
                                    .clickable { op = o }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(o, fontSize = 9.sp, color = if (isSelected) Color.White else Color.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ruleVal,
                        onValueChange = { ruleVal = it },
                        label = { Text("Value / Text to compare") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Visual Style:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row {
                        // Highlighting style presets
                        Column(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFEBEE))
                                .clickable {
                                    textColChoice = 0xFFC62828L
                                    bgColChoice = 0xFFFFEBEEL
                                }
                                .padding(8.dp)
                        ) {
                            Text("Soft Red Alert", fontSize = 10.sp, color = Color(0xFFC62828L))
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE8F5E9))
                                .clickable {
                                    textColChoice = 0xFF1B5E20L
                                    bgColChoice = 0xFFE8F5E9L
                                }
                                .padding(8.dp)
                        ) {
                            Text("Soft Green Success", fontSize = 10.sp, color = Color(0xFF1B5E20L))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConditionalDialog = false
                        viewModel.applyConditionalRule(op, ruleVal, textColChoice, bgColChoice, context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    Text("Apply Rule")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showConditionalDialog = false
                        viewModel.clearConditionalRule(context)
                    }) {
                        Text("Clear Rule", color = Color.Red)
                    }
                    TextButton(onClick = { showConditionalDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Built-in Chart Viewer Dialog
    if (showChartDialog) {
        Dialog(onDismissRequest = { showChartDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Interactive Sheet Chart", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0D9488))
                        IconButton(onClick = { showChartDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Settings controls
                    var tempChartRange by remember { mutableStateOf(chartRange) }
                    var tempChartType by remember { mutableStateOf(chartType) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = tempChartRange,
                            onValueChange = { tempChartRange = it },
                            label = { Text("Cell Range (e.g. A5:B9)", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.setChartSettings(tempChartRange, tempChartType) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Plot", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Type Selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("BAR", "LINE", "PIE").forEach { type ->
                            val selected = tempChartType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color(0xFF0D9488) else Color.LightGray.copy(alpha = 0.5f))
                                    .clickable {
                                        tempChartType = type
                                        viewModel.setChartSettings(tempChartRange, type)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(type, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Color.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Extract data and draw Canvas
                    val data = viewModel.extractChartData()
                    if (data.first.isEmpty() || data.second.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No numerical data found in range.\nEnsure range format is (LabelsCol:ValuesCol) e.g. A5:B9",
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    } else {
                        SpreadsheetChart(
                            labels = data.first,
                            values = data.second,
                            type = chartType,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(0.5.dp, Color.LightGray)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Plotting range: $chartRange ($chartType Chart)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Custom Offline Chart Composable
@Composable
fun SpreadsheetChart(
    labels: List<String>,
    values: List<Double>,
    type: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.padding(16.dp)) {
        val width = size.width
        val height = size.height
        val maxVal = maxOf(1.0, values.maxOrNull() ?: 1.0)
        
        when (type) {
            "BAR" -> {
                val numBars = values.size
                val gap = 12f
                val barWidth = (width - (gap * (numBars + 1))) / numBars
                
                for (i in 0 until numBars) {
                    val value = values[i]
                    val barHeight = (value / maxVal) * (height - 40f)
                    
                    val x = gap + i * (barWidth + gap)
                    val y = height - 20f - barHeight.toFloat()
                    
                    // Draw Bar
                    drawRect(
                        color = Color(0xFF0D9488),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight.toFloat())
                    )
                    
                    // Draw outline
                    drawRect(
                        color = Color(0xFF0F766E),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight.toFloat()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)
                    )
                    
                    // Draw value text
                    drawContext.canvas.nativeCanvas.drawText(
                        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value),
                        x + barWidth / 2f,
                        y - 6f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                    
                    // Draw label text
                    val lbl = labels[i]
                    val drawLbl = if (lbl.length > 8) lbl.substring(0, 6) + ".." else lbl
                    drawContext.canvas.nativeCanvas.drawText(
                        drawLbl,
                        x + barWidth / 2f,
                        height,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.DKGRAY
                            textSize = 22f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
            "LINE" -> {
                val numPoints = values.size
                val stepX = width / (numPoints - 1).coerceAtLeast(1)
                val path = Path()
                
                val points = mutableListOf<Offset>()
                for (i in 0 until numPoints) {
                    val value = values[i]
                    val pointHeight = (value / maxVal) * (height - 40f)
                    val x = i * stepX
                    val y = height - 20f - pointHeight.toFloat()
                    points.add(Offset(x, y))
                    
                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                
                // Draw path line
                drawPath(
                    path = path,
                    color = Color(0xFF1565C0),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(4f)
                )
                
                // Draw nodes
                for (i in 0 until numPoints) {
                    val pt = points[i]
                    drawCircle(
                        color = Color(0xFF0D9488),
                        radius = 8f,
                        center = pt
                    )
                    
                    // Value text
                    drawContext.canvas.nativeCanvas.drawText(
                        values[i].toInt().toString(),
                        pt.x,
                        pt.y - 10f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                    
                    // Label
                    val lbl = labels[i]
                    val drawLbl = if (lbl.length > 8) lbl.substring(0, 6) + ".." else lbl
                    drawContext.canvas.nativeCanvas.drawText(
                        drawLbl,
                        pt.x,
                        height,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.DKGRAY
                            textSize = 22f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
            "PIE" -> {
                val sum = values.sumOf { it }.coerceAtLeast(1.0)
                var startAngle = 0f
                val colors = listOf(
                    Color(0xFF0D9488), Color(0xFF1565C0), Color(0xFFE65100),
                    Color(0xFFC62828), Color(0xFF7B1FA2), Color(0xFF00796B)
                )
                
                val diameter = minOf(width / 1.5f, height - 40f)
                val rect = RectF(
                    width / 2f - diameter / 2f,
                    height / 2f - diameter / 2f,
                    width / 2f + diameter / 2f,
                    height / 2f + diameter / 2f
                )
                
                for (i in values.indices) {
                    val sweep = ((values[i] / sum) * 360f).toFloat()
                    val col = colors[i % colors.size]
                    
                    drawArc(
                        color = col,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(width / 2f - diameter / 2f, height / 2f - diameter / 2f),
                        size = Size(diameter, diameter)
                    )
                    
                    // Draw outline borders
                    drawArc(
                        color = Color.White,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(width / 2f - diameter / 2f, height / 2f - diameter / 2f),
                        size = Size(diameter, diameter),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2f)
                    )
                    
                    startAngle += sweep
                }
                
                // Draw a small colorful list index side legend
                val legendPaint = android.graphics.Paint().apply {
                    textSize = 18f
                    isAntiAlias = true
                }
                for (i in 0 until minOf(values.size, 5)) {
                    val col = colors[i % colors.size]
                    val y = 20f + i * 24f
                    
                    drawCircle(
                        color = col,
                        radius = 6f,
                        center = Offset(20f, y)
                    )
                    
                    val lblStr = "${labels[i]}: ${values[i].toInt()}"
                    drawContext.canvas.nativeCanvas.drawText(
                        if (lblStr.length > 15) lblStr.substring(0, 12) + ".." else lblStr,
                        32f,
                        y + 6f,
                        legendPaint.apply { color = android.graphics.Color.BLACK }
                    )
                }
            }
        }
    }
}

private class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

package com.example

import java.util.Locale

object FormulaEvaluator {

    fun colNameToIndex(name: String): Int {
        var result = 0
        val cleaned = name.uppercase(Locale.ROOT).replace("[^A-Z]".toRegex(), "")
        for (i in 0 until cleaned.length) {
            result *= 26
            result += cleaned[i] - 'A' + 1
        }
        return result
    }

    fun getColName(col: Int): String {
        var temp = col
        val sb = java.lang.StringBuilder()
        while (temp > 0) {
            temp--
            val rem = temp % 26
            sb.append(('A' + rem).toChar())
            temp /= 26
        }
        return sb.reverse().toString()
    }

    fun parseCoordinate(coord: String): Pair<Int, Int>? {
        val regex = Regex("^([A-Z]+)([0-9]+)$")
        val match = regex.matchEntire(coord.uppercase(Locale.ROOT)) ?: return null
        val colName = match.groupValues[1]
        val rowNum = match.groupValues[2].toIntOrNull() ?: return null
        val colIndex = colNameToIndex(colName)
        return Pair(rowNum, colIndex)
    }

    // Expand a range like "A1:B3" into a list of cell coordinates: ["A1", "A2", "A3", "B1", "B2", "B3"]
    fun expandRange(rangeStr: String): List<String> {
        val parts = rangeStr.split(":")
        if (parts.size != 2) return listOf(rangeStr)
        
        val start = parseCoordinate(parts[0]) ?: return listOf(rangeStr)
        val end = parseCoordinate(parts[1]) ?: return listOf(rangeStr)
        
        val startRow = minOf(start.first, end.first)
        val endRow = maxOf(start.first, end.first)
        val startCol = minOf(start.second, end.second)
        val endCol = maxOf(start.second, end.second)
        
        val coordinates = mutableListOf<String>()
        for (r in startRow..endRow) {
            for (c in startCol..endCol) {
                coordinates.add("${getColName(c)}$r")
            }
        }
        return coordinates
    }

    // Evaluates a specific coordinate on a sheet. Uses visited set to detect infinite loops.
    fun evaluateCell(coordinate: String, sheet: Sheet, visited: MutableSet<String> = mutableSetOf()): String {
        val coordUpper = coordinate.uppercase(Locale.ROOT).trim()
        
        // Prevent infinite loops/circular references
        if (visited.contains(coordUpper)) {
            return "#REF!"
        }
        
        val cell = sheet.cells[coordUpper] ?: return ""
        val rawValue = cell.value.trim()
        
        if (!rawValue.startsWith("=")) {
            return rawValue
        }
        
        visited.add(coordUpper)
        val result = try {
            evaluateFormulaString(rawValue.substring(1), sheet, visited)
        } catch (e: Exception) {
            "#VALUE!"
        }
        visited.remove(coordUpper)
        
        return result
    }

    // Evaluates the formula part (without '=')
    private fun evaluateFormulaString(formula: String, sheet: Sheet, visited: MutableSet<String>): String {
        val uppercaseFormula = formula.uppercase(Locale.ROOT).trim()
        
        // 1. Check for basic spreadsheet functions
        val funcRegex = Regex("^([A-Z]+)\\((.*)\\)$")
        val matchResult = funcRegex.matchEntire(uppercaseFormula)
        if (matchResult != null) {
            val funcName = matchResult.groupValues[1]
            val argsStr = matchResult.groupValues[2]
            
            val values = parseArgsAndEvaluate(argsStr, sheet, visited)
            return when (funcName) {
                "SUM" -> {
                    val sum = values.sumOf { it }
                    if (sum % 1.0 == 0.0) sum.toInt().toString() else String.format(Locale.US, "%.4f", sum).trimEnd('0').trimEnd('.')
                }
                "AVERAGE" -> {
                    if (values.isEmpty()) "0" else {
                        val avg = values.average()
                        if (avg % 1.0 == 0.0) avg.toInt().toString() else String.format(Locale.US, "%.4f", avg).trimEnd('0').trimEnd('.')
                    }
                }
                "MIN" -> {
                    if (values.isEmpty()) "0" else {
                        val min = values.minOrNull() ?: 0.0
                        if (min % 1.0 == 0.0) min.toInt().toString() else String.format(Locale.US, "%.4f", min).trimEnd('0').trimEnd('.')
                    }
                }
                "MAX" -> {
                    if (values.isEmpty()) "0" else {
                        val max = values.maxOrNull() ?: 0.0
                        if (max % 1.0 == 0.0) max.toInt().toString() else String.format(Locale.US, "%.4f", max).trimEnd('0').trimEnd('.')
                    }
                }
                "COUNT" -> {
                    values.size.toString()
                }
                else -> "#NAME?"
            }
        }
        
        // 2. Check for basic arithmetic operators: +, -, *, /
        for (op in listOf('+', '-', '*', '/')) {
            val idx = findOperatorSplitIndex(uppercaseFormula, op)
            if (idx != -1) {
                val leftPart = uppercaseFormula.substring(0, idx).trim()
                val rightPart = uppercaseFormula.substring(idx + 1).trim()
                
                val leftVal = evaluateTerm(leftPart, sheet, visited)
                val rightVal = evaluateTerm(rightPart, sheet, visited)
                
                return when (op) {
                    '+' -> formatDoubleResult(leftVal + rightVal)
                    '-' -> formatDoubleResult(leftVal - rightVal)
                    '*' -> formatDoubleResult(leftVal * rightVal)
                    '/' -> {
                        if (rightVal == 0.0) "#DIV/0!" else formatDoubleResult(leftVal / rightVal)
                    }
                    else -> "#VALUE!"
                }
            }
        }
        
        // 3. Simple coordinate lookup or constant
        return evaluateTerm(uppercaseFormula, sheet, visited).let { formatDoubleResult(it) }
    }

    private fun findOperatorSplitIndex(formula: String, op: Char): Int {
        var depth = 0
        // Find operator, ignoring operators inside parentheses
        for (i in formula.length - 1 downTo 0) {
            val char = formula[i]
            if (char == ')') depth++
            if (char == '(') depth--
            if (depth == 0 && char == op) {
                return i
            }
        }
        return -1
    }

    private fun evaluateTerm(term: String, sheet: Sheet, visited: MutableSet<String>): Double {
        if (term.isEmpty()) return 0.0
        
        // Is it a number?
        term.toDoubleOrNull()?.let { return it }
        
        // Is it a coordinate?
        if (parseCoordinate(term) != null) {
            val cellVal = evaluateCell(term, sheet, visited)
            return cellVal.toDoubleOrNull() ?: 0.0
        }
        
        return 0.0
    }

    private fun parseArgsAndEvaluate(argsStr: String, sheet: Sheet, visited: MutableSet<String>): List<Double> {
        val result = mutableListOf<Double>()
        // Arguments can be separated by commas
        val args = argsStr.split(",")
        for (arg in args) {
            val trimmed = arg.trim()
            if (trimmed.contains(":")) {
                // Expanded range
                val expanded = expandRange(trimmed)
                for (coord in expanded) {
                    val evaluated = evaluateCell(coord, sheet, visited)
                    evaluated.toDoubleOrNull()?.let { result.add(it) }
                }
            } else if (parseCoordinate(trimmed) != null) {
                val evaluated = evaluateCell(trimmed, sheet, visited)
                evaluated.toDoubleOrNull()?.let { result.add(it) }
            } else {
                trimmed.toDoubleOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    private fun formatDoubleResult(num: Double): String {
        return if (num % 1.0 == 0.0) {
            num.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", num).trimEnd('0').trimEnd('.')
        }
    }
}

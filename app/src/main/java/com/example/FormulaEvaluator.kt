package com.example

import java.util.Locale

object FormulaEvaluator {

    fun colNameToIndex(name: String): Int {
        var result = 0
        val cleaned = name.uppercase(Locale.ROOT).replace("[^A-Z]".toRegex(), "")
        for (i in cleaned.indices) {
            result *= 26
            result += cleaned[i] - 'A' + 1
        }
        return result
    }

    fun getColName(col: Int): String {
        var temp = col
        val sb = StringBuilder()
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
        if (rowNum <= 0) return null
        val colIndex = colNameToIndex(colName)
        if (colIndex <= 0) return null
        return Pair(rowNum, colIndex)
    }

    fun expandRange(rangeStr: String): List<String> {
        val parts = rangeStr.split(":")
        if (parts.size != 2) return listOf(rangeStr)

        val start = parseCoordinate(parts[0].trim()) ?: return listOf(rangeStr)
        val end = parseCoordinate(parts[1].trim()) ?: return listOf(rangeStr)

        val startRow = minOf(start.first, end.first)
        val endRow = maxOf(start.first, end.first)
        val startCol = minOf(start.second, end.second)
        val endCol = maxOf(start.second, end.second)

        // Safety limit: max 10,000 cells in a range
        val totalCells = (endRow - startRow + 1) * (endCol - startCol + 1)
        if (totalCells > 10_000) return listOf("#REF!")

        val coordinates = mutableListOf<String>()
        for (r in startRow..endRow) {
            for (c in startCol..endCol) {
                coordinates.add("${getColName(c)}$r")
            }
        }
        return coordinates
    }

    fun evaluateCell(
        coordinate: String,
        sheet: Sheet,
        visited: MutableSet<String> = mutableSetOf()
    ): String {
        val coordUpper = coordinate.uppercase(Locale.ROOT).trim()

        if (coordUpper.isBlank()) return ""

        // Circular reference guard
        if (coordUpper in visited) return "#REF!"

        val cell = sheet.cells[coordUpper] ?: return ""
        val rawValue = cell.value.trim()

        if (!rawValue.startsWith("=")) return rawValue

        visited.add(coordUpper)
        val result = try {
            evaluateFormulaString(rawValue.substring(1), sheet, visited)
        } catch (e: ArithmeticException) {
            "#DIV/0!"
        } catch (e: Exception) {
            "#VALUE!"
        }
        visited.remove(coordUpper)

        return result
    }

    private fun evaluateFormulaString(
        formula: String,
        sheet: Sheet,
        visited: MutableSet<String>
    ): String {
        val upper = formula.uppercase(Locale.ROOT).trim()
        if (upper.isEmpty()) return ""

        // ── 1. Function call: NAME(args) ──────────────────────────
        val funcRegex = Regex("^([A-Z]+)\\((.*)\\)$", RegexOption.DOT_MATCHES_ALL)
        val funcMatch = funcRegex.matchEntire(upper)
        if (funcMatch != null) {
            val funcName = funcMatch.groupValues[1]
            val argsStr = funcMatch.groupValues[2]
            return evaluateFunction(funcName, argsStr, sheet, visited)
        }

        // ── 2. String concatenation: & operator ───────────────────
        if (upper.contains("&")) {
            val idx = upper.indexOf("&")
            val left = evaluateFormulaString(formula.substring(0, idx).trim(), sheet, visited)
            val right = evaluateFormulaString(formula.substring(idx + 1).trim(), sheet, visited)
            return left + right
        }

        // ── 3. Comparison operators ───────────────────────────────
        for (op in listOf(">=", "<=", "<>", ">", "<", "=")) {
            val idx = upper.indexOf(op)
            if (idx != -1) {
                val left = evaluateTerm(upper.substring(0, idx).trim(), sheet, visited)
                val right = evaluateTerm(upper.substring(idx + op.length).trim(), sheet, visited)
                val result = when (op) {
                    ">=" -> left >= right
                    "<=" -> left <= right
                    "<>" -> left != right
                    ">" -> left > right
                    "<" -> left < right
                    "=" -> left == right
                    else -> false
                }
                return if (result) "TRUE" else "FALSE"
            }
        }

        // ── 4. Arithmetic: +, -, *, / (right to left, lower precedence first) ──
        // Addition & Subtraction
        for (op in listOf('+', '-')) {
            val idx = findOperatorSplitIndex(upper, op)
            if (idx > 0) {
                val leftVal = evaluateArithmetic(upper.substring(0, idx).trim(), sheet, visited)
                val rightVal = evaluateArithmetic(upper.substring(idx + 1).trim(), sheet, visited)
                return formatDouble(if (op == '+') leftVal + rightVal else leftVal - rightVal)
            }
        }

        // Multiplication & Division
        for (op in listOf('*', '/')) {
            val idx = findOperatorSplitIndex(upper, op)
            if (idx != -1) {
                val leftVal = evaluateTerm(upper.substring(0, idx).trim(), sheet, visited)
                val rightVal = evaluateTerm(upper.substring(idx + 1).trim(), sheet, visited)
                return if (op == '/') {
                    if (rightVal == 0.0) "#DIV/0!" else formatDouble(leftVal / rightVal)
                } else {
                    formatDouble(leftVal * rightVal)
                }
            }
        }

        // ── 5. Single term (coordinate or literal) ────────────────
        return formatDouble(evaluateTerm(upper, sheet, visited))
    }

    private fun evaluateFunction(
        funcName: String,
        argsStr: String,
        sheet: Sheet,
        visited: MutableSet<String>
    ): String {
        return when (funcName) {
            "SUM" -> {
                val values = parseNumericArgs(argsStr, sheet, visited)
                formatDouble(values.sum())
            }
            "AVERAGE" -> {
                val values = parseNumericArgs(argsStr, sheet, visited)
                if (values.isEmpty()) "#DIV/0!" else formatDouble(values.average())
            }
            "MIN" -> {
                val values = parseNumericArgs(argsStr, sheet, visited)
                if (values.isEmpty()) "0" else formatDouble(values.min())
            }
            "MAX" -> {
                val values = parseNumericArgs(argsStr, sheet, visited)
                if (values.isEmpty()) "0" else formatDouble(values.max())
            }
            "COUNT" -> {
                parseNumericArgs(argsStr, sheet, visited).size.toString()
            }
            "COUNTA" -> {
                parseAllArgs(argsStr, sheet, visited).count { it.isNotEmpty() }.toString()
            }
            "IF" -> {
                val parts = splitArgs(argsStr)
                if (parts.size < 2) return "#VALUE!"
                val condition = evaluateFormulaString(parts[0], sheet, visited)
                val isTrue = condition.equals("TRUE", ignoreCase = true) ||
                        condition.toDoubleOrNull()?.let { it != 0.0 } ?: false
                val branch = if (isTrue) parts.getOrElse(1) { "" } else parts.getOrElse(2) { "" }
                evaluateFormulaString(branch, sheet, visited)
            }
            "ROUND" -> {
                val parts = splitArgs(argsStr)
                if (parts.isEmpty()) return "#VALUE!"
                val num = evaluateTerm(parts[0].trim(), sheet, visited)
                val decimals = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                val factor = Math.pow(10.0, decimals.toDouble())
                formatDouble(Math.round(num * factor) / factor)
            }
            "ABS" -> {
                val value = evaluateTerm(argsStr.trim(), sheet, visited)
                formatDouble(Math.abs(value))
            }
            "SQRT" -> {
                val value = evaluateTerm(argsStr.trim(), sheet, visited)
                if (value < 0) "#NUM!" else formatDouble(Math.sqrt(value))
            }
            "POWER" -> {
                val parts = splitArgs(argsStr)
                if (parts.size < 2) return "#VALUE!"
                val base = evaluateTerm(parts[0].trim(), sheet, visited)
                val exp = evaluateTerm(parts[1].trim(), sheet, visited)
                formatDouble(Math.pow(base, exp))
            }
            "CONCATENATE", "CONCAT" -> {
                val parts = parseAllArgs(argsStr, sheet, visited)
                parts.joinToString("")
            }
            "LEN" -> {
                val value = parseAllArgs(argsStr, sheet, visited).firstOrNull() ?: ""
                value.length.toString()
            }
            "UPPER" -> {
                val value = parseAllArgs(argsStr, sheet, visited).firstOrNull() ?: ""
                value.uppercase(Locale.ROOT)
            }
            "LOWER" -> {
                val value = parseAllArgs(argsStr, sheet, visited).firstOrNull() ?: ""
                value.lowercase(Locale.ROOT)
            }
            "TRIM" -> {
                val value = parseAllArgs(argsStr, sheet, visited).firstOrNull() ?: ""
                value.trim()
            }
            else -> "#NAME?"
        }
    }

    private fun findOperatorSplitIndex(formula: String, op: Char): Int {
        var depth = 0
        for (i in formula.indices.reversed()) {
            when (formula[i]) {
                ')' -> depth++
                '(' -> depth--
            }
            if (depth == 0 && formula[i] == op) return i
        }
        return -1
    }

    private fun evaluateArithmetic(term: String, sheet: Sheet, visited: MutableSet<String>): Double {
        // Try multiplication/division first
        for (op in listOf('*', '/')) {
            val idx = findOperatorSplitIndex(term, op)
            if (idx != -1) {
                val left = evaluateTerm(term.substring(0, idx).trim(), sheet, visited)
                val right = evaluateTerm(term.substring(idx + 1).trim(), sheet, visited)
                return if (op == '/') {
                    if (right == 0.0) Double.NaN else left / right
                } else left * right
            }
        }
        return evaluateTerm(term, sheet, visited)
    }

    private fun evaluateTerm(term: String, sheet: Sheet, visited: MutableSet<String>): Double {
        if (term.isEmpty()) return 0.0
        term.toDoubleOrNull()?.let { return it }
        if (term == "TRUE") return 1.0
        if (term == "FALSE") return 0.0
        if (parseCoordinate(term) != null) {
            return evaluateCell(term, sheet, visited).toDoubleOrNull() ?: 0.0
        }
        return 0.0
    }

    private fun splitArgs(argsStr: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (c in argsStr) {
            when {
                c == '(' -> { depth++; current.append(c) }
                c == ')' -> { depth--; current.append(c) }
                c == ',' && depth == 0 -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString().trim())
        return result
    }

    private fun parseNumericArgs(
        argsStr: String,
        sheet: Sheet,
        visited: MutableSet<String>
    ): List<Double> {
        val result = mutableListOf<Double>()
        for (arg in splitArgs(argsStr)) {
            val trimmed = arg.trim()
            when {
                trimmed.contains(":") -> {
                    expandRange(trimmed).forEach { coord ->
                        evaluateCell(coord, sheet, visited).toDoubleOrNull()?.let { result.add(it) }
                    }
                }
                parseCoordinate(trimmed) != null -> {
                    evaluateCell(trimmed, sheet, visited).toDoubleOrNull()?.let { result.add(it) }
                }
                else -> trimmed.toDoubleOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    private fun parseAllArgs(
        argsStr: String,
        sheet: Sheet,
        visited: MutableSet<String>
    ): List<String> {
        return splitArgs(argsStr).map { arg ->
            val trimmed = arg.trim()
            when {
                trimmed.contains(":") -> {
                    expandRange(trimmed).joinToString("") { evaluateCell(it, sheet, visited) }
                }
                parseCoordinate(trimmed) != null -> evaluateCell(trimmed, sheet, visited)
                trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
                    trimmed.substring(1, trimmed.length - 1)
                else -> trimmed
            }
        }
    }

    private fun formatDouble(num: Double): String {
        if (num.isNaN()) return "#DIV/0!"
        if (num.isInfinite()) return "#DIV/0!"
        return if (num % 1.0 == 0.0) {
            num.toLong().toString()
        } else {
            String.format(Locale.US, "%.6f", num)
                .trimEnd('0')
                .trimEnd('.')
        }
    }
}

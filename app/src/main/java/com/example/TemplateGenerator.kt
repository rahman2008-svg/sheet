package com.example

import java.util.UUID

object TemplateGenerator {

    fun createBlankWorkbook(title: String = "Untitled Spreadsheet"): Workbook {
        return Workbook(
            title = title,
            sheets = listOf(
                Sheet(name = "Sheet1")
            )
        )
    }

    fun createBudgetTracker(): Workbook {
        val cells = mutableMapOf<String, CellData>()
        
        // Title
        cells["A1"] = CellData("MONTHLY PERSONAL BUDGET", bold = true, fontSize = 16f, textColor = 0xFF0D9488L, alignment = CellAlignment.LEFT)
        
        // Income Section
        cells["A3"] = CellData("Income Source", bold = true, backgroundColor = 0xFFE8F5E9L)
        cells["B3"] = CellData("Budgeted Amount", bold = true, backgroundColor = 0xFFE8F5E9L, alignment = CellAlignment.RIGHT)
        cells["C3"] = CellData("Actual Amount", bold = true, backgroundColor = 0xFFE8F5E9L, alignment = CellAlignment.RIGHT)
        
        cells["A4"] = CellData("Primary Job Salary")
        cells["B4"] = CellData("3500", alignment = CellAlignment.RIGHT)
        cells["C4"] = CellData("3500", alignment = CellAlignment.RIGHT)
        
        cells["A5"] = CellData("Freelance Work")
        cells["B5"] = CellData("500", alignment = CellAlignment.RIGHT)
        cells["C5"] = CellData("750", alignment = CellAlignment.RIGHT)
        
        cells["A6"] = CellData("Investment Dividends")
        cells["B6"] = CellData("100", alignment = CellAlignment.RIGHT)
        cells["C6"] = CellData("120", alignment = CellAlignment.RIGHT)
        
        cells["A7"] = CellData("Total Income", bold = true)
        cells["B7"] = CellData("=SUM(B4:B6)", bold = true, alignment = CellAlignment.RIGHT, textColor = 0xFF0D9488L)
        cells["C7"] = CellData("=SUM(C4:C6)", bold = true, alignment = CellAlignment.RIGHT, textColor = 0xFF0D9488L)
        
        // Expenses Section
        cells["A9"] = CellData("Expense Category", bold = true, backgroundColor = 0xFFFFEBEEL)
        cells["B9"] = CellData("Budgeted Amount", bold = true, backgroundColor = 0xFFFFEBEEL, alignment = CellAlignment.RIGHT)
        cells["C9"] = CellData("Actual Amount", bold = true, backgroundColor = 0xFFFFEBEEL, alignment = CellAlignment.RIGHT)
        
        cells["A10"] = CellData("Rent/Mortgage")
        cells["B10"] = CellData("1200", alignment = CellAlignment.RIGHT)
        cells["C10"] = CellData("1200", alignment = CellAlignment.RIGHT)
        
        cells["A11"] = CellData("Groceries & Food")
        cells["B11"] = CellData("400", alignment = CellAlignment.RIGHT)
        cells["C11"] = CellData("435", alignment = CellAlignment.RIGHT)
        
        cells["A12"] = CellData("Utilities & Internet")
        cells["B12"] = CellData("250", alignment = CellAlignment.RIGHT)
        cells["C12"] = CellData("240", alignment = CellAlignment.RIGHT)
        
        cells["A13"] = CellData("Entertainment & Dining")
        cells["B13"] = CellData("150", alignment = CellAlignment.RIGHT)
        cells["C13"] = CellData("185", alignment = CellAlignment.RIGHT)
        
        cells["A14"] = CellData("Transportation & Fuel")
        cells["B14"] = CellData("200", alignment = CellAlignment.RIGHT)
        cells["C14"] = CellData("190", alignment = CellAlignment.RIGHT)
        
        cells["A15"] = CellData("Total Expenses", bold = true)
        cells["B15"] = CellData("=SUM(B10:B14)", bold = true, alignment = CellAlignment.RIGHT, textColor = 0xFFC62828L)
        cells["C15"] = CellData("=SUM(C10:C14)", bold = true, alignment = CellAlignment.RIGHT, textColor = 0xFFC62828L)
        
        // Summary
        cells["A17"] = CellData("BUDGET SUMMARY", bold = true, fontSize = 14f, textColor = 0xFF1565C0L)
        cells["A18"] = CellData("Net Savings (Budgeted)", italic = true)
        cells["B18"] = CellData("=B7-B15", bold = true, alignment = CellAlignment.RIGHT)
        cells["A19"] = CellData("Net Savings (Actual)", italic = true)
        cells["B19"] = CellData("=C7-C15", bold = true, alignment = CellAlignment.RIGHT, textColor = 0xFF1565C0L)

        val sheet = Sheet(name = "Personal Budget", cells = cells)
        return Workbook(title = "Monthly Personal Budget", sheets = listOf(sheet))
    }

    fun createStudentResultCalculator(): Workbook {
        val cells = mutableMapOf<String, CellData>()
        
        // Title
        cells["A1"] = CellData("STUDENT RESULTS CALCULATOR", bold = true, fontSize = 16f, textColor = 0xFF0D9488L)
        cells["A2"] = CellData("NexVora School & College (A Grade Analytics)", italic = true, fontSize = 11f, textColor = 0xFF555555L)
        
        // Table Headers
        val headerColor = 0xFFE0F2F1L
        cells["A4"] = CellData("Student Name", bold = true, backgroundColor = headerColor)
        cells["B4"] = CellData("Physics", bold = true, backgroundColor = headerColor, alignment = CellAlignment.RIGHT)
        cells["C4"] = CellData("Chemistry", bold = true, backgroundColor = headerColor, alignment = CellAlignment.RIGHT)
        cells["D4"] = CellData("Mathematics", bold = true, backgroundColor = headerColor, alignment = CellAlignment.RIGHT)
        cells["E4"] = CellData("Total Marks", bold = true, backgroundColor = headerColor, alignment = CellAlignment.RIGHT)
        cells["F4"] = CellData("Average Marks", bold = true, backgroundColor = headerColor, alignment = CellAlignment.RIGHT)
        
        // Student Rows
        val students = listOf(
            "Adnan Chowdhury" to triple(88, 75, 95),
            "Fariha Tasnim" to triple(92, 89, 90),
            "Imran Hossen" to triple(78, 82, 85),
            "Sadia Rahman" to triple(85, 91, 93),
            "Tahsin Ahmed" to triple(65, 70, 72)
        )
        
        students.forEachIndexed { index, (name, marks) ->
            val row = 5 + index
            cells["A$row"] = CellData(name)
            cells["B$row"] = CellData(marks.first.toString(), alignment = CellAlignment.RIGHT)
            cells["C$row"] = CellData(marks.second.toString(), alignment = CellAlignment.RIGHT)
            cells["D$row"] = CellData(marks.third.toString(), alignment = CellAlignment.RIGHT)
            cells["E$row"] = CellData("=SUM(B$row:D$row)", bold = true, alignment = CellAlignment.RIGHT)
            cells["F$row"] = CellData("=AVERAGE(B$row:D$row)", bold = true, alignment = CellAlignment.RIGHT, textColor = 0xFF004D40L)
        }
        
        // Analytics Footer
        val footerRow = 11
        cells["A$footerRow"] = CellData("Class Average", bold = true, backgroundColor = 0xFFE0F2F1L)
        cells["B$footerRow"] = CellData("=AVERAGE(B5:B9)", bold = true, alignment = CellAlignment.RIGHT, backgroundColor = 0xFFE0F2F1L)
        cells["C$footerRow"] = CellData("=AVERAGE(C5:C9)", bold = true, alignment = CellAlignment.RIGHT, backgroundColor = 0xFFE0F2F1L)
        cells["D$footerRow"] = CellData("=AVERAGE(D5:D9)", bold = true, alignment = CellAlignment.RIGHT, backgroundColor = 0xFFE0F2F1L)
        cells["E$footerRow"] = CellData("=AVERAGE(E5:E9)", bold = true, alignment = CellAlignment.RIGHT, backgroundColor = 0xFFE0F2F1L)
        cells["F$footerRow"] = CellData("=AVERAGE(F5:F9)", bold = true, alignment = CellAlignment.RIGHT, backgroundColor = 0xFFE0F2F1L)
        
        cells["A${footerRow + 1}"] = CellData("Class Max Marks", bold = true)
        cells["B${footerRow + 1}"] = CellData("=MAX(B5:B9)", bold = true, alignment = CellAlignment.RIGHT)
        cells["C${footerRow + 1}"] = CellData("=MAX(C5:C9)", bold = true, alignment = CellAlignment.RIGHT)
        cells["D${footerRow + 1}"] = CellData("=MAX(D5:D9)", bold = true, alignment = CellAlignment.RIGHT)
        cells["E${footerRow + 1}"] = CellData("=MAX(E5:E9)", bold = true, alignment = CellAlignment.RIGHT)
        
        cells["A${footerRow + 2}"] = CellData("Class Min Marks", bold = true)
        cells["B${footerRow + 2}"] = CellData("=MIN(B5:B9)", bold = true, alignment = CellAlignment.RIGHT)
        cells["C${footerRow + 2}"] = CellData("=MIN(C5:C9)", bold = true, alignment = CellAlignment.RIGHT)
        cells["D${footerRow + 2}"] = CellData("=MIN(D5:D9)", bold = true, alignment = CellAlignment.RIGHT)
        cells["E${footerRow + 2}"] = CellData("=MIN(E5:E9)", bold = true, alignment = CellAlignment.RIGHT)

        val sheet = Sheet(name = "Class Grades", cells = cells)
        return Workbook(title = "Student Result Calculator", sheets = listOf(sheet))
    }

    fun createAttendanceManager(): Workbook {
        val cells = mutableMapOf<String, CellData>()
        
        // Title
        cells["A1"] = CellData("EMPLOYEE ATTENDANCE MANAGER", bold = true, fontSize = 16f, textColor = 0xFF0D9488L)
        cells["A2"] = CellData("NexVora Labs - Monthly Tracker (1 = Present, 0 = Absent)", italic = true)
        
        // Headers
        val headerColor = 0xFFE1F5FEL
        cells["A4"] = CellData("Employee Name", bold = true, backgroundColor = headerColor)
        cells["B4"] = CellData("Week 1", bold = true, backgroundColor = headerColor, alignment = CellAlignment.CENTER)
        cells["C4"] = CellData("Week 2", bold = true, backgroundColor = headerColor, alignment = CellAlignment.CENTER)
        cells["D4"] = CellData("Week 3", bold = true, backgroundColor = headerColor, alignment = CellAlignment.CENTER)
        cells["E4"] = CellData("Week 4", bold = true, backgroundColor = headerColor, alignment = CellAlignment.CENTER)
        cells["F4"] = CellData("Days Worked", bold = true, backgroundColor = headerColor, alignment = CellAlignment.CENTER)
        
        val employees = listOf(
            "Rahat Karim" to triple(1, 1, 1, 1),
            "Sufia Begum" to triple(1, 0, 1, 1),
            "Jamil Ahmed" to triple(1, 1, 0, 1),
            "Tamanna Islam" to triple(0, 1, 1, 1),
            "Zakir Hasan" to triple(1, 1, 1, 0)
        )
        
        employees.forEachIndexed { index, (name, att) ->
            val row = 5 + index
            cells["A$row"] = CellData(name)
            cells["B$row"] = CellData(att.first.toString(), alignment = CellAlignment.CENTER)
            cells["C$row"] = CellData(att.second.toString(), alignment = CellAlignment.CENTER)
            cells["D$row"] = CellData(att.third.toString(), alignment = CellAlignment.CENTER)
            cells["E$row"] = CellData(att.fourth.toString(), alignment = CellAlignment.CENTER)
            cells["F$row"] = CellData("=SUM(B$row:E$row)", bold = true, alignment = CellAlignment.CENTER, textColor = 0xFF0D47A1L)
        }
        
        cells["A10"] = CellData("Total Presence", bold = true)
        cells["B10"] = CellData("=SUM(B5:B9)", bold = true, alignment = CellAlignment.CENTER)
        cells["C10"] = CellData("=SUM(C5:C9)", bold = true, alignment = CellAlignment.CENTER)
        cells["D10"] = CellData("=SUM(D5:D9)", bold = true, alignment = CellAlignment.CENTER)
        cells["E10"] = CellData("=SUM(E5:E9)", bold = true, alignment = CellAlignment.CENTER)
        cells["F10"] = CellData("=SUM(F5:F9)", bold = true, alignment = CellAlignment.CENTER, textColor = 0xFF0D9488L)

        val sheet = Sheet(name = "Attendance Logs", cells = cells)
        return Workbook(title = "Attendance Manager", sheets = listOf(sheet))
    }

    fun createBusinessInvoice(): Workbook {
        val cells = mutableMapOf<String, CellData>()
        
        // Title
        cells["A1"] = CellData("BUSINESS INVOICE", bold = true, fontSize = 18f, textColor = 0xFF0D9488L)
        cells["A2"] = CellData("NexVora Lab's Ofc - Client Invoice", italic = true)
        
        cells["A4"] = CellData("Invoice No:", bold = true)
        cells["B4"] = CellData("NV-2026-089", italic = true)
        cells["D4"] = CellData("Date:", bold = true)
        cells["E4"] = CellData("June 25, 2026", italic = true)
        
        // Headers
        val headerColor = 0xFFFFF3E0L
        cells["A6"] = CellData("Item Description", bold = true, backgroundColor = headerColor)
        cells["B6"] = CellData("Quantity", bold = true, backgroundColor = headerColor, alignment = CellAlignment.RIGHT)
        cells["C6"] = CellData("Unit Price ($)", bold = true, backgroundColor = headerColor, alignment = CellAlignment.RIGHT)
        cells["D6"] = CellData("Line Total ($)", bold = true, backgroundColor = headerColor, alignment = CellAlignment.RIGHT)
        
        // Items
        val items = listOf(
            "Android App Prototyping" to Pair(1, 1500),
            "UI/UX Visual Design Session" to Pair(2, 450),
            "Local SQLite Database Engine Setup" to Pair(1, 600),
            "Custom Chart Engine Coding" to Pair(1, 800)
        )
        
        items.forEachIndexed { index, (name, details) ->
            val row = 7 + index
            cells["A$row"] = CellData(name)
            cells["B$row"] = CellData(details.first.toString(), alignment = CellAlignment.RIGHT)
            cells["C$row"] = CellData(details.second.toString(), alignment = CellAlignment.RIGHT)
            cells["D$row"] = CellData("=B$row*C$row", bold = true, alignment = CellAlignment.RIGHT)
        }
        
        // Invoice totals
        cells["C12"] = CellData("Subtotal:", bold = true)
        cells["D12"] = CellData("=SUM(D7:D10)", bold = true, alignment = CellAlignment.RIGHT)
        
        cells["C13"] = CellData("VAT / Tax (15%):", bold = true)
        cells["D13"] = CellData("=D12*0.15", bold = true, alignment = CellAlignment.RIGHT)
        
        cells["C14"] = CellData("Total Payable:", bold = true, backgroundColor = 0xFFFFF3E0L)
        cells["D14"] = CellData("=D12+D13", bold = true, alignment = CellAlignment.RIGHT, backgroundColor = 0xFFFFF3E0L, textColor = 0xFFE65100L)

        val sheet = Sheet(name = "Invoice Details", cells = cells)
        return Workbook(title = "Business Invoice Generator", sheets = listOf(sheet))
    }

    fun createExpenseTracker(): Workbook {
        val cells = mutableMapOf<String, CellData>()
        
        // Title
        cells["A1"] = CellData("BUSINESS EXPENSE TRACKER", bold = true, fontSize = 16f, textColor = 0xFF0D9488L)
        cells["A2"] = CellData("Daily Expense Log & Category Analytics", italic = true)
        
        // Headers
        val headerColor = 0xFFECEFF1L
        cells["A4"] = CellData("Date", bold = true, backgroundColor = headerColor)
        cells["B4"] = CellData("Expense Description", bold = true, backgroundColor = headerColor)
        cells["C4"] = CellData("Category", bold = true, backgroundColor = headerColor)
        cells["D4"] = CellData("Amount ($)", bold = true, backgroundColor = headerColor, alignment = CellAlignment.RIGHT)
        
        // Items
        val expenses = listOf(
            quadruple("2026-06-01", "Office Rent", "Rent", 800),
            quadruple("2026-06-05", "High-Speed Internet", "Utilities", 120),
            quadruple("2026-06-10", "Laptop Stand & Hub", "Equipment", 75),
            quadruple("2026-06-15", "Google Cloud API Fees", "Hosting", 45),
            quadruple("2026-06-20", "NexVora Staff Lunch", "Entertainment", 130)
        )
        
        expenses.forEachIndexed { index, item ->
            val row = 5 + index
            cells["A$row"] = CellData(item.first)
            cells["B$row"] = CellData(item.second)
            cells["C$row"] = CellData(item.third)
            cells["D$row"] = CellData(item.fourth.toString(), alignment = CellAlignment.RIGHT)
        }
        
        // Totals
        val totalRow = 11
        cells["C$totalRow"] = CellData("Total Outflow", bold = true)
        cells["D$totalRow"] = CellData("=SUM(D5:D9)", bold = true, alignment = CellAlignment.RIGHT, textColor = 0xFFB71C1CL)
        
        cells["C${totalRow + 1}"] = CellData("Average Spend", bold = true)
        cells["D${totalRow + 1}"] = CellData("=AVERAGE(D5:D9)", bold = true, alignment = CellAlignment.RIGHT)
        
        cells["C${totalRow + 2}"] = CellData("Max Spend", bold = true)
        cells["D${totalRow + 2}"] = CellData("=MAX(D5:D9)", bold = true, alignment = CellAlignment.RIGHT)

        val sheet = Sheet(name = "Expenses", cells = cells)
        return Workbook(title = "Expense Tracker System", sheets = listOf(sheet))
    }

    private fun triple(a: Int, b: Int, c: Int) = Triple(a, b, c)
    private fun triple(a: Int, b: Int, c: Int, d: Int) = Quadruple(a, b, c, d)
    private fun quadruple(a: String, b: String, c: String, d: Int) = Quadruple(a, b, c, d)
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

package br.com.sisgfin.statement

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.awt.Color
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val filenameFmt = DateTimeFormatter.ofPattern("yyyyMMdd")

object StatementExporter {

    fun exportToExcel(
        account: FinancialAccount,
        filter: StatementFilter,
        openingBalance: Money,
        entries: List<StatementEntry>,
        outputDir: File
    ): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Extrato")
        val helper = workbook.creationHelper

        // Styles
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = XSSFColor(Color(30, 58, 138), null).index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply {
                bold = true
                color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index
            })
            alignment = HorizontalAlignment.CENTER
        }
        val totalStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply { bold = true })
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
        }
        val dateFormat = workbook.createCellStyle().apply {
            dataFormat = helper.createDataFormat().getFormat("dd/MM/yyyy")
        }
        val moneyFormat = workbook.createCellStyle().apply {
            dataFormat = helper.createDataFormat().getFormat("#,##0.00")
        }
        val creditColor = XSSFColor(Color(21, 128, 61), null).index
        val debitColor  = XSSFColor(Color(185, 28, 28), null).index

        // Title rows
        var rowIdx = 0
        sheet.createRow(rowIdx++).createCell(0).also {
            it.setCellValue("EXTRATO DE CONTA — ${account.name.uppercase()}")
            workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true; fontHeightInPoints = 14 }) }
        }
        val periodLabel = buildString {
            filter.from?.let { append("De ${it.format(dateFmt)} ") }
            filter.to?.let { append("até ${it.format(dateFmt)}") }
            if (isEmpty()) append("Todos os períodos")
        }
        sheet.createRow(rowIdx++).createCell(0).setCellValue(periodLabel)
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Saldo de abertura: ${MoneyFormatter.format(openingBalance)}")
        rowIdx++ // blank

        // Header
        val headers = listOf("DATA PAG.", "DESCRIÇÃO", "TIPO", "DOC", "FORNECEDOR", "DÉBITO", "CRÉDITO", "SALDO")
        val headerRow = sheet.createRow(rowIdx++)
        headers.forEachIndexed { i, h ->
            headerRow.createCell(i).also {
                it.setCellValue(h)
                it.cellStyle = headerStyle
            }
        }

        // Data rows
        entries.forEach { entry ->
            val tx = entry.transaction
            val row = sheet.createRow(rowIdx++)
            val payDate = tx.paymentDate ?: tx.dueDate

            row.createCell(0).also { it.setCellValue(payDate.format(dateFmt)); it.cellStyle = dateFormat }
            row.createCell(1).setCellValue(tx.description)
            row.createCell(2).setCellValue(tx.type.displayName)
            row.createCell(3).setCellValue(
                listOfNotNull(tx.documentType, tx.documentNumber).joinToString(" ").ifBlank { "" }
            )
            row.createCell(4).setCellValue("") // supplier name placeholder (not loaded here)

            if (entry.isCredit) {
                row.createCell(5).setCellValue("")
                row.createCell(6).also {
                    it.setCellValue(entry.signedAmount.abs().value.toDouble())
                    it.cellStyle = moneyFormat
                }
            } else {
                row.createCell(5).also {
                    it.setCellValue(entry.signedAmount.abs().value.toDouble())
                    it.cellStyle = moneyFormat
                }
                row.createCell(6).setCellValue("")
            }
            row.createCell(7).also {
                it.setCellValue(entry.runningBalance.value.toDouble())
                it.cellStyle = moneyFormat
            }
        }

        // Totals
        rowIdx++
        val totalRow = sheet.createRow(rowIdx)
        val totalCredits = entries.filter { it.isCredit }.fold(Money.ZERO) { a, e -> a + e.signedAmount.abs() }
        val totalDebits  = entries.filterNot { it.isCredit }.fold(Money.ZERO) { a, e -> a + e.signedAmount.abs() }
        val closingBalance = entries.lastOrNull()?.runningBalance ?: openingBalance

        totalRow.createCell(0).also { it.setCellValue("TOTAIS"); it.cellStyle = totalStyle }
        totalRow.createCell(5).also { it.setCellValue(totalDebits.value.toDouble()); it.cellStyle = totalStyle }
        totalRow.createCell(6).also { it.setCellValue(totalCredits.value.toDouble()); it.cellStyle = totalStyle }
        totalRow.createCell(7).also { it.setCellValue(closingBalance.value.toDouble()); it.cellStyle = totalStyle }

        // Column widths
        listOf(12, 40, 14, 16, 24, 16, 16, 16).forEachIndexed { i, w ->
            sheet.setColumnWidth(i, w * 256)
        }

        outputDir.mkdirs()
        val file = File(outputDir, "extrato_${account.name.replace(" ", "_")}_${LocalDate.now().format(filenameFmt)}.xlsx")
        file.outputStream().use { workbook.write(it) }
        workbook.close()
        return file
    }

    fun exportToPdf(
        account: FinancialAccount,
        filter: StatementFilter,
        openingBalance: Money,
        entries: List<StatementEntry>,
        outputDir: File
    ): File {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)

        val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val fontReg  = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontSm   = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)

        val margin = 40f
        val pageW = page.mediaBox.width
        val pageH = page.mediaBox.height
        var y = pageH - margin

        fun newPage(): PDPageContentStream {
            val np = PDPage(PDRectangle.A4)
            doc.addPage(np)
            return PDPageContentStream(doc, np)
        }

        var cs = PDPageContentStream(doc, page)

        fun text(stream: PDPageContentStream, txt: String, x: Float, yPos: Float, font: PDType1Font, size: Float, r: Float = 0f, g: Float = 0f, b: Float = 0f) {
            stream.beginText()
            stream.setFont(font, size)
            stream.setNonStrokingColor(r, g, b)
            stream.newLineAtOffset(x, yPos)
            stream.showText(txt)
            stream.endText()
        }

        fun line(stream: PDPageContentStream, x1: Float, y1: Float, x2: Float, y2: Float) {
            stream.moveTo(x1, y1); stream.lineTo(x2, y2); stream.stroke()
        }

        // Header
        text(cs, "EXTRATO DE CONTA", margin, y, fontBold, 14f)
        y -= 18f
        text(cs, account.name, margin, y, fontBold, 11f)
        y -= 14f
        val periodLabel = buildString {
            filter.from?.let { append("De ${it.format(dateFmt)} ") }
            filter.to?.let { append("até ${it.format(dateFmt)}") }
            if (isEmpty()) append("Todos os períodos")
        }
        text(cs, periodLabel, margin, y, fontSm, 9f)
        y -= 14f
        text(cs, "Saldo de abertura: ${MoneyFormatter.format(openingBalance)}", margin, y, fontReg, 9f)
        y -= 6f
        line(cs, margin, y, pageW - margin, y)
        y -= 14f

        // Table header
        val colX  = floatArrayOf(margin, margin + 60f, margin + 185f, margin + 235f, margin + 285f, margin + 365f, pageW - margin - 75f)
        val hdrs  = arrayOf("DATA", "DESCRIÇÃO", "TIPO", "DOC", "DÉBITO", "CRÉDITO", "SALDO")
        hdrs.forEachIndexed { i, h -> text(cs, h, colX[i], y, fontBold, 8f) }
        y -= 4f
        line(cs, margin, y, pageW - margin, y)
        y -= 12f

        entries.forEach { entry ->
            if (y < margin + 30f) {
                cs.close()
                cs = newPage()
                y = pageH - margin
            }
            val tx = entry.transaction
            val payDate = (tx.paymentDate ?: tx.dueDate).format(dateFmt)
            val desc = tx.description.take(35)
            val docLabel = listOfNotNull(tx.documentType, tx.documentNumber).joinToString(" ").take(12)
            val (dr, dg, db) = if (entry.isCredit) Triple(0.1f, 0.5f, 0.1f) else Triple(0.8f, 0.1f, 0.1f)

            text(cs, payDate, colX[0], y, fontReg, 8f)
            text(cs, desc, colX[1], y, fontReg, 7.5f)
            text(cs, tx.type.displayName.take(10), colX[2], y, fontReg, 7.5f)
            text(cs, docLabel, colX[3], y, fontReg, 7.5f)
            if (!entry.isCredit) text(cs, MoneyFormatter.format(entry.signedAmount.abs()), colX[4], y, fontReg, 7.5f, dr, dg, db)
            if (entry.isCredit)  text(cs, MoneyFormatter.format(entry.signedAmount.abs()), colX[5], y, fontReg, 7.5f, dr, dg, db)
            text(cs, MoneyFormatter.format(entry.runningBalance), colX[6], y, fontBold, 7.5f)
            y -= 12f
        }

        // Totals
        y -= 4f
        line(cs, margin, y, pageW - margin, y)
        y -= 14f
        val totalCredits = entries.filter { it.isCredit }.fold(Money.ZERO) { a, e -> a + e.signedAmount.abs() }
        val totalDebits  = entries.filterNot { it.isCredit }.fold(Money.ZERO) { a, e -> a + e.signedAmount.abs() }
        val closingBalance = entries.lastOrNull()?.runningBalance ?: openingBalance

        text(cs, "TOTAIS", colX[0], y, fontBold, 8f)
        text(cs, MoneyFormatter.format(totalDebits),  colX[4], y, fontBold, 8f)
        text(cs, MoneyFormatter.format(totalCredits), colX[5], y, fontBold, 8f)
        text(cs, MoneyFormatter.format(closingBalance), colX[6], y, fontBold, 8f)

        cs.close()
        outputDir.mkdirs()
        val file = File(outputDir, "extrato_${account.name.replace(" ", "_")}_${LocalDate.now().format(filenameFmt)}.pdf")
        doc.save(file)
        doc.close()
        return file
    }
}

package br.com.sisgfin.reports

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionType
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

private val dateFmt     = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val filenameFmt = DateTimeFormatter.ofPattern("yyyyMMdd")

object ReportsExporter {

    // ── Livro Diário — Excel ─────────────────────────────────────────────────

    fun livroDiarioExcel(
        filter: LivroDiarioFilter,
        entries: List<LivroDiarioEntry>,
        outputDir: File
    ): File {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Livro Diário")

        val blue  = XSSFColor(Color(30, 58, 138), null)
        val hdrStyle = wb.createCellStyle().apply {
            setFillForegroundColor(blue)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().apply { bold = true; color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index })
            alignment = HorizontalAlignment.CENTER
            setBorderBottom(BorderStyle.THIN)
        }
        val boldStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply { bold = true })
        }
        val moneyFmt = wb.createCellStyle().apply {
            dataFormat = wb.creationHelper.createDataFormat().getFormat("#,##0.00")
        }
        val dateCellFmt = wb.createCellStyle().apply {
            dataFormat = wb.creationHelper.createDataFormat().getFormat("dd/MM/yyyy")
        }

        var rowIdx = 0
        fun titleRow(text: String) = sheet.createRow(rowIdx++).createCell(0).apply {
            setCellValue(text)
            cellStyle = boldStyle
        }

        titleRow("LIVRO DIÁRIO")
        titleRow("Período: ${filter.from.format(dateFmt)} a ${filter.to.format(dateFmt)}")
        rowIdx++ // blank

        val headers = listOf("N°", "DATA PAG.", "HISTÓRICO (TCESP)", "TIPO", "CONTA", "VALOR (R$)")
        val hdrRow = sheet.createRow(rowIdx++)
        headers.forEachIndexed { i, h -> hdrRow.createCell(i).also { it.setCellValue(h); it.cellStyle = hdrStyle } }

        var total = Money.ZERO
        entries.forEachIndexed { idx, entry ->
            val tx  = entry.transaction
            val row = sheet.createRow(rowIdx++)
            val payDate = tx.paymentDate ?: tx.dueDate
            val value = tx.paidAmount ?: tx.amount

            row.createCell(0).setCellValue((idx + 1).toDouble())
            row.createCell(1).also { it.setCellValue(payDate.format(dateFmt)); it.cellStyle = dateCellFmt }
            row.createCell(2).setCellValue(entry.tcespDesc)
            row.createCell(3).setCellValue(tx.type.displayName)
            row.createCell(4).setCellValue(entry.accountName)
            row.createCell(5).also { it.setCellValue(value.value.toDouble()); it.cellStyle = moneyFmt }

            total += value
        }

        rowIdx++
        val totRow = sheet.createRow(rowIdx)
        totRow.createCell(0).also { it.setCellValue("TOTAL GERAL"); it.cellStyle = boldStyle }
        totRow.createCell(5).also { it.setCellValue(total.value.toDouble()); it.cellStyle = moneyFmt }

        listOf(6, 14, 52, 14, 20, 16).forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }

        outputDir.mkdirs()
        val file = File(outputDir, "livro_diario_${filter.from.format(filenameFmt)}_${filter.to.format(filenameFmt)}.xlsx")
        file.outputStream().use { wb.write(it) }
        wb.close()
        return file
    }

    // ── Livro Diário — PDF (A4 landscape) ───────────────────────────────────

    fun livroDiarioPdf(
        filter: LivroDiarioFilter,
        entries: List<LivroDiarioEntry>,
        outputDir: File
    ): File {
        val doc = PDDocument()
        val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val fontReg  = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontSm   = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        val pageSize = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) // landscape

        val margin  = 36f
        val pageW   = pageSize.width
        val pageH   = pageSize.height
        var pageNum = 0

        var cs: PDPageContentStream
        var y: Float

        fun addPage(): PDPageContentStream {
            pageNum++
            val p = PDPage(pageSize)
            doc.addPage(p)
            return PDPageContentStream(doc, p)
        }

        fun textAt(stream: PDPageContentStream, txt: String, x: Float, yPos: Float, font: PDType1Font, size: Float) {
            stream.beginText(); stream.setFont(font, size)
            stream.newLineAtOffset(x, yPos); stream.showText(txt); stream.endText()
        }

        fun hLine(stream: PDPageContentStream, yPos: Float) {
            stream.moveTo(margin, yPos); stream.lineTo(pageW - margin, yPos); stream.stroke()
        }

        fun printHeader(stream: PDPageContentStream, startY: Float): Float {
            var ly = startY
            textAt(stream, "LIVRO DIÁRIO", margin, ly, fontBold, 13f)
            ly -= 15f
            textAt(stream, "Período: ${filter.from.format(dateFmt)} a ${filter.to.format(dateFmt)}", margin, ly, fontSm, 9f)
            ly -= 8f; hLine(stream, ly); ly -= 12f

            // column headers
            val cols = floatArrayOf(margin, margin+30f, margin+72f, margin+315f, margin+380f, margin+450f)
            val hdrs = arrayOf("N°", "DATA PAG.", "HISTÓRICO (TCESP)", "TIPO", "CONTA", "VALOR")
            hdrs.forEachIndexed { i, h -> textAt(stream, h, cols[i], ly, fontBold, 8f) }
            ly -= 4f; hLine(stream, ly); ly -= 12f
            return ly
        }

        cs = addPage()
        y  = pageH - margin
        y  = printHeader(cs, y)

        val cols = floatArrayOf(margin, margin+30f, margin+72f, margin+315f, margin+380f, margin+450f)
        var total = Money.ZERO

        entries.forEachIndexed { idx, entry ->
            if (y < margin + 24f) {
                cs.close(); cs = addPage(); y = pageH - margin; y = printHeader(cs, y)
            }
            val tx      = entry.transaction
            val payDate = (tx.paymentDate ?: tx.dueDate).format(dateFmt)
            val value   = tx.paidAmount ?: tx.amount
            val desc    = entry.tcespDesc.take(55)

            textAt(cs, "${idx+1}", cols[0], y, fontReg, 7.5f)
            textAt(cs, payDate,    cols[1], y, fontReg, 7.5f)
            textAt(cs, desc,       cols[2], y, fontReg, 7f)
            textAt(cs, tx.type.displayName.take(14), cols[3], y, fontReg, 7.5f)
            textAt(cs, entry.accountName.take(18),   cols[4], y, fontReg, 7.5f)
            textAt(cs, MoneyFormatter.format(value),  cols[5], y, fontBold, 7.5f)

            total += value
            y -= 12f
        }

        // footer totals
        y -= 4f; hLine(cs, y); y -= 14f
        textAt(cs, "TOTAL GERAL", cols[0], y, fontBold, 8.5f)
        textAt(cs, MoneyFormatter.format(total), cols[5], y, fontBold, 8.5f)
        textAt(cs, "${entries.size} lançamento(s)", cols[1], y, fontSm, 8f)

        cs.close()
        outputDir.mkdirs()
        val file = File(outputDir, "livro_diario_${filter.from.format(filenameFmt)}_${filter.to.format(filenameFmt)}.pdf")
        doc.save(file); doc.close()
        return file
    }

    // ── Balancete — Excel ────────────────────────────────────────────────────

    fun balanceteExcel(
        filter: BalanceteFilter,
        rows: List<BalanceteRow>,
        outputDir: File
    ): File {
        val wb    = XSSFWorkbook()
        val sheet = wb.createSheet("Balancete")

        val blue = XSSFColor(Color(30, 58, 138), null)
        val hdrStyle = wb.createCellStyle().apply {
            setFillForegroundColor(blue)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().apply { bold = true; color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index })
            alignment = HorizontalAlignment.CENTER
        }
        val boldStyle = wb.createCellStyle().apply { setFont(wb.createFont().apply { bold = true }) }
        val redBold   = wb.createCellStyle().apply {
            setFont(wb.createFont().apply { bold = true; color = org.apache.poi.ss.usermodel.IndexedColors.RED.index })
        }
        val moneyFmt = wb.createCellStyle().apply {
            dataFormat = wb.creationHelper.createDataFormat().getFormat("#,##0.00")
        }
        val pctFmt = wb.createCellStyle().apply {
            dataFormat = wb.creationHelper.createDataFormat().getFormat("0.0\"%\"")
        }

        val titleLabel = buildString {
            append("BALANCETE ${filter.year}")
            filter.month?.let { append(" — ${MESES_PT[it - 1].uppercase()}") } ?: append(" — ACUMULADO ANUAL")
        }

        var rowIdx = 0
        sheet.createRow(rowIdx++).createCell(0).also { it.setCellValue(titleLabel); it.cellStyle = boldStyle }
        rowIdx++ // blank

        val hdrs = listOf("CENTRO DE CUSTO", "CATEGORIA", "DOT. MENSAL", "DOT. ANUAL", "REALIZADO", "SALDO", "% UTIL")
        val hRow = sheet.createRow(rowIdx++)
        hdrs.forEachIndexed { i, h -> hRow.createCell(i).also { it.setCellValue(h); it.cellStyle = hdrStyle } }

        var totMonthly = Money.ZERO; var totAnnual  = Money.ZERO
        var totReal    = Money.ZERO; var totBalance = Money.ZERO

        rows.forEach { row ->
            val r = sheet.createRow(rowIdx++)
            r.createCell(0).setCellValue("${row.costCenterCode} ${row.costCenterName}".trim())
            r.createCell(1).setCellValue("${row.categoryCode} ${row.categoryName}".trim())
            r.createCell(2).also { it.setCellValue(row.monthlyAmount.value.toDouble()); it.cellStyle = moneyFmt }
            r.createCell(3).also { it.setCellValue(row.annualAmount.value.toDouble()); it.cellStyle = moneyFmt }
            r.createCell(4).also { it.setCellValue(row.realized.value.toDouble()); it.cellStyle = moneyFmt }
            r.createCell(5).also {
                it.setCellValue(row.balance.value.toDouble())
                it.cellStyle = if (row.isOverBudget) redBold else moneyFmt
            }
            r.createCell(6).also { it.setCellValue(row.utilizationPct / 100.0); it.cellStyle = pctFmt }

            totMonthly += row.monthlyAmount; totAnnual  += row.annualAmount
            totReal    += row.realized;      totBalance += row.balance
        }

        rowIdx++
        val tRow = sheet.createRow(rowIdx)
        tRow.createCell(0).also { it.setCellValue("TOTAL"); it.cellStyle = boldStyle }
        tRow.createCell(2).also { it.setCellValue(totMonthly.value.toDouble()); it.cellStyle = moneyFmt }
        tRow.createCell(3).also { it.setCellValue(totAnnual.value.toDouble());  it.cellStyle = moneyFmt }
        tRow.createCell(4).also { it.setCellValue(totReal.value.toDouble());    it.cellStyle = moneyFmt }
        tRow.createCell(5).also { it.setCellValue(totBalance.value.toDouble()); it.cellStyle = moneyFmt }

        listOf(38, 36, 16, 16, 16, 16, 10).forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }

        outputDir.mkdirs()
        val suffix = filter.month?.let { "_${filter.year}_${it.toString().padStart(2,'0')}" } ?: "_${filter.year}"
        val file = File(outputDir, "balancete$suffix.xlsx")
        file.outputStream().use { wb.write(it) }
        wb.close()
        return file
    }

    // ── Balancete — PDF (A4 landscape) ──────────────────────────────────────

    fun balancetePdf(
        filter: BalanceteFilter,
        rows: List<BalanceteRow>,
        outputDir: File
    ): File {
        val doc      = PDDocument()
        val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val fontReg  = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontSm   = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        val pageSize = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)

        val margin = 36f
        val pageW  = pageSize.width
        val pageH  = pageSize.height

        val titleLabel = buildString {
            append("BALANCETE ${filter.year}")
            filter.month?.let { append(" — ${MESES_PT[it - 1].uppercase()}") } ?: append(" — ACUMULADO ANUAL")
        }

        var cs: PDPageContentStream
        var y: Float

        fun addPage(): PDPageContentStream {
            val p = PDPage(pageSize); doc.addPage(p); return PDPageContentStream(doc, p)
        }

        fun textAt(stream: PDPageContentStream, txt: String, x: Float, yPos: Float,
                   font: PDType1Font, size: Float, r: Float = 0f, g: Float = 0f, b: Float = 0f) {
            stream.beginText(); stream.setFont(font, size)
            stream.setNonStrokingColor(r, g, b)
            stream.newLineAtOffset(x, yPos); stream.showText(txt); stream.endText()
            if (r != 0f || g != 0f || b != 0f) {
                stream.beginText(); stream.setNonStrokingColor(0f, 0f, 0f); stream.endText()
            }
        }

        fun hLine(stream: PDPageContentStream, yPos: Float) {
            stream.moveTo(margin, yPos); stream.lineTo(pageW - margin, yPos); stream.stroke()
        }

        // columns: CC | Cat | Dot.Mês | Dot.Anual | Realizado | Saldo | %
        val cols = floatArrayOf(margin, margin+155f, margin+280f, margin+360f, margin+440f, margin+520f, margin+600f)

        fun printHeader(stream: PDPageContentStream, startY: Float): Float {
            var ly = startY
            textAt(stream, titleLabel, margin, ly, fontBold, 12f)
            ly -= 8f; hLine(stream, ly); ly -= 12f
            val hdrs = arrayOf("CENTRO DE CUSTO", "CATEGORIA", "DOT. MÊS", "DOT. ANUAL", "REALIZADO", "SALDO", "% UTIL")
            hdrs.forEachIndexed { i, h -> textAt(stream, h, cols[i], ly, fontBold, 7.5f) }
            ly -= 4f; hLine(stream, ly); ly -= 12f
            return ly
        }

        cs = addPage(); y = pageH - margin; y = printHeader(cs, y)

        var totMonthly = Money.ZERO; var totAnnual  = Money.ZERO
        var totReal    = Money.ZERO; var totBalance = Money.ZERO

        rows.forEach { row ->
            if (y < margin + 24f) { cs.close(); cs = addPage(); y = pageH - margin; y = printHeader(cs, y) }

            val ccLabel  = "${row.costCenterCode} ${row.costCenterName}".trim().take(24)
            val catLabel = "${row.categoryCode} ${row.categoryName}".trim().take(30)
            val isOver   = row.isOverBudget

            textAt(cs, ccLabel,  cols[0], y, fontReg, 7.5f)
            textAt(cs, catLabel, cols[1], y, fontReg, 7f)
            textAt(cs, MoneyFormatter.format(row.monthlyAmount), cols[2], y, fontReg, 7.5f)
            textAt(cs, MoneyFormatter.format(row.annualAmount),  cols[3], y, fontReg, 7.5f)
            textAt(cs, MoneyFormatter.format(row.realized),      cols[4], y, fontReg, 7.5f)
            if (isOver)
                textAt(cs, MoneyFormatter.format(row.balance), cols[5], y, fontBold, 7.5f, 0.8f, 0.1f, 0.1f)
            else
                textAt(cs, MoneyFormatter.format(row.balance), cols[5], y, fontReg, 7.5f, 0.1f, 0.5f, 0.1f)
            textAt(cs, "%.1f%%".format(row.utilizationPct), cols[6], y, fontReg, 7.5f)

            totMonthly += row.monthlyAmount; totAnnual  += row.annualAmount
            totReal    += row.realized;      totBalance += row.balance
            y -= 12f
        }

        y -= 4f; hLine(cs, y); y -= 14f
        textAt(cs, "TOTAL", cols[0], y, fontBold, 8.5f)
        textAt(cs, MoneyFormatter.format(totMonthly), cols[2], y, fontBold, 8.5f)
        textAt(cs, MoneyFormatter.format(totAnnual),  cols[3], y, fontBold, 8.5f)
        textAt(cs, MoneyFormatter.format(totReal),    cols[4], y, fontBold, 8.5f)
        textAt(cs, MoneyFormatter.format(totBalance), cols[5], y, fontBold, 8.5f)

        cs.close()
        outputDir.mkdirs()
        val suffix = filter.month?.let { "_${filter.year}_${it.toString().padStart(2, '0')}" } ?: "_${filter.year}"
        val file = File(outputDir, "balancete$suffix.pdf")
        doc.save(file); doc.close()
        return file
    }

    // ── Demonstrativo Financeiro — Excel ─────────────────────────────────────

    fun demonstrativoExcel(
        filter: DemonstrativoFilter,
        rows: List<DemonstrativoRow>,
        outputDir: File
    ): File {
        val wb    = XSSFWorkbook()
        val sheet = wb.createSheet("Demonstrativo")

        val blue  = XSSFColor(Color(30, 58, 138), null)
        val green = XSSFColor(Color(20, 83, 45), null)
        val gray  = XSSFColor(Color(71, 85, 105), null)

        val hdrStyle = wb.createCellStyle().apply {
            setFillForegroundColor(blue)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().apply { bold = true; color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index })
            alignment = HorizontalAlignment.CENTER
            setBorderBottom(BorderStyle.THIN)
        }
        val grpStyle = wb.createCellStyle().apply {
            setFillForegroundColor(gray)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().apply { bold = true; color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index })
        }
        val boldStyle = wb.createCellStyle().apply { setFont(wb.createFont().apply { bold = true }) }
        val totStyle  = wb.createCellStyle().apply {
            setFillForegroundColor(green)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().apply { bold = true; color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index })
        }
        val moneyFmt = wb.createCellStyle().apply {
            dataFormat = wb.creationHelper.createDataFormat().getFormat("#,##0.00")
        }
        val moneyBold = wb.createCellStyle().apply {
            dataFormat = wb.creationHelper.createDataFormat().getFormat("#,##0.00")
            setFont(wb.createFont().apply { bold = true })
        }

        var rowIdx = 0
        sheet.createRow(rowIdx++).createCell(0).also {
            it.setCellValue("DEMONSTRATIVO FINANCEIRO — ${filter.from.format(dateFmt)} a ${filter.to.format(dateFmt)}")
            it.cellStyle = boldStyle
        }
        rowIdx++

        val hdrs = listOf("CÓD. AUDESP", "CATEGORIA", "GRUPO", "RECEITA", "DESPESA", "SALDO")
        val hRow = sheet.createRow(rowIdx++)
        hdrs.forEachIndexed { i, h -> hRow.createCell(i).also { it.setCellValue(h); it.cellStyle = hdrStyle } }

        var totIncome  = Money.ZERO
        var totExpense = Money.ZERO

        val grouped = rows.groupBy { it.groupCode to it.groupName }
        grouped.forEach { (grpKey, grpRows) ->
            val (grpCode, grpName) = grpKey
            val label = listOfNotNull(grpCode, grpName).joinToString(" — ").ifBlank { "Sem grupo" }

            val grpRow = sheet.createRow(rowIdx++)
            grpRow.createCell(0).also { it.setCellValue(label); it.cellStyle = grpStyle }
            (1..5).forEach { grpRow.createCell(it).cellStyle = grpStyle }

            var subIncome  = Money.ZERO
            var subExpense = Money.ZERO

            grpRows.forEach { row ->
                val r = sheet.createRow(rowIdx++)
                r.createCell(0).setCellValue(row.categoryCode)
                r.createCell(1).setCellValue(row.categoryName)
                r.createCell(2).setCellValue(label)
                r.createCell(3).also { it.setCellValue(row.income.value.toDouble());   it.cellStyle = moneyFmt }
                r.createCell(4).also { it.setCellValue(row.expense.value.toDouble());  it.cellStyle = moneyFmt }
                r.createCell(5).also { it.setCellValue(row.balance.value.toDouble());  it.cellStyle = moneyFmt }
                subIncome  += row.income
                subExpense += row.expense
            }

            val subRow = sheet.createRow(rowIdx++)
            subRow.createCell(0).also { it.setCellValue("Subtotal"); it.cellStyle = boldStyle }
            subRow.createCell(3).also { it.setCellValue(subIncome.value.toDouble());           it.cellStyle = moneyBold }
            subRow.createCell(4).also { it.setCellValue(subExpense.value.toDouble());          it.cellStyle = moneyBold }
            subRow.createCell(5).also { it.setCellValue((subIncome - subExpense).value.toDouble()); it.cellStyle = moneyBold }
            rowIdx++ // blank

            totIncome  += subIncome
            totExpense += subExpense
        }

        val tRow = sheet.createRow(rowIdx)
        tRow.createCell(0).also { it.setCellValue("TOTAL GERAL"); it.cellStyle = totStyle }
        (1..5).forEach { tRow.createCell(it).cellStyle = totStyle }
        tRow.createCell(3).also { it.setCellValue(totIncome.value.toDouble());              it.cellStyle = totStyle }
        tRow.createCell(4).also { it.setCellValue(totExpense.value.toDouble());             it.cellStyle = totStyle }
        tRow.createCell(5).also { it.setCellValue((totIncome - totExpense).value.toDouble()); it.cellStyle = totStyle }

        listOf(16, 36, 26, 16, 16, 16).forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }

        outputDir.mkdirs()
        val file = File(outputDir, "demonstrativo_${filter.from.format(filenameFmt)}_${filter.to.format(filenameFmt)}.xlsx")
        file.outputStream().use { wb.write(it) }
        wb.close()
        return file
    }

    // ── Demonstrativo Financeiro — PDF (A4 landscape) ────────────────────────

    fun demonstrativoPdf(
        filter: DemonstrativoFilter,
        rows: List<DemonstrativoRow>,
        outputDir: File
    ): File {
        val doc      = PDDocument()
        val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val fontReg  = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontSm   = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        val pageSize = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)

        val margin = 36f
        val pageW  = pageSize.width
        val pageH  = pageSize.height

        val title = "DEMONSTRATIVO FINANCEIRO — ${filter.from.format(dateFmt)} a ${filter.to.format(dateFmt)}"

        // cols: CÓD | CATEGORIA | GRUPO | RECEITA | DESPESA | SALDO
        val cols = floatArrayOf(margin, margin+60f, margin+270f, margin+430f, margin+520f, margin+600f)

        var cs: PDPageContentStream
        var y: Float

        fun addPage(): PDPageContentStream {
            val p = PDPage(pageSize); doc.addPage(p); return PDPageContentStream(doc, p)
        }

        fun textAt(stream: PDPageContentStream, txt: String, x: Float, yPos: Float,
                   font: PDType1Font, size: Float, r: Float = 0f, g: Float = 0f, b: Float = 0f) {
            stream.beginText(); stream.setFont(font, size)
            stream.setNonStrokingColor(r, g, b)
            stream.newLineAtOffset(x, yPos); stream.showText(txt); stream.endText()
        }

        fun hLine(stream: PDPageContentStream, yPos: Float, thick: Float = 0.5f) {
            stream.setLineWidth(thick)
            stream.moveTo(margin, yPos); stream.lineTo(pageW - margin, yPos); stream.stroke()
            stream.setLineWidth(0.5f)
        }

        fun printHeader(stream: PDPageContentStream, startY: Float): Float {
            var ly = startY
            textAt(stream, title, margin, ly, fontBold, 11f)
            ly -= 8f; hLine(stream, ly); ly -= 12f
            val hdrs = arrayOf("CÓD.", "CATEGORIA", "GRUPO", "RECEITA", "DESPESA", "SALDO")
            hdrs.forEachIndexed { i, h -> textAt(stream, h, cols[i], ly, fontBold, 8f) }
            ly -= 4f; hLine(stream, ly); ly -= 12f
            return ly
        }

        cs = addPage(); y = pageH - margin; y = printHeader(cs, y)

        var totIncome  = Money.ZERO
        var totExpense = Money.ZERO

        val grouped = rows.groupBy { it.groupCode to it.groupName }
        grouped.forEach { (grpKey, grpRows) ->
            val (grpCode, grpName) = grpKey
            val grpLabel = listOfNotNull(grpCode, grpName).joinToString(" — ").ifBlank { "Sem grupo" }.take(30)

            if (y < margin + 36f) { cs.close(); cs = addPage(); y = pageH - margin; y = printHeader(cs, y) }

            // Group header row
            textAt(cs, grpLabel, cols[0], y, fontBold, 8f, 0.18f, 0.33f, 0.54f)
            y -= 11f

            var subIncome  = Money.ZERO
            var subExpense = Money.ZERO

            grpRows.forEach { row ->
                if (y < margin + 24f) { cs.close(); cs = addPage(); y = pageH - margin; y = printHeader(cs, y) }
                textAt(cs, row.categoryCode.take(12), cols[0], y, fontReg, 7f)
                textAt(cs, row.categoryName.take(38), cols[1], y, fontReg, 7f)
                textAt(cs, grpLabel.take(22),          cols[2], y, fontSm,  7f)
                textAt(cs, MoneyFormatter.format(row.income),   cols[3], y, fontReg, 7.5f, 0.1f, 0.5f, 0.1f)
                textAt(cs, MoneyFormatter.format(row.expense),  cols[4], y, fontReg, 7.5f, 0.6f, 0.1f, 0.1f)
                val balColor = if (row.balance.isNegative()) Triple(0.6f,0.1f,0.1f) else Triple(0.1f,0.5f,0.1f)
                textAt(cs, MoneyFormatter.format(row.balance), cols[5], y, fontBold, 7.5f, balColor.first, balColor.second, balColor.third)
                subIncome  += row.income
                subExpense += row.expense
                y -= 11f
            }

            // Subtotal
            if (y < margin + 24f) { cs.close(); cs = addPage(); y = pageH - margin; y = printHeader(cs, y) }
            hLine(cs, y + 8f)
            textAt(cs, "Subtotal $grpLabel", cols[0], y, fontBold, 7.5f)
            textAt(cs, MoneyFormatter.format(subIncome),            cols[3], y, fontBold, 7.5f)
            textAt(cs, MoneyFormatter.format(subExpense),           cols[4], y, fontBold, 7.5f)
            textAt(cs, MoneyFormatter.format(subIncome - subExpense), cols[5], y, fontBold, 7.5f)
            y -= 15f

            totIncome  += subIncome
            totExpense += subExpense
        }

        // Total geral
        if (y < margin + 24f) { cs.close(); cs = addPage(); y = pageH - margin; y = printHeader(cs, y) }
        hLine(cs, y + 8f, 1f)
        textAt(cs, "TOTAL GERAL", cols[0], y, fontBold, 9f)
        textAt(cs, MoneyFormatter.format(totIncome),            cols[3], y, fontBold, 9f)
        textAt(cs, MoneyFormatter.format(totExpense),           cols[4], y, fontBold, 9f)
        textAt(cs, MoneyFormatter.format(totIncome - totExpense), cols[5], y, fontBold, 9f)

        cs.close()
        outputDir.mkdirs()
        val file = File(outputDir, "demonstrativo_${filter.from.format(filenameFmt)}_${filter.to.format(filenameFmt)}.pdf")
        doc.save(file); doc.close()
        return file
    }

    // ── Comprovante Individual PDF (RN-31) ───────────────────────────────────

    fun receiptPdf(
        tx: Transaction,
        supplierName: String?,
        accountName: String,
        categoryCode: String?,
        categoryName: String?,
        costCenterName: String?,
        outputDir: File
    ): File {
        val doc      = PDDocument()
        val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val fontReg  = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontSm   = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        val page     = PDPage(PDRectangle.A4)
        doc.addPage(page)

        val pageW  = PDRectangle.A4.width
        val pageH  = PDRectangle.A4.height
        val margin = 50f
        val cs     = PDPageContentStream(doc, page)

        fun textAt(txt: String, x: Float, yPos: Float, font: PDType1Font, size: Float) {
            cs.beginText(); cs.setFont(font, size)
            cs.newLineAtOffset(x, yPos); cs.showText(txt); cs.endText()
        }

        fun hLine(yPos: Float) {
            cs.moveTo(margin, yPos); cs.lineTo(pageW - margin, yPos); cs.stroke()
        }

        fun field(label: String, value: String, x: Float, y: Float, colW: Float = 240f) {
            textAt(label, x, y + 11f, fontSm, 7f)
            cs.setLineWidth(0.3f)
            cs.moveTo(x, y); cs.lineTo(x + colW, y); cs.stroke()
            textAt(value.take(38), x + 2f, y + 2f, fontReg, 9f)
        }

        var y = pageH - margin

        // ── Header ──
        textAt("COMPROVANTE DE PAGAMENTO", margin, y, fontBold, 16f)
        y -= 6f
        textAt("Sistema de Gestão Financeira — SisgFin", margin, y, fontSm, 9f)
        y -= 4f; hLine(y); y -= 18f

        // Type badge
        val typeLabel = when (tx.type) {
            TransactionType.INCOME     -> "RECEITA"
            TransactionType.EXPENSE    -> "DESPESA"
            TransactionType.REVERSAL   -> "ESTORNO"
            TransactionType.TRANSFER   -> "TRANSFERÊNCIA"
            TransactionType.ADJUSTMENT -> "AJUSTE"
        }
        textAt(typeLabel, margin, y, fontBold, 10f)
        textAt("N° ${tx.id}", pageW - margin - 60f, y, fontBold, 10f)
        y -= 18f

        // ── Row 1: Descrição (full width) ──
        field("DESCRIÇÃO", tx.description, margin, y, pageW - 2 * margin)
        y -= 28f

        // ── Row 2: Valor | Status ──
        val value   = tx.paidAmount ?: tx.amount
        val mid     = (pageW - 2 * margin) / 2f
        field("VALOR PAGO", MoneyFormatter.format(value), margin, y, mid - 6f)
        field("STATUS", tx.status.displayName, margin + mid + 6f, y, mid - 6f)
        y -= 28f

        // ── Row 3: Emissão | Vencimento | Pagamento ──
        val third = (pageW - 2 * margin) / 3f
        field("DATA DE EMISSÃO",    tx.issueDate.format(dateFmt),  margin,                 y, third - 4f)
        field("DATA DE VENCIMENTO", tx.dueDate.format(dateFmt),    margin + third + 4f,    y, third - 4f)
        field("DATA DE PAGAMENTO",  tx.paymentDate?.format(dateFmt) ?: "—", margin + 2 * (third + 4f), y, third - 4f)
        y -= 28f

        // ── Row 4: Fornecedor | Conta ──
        field("FORNECEDOR / CREDOR", supplierName ?: "—", margin, y, mid - 6f)
        field("CONTA FINANCEIRA", accountName, margin + mid + 6f, y, mid - 6f)
        y -= 28f

        // ── Row 5: Categoria (AUDESP) | Centro de Custo ──
        val catLabel = listOfNotNull(categoryCode, categoryName).joinToString(" — ").ifBlank { "—" }
        field("CATEGORIA (AUDESP)", catLabel, margin, y, mid - 6f)
        field("CENTRO DE CUSTO", costCenterName ?: "—", margin + mid + 6f, y, mid - 6f)
        y -= 28f

        // ── Row 6: Tipo Doc | Número Doc ──
        if (tx.documentType != null || tx.documentNumber != null) {
            field("TIPO DE DOCUMENTO", tx.documentType ?: "—", margin, y, mid - 6f)
            field("NÚMERO DO DOCUMENTO", tx.documentNumber ?: "—", margin + mid + 6f, y, mid - 6f)
            y -= 28f
        }

        // ── Parcela ──
        if (tx.installmentTotal != null) {
            field("PARCELA", "${tx.installmentCurrent} de ${tx.installmentTotal}", margin, y, mid - 6f)
            y -= 28f
        }

        // ── Observações ──
        if (!tx.notes.isNullOrBlank()) {
            field("OBSERVAÇÕES", tx.notes, margin, y, pageW - 2 * margin)
            y -= 28f
        }

        // ── Separator + Signature ──
        y -= 20f
        hLine(y)
        y -= 40f

        val sigW = (pageW - 2 * margin - 24f) / 2f
        cs.setLineWidth(0.3f)
        cs.moveTo(margin, y); cs.lineTo(margin + sigW, y); cs.stroke()
        cs.moveTo(margin + sigW + 24f, y); cs.lineTo(margin + 2 * sigW + 24f, y); cs.stroke()
        y -= 14f
        textAt("Assinatura do Responsável", margin, y, fontSm, 8f)
        textAt("Assinatura do Tesoureiro", margin + sigW + 24f, y, fontSm, 8f)

        // ── Footer ──
        y = 30f
        hLine(y)
        textAt("Emitido pelo SisgFin — ${LocalDate.now().format(dateFmt)}", margin, y - 14f, fontSm, 7.5f)
        textAt("Página 1 de 1", pageW - margin - 50f, y - 14f, fontSm, 7.5f)

        cs.close()
        outputDir.mkdirs()
        val file = File(outputDir, "comprovante_tx${tx.id}.pdf")
        doc.save(file); doc.close()
        return file
    }
}

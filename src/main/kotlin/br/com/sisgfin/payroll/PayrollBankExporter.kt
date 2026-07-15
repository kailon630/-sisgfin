package br.com.sisgfin.payroll

import br.com.sisgfin.financial.money.Money
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.awt.Color
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class TipoRemessa { ADIANTAMENTO, LIQUIDO }

data class RemessaEntry(
    val cpf: String,
    val agency: String,
    val account: String,
    val value: Money,
    val employeeName: String
)

object PayrollBankExporter {
    private val filenameFmt = DateTimeFormatter.ofPattern("yyyy-MM")

    fun export(
        referenceMonth: YearMonth,
        tipo: TipoRemessa,
        entries: List<RemessaEntry>,
        outputDir: File
    ): File {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Folha BB")

        val blue = XSSFColor(Color(30, 58, 138), null)
        val hdrStyle = wb.createCellStyle().apply {
            setFillForegroundColor(blue)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            })
            alignment = HorizontalAlignment.CENTER
            wrapText = true
        }
        val moneyStyle = wb.createCellStyle().apply {
            dataFormat = wb.creationHelper.createDataFormat().getFormat("#,##0.00")
        }

        val hdrRow = sheet.createRow(0)
        listOf(
            "CPF\n(Exemplo: 012345678-90)",
            "Agência com DV\n(Exemplo: 1234-5)",
            "Conta com DV\n(Exemplo: 12345-6)",
            "Valor\n(Exemplo: 50150,00)"
        ).forEachIndexed { col, text ->
            hdrRow.createCell(col).also {
                it.setCellValue(text)
                it.cellStyle = hdrStyle
            }
        }
        hdrRow.heightInPoints = 36f

        entries.forEachIndexed { idx, entry ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(formatCpf(entry.cpf))
            row.createCell(1).setCellValue(entry.agency)
            row.createCell(2).setCellValue(entry.account)
            row.createCell(3).also {
                it.setCellValue(entry.value.value.toDouble())
                it.cellStyle = moneyStyle
            }
        }

        sheet.setColumnWidth(0, 22 * 256)
        sheet.setColumnWidth(1, 20 * 256)
        sheet.setColumnWidth(2, 20 * 256)
        sheet.setColumnWidth(3, 18 * 256)

        val label = if (tipo == TipoRemessa.ADIANTAMENTO) "adiantamentos" else "liquidos"
        val filename = "remessa-folha-${referenceMonth.format(filenameFmt)}-$label.xlsx"

        outputDir.mkdirs()
        val file = File(outputDir, filename)
        file.outputStream().use { wb.write(it) }
        wb.close()
        return file
    }

    // Formata CPF normalizado (11 dígitos) como "012345678-90" (padrão do modelo BB)
    fun formatCpf(digits: String): String {
        val d = digits.replace(Regex("[^0-9]"), "")
        return if (d.length == 11) "${d.substring(0, 9)}-${d.substring(9)}" else digits
    }
}

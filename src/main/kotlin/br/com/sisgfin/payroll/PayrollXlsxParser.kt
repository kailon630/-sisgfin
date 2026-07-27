package br.com.sisgfin.payroll

import br.com.sisgfin.financial.money.Money
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File

// Parser para o formato SCI Ambiente Contábil ÚNICO (XLSX).
// Suporta dois relatórios: Folha Mensal e Adiantamento Salarial.
// Não faz lookup de BD — apenas extrai os dados brutos da planilha.
class PayrollXlsxParser {

    data class ParseResult(
        val entries: List<PayrollRawEntry>,
        val warnings: List<String>,
        val format: Format = Format.FOLHA_MENSAL
    )

    enum class Format { FOLHA_MENSAL, ADIANTAMENTO }

    fun parse(file: File): ParseResult {
        val allWarnings = mutableListOf<String>()
        val entries = mutableListOf<PayrollRawEntry>()
        var detectedFormat = Format.FOLHA_MENSAL

        WorkbookFactory.create(file).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            detectedFormat = detectFormat(sheet)
            val lastRow = sheet.lastRowNum
            var i = 0
            while (i <= lastRow) {
                val row = sheet.getRow(i)
                if (row != null && isEmployeeStart(row, detectedFormat)) {
                    val (entry, consumed, warnings) = when (detectedFormat) {
                        Format.FOLHA_MENSAL -> parseBlockFolha(sheet, i, lastRow)
                        Format.ADIANTAMENTO -> parseBlockAdiantamento(sheet, i, lastRow)
                    }
                    entries.add(entry)
                    allWarnings.addAll(warnings)
                    i += consumed
                } else {
                    i++
                }
            }
        }

        return ParseResult(entries, allWarnings, detectedFormat)
    }

    // Detecta o formato pela célula A1: título contendo "adiantamento" → Adiantamento Salarial
    private fun detectFormat(sheet: Sheet): Format {
        val title = sheet.getRow(0)?.getCell(0).safeString().lowercase()
        return if ("adiantamento" in title) Format.ADIANTAMENTO else Format.FOLHA_MENSAL
    }

    private fun isEmployeeStart(row: Row, format: Format): Boolean = when (format) {
        Format.FOLHA_MENSAL -> {
            val aVal = row.getCell(COL_A).safeString()
            val eVal = row.getCell(COL_E).safeString()
            aVal.toIntOrNull() != null && eVal.isNotBlank() && !eVal.startsWith("CPF")
        }
        Format.ADIANTAMENTO -> {
            val aVal = row.getCell(COL_A).safeString()
            val cVal = row.getCell(ADIANT_C).safeString()
            aVal.toIntOrNull() != null && cVal.isNotBlank() && !cVal.startsWith("CPF")
        }
    }

    // ── Folha Mensal ──────────────────────────────────────────────────────────

    private fun parseBlockFolha(sheet: Sheet, startRow: Int, lastRow: Int): BlockResult {
        val headerRow = sheet.getRow(startRow)!!
        val matricula = headerRow.getCell(COL_A).safeString().toIntOrNull() ?: 0
        val nome = headerRow.getCell(COL_E).safeString()
        val salaryBase = parseSalaryBase(headerRow.getCell(COL_AB).safeString())

        var cpf = ""
        var funcao = ""
        sheet.getRow(startRow + 1)?.let { cpfRow ->
            val cpfText = cpfRow.getCell(COL_E).safeString()
            cpf = parseCpf(cpfText)
            funcao = parseFuncao(cpfText)
        }

        var adiantamento = Money.ZERO
        var liquido = Money.ZERO
        var liquidoCount = 0
        var rowsConsumed = 2
        val warnings = mutableListOf<String>()

        for (j in (startRow + 2)..lastRow) {
            val row = sheet.getRow(j)
            if (row != null && isEmployeeStart(row, Format.FOLHA_MENSAL)) break
            rowsConsumed++
            if (row == null) continue

            if (row.getCell(COL_AH).safeString() == "903") {
                row.getCell(COL_BH).safeDouble()?.let { adiantamento = Money.fromDouble(it) }
            }

            val atVal = row.getCell(COL_AT).safeString()
            if (atVal.contains("Líquido") || atVal.contains("Liquido")) {
                row.getCell(COL_BH).safeDouble()?.let { v ->
                    liquido = if (liquidoCount == 0) {
                        Money.fromDouble(v)
                    } else {
                        val combined = liquido + Money.fromDouble(v)
                        warnings.add("Funcionário $nome: férias detectadas — valores unificados (líquido = R\$ $combined)")
                        combined
                    }
                    liquidoCount++
                }
            }
        }

        if (salaryBase.isPositive() && adiantamento > salaryBase * 3.0) {
            warnings.add(
                "Funcionário $nome (matrícula $matricula): adiantamento R\$ $adiantamento " +
                    "é anômalo (salário base R\$ $salaryBase) — valor zerado"
            )
            adiantamento = Money.ZERO
        }

        if (cpf.length != 11) {
            warnings.add("Funcionário $nome (matrícula $matricula): CPF não encontrado ou inválido na linha ${startRow + 2}")
        }

        return BlockResult(
            PayrollRawEntry(matricula, nome, cpf, funcao, adiantamento, liquido, salaryBase, liquidoCount),
            rowsConsumed, warnings
        )
    }

    // ── Adiantamento Salarial ─────────────────────────────────────────────────
    // Neste relatório: nome em C, dados em X, líquido (= valor do adiantamento) em AQ→AZ.
    // O campo `liquido` do PayrollRawEntry fica zero — será preenchido pela Folha Mensal.

    private fun parseBlockAdiantamento(sheet: Sheet, startRow: Int, lastRow: Int): BlockResult {
        val headerRow = sheet.getRow(startRow)!!
        val matricula = headerRow.getCell(COL_A).safeString().toIntOrNull() ?: 0
        val nome = headerRow.getCell(ADIANT_C).safeString()
        val salaryBase = parseSalaryBase(headerRow.getCell(ADIANT_X).safeString())

        var cpf = ""
        var funcao = ""
        sheet.getRow(startRow + 1)?.let { cpfRow ->
            val cpfText = cpfRow.getCell(ADIANT_C).safeString()
            cpf = parseCpf(cpfText)
            funcao = parseFuncao(cpfText)
        }

        var adiantamento = Money.ZERO
        var rowsConsumed = 2
        val warnings = mutableListOf<String>()

        for (j in (startRow + 2)..lastRow) {
            val row = sheet.getRow(j)
            if (row != null && isEmployeeStart(row, Format.ADIANTAMENTO)) break
            rowsConsumed++
            if (row == null) continue

            val aqVal = row.getCell(ADIANT_AQ).safeString()
            if (aqVal.contains("Líquido") || aqVal.contains("Liquido")) {
                row.getCell(ADIANT_AZ).safeDouble()?.let { adiantamento = Money.fromDouble(it) }
            }
        }

        if (salaryBase.isPositive() && adiantamento > salaryBase * 3.0) {
            warnings.add(
                "Funcionário $nome (matrícula $matricula): adiantamento R\$ $adiantamento " +
                    "é anômalo (salário base R\$ $salaryBase) — valor zerado"
            )
            adiantamento = Money.ZERO
        }

        if (cpf.length != 11) {
            warnings.add("Funcionário $nome (matrícula $matricula): CPF não encontrado ou inválido na linha ${startRow + 2}")
        }

        return BlockResult(
            PayrollRawEntry(matricula, nome, cpf, funcao, adiantamento, Money.ZERO, salaryBase, 0),
            rowsConsumed, warnings
        )
    }

    // ── Utilitários compartilhados ────────────────────────────────────────────

    // "Admissão em 21/03/2025   Salário base   3.025,00   Horas mensais: 210,00"
    private fun parseSalaryBase(text: String): Money {
        val m = Regex("""Salário base\s+([\d.,]+)""").find(text) ?: return Money.ZERO
        val raw = m.groupValues[1].replace(".", "").replace(",", ".")
        return Money.fromString(raw)
    }

    // "CPF: 254.461.288-69   CTPS: ..."
    private fun parseCpf(text: String): String {
        val m = Regex("""CPF:\s*([\d.\-]+)""").find(text) ?: return ""
        return m.groupValues[1].replace(Regex("[^0-9]"), "")
    }

    // "... Função: AUXILIAR ADMINISTRATIVO"
    private fun parseFuncao(text: String): String {
        val m = Regex("""Função:\s*(.+)""").find(text) ?: return ""
        return m.groupValues[1].trim()
    }

    private fun Cell?.safeString(): String {
        this ?: return ""
        return when (cellType) {
            CellType.STRING -> stringCellValue.trim()
            CellType.NUMERIC -> {
                val d = numericCellValue
                if (d == kotlin.math.floor(d) && !java.lang.Double.isInfinite(d))
                    d.toLong().toString()
                else d.toString()
            }
            CellType.FORMULA -> try {
                when (cachedFormulaResultType) {
                    CellType.STRING -> stringCellValue.trim()
                    CellType.NUMERIC -> numericCellValue.let { d ->
                        if (d == kotlin.math.floor(d)) d.toLong().toString() else d.toString()
                    }
                    else -> ""
                }
            } catch (_: Exception) { "" }
            else -> ""
        }
    }

    private fun Cell?.safeDouble(): Double? {
        this ?: return null
        return when (cellType) {
            CellType.NUMERIC -> numericCellValue
            CellType.STRING -> stringCellValue.trim().replace(",", ".").toDoubleOrNull()
            else -> null
        }
    }

    private data class BlockResult(
        val entry: PayrollRawEntry,
        val rowsConsumed: Int,
        val warnings: List<String>
    )

    companion object {
        // Folha Mensal
        private const val COL_A  = 0   // matrícula
        private const val COL_E  = 4   // nome / linha CPF
        private const val COL_AB = 27  // "Admissão em... Salário base X.XXX,XX"
        private const val COL_AH = 33  // código de desconto (903 = adiantamento)
        private const val COL_AT = 45  // "Líquido - >"
        private const val COL_BH = 59  // valores monetários

        // Adiantamento Salarial (7 colunas mais estreito)
        private const val ADIANT_C  = 2   // nome / linha CPF
        private const val ADIANT_X  = 23  // "Admissão em... Salário base X.XXX,XX"
        private const val ADIANT_AQ = 42  // "Líquido - >" (= valor do adiantamento líquido)
        private const val ADIANT_AZ = 51  // valor monetário
    }
}

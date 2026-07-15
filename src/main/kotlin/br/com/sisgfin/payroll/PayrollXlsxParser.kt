package br.com.sisgfin.payroll

import br.com.sisgfin.financial.money.Money
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File

// Parser para o formato SCI Ambiente Contábil ÚNICO (XLSX).
// Lê blocos de funcionário detectados pela presença de matrícula (col A inteira) + nome (col E).
// Não faz lookup de BD — apenas extrai os dados brutos da planilha.
class PayrollXlsxParser {

    data class ParseResult(
        val entries: List<PayrollRawEntry>,
        val warnings: List<String>
    )

    fun parse(file: File): ParseResult {
        val allWarnings = mutableListOf<String>()
        val entries = mutableListOf<PayrollRawEntry>()

        WorkbookFactory.create(file).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val lastRow = sheet.lastRowNum
            var i = 0
            while (i <= lastRow) {
                val row = sheet.getRow(i)
                if (row != null && isEmployeeStart(row)) {
                    val (entry, consumed, warnings) = parseBlock(sheet, i, lastRow)
                    entries.add(entry)
                    allWarnings.addAll(warnings)
                    i += consumed
                } else {
                    i++
                }
            }
        }

        return ParseResult(entries, allWarnings)
    }

    private data class BlockResult(
        val entry: PayrollRawEntry,
        val rowsConsumed: Int,
        val warnings: List<String>
    )

    private fun parseBlock(sheet: Sheet, startRow: Int, lastRow: Int): BlockResult {
        val headerRow = sheet.getRow(startRow)!!
        val matricula = headerRow.getCell(COL_A).safeString().toIntOrNull() ?: 0
        val nome = headerRow.getCell(COL_E).safeString()
        val salaryBase = parseSalaryBase(headerRow.getCell(COL_AB).safeString())

        var cpf = ""
        var funcao = ""
        val cpfRow = sheet.getRow(startRow + 1)
        if (cpfRow != null) {
            val cpfText = cpfRow.getCell(COL_E).safeString()
            cpf = parseCpf(cpfText)
            funcao = parseFuncao(cpfText)
        }

        var adiantamento = Money.ZERO
        var liquido = Money.ZERO
        var liquidoCount = 0
        val warnings = mutableListOf<String>()
        // Conta as duas linhas fixas do cabeçalho do bloco (header + CPF)
        var rowsConsumed = 2

        for (j in (startRow + 2)..lastRow) {
            val row = sheet.getRow(j)
            // Próximo bloco de funcionário encontrado: encerra este bloco sem contar a linha
            if (row != null && isEmployeeStart(row)) break
            rowsConsumed++
            if (row == null) continue

            // Desconto código 903 = adiantamento já pago na 1ª parcela
            val ahVal = row.getCell(COL_AH).safeString()
            if (ahVal == "903") {
                val v = row.getCell(COL_BH).safeDouble()
                if (v != null) adiantamento = Money.fromDouble(v)
            }

            // "Líquido - >" = valor da 2ª parcela ainda a pagar
            val atVal = row.getCell(COL_AT).safeString()
            if (atVal.contains("Líquido") || atVal.contains("Liquido")) {
                val v = row.getCell(COL_BH).safeDouble()
                if (v != null) {
                    if (liquidoCount == 0) {
                        liquido = Money.fromDouble(v)
                    } else {
                        // Segundo Líquido ocorre em folhas de férias com blocos separados
                        liquido = liquido + Money.fromDouble(v)
                        warnings.add("Funcionário $nome: férias detectadas — valores unificados (líquido = R\$ $liquido)")
                    }
                    liquidoCount++
                }
            }
        }

        // Adiantamento anômalo: valor > salário base × 3 indica erro de leitura da planilha
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
            rowsConsumed,
            warnings
        )
    }

    // Linha de início de bloco: col A tem inteiro (matrícula) E col E tem nome (não CPF)
    private fun isEmployeeStart(row: Row): Boolean {
        val aVal = row.getCell(COL_A).safeString()
        val eVal = row.getCell(COL_E).safeString()
        return aVal.toIntOrNull() != null && eVal.isNotBlank() && !eVal.startsWith("CPF")
    }

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

    companion object {
        private const val COL_A  = 0   // matrícula / código de provento
        private const val COL_E  = 4   // nome / linha de CPF
        private const val COL_AB = 27  // "Admissão em... Salário base X.XXX,XX"
        private const val COL_AH = 33  // código de desconto (ex: "903" = adiantamento)
        private const val COL_AT = 45  // "Líquido - >"
        private const val COL_BH = 59  // valores monetários
    }
}

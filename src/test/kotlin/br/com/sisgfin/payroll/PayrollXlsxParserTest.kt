package br.com.sisgfin.payroll

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PayrollXlsxParserTest {

    private val parser = PayrollXlsxParser()

    // ── 1. Arquivo real ──────────────────────────────────────────────────────

    @Test
    fun `arquivo real folha 06-2026 parseia 80 funcionarios`() {
        val file = File("docs/Espelho e resumo da folha.xlsx")
        if (!file.exists()) return

        val result = parser.parse(file)
        assertEquals(80, result.entries.size, "Esperados 80 funcionários na folha de 06/2026")
    }

    @Test
    fun `adriana bispo martins - cpf adiantamento e liquido corretos`() {
        val file = File("docs/Espelho e resumo da folha.xlsx")
        if (!file.exists()) return

        val result = parser.parse(file)
        val entry = result.entries.firstOrNull { it.nome.contains("ADRIANA BISPO") }
            ?: error("Adriana Bispo Martins não encontrada na folha")

        assertEquals("25446128869", entry.cpf, "CPF normalizado (11 dígitos)")
        assertEquals(0, BigDecimal("1512.50").compareTo(entry.adiantamento.value), "Adiantamento de Adriana")
        assertEquals(0, BigDecimal("1512.50").compareTo(entry.liquido.value), "Líquido de Adriana")
    }

    @Test
    fun `fernanda grandchamp - adiantamento zero e liquido de ferias`() {
        val file = File("docs/Espelho e resumo da folha.xlsx")
        if (!file.exists()) return

        val result = parser.parse(file)
        val entry = result.entries.firstOrNull { it.nome.contains("FERNANDA GRANDCHAMP") }
            ?: error("Fernanda Grandchamp Bruno não encontrada na folha")

        assertTrue(entry.adiantamento.isZero(), "Férias sem adiantamento: adiantamento deve ser zero")
        assertEquals(0, BigDecimal("4476.47").compareTo(entry.liquido.value), "Líquido de Fernanda")
    }

    @Test
    fun `arquivo real adiantamento 07-2026 parseia funcionarios corretamente`() {
        val file = File("docs/Espelho e resumo da folha (1).xlsx")
        if (!file.exists()) return

        val result = parser.parse(file)
        assertEquals(PayrollXlsxParser.Format.ADIANTAMENTO, result.format, "Formato detectado deve ser ADIANTAMENTO")
        assertTrue(result.entries.isNotEmpty(), "Deve parsear ao menos um funcionário")
        assertTrue(result.entries.all { it.adiantamento.isPositive() }, "Todos devem ter adiantamento > 0")
        assertTrue(result.entries.all { it.liquido.isZero() }, "Liquido deve ser zero no relatório de adiantamento")
        assertTrue(result.entries.all { it.cpf.length == 11 }, "CPFs devem estar normalizados com 11 dígitos")
    }

    @Test
    fun `adiantamento - alex costa safra - valor correto`() {
        val file = File("docs/Espelho e resumo da folha (1).xlsx")
        if (!file.exists()) return

        val result = parser.parse(file)
        val entry = result.entries.firstOrNull { it.nome.contains("ALEX COSTA") }
            ?: error("Alex Costa Safra não encontrado")

        assertEquals("42262292825", entry.cpf)
        assertEquals(0, java.math.BigDecimal("1210.00").compareTo(entry.adiantamento.value), "Adiantamento de Alex")
        assertTrue(entry.liquido.isZero(), "Líquido deve ser zero no relatório de adiantamento")
    }

    // ── 2. Edge cases (XLSX sintético em memória) ────────────────────────────

    @Test
    fun `adiantamento anomalo e zerado com warning`() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet()

        val h = sheet.createRow(0)
        h.createCell(COL_A).setCellValue(99.0)
        h.createCell(COL_E).setCellValue("FUNCIONARIO ANOMALO")
        h.createCell(COL_AB).setCellValue("Admissão em 01/01/2024   Salário base   1.500,00   Horas mensais: 220")

        val cpfR = sheet.createRow(1)
        cpfR.createCell(COL_E).setCellValue("CPF: 111.222.333-44   CTPS: 99988   CBO: 411005   Função: AUXILIAR")

        // Adiantamento anômalo: 150000 >> salário base 1500 × 3 = 4500
        val prov = sheet.createRow(2)
        prov.createCell(COL_AH).setCellValue("903")
        prov.createCell(COL_BH).setCellValue(150000.0)

        val liq = sheet.createRow(3)
        liq.createCell(COL_AT).setCellValue("Líquido - >")
        liq.createCell(COL_BH).setCellValue(750.0)

        val tmp = File.createTempFile("sisgfin_anomalo", ".xlsx")
        wb.write(tmp.outputStream()); wb.close()

        val result = parser.parse(tmp)
        tmp.delete()

        assertEquals(1, result.entries.size)
        assertTrue(result.entries.first().adiantamento.isZero(), "Adiantamento anômalo deve ser zerado")
        assertEquals(0, BigDecimal("750.00").compareTo(result.entries.first().liquido.value))
        assertTrue(result.warnings.any { it.contains("anômal") }, "Deve emitir warning de anomalia")
    }

    @Test
    fun `dois blocos liquido acumulam valor com warning de ferias`() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet()

        val h = sheet.createRow(0)
        h.createCell(COL_A).setCellValue(1.0)
        h.createCell(COL_E).setCellValue("FUNCIONARIO FERIAS")
        h.createCell(COL_AB).setCellValue("Admissão em 01/01/2023   Salário base   3.000,00   Horas mensais: 220")

        val cpfR = sheet.createRow(1)
        cpfR.createCell(COL_E).setCellValue("CPF: 123.456.789-00   CTPS: 12345   CBO: 411005   Função: ANALISTA")

        val liq1 = sheet.createRow(2)
        liq1.createCell(COL_AT).setCellValue("Líquido - >")
        liq1.createCell(COL_BH).setCellValue(2000.0)

        val liq2 = sheet.createRow(3)
        liq2.createCell(COL_AT).setCellValue("Líquido - >")
        liq2.createCell(COL_BH).setCellValue(1500.0)

        val tmp = File.createTempFile("sisgfin_ferias", ".xlsx")
        wb.write(tmp.outputStream()); wb.close()

        val result = parser.parse(tmp)
        tmp.delete()

        assertEquals(1, result.entries.size)
        assertEquals(0, BigDecimal("3500.00").compareTo(result.entries.first().liquido.value), "Férias acumula os dois Líquidos")
        assertTrue(result.warnings.any { it.contains("férias") }, "Deve emitir warning de férias acumuladas")
    }

    @Test
    fun `funcionario sem cpf valido gera warning`() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet()

        val h = sheet.createRow(0)
        h.createCell(COL_A).setCellValue(42.0)
        h.createCell(COL_E).setCellValue("FUNCIONARIO SEM CPF")

        val cpfR = sheet.createRow(1)
        cpfR.createCell(COL_E).setCellValue("CTPS: 999888   Função: ANALISTA")  // sem CPF

        val liq = sheet.createRow(2)
        liq.createCell(COL_AT).setCellValue("Líquido - >")
        liq.createCell(COL_BH).setCellValue(2000.0)

        val tmp = File.createTempFile("sisgfin_semcpf", ".xlsx")
        wb.write(tmp.outputStream()); wb.close()

        val result = parser.parse(tmp)
        tmp.delete()

        assertEquals(1, result.entries.size)
        assertTrue(result.entries.first().cpf.length != 11, "CPF ausente não deve ter 11 dígitos")
        assertTrue(result.warnings.any { it.contains("CPF") && it.contains("FUNCIONARIO SEM CPF") })
    }

    companion object {
        private const val COL_A  = 0
        private const val COL_E  = 4
        private const val COL_AB = 27
        private const val COL_AH = 33
        private const val COL_AT = 45
        private const val COL_BH = 59
    }
}

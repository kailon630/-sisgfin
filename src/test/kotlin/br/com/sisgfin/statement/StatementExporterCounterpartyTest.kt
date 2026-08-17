package br.com.sisgfin.statement

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.TransactionPayment
import br.com.sisgfin.financial.transactions.CounterpartyMap
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * T-05 — StatementExporter.exportToExcel() popula a coluna FORNECEDOR
 * via CounterpartyMap em vez de deixar vazio.
 */
class StatementExporterCounterpartyTest {

    private val account = FinancialAccount(id = 1, name = "Conta Corrente")

    private fun tx(id: Int, supplierId: Int? = null, employeeId: Int? = null) = Transaction(
        id          = id,
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PAID,
        description = "Pagamento $id",
        amount      = Money.fromString("500.00"),
        paidAmount  = Money.fromString("500.00"),
        issueDate   = LocalDateTime.of(2026, 1, 10, 0, 0),
        dueDate     = LocalDateTime.of(2026, 1, 20, 0, 0),
        paymentDate = LocalDateTime.of(2026, 1, 20, 0, 0),
        accountId   = 1,
        supplierId  = supplierId,
        employeeId  = employeeId
    )

    private fun entry(tx: Transaction, balance: Money = Money.fromString("1000.00")): StatementEntry {
        val amount = tx.paidAmount ?: tx.amount
        val signed = if (tx.type == TransactionType.INCOME) amount else amount.negate()
        val payment = TransactionPayment(
            id              = 0,
            transactionId   = tx.id,
            paymentDate     = tx.paymentDate?.toLocalDate() ?: LocalDate.of(2026, 1, 20),
            accountId       = tx.accountId,
            principalAmount = amount,
            createdAt       = tx.paymentDate ?: LocalDateTime.of(2026, 1, 20, 0, 0)
        )
        return StatementEntry(tx, payment, signed, balance)
    }

    @Test
    fun `exportToExcel preenche coluna FORNECEDOR com nome do fornecedor`(@TempDir tmpDir: Path) {
        val transaction = tx(1, supplierId = 5)
        val map = CounterpartyMap(
            suppliers = mapOf(5 to "Fornecedor ACME"),
            employees = emptyMap()
        )
        val entries = listOf(entry(transaction))
        val file = StatementExporter.exportToExcel(
            account        = account,
            filter         = StatementFilter(),
            openingBalance = Money.fromString("2000.00"),
            entries        = entries,
            outputDir      = tmpDir.toFile(),
            counterpartyMap = map
        )

        val wb = XSSFWorkbook(file.inputStream())
        val sheet = wb.getSheetAt(0)
        // Rows: 0=title, 1=period, 2=opening, 3=blank, 4=header, 5=first data row
        val supplierCell = sheet.getRow(5).getCell(4)
        assertEquals("Fornecedor ACME", supplierCell.stringCellValue)
        wb.close()
    }

    @Test
    fun `exportToExcel preenche coluna FORNECEDOR com nome do funcionario`(@TempDir tmpDir: Path) {
        val transaction = tx(2, employeeId = 3)
        val map = CounterpartyMap(
            suppliers = emptyMap(),
            employees = mapOf(3 to "João Silva")
        )
        val entries = listOf(entry(transaction))
        val file = StatementExporter.exportToExcel(
            account        = account,
            filter         = StatementFilter(),
            openingBalance = Money.fromString("2000.00"),
            entries        = entries,
            outputDir      = tmpDir.toFile(),
            counterpartyMap = map
        )

        val wb = XSSFWorkbook(file.inputStream())
        val sheet = wb.getSheetAt(0)
        val supplierCell = sheet.getRow(5).getCell(4)
        assertEquals("João Silva", supplierCell.stringCellValue)
        wb.close()
    }

    @Test
    fun `exportToExcel com EMPTY map deixa coluna vazia - compatibilidade retroativa`(@TempDir tmpDir: Path) {
        val transaction = tx(3, supplierId = 99)
        val entries = listOf(entry(transaction))
        val file = StatementExporter.exportToExcel(
            account        = account,
            filter         = StatementFilter(),
            openingBalance = Money.fromString("2000.00"),
            entries        = entries,
            outputDir      = tmpDir.toFile()
            // counterpartyMap default = CounterpartyMap.EMPTY
        )

        val wb = XSSFWorkbook(file.inputStream())
        val sheet = wb.getSheetAt(0)
        val supplierCell = sheet.getRow(5).getCell(4)
        assertEquals("", supplierCell.stringCellValue)
        wb.close()
    }
}

package br.com.sisgfin.financial.payments

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.FinancialTransactionsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Assertions.assertTrue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FindReconciliationDivergencesTest {

    private val repo = TransactionPaymentRepository()
    private val now     = LocalDateTime.of(2026, 8, 19, 0, 0)
    private val payDate = LocalDate.of(2026, 8, 19)

    @BeforeAll
    fun connect() {
        Database.connect(
            "jdbc:h2:mem:recon_div;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver"
        )
    }

    @BeforeEach
    fun createSchema() {
        transaction {
            SchemaUtils.create(FinancialTransactionsTable, TransactionPaymentsTable)
        }
    }

    @AfterEach
    fun dropSchema() {
        transaction {
            SchemaUtils.drop(TransactionPaymentsTable, FinancialTransactionsTable)
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun insertTx(
        paidAmount: BigDecimal? = null,
        isActive: Boolean = true
    ): Int = transaction {
        FinancialTransactionsTable.insert {
            it[type]        = "EXPENSE"
            it[status]      = "PAID"
            it[description] = "Título de teste"
            it[amount]      = BigDecimal("1000.00")
            it[issueDate]   = now
            it[dueDate]     = now
            it[accountId]   = 1
            it[createdAt]   = now
            it[updatedAt]   = now
            it[FinancialTransactionsTable.isActive]   = isActive
            it[FinancialTransactionsTable.paidAmount] = paidAmount
        } get FinancialTransactionsTable.id
    }

    private fun insertPayment(
        txId: Int,
        principal: BigDecimal,
        interest: BigDecimal = BigDecimal.ZERO,
        fine: BigDecimal = BigDecimal.ZERO,
        discount: BigDecimal = BigDecimal.ZERO,
        reversedById: Int? = null
    ) = transaction {
        TransactionPaymentsTable.insert {
            it[transactionId]   = txId
            it[paymentDate]     = payDate
            it[accountId]       = 1
            it[principalAmount] = principal
            it[interestAmount]  = interest
            it[fineAmount]      = fine
            it[discountAmount]  = discount
            it[TransactionPaymentsTable.reversedById] = reversedById
            it[createdAt]       = now
        }
    }

    // ── RECON-01: soma exata → não aparece ───────────────────────────────────

    @Test
    fun `RECON-01 titulo com soma de baixas igual ao paid_amount nao aparece`() {
        val txId = insertTx(paidAmount = BigDecimal("1000.00"))
        insertPayment(txId, principal = BigDecimal("1000.00"))

        assertTrue(repo.findReconciliationDivergences().none { it.transactionId == txId })
    }

    // ── RECON-02: soma menor → aparece com delta correto ─────────────────────

    @Test
    fun `RECON-02 soma menor que paid_amount aparece com delta correto`() {
        val txId = insertTx(paidAmount = BigDecimal("1000.00"))
        insertPayment(txId, principal = BigDecimal("800.00"))

        val div = repo.findReconciliationDivergences().single { it.transactionId == txId }
        assertEquals(Money.fromString("800.00"),   div.somaBaixas)
        assertEquals(Money.fromString("-200.00"),  div.delta)
    }

    // ── RECON-03: soma maior → aparece ───────────────────────────────────────

    @Test
    fun `RECON-03 soma maior que paid_amount aparece`() {
        val txId = insertTx(paidAmount = BigDecimal("500.00"))
        insertPayment(txId, principal = BigDecimal("600.00"))

        val div = repo.findReconciliationDivergences().single { it.transactionId == txId }
        assertEquals(Money.fromString("600.00"), div.somaBaixas)
        assertEquals(Money.fromString("100.00"), div.delta)
    }

    // ── RECON-04: baixa estornada excluída → gera divergência ────────────────

    @Test
    fun `RECON-04 baixa estornada excluida da soma gera divergencia`() {
        val txId = insertTx(paidAmount = BigDecimal("1000.00"))
        insertPayment(txId, principal = BigDecimal("600.00"))                    // ativa
        insertPayment(txId, principal = BigDecimal("400.00"), reversedById = 1) // estornada

        // soma(ativas) = 600, paidAmount = 1000 → divergência
        val div = repo.findReconciliationDivergences().single { it.transactionId == txId }
        assertEquals(Money.fromString("600.00"),  div.somaBaixas)
        assertEquals(Money.fromString("-400.00"), div.delta)
    }

    // ── RECON-05: paid_amount nulo → não aparece (filtrado pelo WHERE) ────────

    @Test
    fun `RECON-05 titulo sem paid_amount nao aparece`() {
        val txId = insertTx(paidAmount = null)

        assertTrue(repo.findReconciliationDivergences().none { it.transactionId == txId })
    }

    // ── RECON-06: is_active = false → não aparece ────────────────────────────

    @Test
    fun `RECON-06 titulo inativo nao aparece mesmo com divergencia`() {
        val txId = insertTx(paidAmount = BigDecimal("1000.00"), isActive = false)
        insertPayment(txId, principal = BigDecimal("500.00"))

        assertTrue(repo.findReconciliationDivergences().none { it.transactionId == txId })
    }

    // ── RECON-07: desconto abate soma; sem divergência ────────────────────────

    @Test
    fun `RECON-07 soma com desconto usa principal mais juros mais multa menos desconto`() {
        // 800 + 50 + 100 − 50 = 900 = paidAmount → sem divergência
        val txId = insertTx(paidAmount = BigDecimal("900.00"))
        insertPayment(
            txId,
            principal = BigDecimal("800.00"),
            interest  = BigDecimal("50.00"),
            fine      = BigDecimal("100.00"),
            discount  = BigDecimal("50.00")
        )

        assertTrue(repo.findReconciliationDivergences().none { it.transactionId == txId })
    }

    // ── RECON-08: base sem divergência nenhuma → lista vazia ─────────────────

    @Test
    fun `RECON-08 base sem divergencias retorna lista vazia`() {
        val tx1 = insertTx(paidAmount = BigDecimal("500.00"))
        val tx2 = insertTx(paidAmount = BigDecimal("300.00"))
        insertPayment(tx1, principal = BigDecimal("500.00"))
        insertPayment(tx2, principal = BigDecimal("300.00"))

        assertTrue(repo.findReconciliationDivergences().isEmpty())
    }

    // ── helper de comparação por valor ────────────────────────────────────────

    private fun assertEquals(expected: Money, actual: Money) {
        org.junit.jupiter.api.Assertions.assertEquals(
            0, expected.compareTo(actual),
            "esperado=$expected, obtido=$actual"
        )
    }
}

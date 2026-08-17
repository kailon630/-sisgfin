package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.TransactionPayment
import br.com.sisgfin.financial.payments.TransactionPaymentsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * R4.5 — testes REAIS contra H2 (PostgreSQL-mode) verificando o lock otimista
 * em TransactionRepository. Nenhum mock: o repositório usa a base H2 in-memory.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OptimisticLockTest {

    private val repo = TransactionRepository()

    @BeforeAll
    fun connect() {
        Database.connect(
            "jdbc:h2:mem:optlock;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun baseExpense(description: String = "Despesa lock test") = Transaction(
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PENDING,
        description = description,
        amount      = Money.fromString("500.00"),
        issueDate   = LocalDateTime.of(2026, 8, 1, 0, 0),
        dueDate     = LocalDateTime.of(2026, 8, 31, 0, 0),
        accountId   = 1
    )

    private fun insertAndLoad(description: String = "Despesa lock test"): Transaction {
        val id = repo.insert(baseExpense(description))
        return repo.findById(id)!!
    }

    private fun payment(txId: Int) = TransactionPayment(
        id              = 0,
        transactionId   = txId,
        paymentDate     = LocalDate.of(2026, 8, 15),
        accountId       = 1,
        principalAmount = Money.fromString("500.00"),
        createdAt       = LocalDateTime.of(2026, 8, 15, 10, 0)
    )

    private fun versionInDb(id: Int): Int = transaction {
        FinancialTransactionsTable
            .select(FinancialTransactionsTable.version)
            .where { FinancialTransactionsTable.id eq id }
            .single()[FinancialTransactionsTable.version]
    }

    private fun descriptionInDb(id: Int): String = transaction {
        FinancialTransactionsTable
            .select(FinancialTransactionsTable.description)
            .where { FinancialTransactionsTable.id eq id }
            .single()[FinancialTransactionsTable.description]
    }

    private fun isActiveInDb(id: Int): Boolean = transaction {
        FinancialTransactionsTable
            .select(FinancialTransactionsTable.isActive)
            .where { FinancialTransactionsTable.id eq id }
            .single()[FinancialTransactionsTable.isActive]
    }

    private fun paymentCountInDb(txId: Int): Long = transaction {
        TransactionPaymentsTable.selectAll()
            .where { TransactionPaymentsTable.transactionId eq txId }
            .count()
    }

    // ── update com version correta ────────────────────────────────────────────

    @Test
    fun `LOCK-01 update com version correta grava e incrementa version`() {
        val tx = insertAndLoad()
        assertEquals(0, tx.version)

        repo.update(tx.copy(description = "Atualizado"))

        assertEquals(1, versionInDb(tx.id))
        assertEquals("Atualizado", descriptionInDb(tx.id))
    }

    // ── update com version obsoleta ───────────────────────────────────────────

    @Test
    fun `LOCK-02 update com version obsoleta lanca excecao e nao altera o registro`() {
        val tx = insertAndLoad()
        // Simula escrita concorrente: incrementa version no banco sem passar pelo repo
        transaction {
            FinancialTransactionsTable.update({ FinancialTransactionsTable.id eq tx.id }) {
                it[FinancialTransactionsTable.version] = 1
            }
        }
        // tx ainda carrega version=0 — stale
        val ex = assertThrows<ConcurrentModificationException> {
            repo.update(tx.copy(description = "Não deve gravar"))
        }
        assertTrue(ex.message!!.contains(tx.id.toString()))
        assertEquals("Despesa lock test", descriptionInDb(tx.id)) // intacto
    }

    // ── ler-ler-gravar-gravar ────────────────────────────────────────────────

    @Test
    fun `LOCK-03 duas leituras gravar com a primeira faz a segunda falhar`() {
        val id = repo.insert(baseExpense())

        val snapshotA = repo.findById(id)!! // version=0
        val snapshotB = repo.findById(id)!! // version=0 (mesmo estado)

        // A grava primeiro — DB passa para version=1
        repo.update(snapshotA.copy(description = "Escritor A"))
        assertEquals(1, versionInDb(id))

        // B tenta com version=0 stale → falha
        assertThrows<ConcurrentModificationException> {
            repo.update(snapshotB.copy(description = "Escritor B"))
        }
        // Dado de A preservado
        assertEquals("Escritor A", descriptionInDb(id))
    }

    // ── updateWithPayment: falha NÃO insere baixa ─────────────────────────────

    @Test
    fun `LOCK-04 updateWithPayment com version obsoleta nao insere a baixa`() {
        val tx = insertAndLoad()
        transaction {
            FinancialTransactionsTable.update({ FinancialTransactionsTable.id eq tx.id }) {
                it[FinancialTransactionsTable.version] = 1
            }
        }

        val paid = tx.copy(status = TransactionStatus.PAID, paidAmount = Money.fromString("500.00"))
        assertThrows<ConcurrentModificationException> {
            repo.updateWithPayment(paid, payment(tx.id))
        }
        assertEquals(0L, paymentCountInDb(tx.id))      // nenhuma baixa inserida
        assertEquals(TransactionStatus.PENDING.name,    // status intacto
            transaction {
                FinancialTransactionsTable
                    .select(FinancialTransactionsTable.status)
                    .where { FinancialTransactionsTable.id eq tx.id }
                    .single()[FinancialTransactionsTable.status]
            }
        )
    }

    // ── deactivate com version correta ────────────────────────────────────────

    @Test
    fun `LOCK-05 deactivate com version correta cancela e incrementa version`() {
        val tx = insertAndLoad()
        repo.deactivate(tx.id, tx.version)

        assertFalse(isActiveInDb(tx.id))
        assertEquals(1, versionInDb(tx.id))
    }

    // ── deactivate com version obsoleta ──────────────────────────────────────

    @Test
    fun `LOCK-06 deactivate com version obsoleta lanca excecao e titulo permanece ativo`() {
        val tx = insertAndLoad()
        transaction {
            FinancialTransactionsTable.update({ FinancialTransactionsTable.id eq tx.id }) {
                it[FinancialTransactionsTable.version] = 1
            }
        }

        val ex = assertThrows<ConcurrentModificationException> {
            repo.deactivate(tx.id, 0) // stale
        }
        assertTrue(ex.message!!.contains(tx.id.toString()))
        assertTrue(isActiveInDb(tx.id))   // permanece ativo
        assertEquals(1, versionInDb(tx.id)) // version inalterada pelo deactivate
    }

    // ── cancelFutureByRecurrenceTemplate lê version atual e incrementa por linha ──

    @Test
    fun `LOCK-07 cancelFutureByRecurrenceTemplate cancela e incrementa version por linha`() {
        val idA = repo.insert(
            baseExpense("Recurrence A").copy(recurrenceTemplateId = 99, status = TransactionStatus.PENDING)
        )
        val idB = repo.insert(
            baseExpense("Recurrence B").copy(recurrenceTemplateId = 99, status = TransactionStatus.PENDING)
        )

        repo.cancelFutureByRecurrenceTemplate(99, LocalDate.of(2026, 1, 1))

        assertFalse(isActiveInDb(idA))
        assertFalse(isActiveInDb(idB))
        assertEquals(1, versionInDb(idA))
        assertEquals(1, versionInDb(idB))
        // Um registro com status incompatível (PAID) não é tocado pelo bulk-cancel
        val idPaid = repo.insert(
            baseExpense("Paid — intocado").copy(
                recurrenceTemplateId = 99,
                status = TransactionStatus.PAID
            )
        )
        // Versão do título PAID permanece 0 após um segundo cancelamento em massa
        repo.cancelFutureByRecurrenceTemplate(99, LocalDate.of(2026, 1, 1))
        assertEquals(0, versionInDb(idPaid))
        assertTrue(isActiveInDb(idPaid))
    }
}

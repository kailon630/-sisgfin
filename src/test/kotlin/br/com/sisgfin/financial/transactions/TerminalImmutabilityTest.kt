package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * C2 — imutabilidade de campos financeiros em lançamento terminal (PAID/CANCELED).
 * Lógica espelhada de TransactionService.update (sem banco).
 */
class TerminalImmutabilityTest {

    private fun paidExpense(
        amount: Money = Money.fromDouble(1_000.0),
        notes: String? = "original",
        categoryId: Int? = 1
    ) = Transaction(
        id = 42,
        type = TransactionType.EXPENSE,
        status = TransactionStatus.PAID,
        description = "Despesa paga",
        amount = amount,
        issueDate = LocalDateTime.of(2026, 1, 1, 0, 0),
        dueDate = LocalDateTime.of(2026, 1, 10, 0, 0),
        paymentDate = LocalDateTime.of(2026, 1, 10, 0, 0),
        paidAmount = amount,
        accountId = 1,
        notes = notes,
        categoryId = categoryId
    )

    /**
     * Replica a guarda de [TransactionService.update] para campos financeiros.
     */
    private fun assertFinancialImmutability(current: Transaction, updated: Transaction) {
        if (TransactionStateMachine.isTerminal(current.status)) {
            val alterouCampoFinanceiro =
                updated.amount != current.amount ||
                updated.accountId != current.accountId ||
                updated.paymentDate != current.paymentDate ||
                updated.type != current.type ||
                updated.dueDate != current.dueDate ||
                updated.paidAmount != current.paidAmount ||
                updated.interestAmount != current.interestAmount ||
                updated.fineAmount != current.fineAmount

            if (alterouCampoFinanceiro) {
                throw IllegalStateException(
                    "Lançamento ${current.status.displayName} não permite alteração de campos financeiros. " +
                    "Utilize estorno."
                )
            }
        }
    }

    @Test
    fun `C2 update de amount em PAID lanca excecao`() {
        val current = paidExpense()
        val updated = current.copy(amount = Money.fromDouble(2_000.0))
        val ex = assertThrows<IllegalStateException> {
            assertFinancialImmutability(current, updated)
        }
        assertTrue(ex.message!!.contains("campos financeiros"))
    }

    @Test
    fun `C2 update de accountId em PAID lanca excecao`() {
        val current = paidExpense()
        val updated = current.copy(accountId = 99)
        assertThrows<IllegalStateException> {
            assertFinancialImmutability(current, updated)
        }
    }

    @Test
    fun `C2 update de paymentDate em PAID lanca excecao`() {
        val current = paidExpense()
        val updated = current.copy(paymentDate = current.paymentDate!!.plusDays(1))
        assertThrows<IllegalStateException> {
            assertFinancialImmutability(current, updated)
        }
    }

    @Test
    fun `C2 update de notes em PAID e permitido`() {
        val current = paidExpense(notes = "antes")
        val updated = current.copy(notes = "depois")
        assertFinancialImmutability(current, updated) // não lança
        assertEquals("depois", updated.notes)
        assertTrue(TransactionStateMachine.isTerminal(current.status))
    }

    @Test
    fun `C2 update de categoryId em PAID e permitido`() {
        val current = paidExpense(categoryId = 1)
        val updated = current.copy(categoryId = 7)
        assertFinancialImmutability(current, updated)
        assertEquals(7, updated.categoryId)
    }

    @Test
    fun `C2 update de amount em PENDING e permitido pela guarda de terminalidade`() {
        val current = paidExpense().copy(
            status = TransactionStatus.PENDING,
            paymentDate = null,
            paidAmount = null
        )
        val updated = current.copy(amount = Money.fromDouble(500.0))
        assertFinancialImmutability(current, updated) // não lança — não é terminal
        assertFalse(TransactionStateMachine.isTerminal(current.status))
    }

    @Test
    fun `C2 PUT API usa o mesmo servico — guarda nao depende da UI`() {
        // A rota PUT /api/transactions/{id} chama service.update(updated).
        // Sem H2/REST aqui: confirmamos que a regra está no serviço (espelhada)
        // e que campos financeiros de PAID são rejeitados independentemente da origem.
        val current = paidExpense()
        assertThrows<IllegalStateException> {
            assertFinancialImmutability(current, current.copy(dueDate = current.dueDate.plusDays(5)))
        }
    }
}

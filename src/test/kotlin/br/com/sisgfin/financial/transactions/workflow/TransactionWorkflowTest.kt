package br.com.sisgfin.financial.transactions.workflow

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionWorkflowTest {

    @Test
    fun `test state machine transitions`() {
        // Valid transitions
        TransactionStateMachine.assertTransition(TransactionStatus.PENDING, TransactionStatus.PAID)
        TransactionStateMachine.assertTransition(TransactionStatus.PENDING, TransactionStatus.PARTIAL)
        TransactionStateMachine.assertTransition(TransactionStatus.PENDING, TransactionStatus.CANCELED)
        TransactionStateMachine.assertTransition(TransactionStatus.OVERDUE, TransactionStatus.PAID)

        // Invalid transitions
        assertThrows<IllegalStateException> {
            TransactionStateMachine.assertTransition(TransactionStatus.PAID, TransactionStatus.PENDING)
        }
        assertThrows<IllegalStateException> {
            TransactionStateMachine.assertTransition(TransactionStatus.CANCELED, TransactionStatus.PENDING)
        }
    }

    @Test
    fun `test overdue engine`() {
        val base = Transaction(
            description = "Test",
            amount = Money.fromDouble(100.0),
            status = TransactionStatus.PENDING,
            type = TransactionType.EXPENSE,
            issueDate = LocalDateTime.now().minusDays(10),
            dueDate = LocalDateTime.now().minusDays(1),
            accountId = 1
        )

        assertTrue(OverdueEngine.shouldMarkOverdue(base))

        val updated = OverdueEngine.applyOverdueStatus(base)
        assertEquals(TransactionStatus.OVERDUE, updated.status)

        val notOverdue = base.copy(dueDate = LocalDateTime.now().plusDays(1))
        assertFalse(OverdueEngine.shouldMarkOverdue(notOverdue))
    }

    @Test
    fun `test partial payment status logic in service would be here but we test the math`() {
        val amount = Money.fromDouble(100.0)
        val paidFull = Money.fromDouble(100.0)
        val paidPartial = Money.fromDouble(40.0)

        assertTrue(paidFull >= amount)
        assertFalse(paidPartial >= amount)
    }
}

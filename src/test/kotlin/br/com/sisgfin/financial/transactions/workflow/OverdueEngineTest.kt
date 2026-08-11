package br.com.sisgfin.financial.transactions.workflow

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverdueEngineTest {

    private val today = LocalDate.now()

    private fun base(
        status: TransactionStatus = TransactionStatus.PENDING,
        dueDate: LocalDateTime = LocalDateTime.now().minusDays(1),
        isActive: Boolean = true
    ) = Transaction(
        description = "Test",
        amount = Money.fromDouble(100.0),
        status = status,
        type = TransactionType.INCOME,
        issueDate = LocalDateTime.now().minusDays(10),
        dueDate = dueDate,
        accountId = 1,
        isActive = isActive
    )

    // T1: PENDING, vencimento ontem → OVERDUE
    @Test
    fun `T1 PENDING dueDate ontem deve marcar OVERDUE`() {
        val tx = base(dueDate = LocalDateTime.now().minusDays(1))
        assertTrue(OverdueEngine.shouldMarkOverdue(tx, today))
    }

    // T2: PENDING, vencimento amanhã → não OVERDUE
    @Test
    fun `T2 PENDING dueDate amanha nao deve marcar OVERDUE`() {
        val tx = base(dueDate = LocalDateTime.now().plusDays(1))
        assertFalse(OverdueEngine.shouldMarkOverdue(tx, today))
    }

    // T3: PENDING, vencimento hoje → não OVERDUE (isBefore estrito)
    @Test
    fun `T3 PENDING dueDate hoje nao deve marcar OVERDUE pois isBefore e estrito`() {
        val tx = base(dueDate = LocalDateTime.now())
        assertFalse(OverdueEngine.shouldMarkOverdue(tx, today))
    }

    // T4: PAID, vencimento ontem → não OVERDUE
    @Test
    fun `T4 PAID dueDate ontem nao deve marcar OVERDUE`() {
        val tx = base(status = TransactionStatus.PAID, dueDate = LocalDateTime.now().minusDays(1))
        assertFalse(OverdueEngine.shouldMarkOverdue(tx, today))
    }

    // T5: CANCELED, vencimento ontem → não OVERDUE
    @Test
    fun `T5 CANCELED dueDate ontem nao deve marcar OVERDUE`() {
        val tx = base(status = TransactionStatus.CANCELED, dueDate = LocalDateTime.now().minusDays(1))
        assertFalse(OverdueEngine.shouldMarkOverdue(tx, today))
    }

    // T6: PENDING, isActive=false, vencimento ontem → não OVERDUE
    @Test
    fun `T6 PENDING isActive false dueDate ontem nao deve marcar OVERDUE`() {
        val tx = base(dueDate = LocalDateTime.now().minusDays(1), isActive = false)
        assertFalse(OverdueEngine.shouldMarkOverdue(tx, today))
    }
}

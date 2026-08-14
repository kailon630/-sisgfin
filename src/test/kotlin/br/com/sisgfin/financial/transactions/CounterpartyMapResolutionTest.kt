package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T-07 — CounterpartyMap.nameFor() resolve funcionário (employeeId)
 * e fornecedor (supplierId); documenta fix do TransactionDetailsPanel
 * que antes usava suppliers.find{} perdendo casos de folha de pagamento.
 */
class CounterpartyMapResolutionTest {

    private fun tx(id: Int, supplierId: Int? = null, employeeId: Int? = null) = Transaction(
        id          = id,
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PENDING,
        description = "Tx $id",
        amount      = Money.fromString("100.00"),
        issueDate   = LocalDateTime.now(),
        dueDate     = LocalDateTime.now().plusDays(7),
        accountId   = 1,
        supplierId  = supplierId,
        employeeId  = employeeId
    )

    private val map = CounterpartyMap(
        suppliers = mapOf(10 to "Fornecedor Alpha"),
        employees = mapOf(20 to "Maria Folha")
    )

    @Test
    fun `nameFor retorna nome do fornecedor quando supplierId definido`() {
        assertEquals("Fornecedor Alpha", map.nameFor(tx(1, supplierId = 10)))
    }

    @Test
    fun `nameFor retorna nome do funcionario quando apenas employeeId definido`() {
        // Correção T-07: suppliers.find{} retornava null para transações de folha
        assertEquals("Maria Folha", map.nameFor(tx(2, employeeId = 20)))
    }

    @Test
    fun `nameFor prefere supplierId quando ambos definidos`() {
        assertEquals("Fornecedor Alpha", map.nameFor(tx(3, supplierId = 10, employeeId = 20)))
    }

    @Test
    fun `nameFor retorna null quando nenhum id definido`() {
        assertNull(map.nameFor(tx(4)))
    }

    @Test
    fun `nameFor retorna null quando id nao existe no mapa`() {
        assertNull(map.nameFor(tx(5, supplierId = 999)))
    }

    @Test
    fun `EMPTY map retorna null para qualquer transacao`() {
        assertNull(CounterpartyMap.EMPTY.nameFor(tx(6, supplierId = 10, employeeId = 20)))
    }
}

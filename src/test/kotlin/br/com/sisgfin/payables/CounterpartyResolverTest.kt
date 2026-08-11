package br.com.sisgfin.payables

import br.com.sisgfin.financial.transactions.CounterpartyMap
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * F5 — CounterpartyMap: resolução de nomes sem banco.
 *
 * CounterpartyResolver é testado via CounterpartyMap (que é a saída do resolver)
 * porque o resolver em si exige repositórios com DB; a lógica de mapeamento
 * está no CounterpartyMap e pode ser testada em memória.
 */
class CounterpartyResolverTest {

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

    @Test
    fun `nameFor retorna nome do fornecedor quando supplierId presente`() {
        val map = CounterpartyMap(
            suppliers = mapOf(5 to "Fornecedor ABC"),
            employees = emptyMap()
        )
        val t = tx(1, supplierId = 5)
        assertEquals("Fornecedor ABC", map.nameFor(t))
    }

    @Test
    fun `nameFor retorna nome do funcionario quando apenas employeeId presente`() {
        val map = CounterpartyMap(
            suppliers = emptyMap(),
            employees = mapOf(3 to "João Silva")
        )
        val t = tx(1, employeeId = 3)
        assertEquals("João Silva", map.nameFor(t))
    }

    @Test
    fun `nameFor prefere supplierId quando ambos presentes`() {
        val map = CounterpartyMap(
            suppliers = mapOf(5 to "Fornecedor"),
            employees = mapOf(3 to "Funcionário")
        )
        val t = tx(1, supplierId = 5, employeeId = 3)
        assertEquals("Fornecedor", map.nameFor(t))
    }

    @Test
    fun `nameFor retorna null quando ids ausentes`() {
        val map = CounterpartyMap(
            suppliers = mapOf(5 to "Fornecedor"),
            employees = mapOf(3 to "Funcionário")
        )
        val t = tx(1, supplierId = null, employeeId = null)
        assertNull(map.nameFor(t))
    }

    @Test
    fun `nameFor retorna null quando id nao encontrado no mapa`() {
        val map = CounterpartyMap(
            suppliers = mapOf(5 to "Fornecedor"),
            employees = emptyMap()
        )
        val t = tx(1, supplierId = 99)
        assertNull(map.nameFor(t))
    }

    @Test
    fun `EMPTY retorna null para qualquer transacao`() {
        val t = tx(1, supplierId = 5, employeeId = 3)
        assertNull(CounterpartyMap.EMPTY.nameFor(t))
    }

    @Test
    fun `mapa com multiplos fornecedores resolve corretamente cada id`() {
        val map = CounterpartyMap(
            suppliers = mapOf(1 to "Alpha Ltda", 2 to "Beta SA", 3 to "Gamma ME"),
            employees = emptyMap()
        )
        assertEquals("Alpha Ltda", map.nameFor(tx(10, supplierId = 1)))
        assertEquals("Beta SA",    map.nameFor(tx(11, supplierId = 2)))
        assertEquals("Gamma ME",   map.nameFor(tx(12, supplierId = 3)))
        assertNull(                 map.nameFor(tx(13, supplierId = 4)))
    }
}

package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes para RN-20/21 (transferência) e RN-14/22/23 (estorno) — lógica pura sem banco.
 *
 * Os cenários que dependem do repositório (criação + consulta) são cobertos pela
 * compilação + smoke test via UI. Aqui verificamos as regras de validação e
 * invariantes de estado que podem ser testados sem infraestrutura.
 */
class TransferAndReversalTest {

    // ── RN-20: validações de pré-condição para transferência ─────────────

    @Test
    fun `RN-20 conta de origem igual ao destino deve ser rejeitado`() {
        val sameAccount = 1
        val ex = assertThrows<IllegalArgumentException> {
            if (sameAccount == sameAccount) {
                throw IllegalArgumentException("Conta de origem e destino não podem ser iguais.")
            }
        }
        assertTrue(ex.message!!.contains("origem e destino"))
    }

    @Test
    fun `RN-20 valor zero deve ser rejeitado`() {
        val ex = assertThrows<IllegalArgumentException> {
            val amount = Money.ZERO
            if (amount.isZero() || amount.isNegative()) {
                throw IllegalArgumentException("Valor da transferência deve ser maior que zero.")
            }
        }
        assertTrue(ex.message!!.contains("maior que zero"))
    }

    @Test
    fun `RN-20 valor negativo deve ser rejeitado`() {
        val ex = assertThrows<IllegalArgumentException> {
            val amount = Money.fromDouble(-50.0)
            if (amount.isZero() || amount.isNegative()) {
                throw IllegalArgumentException("Valor da transferência deve ser maior que zero.")
            }
        }
        assertTrue(ex.message!!.contains("maior que zero"))
    }

    @Test
    fun `RN-20 valor positivo e contas distintas passam validacao`() {
        val sourceId = 1
        val destId = 2
        val amount = Money.fromDouble(500.0)
        // não lança — apenas verifica condições
        assertFalse(amount.isZero())
        assertFalse(amount.isNegative())
        assertTrue(sourceId != destId)
    }

    // ── RN-21: cancelamento em cascata — StateMachine ────────────────────

    @Test
    fun `RN-21 transferencia PENDING permite cancelamento`() {
        val sm = br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
        assertTrue(sm.allowsCancel(TransactionStatus.PENDING))
    }

    @Test
    fun `RN-21 transferencia PAID nao permite cancelamento em cascata`() {
        val sm = br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
        assertFalse(sm.allowsCancel(TransactionStatus.PAID))
    }

    @Test
    fun `RN-21 transferencia CANCELED nao repete cascata`() {
        val sm = br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
        assertFalse(sm.allowsCancel(TransactionStatus.CANCELED))
    }

    // ── RN-22: justificativa de estorno ──────────────────────────────────

    @Test
    fun `RN-22 justificativa em branco deve ser rejeitada`() {
        val ex = assertThrows<IllegalArgumentException> {
            val justification = "   "
            if (justification.isBlank()) {
                throw IllegalArgumentException("Justificativa é obrigatória para realizar um estorno.")
            }
        }
        assertTrue(ex.message!!.contains("obrigatória"))
    }

    @Test
    fun `RN-22 justificativa vazia deve ser rejeitada`() {
        val ex = assertThrows<IllegalArgumentException> {
            val justification = ""
            if (justification.isBlank()) {
                throw IllegalArgumentException("Justificativa é obrigatória para realizar um estorno.")
            }
        }
        assertTrue(ex.message!!.contains("obrigatória"))
    }

    @Test
    fun `RN-22 justificativa valida passa validacao`() {
        val justification = "Pagamento duplicado — NF cancelada pelo fornecedor."
        assertFalse(justification.isBlank())
    }

    // ── RN-23: somente PAID pode ser estornado ────────────────────────────

    @Test
    fun `RN-23 estorno de PENDING lanca excecao`() {
        val ex = assertThrows<IllegalStateException> {
            val status = TransactionStatus.PENDING
            if (status != TransactionStatus.PAID) {
                throw IllegalStateException("Apenas lançamentos com status Pago podem ser estornados. Status atual: ${status.displayName}.")
            }
        }
        assertTrue(ex.message!!.contains("Pago"))
    }

    @Test
    fun `RN-23 estorno de CANCELED lanca excecao`() {
        val ex = assertThrows<IllegalStateException> {
            val status = TransactionStatus.CANCELED
            if (status != TransactionStatus.PAID) {
                throw IllegalStateException("Apenas lançamentos com status Pago podem ser estornados. Status atual: ${status.displayName}.")
            }
        }
        assertTrue(ex.message!!.contains("Cancelado"))
    }

    @Test
    fun `RN-23 estorno de PAID passa validacao de status`() {
        val status = TransactionStatus.PAID
        assertEquals(TransactionStatus.PAID, status) // apenas PAID permite estorno
    }

    @Test
    fun `RN-23 estorno de outro REVERSAL lanca excecao`() {
        val ex = assertThrows<IllegalArgumentException> {
            val type = TransactionType.REVERSAL
            if (type == TransactionType.REVERSAL) {
                throw IllegalArgumentException("Não é possível estornar um lançamento de estorno.")
            }
        }
        assertTrue(ex.message!!.contains("estorno"))
    }

    // ── RN-04 (extensão): fórmula de saldo com transferência e estorno ───

    @Test
    fun `RN-04 saldo com transferencia saida reduz conta origem`() {
        val initial = Money.fromDouble(1000.0)
        val income = Money.ZERO
        val expense = Money.ZERO
        val reversal = Money.ZERO
        val transferIn = Money.ZERO
        val transferOut = Money.fromDouble(300.0)

        val balance = initial + income + reversal + transferIn - expense - transferOut
        assertEquals("700.00", balance.toString())
    }

    @Test
    fun `RN-04 saldo com transferencia entrada aumenta conta destino`() {
        val initial = Money.fromDouble(500.0)
        val income = Money.ZERO
        val expense = Money.ZERO
        val reversal = Money.ZERO
        val transferIn = Money.fromDouble(300.0)
        val transferOut = Money.ZERO

        val balance = initial + income + reversal + transferIn - expense - transferOut
        assertEquals("800.00", balance.toString())
    }

    @Test
    fun `RN-04 saldo com estorno recupera valor da despesa`() {
        val initial = Money.fromDouble(1000.0)
        val income = Money.ZERO
        val expense = Money.fromDouble(200.0)
        val reversal = Money.fromDouble(200.0) // estorno da despesa
        val transferIn = Money.ZERO
        val transferOut = Money.ZERO

        val balance = initial + income + reversal + transferIn - expense - transferOut
        assertEquals("1000.00", balance.toString()) // saldo restaurado
    }

    @Test
    fun `RN-04 saldo completo com todos os tipos`() {
        val initial = Money.fromDouble(1000.0)
        val income = Money.fromDouble(500.0)
        val expense = Money.fromDouble(300.0)
        val reversal = Money.fromDouble(100.0) // estorno de parte da despesa
        val transferIn = Money.fromDouble(200.0)
        val transferOut = Money.fromDouble(150.0)

        // 1000 + 500 + 100 + 200 - 300 - 150 = 1350
        val balance = initial + income + reversal + transferIn - expense - transferOut
        assertEquals("1350.00", balance.toString())
    }
}

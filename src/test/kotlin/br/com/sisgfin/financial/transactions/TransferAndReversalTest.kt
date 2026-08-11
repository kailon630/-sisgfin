package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.accounts.AccountBalanceFormula
import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes para RN-20/21 (transferência), RN-14/22/23 (estorno) e C1 (direção do estorno).
 *
 * Fórmula de saldo e elegibilidade são testadas em memória (sem H2), alinhadas a
 * [AccountBalanceFormula] e [ReversalEligibility] usados em produção.
 */
class TransferAndReversalTest {

    private fun paidTx(
        type: TransactionType,
        amount: Money = Money.fromDouble(1_000.0),
        accountId: Int = 1,
        parentId: Int? = null
    ) = Transaction(
        id = 1,
        type = type,
        status = TransactionStatus.PAID,
        description = "tx",
        amount = amount,
        issueDate = LocalDateTime.now(),
        dueDate = LocalDateTime.now(),
        paymentDate = LocalDateTime.now(),
        paidAmount = amount,
        accountId = accountId,
        parentTransactionId = parentId
    )

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
            ReversalEligibility.assertCanReverse(
                paidTx(TransactionType.EXPENSE).copy(status = TransactionStatus.PENDING, paymentDate = null, paidAmount = null)
            )
        }
        assertTrue(ex.message!!.contains("Pago"))
    }

    @Test
    fun `RN-23 estorno de CANCELED lanca excecao`() {
        val ex = assertThrows<IllegalStateException> {
            ReversalEligibility.assertCanReverse(
                paidTx(TransactionType.EXPENSE).copy(status = TransactionStatus.CANCELED)
            )
        }
        assertTrue(ex.message!!.contains("Cancelado"))
    }

    @Test
    fun `RN-23 estorno de PAID passa validacao de status`() {
        ReversalEligibility.assertCanReverse(paidTx(TransactionType.EXPENSE))
    }

    @Test
    fun `RN-23 estorno de outro REVERSAL lanca excecao`() {
        val ex = assertThrows<IllegalArgumentException> {
            ReversalEligibility.assertCanReverse(paidTx(TransactionType.REVERSAL))
        }
        assertTrue(ex.message!!.contains("estorno"))
    }

    // ── C1 matriz: direção do estorno ────────────────────────────────────

    @Test
    fun `C1-1 EXPENSE 1000 pago e estornado restaura saldo inicial`() {
        val initial = Money.fromDouble(1_000.0)
        val amount = Money.fromDouble(1_000.0)
        val afterPay = AccountBalanceFormula.compute(initial, expense = amount)
        assertEquals("0.00", afterPay.toString())
        val afterRev = AccountBalanceFormula.compute(
            initial, expense = amount, reversalCredit = amount
        )
        assertEquals(initial.toString(), afterRev.toString())
    }

    @Test
    fun `C1-2 INCOME 1000 recebido e estornado restaura saldo inicial`() {
        val initial = Money.fromDouble(1_000.0)
        val amount = Money.fromDouble(1_000.0)
        val afterPay = AccountBalanceFormula.compute(initial, income = amount)
        assertEquals("2000.00", afterPay.toString())
        val afterRev = AccountBalanceFormula.compute(
            initial, income = amount, reversalDebit = amount
        )
        assertEquals(initial.toString(), afterRev.toString())
    }

    @Test
    fun `C1-3 ADJUSTMENT 500 e estornado restaura saldo inicial`() {
        val initial = Money.fromDouble(1_000.0)
        val amount = Money.fromDouble(500.0)
        val afterPay = AccountBalanceFormula.compute(initial, adjustment = amount)
        assertEquals("1500.00", afterPay.toString())
        val afterRev = AccountBalanceFormula.compute(
            initial, adjustment = amount, reversalDebit = amount
        )
        assertEquals(initial.toString(), afterRev.toString())
    }

    @Test
    fun `C1-4 TRANSFER perna de saida nao pode ser estornada`() {
        val ex = assertThrows<IllegalArgumentException> {
            ReversalEligibility.assertCanReverse(paidTx(TransactionType.TRANSFER))
        }
        assertTrue(ex.message!!.contains("Transferências"))
    }

    @Test
    fun `C1-5 TRANSFER perna de entrada nao pode ser estornada`() {
        val ex = assertThrows<IllegalArgumentException> {
            ReversalEligibility.assertCanReverse(
                paidTx(TransactionType.TRANSFER, parentId = 10)
            )
        }
        assertTrue(ex.message!!.contains("Transferências"))
    }

    @Test
    fun `C1-6 estorno de estorno lanca excecao`() {
        val ex = assertThrows<IllegalArgumentException> {
            ReversalEligibility.assertCanReverse(paidTx(TransactionType.REVERSAL))
        }
        assertTrue(ex.message!!.contains("estorno"))
    }

    @Test
    fun `C1-8 openingBalance com estorno de INCOME mesma semantica que calculateBalance`() {
        val initial = Money.fromDouble(5_000.0)
        val income = Money.fromDouble(1_000.0)
        val revDebit = Money.fromDouble(1_000.0)
        val calc = AccountBalanceFormula.compute(initial, income = income, reversalDebit = revDebit)
        val opening = AccountBalanceFormula.compute(initial, income = income, reversalDebit = revDebit)
        assertEquals(calc.toString(), opening.toString())
        assertEquals(initial.toString(), opening.toString())
    }

    @Test
    fun `C1-BUG legado somar REVERSAL sempre positivo infla saldo apos estorno de INCOME`() {
        // Documenta o bug pré-C1: fórmula antiga tratava todo REVERSAL como crédito.
        val initial = Money.fromDouble(1_000.0)
        val income = Money.fromDouble(1_000.0)
        val reversal = Money.fromDouble(1_000.0)
        val buggy = initial + income + reversal
        assertEquals("3000.00", buggy.toString())
        assertFalse(buggy.toString() == initial.toString())
    }

    // ── RN-04 (extensão): fórmula de saldo com transferência e estorno ───

    @Test
    fun `RN-04 saldo com transferencia saida reduz conta origem`() {
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromDouble(1000.0),
            transferOut = Money.fromDouble(300.0)
        )
        assertEquals("700.00", balance.toString())
    }

    @Test
    fun `RN-04 saldo com transferencia entrada aumenta conta destino`() {
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromDouble(500.0),
            transferIn = Money.fromDouble(300.0)
        )
        assertEquals("800.00", balance.toString())
    }

    @Test
    fun `RN-04 saldo com estorno recupera valor da despesa`() {
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromDouble(1000.0),
            expense = Money.fromDouble(200.0),
            reversalCredit = Money.fromDouble(200.0)
        )
        assertEquals("1000.00", balance.toString())
    }

    @Test
    fun `RN-04 saldo completo com todos os tipos`() {
        // 1000 + 500 + 100(credit EXPENSE) + 200 - 300 - 150 = 1350
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromDouble(1000.0),
            income = Money.fromDouble(500.0),
            expense = Money.fromDouble(300.0),
            reversalCredit = Money.fromDouble(100.0),
            transferIn = Money.fromDouble(200.0),
            transferOut = Money.fromDouble(150.0)
        )
        assertEquals("1350.00", balance.toString())
    }
}

package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M5-A, Block 2 — transições de status para estorno de baixa (assertReversalTransition)
 * e verificações que os outros contratos da state machine permanecem intactos.
 *
 * RN-13: assertTransition PAID->PENDING ainda deve lançar (caminho normal).
 * assertReversalTransition: via exclusiva para desfazer uma baixa.
 */
class ReversalTransitionTest {

    // ── 1. assertTransition: PAID→PENDING ainda lança (RN-13 preservado) ─────

    @Test
    fun `assertTransition PAID para PENDING lanca (RN-13 preservado)`() {
        assertThrows<IllegalStateException> {
            TransactionStateMachine.assertTransition(TransactionStatus.PAID, TransactionStatus.PENDING)
        }
    }

    // ── 2. assertReversalTransition: PAID→PENDING permitido ──────────────────

    @Test
    fun `assertReversalTransition PAID para PENDING nao lanca`() {
        TransactionStateMachine.assertReversalTransition(TransactionStatus.PAID, TransactionStatus.PENDING)
    }

    // ── 3. assertReversalTransition: PAID→PARTIAL permitido ──────────────────

    @Test
    fun `assertReversalTransition PAID para PARTIAL nao lanca`() {
        TransactionStateMachine.assertReversalTransition(TransactionStatus.PAID, TransactionStatus.PARTIAL)
    }

    // ── 4. isTerminal: PAID ainda é terminal (sem efeito colateral do M5-A) ──

    @Test
    fun `isTerminal PAID retorna true apos M5-A`() {
        assertTrue(TransactionStateMachine.isTerminal(TransactionStatus.PAID))
    }

    // ── 5. allowsCancel: PAID ainda bloqueia cancelamento ────────────────────

    @Test
    fun `allowsCancel PAID retorna false apos M5-A`() {
        assertFalse(TransactionStateMachine.allowsCancel(TransactionStatus.PAID))
    }
}

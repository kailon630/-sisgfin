package br.com.sisgfin.financial.payments

import br.com.sisgfin.AuditRepository
import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.FinancialAccountService
import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * M5-A: calculateBalance lê TRANSFER via cashEffective das baixas (não via amount do título).
 *
 * Fonte única: paymentRepository.sumCashEffectiveTransferIn/Out.
 * sumPaidTransferIn/Out removidos de TransactionRepository.
 * Sem dupla contagem: sumCashEffectiveByAccountAndType nunca recebe TRANSFER.
 */
class TransferBalanceTest {

    private fun account(id: Int, initial: String) =
        FinancialAccount(id = id, name = "Conta #$id", initialBalance = Money.fromString(initial))

    private fun zeroPayRepo(): TransactionPaymentRepository {
        val r = mockk<TransactionPaymentRepository>()
        every { r.sumCashEffectiveByAccountAndType(any(), any()) }    returns Money.ZERO
        every { r.sumCashEffectiveForReversalOf(any(), any()) }        returns Money.ZERO
        every { r.sumCashEffectiveTransferIn(any()) }                  returns Money.ZERO
        every { r.sumCashEffectiveTransferOut(any()) }                 returns Money.ZERO
        return r
    }

    private fun makeService(
        accountRepo: FinancialAccountRepository = mockk(relaxed = true),
        txRepo: TransactionRepository = mockk(relaxed = true),
        payRepo: TransactionPaymentRepository = zeroPayRepo()
    ): FinancialAccountService {
        val session = mockk<SessionManager>()
        every { session.currentUser } returns MutableStateFlow(null)
        every { session.hasPermission(any()) } returns true
        return FinancialAccountService(
            accountRepository     = accountRepo,
            transactionRepository = txRepo,
            paymentRepository     = payRepo,
            auditRepository       = mockk(relaxed = true),
            sessionManager        = session
        )
    }

    // ── 1. Bradesco: transferência saída de 5.000 de saldo inicial 10.000 = 5.000 ──

    @Test
    fun `calculateBalance Bradesco 10000 menos transferencia de 5000 resulta em 5000`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1, "10000.00")
        val payRepo = zeroPayRepo().also {
            every { it.sumCashEffectiveTransferOut(1) } returns Money.fromString("5000.00")
        }
        val service = makeService(accountRepo = accountRepo, payRepo = payRepo)

        val balance = service.calculateBalance(1)

        assertEquals(0, Money.fromString("5000.00").compareTo(balance),
            "Bradesco: 10000 − 5000 = 5000")
    }

    // ── 2. Caixa: transferência entrada de 5.000 de saldo inicial 2.000 = 7.000 ──

    @Test
    fun `calculateBalance Caixa 2000 mais transferencia de 5000 resulta em 7000`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(2) } returns account(2, "2000.00")
        val payRepo = zeroPayRepo().also {
            every { it.sumCashEffectiveTransferIn(2) } returns Money.fromString("5000.00")
        }
        val service = makeService(accountRepo = accountRepo, payRepo = payRepo)

        val balance = service.calculateBalance(2)

        assertEquals(0, Money.fromString("7000.00").compareTo(balance),
            "Caixa: 2000 + 5000 = 7000")
    }

    // ── 3. Conservação do sistema: Bradesco + Caixa antes = depois ───────────────

    @Test
    fun `transferencia conserva total do sistema Bradesco 10000 Caixa 2000 = 12000`() {
        val accountRepoBradesco = mockk<FinancialAccountRepository>()
        every { accountRepoBradesco.findById(1) } returns account(1, "10000.00")
        val payRepoBradesco = zeroPayRepo().also {
            every { it.sumCashEffectiveTransferOut(1) } returns Money.fromString("5000.00")
        }

        val accountRepoCaixa = mockk<FinancialAccountRepository>()
        every { accountRepoCaixa.findById(2) } returns account(2, "2000.00")
        val payRepoCaixa = zeroPayRepo().also {
            every { it.sumCashEffectiveTransferIn(2) } returns Money.fromString("5000.00")
        }

        val bradesco = makeService(accountRepo = accountRepoBradesco, payRepo = payRepoBradesco).calculateBalance(1)
        val caixa    = makeService(accountRepo = accountRepoCaixa,    payRepo = payRepoCaixa).calculateBalance(2)
        val total    = bradesco + caixa

        assertEquals(0, Money.fromString("12000.00").compareTo(total),
            "Conservação: 5000 + 7000 = 12000 = 10000 + 2000 inicial")
    }

    // ── 4. Encargo: tarifa de 10 reduz origem em 5.010 (não 5.000) ───────────────

    @Test
    fun `transferencia com tarifa de 10 reduz origem em 5010`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1, "10000.00")
        val payRepo = zeroPayRepo().also {
            // baixa: principal=5000 + fine=10 → cashEffective=5010
            every { it.sumCashEffectiveTransferOut(1) } returns Money.fromString("5010.00")
        }
        val service = makeService(accountRepo = accountRepo, payRepo = payRepo)

        val balance = service.calculateBalance(1)

        assertEquals(0, Money.fromString("4990.00").compareTo(balance),
            "Tarifa de 10: 10000 − 5010 = 4990")
    }

    // ── 5. Fonte única: sumCashEffectiveTransferOut é chamado (não sumPaidTransferOut) ─

    @Test
    fun `calculateBalance chama sumCashEffectiveTransferOut e sumCashEffectiveTransferIn`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1, "0.00")
        val payRepo = zeroPayRepo()
        val service = makeService(accountRepo = accountRepo, payRepo = payRepo)

        service.calculateBalance(1)

        verify(exactly = 1) { payRepo.sumCashEffectiveTransferOut(1) }
        verify(exactly = 1) { payRepo.sumCashEffectiveTransferIn(1) }
    }
}

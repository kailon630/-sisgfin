package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * M5-B Block 2 — Q-02: saldo devedor alimentado ao diálogo de quitação.
 *
 * `PaymentRecordDialog` pré-preenche com `item.outstandingPrincipal`.
 * Estes testes verificam que a propriedade retorna o valor correto
 * para cada status — garantindo que o pré-preenchimento será correto.
 *
 * B2-01: PENDING → outstanding == totalAmount (campo pré-preenchido com total)
 * B2-02: PARTIAL 1000 com 300 pagos → outstanding == 700
 * B2-03: encargos não reduzem outstanding (só principal importa)
 */
class SaldoDevedorTest {

    private fun tx(
        amount: String,
        paidAmount: String? = null,
        interestAmount: String? = null,
        fineAmount: String? = null,
        status: TransactionStatus = TransactionStatus.PENDING
    ) = Transaction(
        type            = TransactionType.EXPENSE,
        status          = status,
        description     = "Test",
        amount          = Money.fromString(amount),
        paidAmount      = paidAmount?.let { Money.fromString(it) },
        interestAmount  = interestAmount?.let { Money.fromString(it) },
        fineAmount      = fineAmount?.let { Money.fromString(it) },
        issueDate       = LocalDateTime.now(),
        dueDate         = LocalDateTime.now().plusDays(7),
        accountId       = 1
    )

    // ── B2-01 ────────────────────────────────────────────────────────────────

    @Test
    fun `B2-01 PENDING - outstandingPrincipal igual ao valor total`() {
        val t = tx("500.00")
        assertEquals(
            Money.fromString("500.00"),
            t.outstandingPrincipal,
            "PENDING: campo deve ser pré-preenchido com o valor total"
        )
    }

    // ── B2-02 ────────────────────────────────────────────────────────────────

    @Test
    fun `B2-02 PARTIAL 1000 com 300 pagos - outstandingPrincipal e 700`() {
        val t = tx("1000.00", paidAmount = "300.00", status = TransactionStatus.PARTIAL)
        assertEquals(
            Money.fromString("700.00"),
            t.outstandingPrincipal,
            "PARTIAL: campo deve ser pré-preenchido com o saldo devedor (700)"
        )
    }

    // ── B2-03 ────────────────────────────────────────────────────────────────

    @Test
    fun `B2-03 encargos nao alteram o saldo devedor do principal`() {
        // paidAmount = 300 (principal puro), interestAmount = 50 (separado)
        // principalPaid = paidAmount = 300 → outstanding = 1000 - 300 = 700
        val t = tx(
            "1000.00",
            paidAmount     = "300.00",
            interestAmount = "50.00",
            status = TransactionStatus.PARTIAL
        )
        assertEquals(
            Money.fromString("700.00"),
            t.outstandingPrincipal,
            "Encargos em coluna separada não alteram o saldo devedor do principal"
        )
    }
}

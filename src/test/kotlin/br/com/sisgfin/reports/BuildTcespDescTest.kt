package br.com.sisgfin.reports

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class BuildTcespDescTest {

    private fun tx(
        type: TransactionType = TransactionType.EXPENSE,
        documentType: String? = null,
        documentNumber: String? = null
    ) = Transaction(
        type           = type,
        status         = TransactionStatus.PAID,
        description    = "Descricao",
        amount         = Money.fromString("100.00"),
        issueDate      = LocalDateTime.now(),
        dueDate        = LocalDateTime.now(),
        accountId      = 1,
        documentType   = documentType,
        documentNumber = documentNumber
    )

    @Test
    fun `EXPENSE com creditorName retorna prefixo PAGO A`() {
        val result = buildTcespDesc(tx(), "Fornecedor ACME")
        assertEquals("PAGO A, FORNECEDOR ACME", result)
    }

    @Test
    fun `INCOME com creditorName retorna prefixo RECEBIDO DE`() {
        val result = buildTcespDesc(tx(type = TransactionType.INCOME), "Cliente XYZ")
        assertEquals("RECEBIDO DE, CLIENTE XYZ", result)
    }

    @Test
    fun `REVERSAL com creditorName retorna prefixo RECEBIDO DE`() {
        val result = buildTcespDesc(tx(type = TransactionType.REVERSAL), "Fornecedor ACME")
        assertEquals("RECEBIDO DE, FORNECEDOR ACME", result)
    }

    @Test
    fun `EXPENSE com creditorName nulo retorna CREDOR NAO IDENTIFICADO`() {
        val result = buildTcespDesc(tx(), null)
        assertEquals("PAGO A, CREDOR NÃO IDENTIFICADO", result)
    }

    @Test
    fun `INCOME com creditorName nulo retorna CREDOR NAO IDENTIFICADO`() {
        val result = buildTcespDesc(tx(type = TransactionType.INCOME), null)
        assertEquals("RECEBIDO DE, CREDOR NÃO IDENTIFICADO", result)
    }

    @Test
    fun `creditorName minusculo e convertido para maiusculo`() {
        val result = buildTcespDesc(tx(), "fornecedor acme")
        assertEquals("PAGO A, FORNECEDOR ACME", result)
    }

    @Test
    fun `documentType e documentNumber ambos presentes aparecem no sufixo`() {
        val result = buildTcespDesc(tx(documentType = "NF", documentNumber = "123"), "Empresa")
        assertEquals("PAGO A, EMPRESA CF NF 123", result)
    }

    @Test
    fun `apenas documentNumber presente usa prefixo CF DOC`() {
        val result = buildTcespDesc(tx(documentNumber = "456"), "Empresa")
        assertEquals("PAGO A, EMPRESA CF DOC 456", result)
    }

    // ── Ordinal de pagamento (M4b) ────────────────────────────────────────────

    @Test
    fun `baixa unica sem sufixo - ordinal 1 total 1`() {
        val result = buildTcespDesc(tx(), "Empresa", paymentOrdinal = 1, totalPayments = 1)
        assertEquals("PAGO A, EMPRESA", result)
    }

    @Test
    fun `baixa 2 de 3 gera sufixo PAGTO 2 barra 3`() {
        val result = buildTcespDesc(tx(), "Empresa", paymentOrdinal = 2, totalPayments = 3)
        assertEquals("PAGO A, EMPRESA (PAGTO 2/3)", result)
    }

    @Test
    fun `total desconhecido titulo PARTIAL gera sufixo sem denominador`() {
        val result = buildTcespDesc(tx(), "Empresa", paymentOrdinal = 2, totalPayments = null)
        assertEquals("PAGO A, EMPRESA (PAGTO 2)", result)
    }

    @Test
    fun `titulo parcela e duas baixas - sufixo docNumber e PAGTO coexistem`() {
        val result = buildTcespDesc(
            tx(documentType = "NF", documentNumber = "123-PARC-2/3"),
            "Empresa",
            paymentOrdinal = 2,
            totalPayments  = 2
        )
        assert(result.contains("NF 123-PARC-2/3")) { "esperado documentNumber em '$result'" }
        assert(result.contains("(PAGTO 2/2)"))     { "esperado ordinal em '$result'" }
    }

    @Test
    fun `primeira baixa de tres tem sufixo PAGTO 1 barra 3`() {
        val result = buildTcespDesc(tx(), "Empresa", paymentOrdinal = 1, totalPayments = 3)
        assertEquals("PAGO A, EMPRESA (PAGTO 1/3)", result)
    }

    @Test
    fun `prefixo credor e CF preservados com ordinal`() {
        val result = buildTcespDesc(
            tx(type = TransactionType.INCOME, documentType = "REC", documentNumber = "99"),
            "Cliente ABC",
            paymentOrdinal = 1,
            totalPayments  = 2
        )
        assertEquals("RECEBIDO DE, CLIENTE ABC CF REC 99 (PAGTO 1/2)", result)
    }
}

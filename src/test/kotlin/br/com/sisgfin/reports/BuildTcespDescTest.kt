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
}

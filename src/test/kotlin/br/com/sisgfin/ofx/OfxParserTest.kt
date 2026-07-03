package br.com.sisgfin.ofx

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfxParserTest {

    private val parser = OfxParser()

    // ── Documento OFX mínimo para reutilização nos testes ────────────────────

    private fun ofxDocument(transactions: String, dtStart: String = "20260101", dtEnd: String = "20260131") = """
OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE
<OFX>
<BANKMSGSRSV1>
 <STMTTRNRS>
  <STMTRS>
   <CURDEF>BRL
   <BANKACCTFROM>
    <BANKID>001
    <ACCTID>501145-0
   </BANKACCTFROM>
   <BANKTRANLIST>
    <DTSTART>$dtStart
    <DTEND>$dtEnd
$transactions
   </BANKTRANLIST>
  </STMTRS>
 </STMTTRNRS>
</BANKMSGSRSV1>
</OFX>
""".trimIndent()

    // ── 1. DEP positivo ──────────────────────────────────────────────────────

    @Test
    fun `DEP positivo e parseado como entrada com isInflow true`() {
        val content = ofxDocument("""
   <STMTTRN>
    <TRNTYPE>DEP
    <DTPOSTED>20260102
    <TRNAMT>330.00
    <FITID>20260102033000
    <CHECKNUM>20820528072252
    <MEMO>PIX - RECEBIDO - 02/01 08:20 MARILDA ALV
   </STMTTRN>
""")
        val stmt = parser.parse(content)

        assertEquals(1, stmt.transactions.size)
        val trn = stmt.transactions[0]
        assertEquals(OfxTrnType.DEP, trn.type)
        assertEquals(LocalDate.of(2026, 1, 2), trn.date)
        assertEquals(0, BigDecimal("330.00").compareTo(trn.amount.value))
        assertEquals("20260102033000", trn.fitId)
        assertEquals("20820528072252", trn.checkNum)
        assertTrue(trn.memo.contains("PIX - RECEBIDO"))
        assertTrue(trn.isInflow)
    }

    // ── 2. DEBIT negativo ────────────────────────────────────────────────────

    @Test
    fun `DEBIT negativo e parseado como saida com isInflow false`() {
        val content = ofxDocument("""
   <STMTTRN>
    <TRNTYPE>DEBIT
    <DTPOSTED>20260103
    <TRNAMT>-188.67
    <FITID>20260103118867
    <CHECKNUM>10202
    <MEMO>PAGAMENTO DE BOLETO - EMPRESA LTDA
   </STMTTRN>
""")
        val stmt = parser.parse(content)

        assertEquals(1, stmt.transactions.size)
        val trn = stmt.transactions[0]
        assertEquals(OfxTrnType.DEBIT, trn.type)
        assertEquals(LocalDate.of(2026, 1, 3), trn.date)
        assertEquals(0, BigDecimal("-188.67").compareTo(trn.amount.value))
        assertFalse(trn.isInflow)
    }

    // ── 3. XFER positivo (entrada) e negativo (saída) ────────────────────────

    @Test
    fun `XFER positivo e tratado como entrada`() {
        val content = ofxDocument("""
   <STMTTRN>
    <TRNTYPE>XFER
    <DTPOSTED>20260105
    <TRNAMT>220.00
    <FITID>20260105022000
    <CHECKNUM>605966000000100
    <MEMO>TRANSFERENCIA RECEBIDA - 05/01 PRISCILA
   </STMTTRN>
""")
        val trn = parser.parse(content).transactions.first()
        assertEquals(OfxTrnType.XFER, trn.type)
        assertTrue(trn.isInflow)
        assertEquals(0, BigDecimal("220.00").compareTo(trn.amount.value))
    }

    @Test
    fun `XFER negativo e tratado como saida`() {
        val content = ofxDocument("""
   <STMTTRN>
    <TRNTYPE>XFER
    <DTPOSTED>20260130
    <TRNAMT>-1177.78
    <FITID>202601301117778
    <CHECKNUM>557088008076520
    <MEMO>TRANSFERENCIA ENVIADA - 30/01 PAULO CESAR
   </STMTTRN>
""")
        val trn = parser.parse(content).transactions.first()
        assertEquals(OfxTrnType.XFER, trn.type)
        assertFalse(trn.isInflow)
        assertEquals(0, BigDecimal("-1177.78").compareTo(trn.amount.value))
    }

    // ── 4. Encoding Windows-1252 com caracteres acentuados ───────────────────

    @Test
    fun `encoding windows-1252 preserva acentuacao em MEMO`(@TempDir tempDir: Path) {
        // Escreve bytes W-1252 diretamente: TRANSFERÊNCIA e ANTECIPAÇÃO
        val memoW1252 = "TRANSFERÊNCIA RECEBIDA - PIX".toByteArray(OfxParser.CHARSET)
        val memoAntecip = "CIELO ANTECIPAÇÃO DE RECE".toByteArray(OfxParser.CHARSET)

        val bodyTemplate = """
OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE
<OFX>
<BANKMSGSRSV1>
 <STMTTRNRS>
  <STMTRS>
   <BANKTRANLIST>
    <DTSTART>20260101
    <DTEND>20260131
    <STMTTRN>
     <TRNTYPE>XFER
     <DTPOSTED>20260105
     <TRNAMT>220.00
     <FITID>FID001
     <MEMO>""".trimIndent()

        val closingTemplate = """

    </STMTTRN>
    <STMTTRN>
     <TRNTYPE>DEP
     <DTPOSTED>20260110
     <TRNAMT>500.00
     <FITID>FID002
     <MEMO>""".trimIndent()

        val finalClose = """

    </STMTTRN>
   </BANKTRANLIST>
  </STMTRS>
 </STMTTRNRS>
</BANKMSGSRSV1>
</OFX>""".trimIndent()

        val file = tempDir.resolve("extrato.ofx").toFile()
        file.outputStream().use { out ->
            out.write(bodyTemplate.toByteArray(OfxParser.CHARSET))
            out.write(memoW1252)
            out.write(closingTemplate.toByteArray(OfxParser.CHARSET))
            out.write(memoAntecip)
            out.write(finalClose.toByteArray(OfxParser.CHARSET))
        }

        val stmt = parser.parse(file)
        assertEquals(2, stmt.transactions.size)
        assertTrue(stmt.transactions[0].memo.contains("TRANSFERÊNCIA"),
            "Esperado TRANSFERÊNCIA mas foi: '${stmt.transactions[0].memo}'")
        assertTrue(stmt.transactions[1].memo.contains("ANTECIPAÇÃO"),
            "Esperado ANTECIPAÇÃO mas foi: '${stmt.transactions[1].memo}'")
    }

    // ── 5. FITID duplicado no mesmo arquivo ──────────────────────────────────

    @Test
    fun `FITID duplicado no mesmo arquivo e incluido duas vezes pelo parser`() {
        // Deduplicação é responsabilidade do OfxImportService (via DB), não do parser
        val content = ofxDocument("""
   <STMTTRN>
    <TRNTYPE>DEP
    <DTPOSTED>20260102
    <TRNAMT>100.00
    <FITID>FITID_DUPLICADO
    <MEMO>PRIMEIRA OCORRENCIA
   </STMTTRN>
   <STMTTRN>
    <TRNTYPE>DEP
    <DTPOSTED>20260103
    <TRNAMT>200.00
    <FITID>FITID_DUPLICADO
    <MEMO>SEGUNDA OCORRENCIA
   </STMTTRN>
""")
        val stmt = parser.parse(content)
        assertEquals(2, stmt.transactions.size)
        assertTrue(stmt.transactions.all { it.fitId == "FITID_DUPLICADO" })
    }

    // ── 6. Tag sem valor (malformado) ────────────────────────────────────────

    @Test
    fun `transacao com FITID vazio e ignorada silenciosamente`() {
        val content = ofxDocument("""
   <STMTTRN>
    <TRNTYPE>DEP
    <DTPOSTED>20260102
    <TRNAMT>330.00
    <FITID>
    <MEMO>SEM FITID
   </STMTTRN>
""")
        // FITID em branco → transação inválida, não deve ser incluída
        val stmt = parser.parse(content)
        assertEquals(0, stmt.transactions.size)
    }

    @Test
    fun `transacao com TRNAMT ausente e ignorada silenciosamente`() {
        val content = ofxDocument("""
   <STMTTRN>
    <TRNTYPE>DEP
    <DTPOSTED>20260102
    <FITID>FID999
    <MEMO>SEM VALOR
   </STMTTRN>
""")
        val stmt = parser.parse(content)
        assertEquals(0, stmt.transactions.size)
    }

    // ── 7. Metadados do extrato ──────────────────────────────────────────────

    @Test
    fun `metadados bankId acctId e periodo sao parseados corretamente`() {
        val content = ofxDocument("", dtStart = "20260101", dtEnd = "20260131")
        val stmt = parser.parse(content)

        assertEquals("001", stmt.bankId)
        assertEquals("501145-0", stmt.acctId)
        assertEquals("BRL", stmt.currency)
        assertEquals(LocalDate.of(2026, 1, 1), stmt.dtStart)
        assertEquals(LocalDate.of(2026, 1, 31), stmt.dtEnd)
    }

    // ── 8. Tags com fechamento XML opcional ──────────────────────────────────

    @Test
    fun `tag com fechamento XML opcional e parseada corretamente`() {
        // Alguns campos no SGML 102 podem ter a tag de fechamento por outros geradores
        val content = ofxDocument("""
   <STMTTRN>
    <TRNTYPE>DEP</TRNTYPE>
    <DTPOSTED>20260115</DTPOSTED>
    <TRNAMT>500.00</TRNAMT>
    <FITID>CLOSED_TAG_TEST</FITID>
    <MEMO>TAG COM FECHAMENTO</MEMO>
   </STMTTRN>
""")
        val stmt = parser.parse(content)
        assertEquals(1, stmt.transactions.size)
        val trn = stmt.transactions[0]
        assertEquals("CLOSED_TAG_TEST", trn.fitId)
        assertEquals(LocalDate.of(2026, 1, 15), trn.date)
        assertEquals(0, BigDecimal("500.00").compareTo(trn.amount.value))
    }

    // ── 9. Arquivo OFX real ──────────────────────────────────────────────────

    @Test
    fun `arquivo OFX real janeiro 2026 parseia 1241 transacoes`() {
        val file = File("docs/01 2026 Extrato65205011450.ofx")
        if (!file.exists()) return // skip se não estiver disponível no ambiente de CI

        val stmt = parser.parse(file)
        assertEquals("001", stmt.bankId)
        assertEquals(1241, stmt.transactions.size)
        assertTrue(stmt.transactions.any { it.memo.contains("TRANSFERÊNCIA") },
            "Esperado memo com acento TRANSFERÊNCIA")
        assertTrue(stmt.transactions.any { it.memo.contains("ANTECIPAÇÃO") },
            "Esperado memo com acento ANTECIPAÇÃO")
        assertTrue(stmt.transactions.any { it.type == OfxTrnType.DEP })
        assertTrue(stmt.transactions.any { it.type == OfxTrnType.DEBIT })
        assertTrue(stmt.transactions.any { it.type == OfxTrnType.XFER })
    }

    // ── 10. CHECKNUM ausente → null ──────────────────────────────────────────

    @Test
    fun `CHECKNUM ausente resulta em null`() {
        val content = ofxDocument("""
   <STMTTRN>
    <TRNTYPE>DEP
    <DTPOSTED>20260102
    <TRNAMT>100.00
    <FITID>FID_NO_CHECK
    <MEMO>SEM CHECKNUM
   </STMTTRN>
""")
        val trn = parser.parse(content).transactions.first()
        assertNull(trn.checkNum)
    }
}

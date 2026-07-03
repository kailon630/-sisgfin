package br.com.sisgfin.ofx

import br.com.sisgfin.financial.money.Money
import java.io.File
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Parser de arquivos OFX SGML versão 102 (Banco do Brasil / padrão antigo).
 *
 * O formato SGML 102 é diferente do XML: tags folha não possuem tag de fechamento,
 * o valor vem imediatamente após o `>` na mesma linha, com possível lixo/espaços ao final.
 * Exemplo:  <TRNAMT>-330.00<whitespace>
 *
 * Encoding: os arquivos do BB declaram CHARSET:1252 e devem ser lidos com windows-1252
 * para preservar acentuação (TRANSFERÊNCIA, ANTECIPAÇÃO etc.).
 */
class OfxParser {

    companion object {
        val CHARSET: Charset = Charset.forName("windows-1252")
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    /** Lê e decodifica o arquivo com windows-1252, depois faz o parse. */
    fun parse(file: File): OfxStatement = parse(file.readText(CHARSET))

    /**
     * Parseia o conteúdo OFX já decodificado para String.
     * Exposto separado para facilitar testes sem I/O.
     */
    fun parse(content: String): OfxStatement {
        var bankId   = ""
        var acctId   = ""
        var currency = "BRL"
        var dtStart  = LocalDate.MIN
        var dtEnd    = LocalDate.MAX

        val transactions = mutableListOf<OfxTransaction>()

        // Acumuladores do bloco <STMTTRN> em andamento
        var inTrn    = false
        var trnType  : OfxTrnType? = null
        var dtPosted : LocalDate?  = null
        var rawAmt   : BigDecimal? = null
        var fitId    : String?     = null
        var checkNum : String?     = null
        var memo     : String?     = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                // ── Abre bloco de transação ─────────────────────────────────
                line.startsWith("<STMTTRN>") -> {
                    inTrn    = true
                    trnType  = null; dtPosted = null; rawAmt = null
                    fitId    = null; checkNum = null; memo   = null
                }

                // ── Fecha bloco: valida e emite transação ───────────────────
                line.startsWith("</STMTTRN>") -> {
                    if (inTrn) {
                        val id   = fitId
                        val date = dtPosted
                        val amt  = rawAmt
                        val type = trnType
                        if (id != null && id.isNotBlank() && date != null && amt != null && type != null) {
                            transactions += OfxTransaction(
                                fitId    = id,
                                type     = type,
                                date     = date,
                                amount   = Money(amt),
                                checkNum = checkNum?.ifBlank { null },
                                memo     = memo?.trim() ?: ""
                            )
                        }
                        inTrn = false
                    }
                }

                // ── Dentro de uma transação: extrai cada campo ──────────────
                inTrn -> when {
                    line.startsWith("<TRNTYPE>")  -> trnType  = parseType(tagValue(line))
                    line.startsWith("<DTPOSTED>") -> dtPosted = parseDate(tagValue(line))
                    line.startsWith("<TRNAMT>")   -> rawAmt   = tagValue(line).toBigDecimalOrNull()
                    line.startsWith("<FITID>")    -> fitId    = tagValue(line)
                    line.startsWith("<CHECKNUM>") -> checkNum = tagValue(line)
                    line.startsWith("<MEMO>")     -> memo     = tagValue(line)
                }

                // ── Fora de transação: metadados do extrato ─────────────────
                else -> when {
                    line.startsWith("<BANKID>")  -> bankId   = tagValue(line)
                    line.startsWith("<ACCTID>")  -> acctId   = tagValue(line)
                    line.startsWith("<CURDEF>")  -> currency = tagValue(line)
                    line.startsWith("<DTSTART>") -> dtStart  = parseDate(tagValue(line))
                    line.startsWith("<DTEND>")   -> dtEnd    = parseDate(tagValue(line))
                }
            }
        }

        return OfxStatement(bankId, acctId, currency, dtStart, dtEnd, transactions)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Extrai o valor de uma tag SGML 102.
     * Suporta tanto a forma aberta  `<TAG>valor`
     * quanto a forma fechada        `<TAG>valor</TAG>`.
     * Remove espaços e tabs ao redor do valor.
     */
    private fun tagValue(line: String): String {
        val gt = line.indexOf('>')
        if (gt < 0) return ""
        val raw = line.substring(gt + 1)
        val lt  = raw.indexOf('<')
        return if (lt >= 0) raw.substring(0, lt).trim() else raw.trim()
    }

    /**
     * Parseia data no formato YYYYMMDD.
     * Alguns bancos acrescentam hora: YYYYMMDDHHMMSS[.xxx][+TZ] — apenas os 8 primeiros caracteres são usados.
     * Retorna [LocalDate.MIN] se o valor for inválido em vez de lançar exceção.
     */
    private fun parseDate(value: String): LocalDate {
        return try {
            LocalDate.parse(value.take(8), DATE_FMT)
        } catch (_: DateTimeParseException) {
            LocalDate.MIN
        }
    }

    private fun parseType(value: String): OfxTrnType = when (value.uppercase()) {
        "DEP"   -> OfxTrnType.DEP
        "DEBIT" -> OfxTrnType.DEBIT
        "XFER"  -> OfxTrnType.XFER
        else    -> OfxTrnType.OTHER
    }
}

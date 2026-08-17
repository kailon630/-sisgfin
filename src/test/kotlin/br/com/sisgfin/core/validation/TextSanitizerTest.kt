package br.com.sisgfin.core.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T-19 — testes unitarios do TextSanitizer.
 * Nenhum acesso a BD; verificam apenas transformacoes de string.
 */
class TextSanitizerTest {

    // ── clean() ───────────────────────────────────────────────────────────────

    @Test
    fun `SANIT-01 null retorna null`() {
        assertNull(TextSanitizer.clean(null))
    }

    @Test
    fun `SANIT-02 string limpa retorna intacta`() {
        assertEquals("RICHARD EDUARDO INACIO DA SILVA", TextSanitizer.clean("RICHARD EDUARDO INACIO DA SILVA"))
    }

    @Test
    fun `SANIT-03 CR no final e removido`() {
        assertEquals("AUXILIAR DE EQUOTERAPIA", TextSanitizer.clean("AUXILIAR DE EQUOTERAPIA\r"))
    }

    @Test
    fun `SANIT-04 CR no meio e removido e espacos colapsados`() {
        assertEquals("NOME SOBRENOME", TextSanitizer.clean("NOME\rSOBRENOME"))
    }

    @Test
    fun `SANIT-05 CRLF no meio e removido`() {
        assertEquals("LINHA UM LINHA DOIS", TextSanitizer.clean("LINHA UM\r\nLINHA DOIS"))
    }

    @Test
    fun `SANIT-06 tab e removido`() {
        assertEquals("CAMPO VALOR", TextSanitizer.clean("CAMPO\tVALOR"))
    }

    @Test
    fun `SANIT-07 NBSP convertido para espaco e colapsado`() {
        val nbsp = " "
        assertEquals("NOME SILVA", TextSanitizer.clean("NOME${nbsp}SILVA"))
    }

    @Test
    fun `SANIT-08 zero-width chars removidos`() {
        // U+200B entre letras — deve desaparecer sem adicionar espaco
        val zwsp = "​"
        assertEquals("ABC", TextSanitizer.clean("A${zwsp}B${zwsp}C"))
    }

    @Test
    fun `SANIT-09 BOM removido`() {
        val bom = "﻿"
        assertEquals("NOME", TextSanitizer.clean("${bom}NOME"))
    }

    @Test
    fun `SANIT-10 espacos multiplos internos colapsados`() {
        assertEquals("NOME DUPLO ESPACO", TextSanitizer.clean("NOME  DUPLO   ESPACO"))
    }

    @Test
    fun `SANIT-11 trim nas extremidades`() {
        assertEquals("NOME", TextSanitizer.clean("  NOME  "))
    }

    @Test
    fun `SANIT-12 acento e caixa preservados`() {
        assertEquals("João SILVÉRIO", TextSanitizer.clean("João SILVÉRIO"))
    }

    @Test
    fun `SANIT-13 string com apenas controles retorna vazia`() {
        assertEquals("", TextSanitizer.clean("\r\n\t"))
    }

    @Test
    fun `SANIT-14 string vazia retorna vazia`() {
        assertEquals("", TextSanitizer.clean(""))
    }

    // ── cleanPreserveNewlines() ───────────────────────────────────────────────

    @Test
    fun `SANIT-15 cleanPreserveNewlines mantem LF interno`() {
        val input = "Linha 1\nLinha 2"
        assertEquals("Linha 1\nLinha 2", TextSanitizer.cleanPreserveNewlines(input))
    }

    @Test
    fun `SANIT-16 cleanPreserveNewlines normaliza CRLF para LF`() {
        val input = "Linha 1\r\nLinha 2"
        assertEquals("Linha 1\nLinha 2", TextSanitizer.cleanPreserveNewlines(input))
    }

    @Test
    fun `SANIT-17 cleanPreserveNewlines remove CR isolado`() {
        val input = "Linha 1\rLinha 2"
        assertEquals("Linha 1Linha 2", TextSanitizer.cleanPreserveNewlines(input))
    }

    @Test
    fun `SANIT-18 cleanPreserveNewlines mantem tab interno`() {
        val input = "Col1\tCol2"
        assertEquals("Col1\tCol2", TextSanitizer.cleanPreserveNewlines(input))
    }

    @Test
    fun `SANIT-19 cleanPreserveNewlines null retorna null`() {
        assertNull(TextSanitizer.cleanPreserveNewlines(null))
    }
}

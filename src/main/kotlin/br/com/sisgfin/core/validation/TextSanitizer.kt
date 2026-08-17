package br.com.sisgfin.core.validation

/**
 * Remove caracteres de controle de campos de texto livre antes de persistir.
 *
 * Alcance de [clean]:
 *  - Substitui C0 (U+0000-U+001F) e C1 (U+007F-U+009F) por espaco, incluindo \r, \n, \t.
 *    Para nome e funcao, quebras de linha sao artefatos de celula multi-linha do SCI/Excel.
 *  - Substitui U+00A0 (NBSP) por espaco normal.
 *  - Remove U+200B, U+200C, U+200D, U+FEFF (largura zero).
 *  - Colapsa sequencias de espacos para um unico espaco; trim nas extremidades.
 *  - NAO altera caixa nem remove acentos ou Unicode imprimivel.
 *
 * Para campos onde \n interno pode ser intencional (notes, description),
 * use [cleanPreserveNewlines].
 */
object TextSanitizer {

    // C0 (U+0000-U+001F) + DEL (U+007F) + C1 (U+0080-U+009F)
    private val CONTROLS = Regex("[\u0000-\u001F\u007F-\u009F]")

    // zero-width: U+200B, U+200C, U+200D, U+FEFF
    private val ZERO_WIDTH = Regex("[\u200B\u200C\u200D\uFEFF]")

    private val MULTI_SPACE = Regex(" {2,}")

    /**
     * Limpa campos de texto livre onde quebras de linha sao artefatos:
     * nome, funcao, email, fornecedor, etc.
     * Retorna null se [raw] for null.
     */
    fun clean(raw: String?): String? {
        raw ?: return null
        return raw
            .replace(ZERO_WIDTH, "")
            .replace('\u00A0', ' ')      // NBSP -> espaco normal
            .replace(CONTROLS, " ")        // controles -> espaco
            .replace(MULTI_SPACE, " ")     // colapsa multiplos espacos
            .trim()
            .ifEmpty { "" }
    }

    /**
     * Limpa campos onde \n interno pode ser intencional (notes, description).
     * Remove \r, C0/C1 exceto \n (U+000A) e \t (U+0009); normaliza CRLF para LF.
     */
    fun cleanPreserveNewlines(raw: String?): String? {
        raw ?: return null
        // Remove C0 sem HT (U+0009) e LF (U+000A); DEL + C1
        val controlsExceptNewlineTab = Regex("[\u0000-\u0008\u000B-\u001F\u007F-\u009F]")
        return raw
            .replace(ZERO_WIDTH, "")
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace("\r", "")
            .replace(controlsExceptNewlineTab, " ")
            .trim()
            .ifEmpty { "" }
    }
}

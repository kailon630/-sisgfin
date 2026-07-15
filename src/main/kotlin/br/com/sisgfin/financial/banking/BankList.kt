package br.com.sisgfin.financial.banking

object BankList {
    data class Bank(val code: String, val name: String)

    val banks = listOf(
        Bank("001", "Banco do Brasil"),
        Bank("033", "Santander"),
        Bank("041", "Banrisul"),
        Bank("070", "BRB — Banco de Brasília"),
        Bank("077", "Banco Inter"),
        Bank("084", "Uniprime Norte do Paraná"),
        Bank("085", "CECRED — Cooperativa Central de Crédito"),
        Bank("104", "Caixa Econômica Federal"),
        Bank("133", "Cresol"),
        Bank("136", "Unicred"),
        Bank("208", "BTG Pactual"),
        Bank("237", "Bradesco"),
        Bank("260", "Nu Pagamentos (Nubank)"),
        Bank("290", "PagBank"),
        Bank("318", "Banco BMG"),
        Bank("336", "Banco C6"),
        Bank("341", "Itaú"),
        Bank("389", "Banco Mercantil do Brasil"),
        Bank("422", "Banco Safra"),
        Bank("655", "Votorantim"),
        Bank("707", "Banco Daycoval"),
        Bank("748", "Sicredi"),
        Bank("756", "Sicoob"),
    )

    fun findByCode(code: String): Bank? = banks.find { it.code == code }

    fun displayName(code: String): String =
        findByCode(code)?.let { "${it.code} — ${it.name}" } ?: code

    fun allAsOptions(): List<Pair<Int, String>> =
        banks.mapIndexed { i, b -> i to "${b.code} — ${b.name}" }

    fun codeByIndex(index: Int): String? = banks.getOrNull(index)?.code

    fun indexByCode(code: String): Int? =
        banks.indexOfFirst { it.code == code }.takeIf { it >= 0 }
}

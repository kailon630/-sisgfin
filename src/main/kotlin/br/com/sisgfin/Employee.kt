package br.com.sisgfin

import br.com.sisgfin.core.domain.Identifiable
import br.com.sisgfin.financial.money.Money
import java.time.LocalDateTime

data class Employee(
    override val id: Int = 0,
    val name: String,
    val document: String,
    val phone: String,
    val email: String,
    val role: String,
    val salary: Money,
    val paymentDay: Int,
    val paymentDays: String? = null,
    val employmentType: String? = null,
    val bankCode: String? = null,
    val agencyNumber: String? = null,
    val agencyDv: String? = null,
    val accountNumber: String? = null,
    val accountDv: String? = null,
    val accountType: String? = "CS",
    val active: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now()
) : Identifiable {

    val formattedAgency: String?
        get() = if (!agencyNumber.isNullOrBlank() && !agencyDv.isNullOrBlank())
            "$agencyNumber-$agencyDv" else agencyNumber

    val formattedAccount: String?
        get() = if (!accountNumber.isNullOrBlank() && !accountDv.isNullOrBlank())
            "$accountNumber-$accountDv" else accountNumber

    val hasBankingData: Boolean
        get() = !bankCode.isNullOrBlank() && !agencyNumber.isNullOrBlank() && !accountNumber.isNullOrBlank()

    // Retorna a lista de dias efetivos para geração automática.
    // paymentDays ("5,20") tem precedência; paymentDay é fallback quando paymentDays está nulo.
    // Retorna emptyList() quando não há configuração — significa: sem auto-geração.
    fun effectivePaymentDays(): List<Int> {
        val raw = paymentDays?.trim()
        if (!raw.isNullOrEmpty()) {
            return raw.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..31 }
                .distinct()
                .sorted()
        }
        return emptyList()
    }
}

enum class EmploymentType(val label: String) {
    CLT("CLT"),
    PJ("PJ"),
    ESTAGIO("Estágio"),
    OUTROS("Outros")
}

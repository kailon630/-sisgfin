package br.com.sisgfin.contracts

import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionRepository
import java.math.BigDecimal
import java.time.LocalDateTime

data class ContractExecution(
    val totalValue: Money,
    val consumed: Money,
    val remaining: Money,
    val percentUsed: Double
)

class ContractService(
    private val repository: ContractRepository,
    private val transactionRepository: TransactionRepository,
    private val sessionManager: SessionManager
) {
    fun listAll(): List<Contract> = repository.findAll()

    fun findById(id: Int): Contract? = repository.findById(id)

    fun findActive(): List<Contract> = repository.findActive()

    fun save(contract: Contract): Int {
        require(contract.number.isNotBlank()) { "Número do contrato é obrigatório." }
        require(contract.description.isNotBlank()) { "Descrição é obrigatória." }
        require(contract.contractorId > 0) { "Contratado é obrigatório." }
        require(contract.totalValue.value > BigDecimal.ZERO) { "Valor total deve ser maior que zero." }
        if (contract.endDate != null) {
            require(!contract.endDate.isBefore(contract.startDate)) {
                "Data de encerramento deve ser posterior à data de início."
            }
        }
        if (repository.numberExists(contract.number, excludeId = contract.id)) {
            error("Já existe um contrato com o número '${contract.number}'.")
        }

        val userId = sessionManager.currentUser.value?.id
        val now    = LocalDateTime.now()
        return if (contract.id == 0) {
            repository.insert(contract.copy(createdBy = userId, createdAt = now, updatedAt = now))
        } else {
            repository.update(contract.copy(updatedAt = now))
            contract.id
        }
    }

    fun updateStatus(id: Int, newStatus: ContractStatus) {
        val contract = repository.findById(id) ?: error("Contrato #$id não encontrado.")
        if (newStatus in listOf(ContractStatus.ENCERRADO, ContractStatus.CANCELADO)) {
            require(!transactionRepository.existsPendingByContract(id)) {
                "Não é possível ${newStatus.displayName.lowercase()} um contrato com lançamentos pendentes."
            }
        }
        repository.update(contract.copy(status = newStatus, updatedAt = LocalDateTime.now()))
    }

    fun getExecutionSummary(contractId: Int): ContractExecution {
        val contract = repository.findById(contractId) ?: error("Contrato #$contractId não encontrado.")
        val consumed = transactionRepository.sumConsumedByContract(contractId)
        val remaining = Money((contract.totalValue.value - consumed.value).coerceAtLeast(BigDecimal.ZERO))
        val percentUsed = if (contract.totalValue.value > BigDecimal.ZERO)
            consumed.value.toDouble() / contract.totalValue.value.toDouble() * 100.0
        else 0.0
        return ContractExecution(contract.totalValue, consumed, remaining, percentUsed)
    }

    // Retorna true se adicionar o valor excederia o total contratado (aviso — não bloqueia)
    fun wouldExceedTotal(contractId: Int, additionalAmount: Money): Boolean {
        val contract = repository.findById(contractId) ?: return false
        val consumed = transactionRepository.sumConsumedByContract(contractId)
        return (consumed.value + additionalAmount.value) > contract.totalValue.value
    }

    fun getRecentTransactions(contractId: Int): List<Transaction> =
        transactionRepository.findByContract(contractId)
}

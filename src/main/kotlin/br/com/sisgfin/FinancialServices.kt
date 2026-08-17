package br.com.sisgfin

import br.com.sisgfin.core.domain.AuditedCrudService
import br.com.sisgfin.core.domain.MutableEntityRepository
import br.com.sisgfin.core.validation.DocumentValidator
import br.com.sisgfin.core.validation.TextSanitizer
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionType

class SupplierService(
    private val supplierRepository: SupplierRepository,
    auditRepository: AuditRepository,
    sessionManager: SessionManager
) : AuditedCrudService<Supplier>(
    repository = supplierRepository,
    auditRepository = auditRepository,
    sessionManager = sessionManager,
    entityType = "SUPPLIER",
    displayName = { it.name },
    withCreatedBy = { item, userId -> item.copy(createdBy = userId) },
    withActiveFlag = { item, active -> item.copy(isActive = active) },
    isActive = { it.isActive }
) {
    // RN-03 + RN-01
    override fun save(item: Supplier) {
        val normalized = item.copy(
            document = DocumentValidator.normalize(item.document),
            name     = TextSanitizer.clean(item.name) ?: "",
        )

        DocumentValidator.validate(normalized.document)

        val existing = supplierRepository.findByDocument(normalized.document)
        if (existing != null && existing.id != normalized.id) {
            throw IllegalArgumentException(
                "Já existe um fornecedor cadastrado com este documento (${normalized.document})."
            )
        }

        super.save(normalized)
    }
}

class FinancialAccountService(
    private val accountRepository: FinancialAccountRepository,
    private val transactionRepository: TransactionRepository,
    private val paymentRepository: TransactionPaymentRepository,
    auditRepository: AuditRepository,
    sessionManager: SessionManager
) : AuditedCrudService<FinancialAccount>(
    repository = accountRepository,
    auditRepository = auditRepository,
    sessionManager = sessionManager,
    entityType = "FINANCIAL_ACCOUNT",
    displayName = { it.name },
    withCreatedBy = { item, userId -> item.copy(createdBy = userId) },
    withActiveFlag = { item, active -> item.copy(isActive = active) },
    isActive = { it.isActive }
) {
    // RN-04 + RN-06 + C1: saldo via cashEffective das baixas (M4)
    fun calculateBalance(accountId: Int): Money {
        val account = accountRepository.findById(accountId) ?: return Money.ZERO
        val income         = paymentRepository.sumCashEffectiveByAccountAndType(accountId, TransactionType.INCOME)
        val expense        = paymentRepository.sumCashEffectiveByAccountAndType(accountId, TransactionType.EXPENSE)
        val adjustment     = paymentRepository.sumCashEffectiveByAccountAndType(accountId, TransactionType.ADJUSTMENT)
        val transferIn     = paymentRepository.sumCashEffectiveTransferIn(accountId)
        val transferOut    = paymentRepository.sumCashEffectiveTransferOut(accountId)
        val reversalCredit = paymentRepository.sumCashEffectiveForReversalOf(
            accountId, listOf(TransactionType.EXPENSE)
        )
        val reversalDebit  = paymentRepository.sumCashEffectiveForReversalOf(
            accountId, listOf(TransactionType.INCOME, TransactionType.ADJUSTMENT)
        )
        return br.com.sisgfin.financial.accounts.AccountBalanceFormula.compute(
            initialBalance = account.initialBalance,
            income         = income,
            expense        = expense,
            adjustment     = adjustment,
            transferIn     = transferIn,
            transferOut    = transferOut,
            reversalCredit = reversalCredit,
            reversalDebit  = reversalDebit
        )
    }

    // RN-05: bloqueia inativação se saldo ≠ 0
    override fun toggleActive(id: Int) {
        val account = accountRepository.findById(id) ?: return
        if (account.isActive) {
            val balance = calculateBalance(id)
            if (!balance.isZero()) {
                throw IllegalStateException(
                    "A conta \"${account.name}\" possui saldo de ${MoneyFormatter.format(balance)} " +
                    "e não pode ser inativada. Transfira ou zere o saldo antes de inativar."
                )
            }
        }
        super.toggleActive(id)
    }
}

class CostCenterService(
    private val costCenterRepository: CostCenterRepository,
    private val transactionRepository: TransactionRepository,
    private val auditRepo: AuditRepository,
    private val session: SessionManager
) : AuditedCrudService<CostCenter>(
    repository = costCenterRepository,
    auditRepository = auditRepo,
    sessionManager = session,
    entityType = "COST_CENTER",
    displayName = { it.name },
    withCreatedBy = { item, userId -> item.copy(createdBy = userId) },
    withActiveFlag = { item, active -> item.copy(isActive = active) },
    isActive = { it.isActive }
) {
    // RN-07
    fun delete(id: Int) {
        val costCenter = costCenterRepository.findById(id)
            ?: throw IllegalArgumentException("Centro de custo não encontrado.")
        if (transactionRepository.existsByCostCenterId(id)) {
            throw IllegalStateException(
                "O centro de custo \"${costCenter.name}\" possui lançamentos vinculados e não pode ser excluído. " +
                "Use Inativar para encerrá-lo sem perder o histórico."
            )
        }
        costCenterRepository.hardDelete(id)
        auditRepo.insert(
            AuditLog(
                entityType = "COST_CENTER",
                entityId = id,
                action = "DELETE",
                oldValue = costCenter.name,
                performedBy = session.currentUser.value?.id
            )
        )
    }
}

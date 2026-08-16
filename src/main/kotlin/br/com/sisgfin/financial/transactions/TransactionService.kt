package br.com.sisgfin.financial.transactions

import br.com.sisgfin.AuditLog
import br.com.sisgfin.AuditRepository
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.Permission
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.core.crud.CrudOperations
import br.com.sisgfin.financial.ledger.LedgerService
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.RoundingPolicy
import br.com.sisgfin.financial.payments.TransactionPayment
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import br.com.sisgfin.financial.transactions.timeline.TimelineEventType
import java.math.BigDecimal
import java.math.RoundingMode
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineEvent
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineRepository
import br.com.sisgfin.financial.transactions.workflow.OverdueEngine
import br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class TransactionService(
    private val repository: TransactionRepository,
    private val accountRepository: FinancialAccountRepository,
    private val supplierRepository: SupplierRepository,
    private val costCenterRepository: CostCenterRepository,
    private val auditRepository: AuditRepository,
    private val timelineRepository: TransactionTimelineRepository,
    private val sessionManager: SessionManager,
    private val ledgerService: LedgerService = LedgerService(),
    private val employeeRepository: EmployeeRepository? = null,
    private val paymentRepository: TransactionPaymentRepository? = null
) : CrudOperations<Transaction> {

    var listFilter: TransactionListFilter = TransactionListFilter.All
    var searchQuery: String? = null

    override fun listAll(): List<Transaction> {
        syncOverdueStatuses()
        val q = searchQuery?.trim()
        if (!q.isNullOrBlank()) return repository.search(q)
        return when (val f = listFilter) {
            is TransactionListFilter.All -> repository.findAll()
            TransactionListFilter.ActionRequired -> repository.filterActionRequired()
            is TransactionListFilter.ByStatus -> repository.filterByStatus(f.status)
            is TransactionListFilter.ByType -> repository.filterByType(f.type)
            TransactionListFilter.DueToday -> repository.filterDueToday()
            TransactionListFilter.Overdue -> repository.filterOverdue()
            TransactionListFilter.Paid -> repository.filterPaid()
            is TransactionListFilter.DuePeriod -> repository.filterByDuePeriod(f.from, f.to)
        }
    }

    fun findById(id: Int): Transaction? = repository.findById(id)

    // F0: ponto de leitura unificado para telas que usam TransactionQuery
    fun listByQuery(query: TransactionQuery): List<Transaction> = repository.find(query)

    fun create(transaction: Transaction): Int {
        val n = transaction.installmentTotal ?: 1
        val totalAmount = transaction.amount

        // RN-17/18: parent recebe a parcela "piso"; a última absorve o arredondamento
        val parentAmount = if (n > 1) {
            val sliceValue = totalAmount.value.divide(BigDecimal(n), RoundingPolicy.DEFAULT_SCALE, RoundingMode.FLOOR)
            Money(sliceValue)
        } else totalAmount

        val userId = sessionManager.currentUser.value?.id
        var prepared = transaction.copy(
            id = 0,
            amount = parentAmount,
            installmentCurrent = if (n > 1) 1 else transaction.installmentCurrent,
            status = if (transaction.status == TransactionStatus.DRAFT) TransactionStatus.DRAFT else transaction.status,
            createdBy = userId,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            paidAmount = null,
            paymentDate = null,
            origin = transaction.origin
        )
        if (prepared.status == TransactionStatus.DRAFT) {
            // mantém rascunho
        } else if (prepared.status !in setOf(TransactionStatus.PENDING, TransactionStatus.SCHEDULED)) {
            prepared = prepared.copy(status = TransactionStatus.PENDING)
        }
        TransactionValidator.validateForSave(prepared, existing = null)
        validateAccount(prepared.accountId)
        validateSupplier(prepared.supplierId)
        validateEmployee(prepared.employeeId)
        val id = repository.insert(prepared)
        val createdMsg = if (n > 1) "Transação criada (parcela 1/$n)" else "Transação criada"
        addTimeline(id, TimelineEventType.CREATED, createdMsg, null, null, prepared.status)
        audit("TRANSACTION_CREATED", id, auditDetail(prepared.status, null, prepared.amount))
        warnIfOutsideCostCenterPeriod(prepared, id)

        // RN-17: gerar parcelas filhas
        if (n > 1) generateInstallments(parentId = id, totalAmount = totalAmount, template = prepared)

        return id
    }

    fun update(transaction: Transaction) {
        val existing = repository.findById(transaction.id)
            ?: throw IllegalArgumentException("Transação não encontrada.")

        // T-14: lançamentos de folha não podem ter employeeId alterado
        val payrollOrigins = setOf(TransactionOrigin.PAYROLL_ENGINE, TransactionOrigin.PAYROLL_IMPORT)
        if (existing.origin in payrollOrigins && transaction.employeeId != existing.employeeId) {
            throw IllegalStateException(
                "Lançamento de folha de pagamento (origin=${existing.origin}) não permite alteração de funcionário vinculado."
            )
        }

        // T-14: supplierId e employeeId são mutuamente exclusivos
        val resolved = when {
            transaction.employeeId != null -> transaction.copy(supplierId = null)
            transaction.supplierId != null -> transaction.copy(employeeId = null)
            else -> transaction
        }

        // C2: lançamento terminal não permite alteração de campos financeiros
        if (TransactionStateMachine.isTerminal(existing.status)) {
            val alterouCampoFinanceiro =
                resolved.amount != existing.amount ||
                resolved.accountId != existing.accountId ||
                resolved.paymentDate != existing.paymentDate ||
                resolved.type != existing.type ||
                resolved.dueDate != existing.dueDate ||
                resolved.paidAmount != existing.paidAmount ||
                resolved.interestAmount != existing.interestAmount ||
                resolved.fineAmount != existing.fineAmount

            if (alterouCampoFinanceiro) {
                throw IllegalStateException(
                    "Lançamento ${existing.status.displayName} não permite alteração de campos financeiros. " +
                    "Utilize estorno."
                )
            }
        }

        TransactionValidator.validateForSave(resolved, existing)
        validateAccount(resolved.accountId)
        validateSupplier(resolved.supplierId)
        validateEmployee(resolved.employeeId)

        val statusChanged = existing.status != resolved.status
        if (statusChanged) {
            TransactionStateMachine.assertTransition(existing.status, resolved.status)
        }

        repository.update(resolved.copy(updatedAt = LocalDateTime.now()))

        val reclassMsg = buildReclassificationMessage(existing, resolved)
        addTimeline(
            resolved.id,
            TimelineEventType.UPDATED,
            reclassMsg ?: "Dados da transação atualizados",
            resolved.amount,
            existing.status,
            resolved.status
        )
        audit("TRANSACTION_UPDATED", resolved.id, auditDetail(resolved.status, existing.status, resolved.amount))
        warnIfOutsideCostCenterPeriod(resolved, resolved.id)
        if (statusChanged) {
            addTimeline(
                resolved.id,
                TimelineEventType.STATUS_CHANGED,
                "Status: ${existing.status.displayName} → ${resolved.status.displayName}",
                null,
                existing.status,
                resolved.status
            )
            audit(
                "TRANSACTION_STATUS_CHANGED",
                resolved.id,
                auditDetail(resolved.status, existing.status, resolved.amount)
            )
        }
    }

    /** Diff de campos não-financeiros para timeline (reclassificação pós-liquidação). */
    private fun buildReclassificationMessage(existing: Transaction, updated: Transaction): String? {
        val parts = mutableListOf<String>()
        if (existing.notes != updated.notes) {
            parts += "notes: '${existing.notes}' → '${updated.notes}'"
        }
        if (existing.documentNumber != updated.documentNumber) {
            parts += "documentNumber: '${existing.documentNumber}' → '${updated.documentNumber}'"
        }
        if (existing.documentType != updated.documentType) {
            parts += "documentType: '${existing.documentType}' → '${updated.documentType}'"
        }
        if (existing.categoryId != updated.categoryId) {
            parts += "categoryId: ${existing.categoryId} → ${updated.categoryId}"
        }
        if (existing.costCenterId != updated.costCenterId) {
            parts += "costCenterId: ${existing.costCenterId} → ${updated.costCenterId}"
        }
        if (existing.projectId != updated.projectId) {
            parts += "projectId: ${existing.projectId} → ${updated.projectId}"
        }
        if (existing.description != updated.description) {
            parts += "description: '${existing.description}' → '${updated.description}'"
        }
        if (parts.isEmpty()) return null
        return "Reclassificação: " + parts.joinToString("; ")
    }

    override fun save(item: Transaction) {
        if (item.id == 0) create(item) else update(item)
    }

    override fun toggleActive(id: Int) {
        cancel(id)
    }

    fun cancel(id: Int) {
        val existing = repository.findById(id) ?: return
        TransactionStateMachine.assertTransition(existing.status, TransactionStatus.CANCELED)

        // RN-21: resolve and validate transfer counterpart BEFORE any mutation.
        // Without this guard, deactivate(id) runs first and leaves the main leg
        // canceled while the sister leg stays PAID — inconsistent state.
        val transferCounterpart: Transaction? = if (existing.type == TransactionType.TRANSFER) {
            val other = if (existing.parentTransactionId != null) {
                repository.findById(existing.parentTransactionId)
                    ?.takeIf { it.type == TransactionType.TRANSFER }
            } else {
                repository.findTransferDestination(id)
            }
            other?.also { o ->
                if (!TransactionStateMachine.allowsCancel(o.status)) {
                    throw IllegalStateException(
                        "Não é possível cancelar: a outra perna desta transferência (#${o.id}) " +
                        "está com status ${o.status} e não pode ser cancelada. " +
                        "Cancelar apenas um lado deixaria o valor fora das duas contas."
                    )
                }
            }
        } else null

        repository.deactivate(id)
        addTimeline(id, TimelineEventType.CANCELED, "Transação cancelada", null, existing.status, TransactionStatus.CANCELED)
        audit("TRANSACTION_CANCELED", id, auditDetail(TransactionStatus.CANCELED, existing.status, existing.amount))
        audit("TRANSACTION_STATUS_CHANGED", id, auditDetail(TransactionStatus.CANCELED, existing.status, existing.amount))

        // RN-19: cancelar pai cancela filhos PENDING/DRAFT automaticamente
        val isInstallmentParent = (existing.installmentTotal ?: 1) > 1 && existing.parentTransactionId == null
        if (isInstallmentParent) {
            repository.findActiveChildrenOf(id).forEach { child ->
                repository.deactivate(child.id)
                addTimeline(
                    child.id, TimelineEventType.CANCELED,
                    "Cancelada em cascata — parcela ${child.installmentCurrent}/${child.installmentTotal}",
                    null, child.status, TransactionStatus.CANCELED
                )
                audit("TRANSACTION_CANCELED", child.id, auditDetail(TransactionStatus.CANCELED, child.status, child.amount))
            }
        }

        // RN-21: execute counterpart cancellation (already validated above)
        transferCounterpart?.let { other ->
            repository.deactivate(other.id)
            addTimeline(
                other.id, TimelineEventType.CANCELED,
                "Cancelada em cascata — transferência vinculada #$id",
                null, other.status, TransactionStatus.CANCELED
            )
            audit("TRANSACTION_CANCELED", other.id, auditDetail(TransactionStatus.CANCELED, other.status, other.amount))
        }
    }

    // RN-20/21: cria par TRANSFER atômico — EXPENSE na origem, INCOME no destino
    fun createTransfer(
        sourceAccountId: Int,
        destinationAccountId: Int,
        amount: Money,
        date: LocalDateTime,
        description: String,
        notes: String? = null,
        costCenterId: Int? = null,
        categoryId: Int? = null
    ): Pair<Int, Int> {
        if (sourceAccountId == destinationAccountId) {
            throw IllegalArgumentException("Conta de origem e destino não podem ser iguais.")
        }
        if (amount.isZero() || amount.isNegative()) {
            throw IllegalArgumentException("Valor da transferência deve ser maior que zero.")
        }
        validateAccount(sourceAccountId)
        validateAccount(destinationAccountId)

        val userId = sessionManager.currentUser.value?.id
        val now = LocalDateTime.now()

        val source = Transaction(
            type = TransactionType.TRANSFER,
            status = TransactionStatus.PENDING,
            amount = amount,
            description = description,
            issueDate = now,
            dueDate = date,
            accountId = sourceAccountId,
            costCenterId = costCenterId,
            categoryId = categoryId,
            notes = notes,
            createdBy = userId,
            createdAt = now,
            updatedAt = now,
            origin = TransactionOrigin.TRANSFER
        )
        val destinationTemplate = source.copy(
            accountId = destinationAccountId,
            description = "Recebimento: $description"
        )
        val (sourceId, destinationId) = repository.insertTransferPair(source, destinationTemplate)

        addTimeline(sourceId, TimelineEventType.TRANSFER_OUT,
            "Transferência de $amount enviada para conta #$destinationAccountId",
            amount, null, source.status)
        audit("TRANSFER_CREATED", sourceId, "source=$sourceAccountId;dest=$destinationAccountId;amount=$amount")

        addTimeline(destinationId, TimelineEventType.TRANSFER_IN,
            "Transferência de $amount recebida da conta #$sourceAccountId",
            amount, null, source.status)
        audit("TRANSFER_CREATED", destinationId, "source=$sourceAccountId;dest=$destinationAccountId;amount=$amount;pair=#$sourceId")

        return sourceId to destinationId
    }

    // RN-14/22/23 + C1: cria estorno de lançamento PAID com justificativa e direção
    fun reverseTransaction(originalId: Int, justification: String): Int {
        requirePermission(Permission.ConfirmPayment)
        // RN-22: justificativa obrigatória
        if (justification.isBlank()) {
            throw IllegalArgumentException("Justificativa é obrigatória para realizar um estorno.")
        }

        val original = repository.findById(originalId)
            ?: throw IllegalArgumentException("Lançamento não encontrado.")

        ReversalEligibility.assertCanReverse(original)

        if (repository.hasReversal(originalId)) {
            throw IllegalStateException("Este lançamento já possui um estorno registrado.")
        }

        val userId = sessionManager.currentUser.value?.id
        val now = LocalDateTime.now()

        val reversal = Transaction(
            id = 0,
            type = TransactionType.REVERSAL,
            status = TransactionStatus.PAID,
            amount = original.amount,
            description = "Estorno: ${original.description}",
            issueDate = now,
            dueDate = now,
            paymentDate = now,
            paidAmount = original.amount,
            accountId = original.accountId,
            supplierId = original.supplierId,
            costCenterId = original.costCenterId,
            categoryId = original.categoryId,
            notes = justification,
            parentTransactionId = originalId,
            createdBy = userId,
            createdAt = now,
            updatedAt = now,
            reversedType = original.type,
            origin = TransactionOrigin.REVERSAL
        )
        val reversalId = repository.insert(reversal)

        addTimeline(reversalId, TimelineEventType.REVERSAL_OF,
            "Estorno do lançamento #$originalId", reversal.amount, null, TransactionStatus.PAID)
        audit("TRANSACTION_REVERSAL_CREATED", reversalId,
            "original=#$originalId;reversedType=${original.type};justification=$justification;${auditDetail(TransactionStatus.PAID, null, reversal.amount)}")

        addTimeline(originalId, TimelineEventType.REVERSED,
            "Estornado — ver lançamento #$reversalId. Motivo: $justification",
            reversal.amount, TransactionStatus.PAID, TransactionStatus.PAID)
        audit("TRANSACTION_REVERSED", originalId,
            "reversalId=#$reversalId;justification=$justification")

        return reversalId
    }

    // RN-12: visível para o ViewModel controlar botões
    fun canConfirmPayment(): Boolean = sessionManager.hasPermission(Permission.ConfirmPayment)

    fun recordPayment(
        id: Int,
        paymentDate: LocalDateTime,
        paidAmount: Money,
        interestAmount: Money? = null,
        fineAmount: Money? = null,
        discountAmount: Money = Money.ZERO
    ) {
        requirePermission(Permission.ConfirmPayment)
        val existing = repository.findById(id)
            ?: throw IllegalArgumentException("Transação não encontrada.")
        if (!TransactionStateMachine.allowsPayment(existing.status)) {
            throw IllegalStateException("Status ${existing.status.displayName} não permite quitação.")
        }

        val juros = interestAmount ?: Money.ZERO
        val multa = fineAmount ?: Money.ZERO

        TransactionValidator.validatePayment(
            outstanding = existing.outstandingPrincipal,
            principal   = paidAmount,
            interest    = interestAmount,
            fine        = fineAmount,
            paymentDate = paymentDate,
            issueDate   = existing.issueDate
        )

        // Acumula sobre o que já existe — paidAmount armazena (principal + juros + multa) cumulativos.
        // discountAmount NÃO entra em paidAmount (dual-write mantém semântica atual para leituras).
        val newPaidAmount     = (existing.paidAmount     ?: Money.ZERO) + paidAmount + juros + multa
        val newInterestAmount = (existing.interestAmount ?: Money.ZERO) + juros
        val newFineAmount     = (existing.fineAmount     ?: Money.ZERO) + multa
        val newPrincipalPaid  = newPaidAmount - newInterestAmount - newFineAmount

        // D3(a): desconto conta para quitação — verifica saldo histórico de desconto se houver
        val totalDiscount = if (!discountAmount.isZero() && paymentRepository != null) {
            paymentRepository.sumDiscountByTransaction(id) + discountAmount
        } else {
            discountAmount
        }
        val principalQuitado = newPrincipalPaid + totalDiscount

        val newStatus = TransactionStateMachine.resolveStatusAfterPayment(
            existing.amount.value,
            principalQuitado.value
        )
        TransactionStateMachine.assertTransition(existing.status, newStatus)

        val cashThisBaixa = paidAmount + juros + multa
        val newOutstanding  = existing.outstandingPrincipal - paidAmount

        val updated = existing.copy(
            status         = newStatus,
            paymentDate    = paymentDate,
            paidAmount     = newPaidAmount,
            interestAmount = if (newInterestAmount.isZero()) null else newInterestAmount,
            fineAmount     = if (newFineAmount.isZero()) null else newFineAmount,
            updatedAt      = LocalDateTime.now()
        )
        TransactionValidator.validateForSave(updated, existing)

        // M2 (atômico): C-15 — update do título e insert da baixa no mesmo transaction {}.
        val userId = sessionManager.currentUser.value?.id
        if (paymentRepository != null) {
            val payment = TransactionPayment(
                transactionId   = id,
                paymentDate     = paymentDate.toLocalDate(),
                accountId       = existing.accountId,
                principalAmount = paidAmount,
                interestAmount  = juros,
                fineAmount      = multa,
                discountAmount  = discountAmount,
                idempotencyKey  = buildIdempotencyKey(id, paymentDate, paidAmount, juros, multa, discountAmount, userId),
                createdBy       = userId,
                createdAt       = LocalDateTime.now()
            )
            repository.updateWithPayment(updated, payment)
        } else {
            repository.update(updated)
        }

        ledgerService.recordPayment(updated, cashThisBaixa, paymentDate)

        val timelineType = if (newStatus == TransactionStatus.PAID) {
            TimelineEventType.PAYMENT
        } else {
            TimelineEventType.PARTIAL_PAYMENT
        }
        val timelineDesc = if (newStatus == TransactionStatus.PAID) {
            "Quitada — $cashThisBaixa"
        } else {
            "Pagamento parcial — $paidAmount (saldo devedor: $newOutstanding)"
        }
        addTimeline(id, timelineType, timelineDesc, cashThisBaixa, existing.status, newStatus)

        val auditAction = if (newStatus == TransactionStatus.PAID) "TRANSACTION_PAID" else "TRANSACTION_PARTIAL_PAYMENT"
        audit(auditAction, id, auditDetail(newStatus, existing.status, cashThisBaixa))
        audit("TRANSACTION_STATUS_CHANGED", id, auditDetail(newStatus, existing.status, cashThisBaixa))
    }

    private fun buildIdempotencyKey(
        transactionId: Int,
        paymentDate: LocalDateTime,
        principal: Money,
        interest: Money,
        fine: Money,
        discount: Money,
        userId: Int?
    ): String = "$transactionId:${paymentDate.toLocalDate()}:${principal.value}:${interest.value}:${fine.value}:${discount.value}:${userId ?: "anon"}"

    fun markAsPaid(id: Int, paymentDate: LocalDateTime = LocalDateTime.now()) {
        requirePermission(Permission.ConfirmPayment)
        val existing = repository.findById(id) ?: throw IllegalArgumentException("Transação não encontrada.")
        recordPayment(id, paymentDate, existing.outstandingPrincipal)
    }

    fun duplicate(id: Int): Int {
        val source = repository.findById(id) ?: throw IllegalArgumentException("Transação não encontrada.")
        if (source.type == TransactionType.TRANSFER) {
            throw IllegalArgumentException(
                "Transferências não podem ser duplicadas. Crie uma nova transferência pelo botão Transferência."
            )
        }
        val copy = source.copy(
            id = 0,
            status = TransactionStatus.PENDING,
            paymentDate = null,
            paidAmount = null,
            issueDate = LocalDateTime.now(),
            dueDate = source.dueDate,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            isActive = true,
            parentTransactionId = source.id,
            ledgerEntryId = null,
            origin = TransactionOrigin.DUPLICATE
        )
        val newId = create(copy)
        addTimeline(newId, TimelineEventType.DUPLICATED, "Duplicada a partir da transação #${source.id}", source.amount, null, TransactionStatus.PENDING)
        audit("TRANSACTION_DUPLICATED", newId, "sourceId=${source.id};${source.description}")
        return newId
    }

    // RN-17/18: cria parcelas 2..N; o pai já foi inserido com parcela 1 e amount=slice
    private fun generateInstallments(parentId: Int, totalAmount: Money, template: Transaction) {
        val n = template.installmentTotal ?: return
        val slice = template.amount // parcela-piso já calculada em create()
        for (i in 2..n) {
            // RN-18: última parcela absorve o arredondamento
            val childAmount = if (i == n) {
                totalAmount - (slice * BigDecimal(n - 1))
            } else {
                slice
            }
            val child = template.copy(
                id = 0,
                installmentCurrent = i,
                amount = childAmount,
                dueDate = template.dueDate.plusMonths((i - 1).toLong()),
                parentTransactionId = parentId,
                paymentDate = null,
                paidAmount = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                ledgerEntryId = null,
                origin = TransactionOrigin.INSTALLMENT
            )
            val childId = repository.insert(child)
            addTimeline(childId, TimelineEventType.CREATED, "Parcela $i/$n criada automaticamente", childAmount, null, child.status)
            audit("TRANSACTION_CREATED", childId, "installment=$i/$n;parent=#$parentId;${auditDetail(child.status, null, childAmount)}")
        }
    }

    fun getTimeline(transactionId: Int): List<TransactionTimelineEvent> =
        timelineRepository.findByTransactionId(transactionId)

    /**
     * Cria um lançamento originado de importação OFX.
     * Difere de [create]: permite status PAID diretamente, não gera parcelas,
     * não executa validação de fornecedor (OFX não tem esse dado) e registra
     * o evento de timeline como OFX_IMPORT.
     */
    fun createFromOfx(tx: Transaction): Int {
        val userId = sessionManager.currentUser.value?.id ?: tx.createdBy
        val now    = LocalDateTime.now()
        val prepared = tx.copy(id = 0, createdBy = userId, createdAt = now, updatedAt = now, origin = TransactionOrigin.OFX)
        validateAccount(prepared.accountId)
        val id = repository.insert(prepared)
        val amount = prepared.paidAmount ?: prepared.amount
        addTimeline(id, TimelineEventType.OFX_IMPORT,
            "Importado via OFX — FITID: ${tx.ofxFitId}", amount, null, prepared.status)
        audit("TRANSACTION_CREATED", id, auditDetail(prepared.status, null, amount))
        return id
    }

    /**
     * Cria um lançamento originado de importação de folha de pagamento (Fase 8-C).
     * Análogo a [createFromOfx]: status PENDING direto, sem parcelamento, sem validação de fornecedor
     * (já verificado no vínculo Employee → Supplier). Timeline marcada como PAYROLL_IMPORT.
     */
    fun createFromPayrollImport(tx: Transaction): Int {
        val userId = sessionManager.currentUser.value?.id ?: tx.createdBy
        val now = LocalDateTime.now()
        val prepared = tx.copy(id = 0, createdBy = userId, createdAt = now, updatedAt = now, origin = TransactionOrigin.PAYROLL_IMPORT)
        validateAccount(prepared.accountId)
        val id = repository.insert(prepared)
        addTimeline(id, TimelineEventType.PAYROLL_IMPORT,
            "Importado via folha de pagamento — funcionário #${tx.employeeId}",
            prepared.amount, null, prepared.status)
        audit("TRANSACTION_CREATED", id, auditDetail(prepared.status, null, prepared.amount))
        return id
    }

    /**
     * Cancela lançamentos PENDING/DRAFT/SCHEDULED do funcionário dentro do mês de referência
     * e do mês seguinte. Chamado antes de criar os lançamentos da importação para evitar duplicatas
     * com os gerados pelo [PayrollEngine] (que usa salário fixo, não o valor real da folha).
     */
    fun cancelPendingPayrollForMonth(employeeId: Int, month: YearMonth): Int {
        val toCancel = repository.findPendingPayrollForMonth(employeeId, month)
        toCancel.forEach { tx ->
            repository.deactivate(tx.id)
            val monthLabel = "${month.monthValue.toString().padStart(2, '0')}/${month.year}"
            addTimeline(tx.id, TimelineEventType.CANCELED,
                "Cancelado — substituído por importação de folha de pagamento $monthLabel",
                null, tx.status, TransactionStatus.CANCELED)
            audit("TRANSACTION_CANCELED", tx.id, auditDetail(TransactionStatus.CANCELED, tx.status, tx.amount))
        }
        return toCancel.size
    }

    /**
     * Cria um lançamento gerado pelo motor de recorrência (Fase 7-A).
     * Difere de [create]: sem enforcement de parcelamento, sem validação de fornecedor,
     * status sempre PENDING, timeline marcado como RECURRENCE_GENERATED.
     */
    fun createFromRecurrence(tx: Transaction): Int {
        val userId  = sessionManager.currentUser.value?.id ?: tx.createdBy
        val now     = LocalDateTime.now()
        val prepared = tx.copy(
            id        = 0,
            status    = TransactionStatus.PENDING,
            createdBy = userId,
            createdAt = now,
            updatedAt = now,
            paymentDate  = null,
            paidAmount   = null,
            ledgerEntryId = null,
            origin    = TransactionOrigin.RECURRENCE
        )
        validateAccount(prepared.accountId)
        val id = repository.insert(prepared)
        addTimeline(id, TimelineEventType.RECURRENCE_GENERATED,
            "Gerado automaticamente pelo template de recorrência #${tx.recurrenceTemplateId}",
            prepared.amount, null, TransactionStatus.PENDING)
        audit("TRANSACTION_CREATED", id, auditDetail(TransactionStatus.PENDING, null, prepared.amount))
        return id
    }

    /**
     * Busca lançamentos PENDING/OVERDUE na conta com o valor exato e dueDate dentro do intervalo.
     * Exclui lançamentos criados via OFX (já possuem ofxFitId) para evitar auto-conciliação.
     */
    fun findPendingCandidates(accountId: Int, amount: Money, date: LocalDate): List<Transaction> =
        repository.findPendingByAmountAndDateRange(
            accountId = accountId,
            amount    = amount,
            from      = date.minusDays(3),
            to        = date.plusDays(3)
        ).filter { it.ofxFitId == null }

    fun findPendingInPeriodWithoutReconciliation(
        accountId: Int,
        from: LocalDate,
        to: LocalDate
    ): List<Transaction> =
        repository.findPendingInPeriodWithoutReconciliation(accountId, from, to)

    /**
     * Concilia um lançamento manual com um lançamento OFX importado.
     * O lançamento manual é marcado como PAID e [ofxFitId] é registrado em [reconciledWithFitId].
     * O lançamento OFX duplicado é desativado (sem passar pela state machine, pois PAID→CANCELED
     * não é permitida na state machine normal — aqui é uma operação administrativa).
     */
    fun reconcile(manualTxId: Int, ofxTxId: Int, ofxFitId: String) {
        requirePermission(Permission.ConfirmPayment)

        val manual = repository.findById(manualTxId)
            ?: throw IllegalArgumentException("Lançamento manual não encontrado.")
        if (!TransactionStateMachine.allowsPayment(manual.status)) {
            throw IllegalStateException("Lançamento #$manualTxId não está em estado de pagamento.")
        }
        val ofxEntry = repository.findById(ofxTxId)
            ?: throw IllegalArgumentException("Lançamento OFX não encontrado.")

        val now = LocalDateTime.now()
        repository.update(manual.copy(
            status              = TransactionStatus.PAID,
            paymentDate         = ofxEntry.paymentDate ?: now,
            paidAmount          = manual.amount,
            reconciledWithFitId = ofxFitId,
            updatedAt           = now
        ))
        addTimeline(
            manualTxId, TimelineEventType.RECONCILED,
            "Conciliado com extrato OFX — FITID: $ofxFitId (lançamento #$ofxTxId removido)",
            manual.amount, manual.status, TransactionStatus.PAID
        )
        audit("TRANSACTION_RECONCILED", manualTxId,
            "ofxFitId=$ofxFitId;ofxTxId=$ofxTxId;${auditDetail(TransactionStatus.PAID, manual.status, manual.amount)}")

        // Remove o lançamento OFX duplicado (bypass state machine: PAID não transiciona normalmente)
        repository.deactivate(ofxTxId)
        addTimeline(
            ofxTxId, TimelineEventType.CANCELED,
            "Removido — substituído pela conciliação com lançamento manual #$manualTxId",
            null, TransactionStatus.PAID, TransactionStatus.CANCELED
        )
        audit("TRANSACTION_CANCELED", ofxTxId,
            "reason=reconciliation;manualTxId=$manualTxId;ofxFitId=$ofxFitId")
    }

    fun applyListFilter(filter: TransactionListFilter) {
        listFilter = filter
        searchQuery = null
    }

    fun applySearch(query: String?) {
        searchQuery = query
        if (!query.isNullOrBlank()) listFilter = TransactionListFilter.All
    }

    fun clearFilters() {
        listFilter = TransactionListFilter.All
        searchQuery = null
    }

    /** Domínio: aplica OVERDUE em PENDING vencidos e persiste. */
    fun syncOverdueStatuses(today: LocalDate = LocalDate.now()) {
        val pending = repository.findPendingActive()
        pending.forEach { tx ->
            if (!OverdueEngine.shouldMarkOverdue(tx, today)) return@forEach
            TransactionStateMachine.assertTransition(TransactionStatus.PENDING, TransactionStatus.OVERDUE)
            val updated = tx.copy(status = TransactionStatus.OVERDUE, updatedAt = LocalDateTime.now())
            repository.update(updated)
            addTimeline(
                tx.id,
                TimelineEventType.OVERDUE,
                "Vencimento ultrapassado (${tx.dueDate.toLocalDate()})",
                null,
                TransactionStatus.PENDING,
                TransactionStatus.OVERDUE
            )
            audit("TRANSACTION_OVERDUE", tx.id, auditDetail(TransactionStatus.OVERDUE, TransactionStatus.PENDING, tx.amount))
            audit("TRANSACTION_STATUS_CHANGED", tx.id, auditDetail(TransactionStatus.OVERDUE, TransactionStatus.PENDING, tx.amount))
        }
    }

    private fun requirePermission(permission: Permission) {
        if (!sessionManager.hasPermission(permission)) {
            throw SecurityException("Permissão negada. Esta operação requer perfil Administrador.")
        }
    }

    private fun validateAccount(accountId: Int) {
        accountRepository.findById(accountId)
            ?: throw IllegalArgumentException("Conta financeira inválida ou inexistente.")
    }

    private fun warnIfOutsideCostCenterPeriod(transaction: Transaction, transactionId: Int) {
        if (transaction.type != TransactionType.EXPENSE) return
        val costCenterId = transaction.costCenterId ?: return
        val project = costCenterRepository.findById(costCenterId) ?: return
        val warning = TransactionValidator.checkProjectPeriod(
            transaction.dueDate,
            project.startDate,
            project.endDate
        ) ?: return
        addTimeline(transactionId, TimelineEventType.WARNING, warning, null, null, transaction.status)
        audit("TRANSACTION_WARNING", transactionId, warning)
    }

    private fun validateSupplier(supplierId: Int?) {
        if (supplierId == null) return
        val supplier = supplierRepository.findById(supplierId)
            ?: throw IllegalArgumentException("Fornecedor não encontrado.")
        if (!supplier.isActive) {
            throw IllegalArgumentException("Fornecedor \"${supplier.name}\" está inativo e não pode ser vinculado a um lançamento.")
        }
    }

    private fun validateEmployee(employeeId: Int?) {
        if (employeeId == null || employeeRepository == null) return
        val employee = employeeRepository.getById(employeeId)
            ?: throw IllegalArgumentException("Funcionário não encontrado.")
        if (!employee.active) {
            throw IllegalArgumentException("Funcionário \"${employee.name}\" está inativo e não pode ser vinculado a um lançamento.")
        }
    }

    private fun addTimeline(
        transactionId: Int,
        type: TimelineEventType,
        message: String,
        amount: Money?,
        from: TransactionStatus?,
        to: TransactionStatus?
    ) {
        timelineRepository.insert(
            TransactionTimelineEvent(
                transactionId = transactionId,
                eventType = type,
                message = message,
                amountValue = amount,
                statusFrom = from,
                statusTo = to,
                performedBy = sessionManager.currentUser.value?.id
            )
        )
    }

    private fun audit(action: String, entityId: Int, detail: String) {
        val userId = sessionManager.currentUser.value?.id
        auditRepository.insert(
            AuditLog(
                entityType = "TRANSACTION",
                entityId = entityId,
                action = action,
                newValue = detail,
                performedBy = userId
            )
        )
    }

    private fun auditDetail(
        newStatus: TransactionStatus,
        oldStatus: TransactionStatus?,
        amount: Money
    ): String = buildString {
        append("status=")
        append(newStatus.name)
        if (oldStatus != null) {
            append(";from=")
            append(oldStatus.name)
        }
        append(";amount=")
        append(amount)
    }
}

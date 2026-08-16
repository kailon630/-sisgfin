package br.com.sisgfin.financial.payments

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.toMoney
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.sql.SQLException
import java.time.LocalDateTime

class TransactionPaymentRepository {

    fun insert(payment: TransactionPayment): Int {
        require(!payment.principalAmount.isZero() && !payment.principalAmount.isNegative()) {
            "principal_amount deve ser maior que zero."
        }
        return transaction { doInsert(payment) }
    }

    /**
     * Insere a baixa; retorna false (sem erro) se a idempotency_key já existir.
     * Decisão: no-op silencioso, não exceção. Duplo clique = intenção já executada,
     * não novo evento; lançar erro confundiria o operador sobre algo que já deu certo.
     */
    fun insertOrIgnore(payment: TransactionPayment): Boolean {
        require(!payment.principalAmount.isZero() && !payment.principalAmount.isNegative()) {
            "principal_amount deve ser maior que zero."
        }
        return transaction {
            val key = payment.idempotencyKey
            if (key != null) {
                val exists = TransactionPaymentsTable.selectAll()
                    .where { TransactionPaymentsTable.idempotencyKey eq key }
                    .count() > 0
                if (exists) return@transaction false
            }
            try {
                doInsert(payment)
                true
            } catch (e: Exception) {
                val isUniqueViolation = generateSequence(e as Throwable) { it.cause }
                    .filterIsInstance<SQLException>()
                    .any { it.sqlState == "23505" }
                if (isUniqueViolation) false else throw e
            }
        }
    }

    fun findByTransaction(transactionId: Int): List<TransactionPayment> = transaction {
        TransactionPaymentsTable.selectAll()
            .where { TransactionPaymentsTable.transactionId eq transactionId }
            .orderBy(TransactionPaymentsTable.createdAt to SortOrder.ASC)
            .map { rowToPayment(it) }
    }

    /** Soma caixa efetivo (principal + juros + multa − desconto) das baixas ativas. */
    fun sumActiveByTransaction(transactionId: Int): Money = transaction {
        TransactionPaymentsTable.selectAll()
            .where {
                (TransactionPaymentsTable.transactionId eq transactionId) and
                TransactionPaymentsTable.reversedById.isNull()
            }
            .fold(BigDecimal.ZERO) { acc, row ->
                acc +
                row[TransactionPaymentsTable.principalAmount] +
                row[TransactionPaymentsTable.interestAmount] +
                row[TransactionPaymentsTable.fineAmount] -
                row[TransactionPaymentsTable.discountAmount]
            }
            .toMoney()
    }

    /** Soma discount_amount ativo — usado em D3(a) para quitamento com desconto. */
    fun sumDiscountByTransaction(transactionId: Int): Money = transaction {
        TransactionPaymentsTable.selectAll()
            .where {
                (TransactionPaymentsTable.transactionId eq transactionId) and
                TransactionPaymentsTable.reversedById.isNull()
            }
            .fold(BigDecimal.ZERO) { acc, row ->
                acc + row[TransactionPaymentsTable.discountAmount]
            }
            .toMoney()
    }

    /**
     * M3 — portão de reconciliação.
     * Retorna títulos onde Σ(baixas ativas) ≠ paid_amount.
     * Zero linhas = sistema pode avançar para M4.
     */
    fun findReconciliationDivergences(): List<ReconciliationDivergence> = transaction {
        exec("""
            SELECT t.id, t.amount, t.paid_amount,
                   COALESCE(SUM(p.principal_amount + p.interest_amount + p.fine_amount
                                - p.discount_amount), 0) AS soma_baixas
              FROM financial_transactions t
              LEFT JOIN transaction_payments p
                     ON p.transaction_id = t.id AND p.reversed_by_id IS NULL
             WHERE t.is_active = true
               AND t.paid_amount IS NOT NULL
               AND t.paid_amount > 0
             GROUP BY t.id, t.amount, t.paid_amount
            HAVING COALESCE(SUM(p.principal_amount + p.interest_amount + p.fine_amount
                                - p.discount_amount), 0) <> t.paid_amount
        """.trimIndent()) { rs ->
            val result = mutableListOf<ReconciliationDivergence>()
            while (rs.next()) {
                result += ReconciliationDivergence(
                    transactionId     = rs.getInt("id"),
                    transactionAmount = rs.getBigDecimal("amount").toMoney(),
                    paidAmount        = rs.getBigDecimal("paid_amount").toMoney(),
                    somaBaixas        = rs.getBigDecimal("soma_baixas").toMoney()
                )
            }
            result
        } ?: emptyList()
    }

    private fun doInsert(payment: TransactionPayment): Int =
        TransactionPaymentsTable.insert {
            it[transactionId]   = payment.transactionId
            it[paymentDate]     = payment.paymentDate
            it[accountId]       = payment.accountId
            it[principalAmount] = payment.principalAmount.value
            it[interestAmount]  = payment.interestAmount.value
            it[fineAmount]      = payment.fineAmount.value
            it[discountAmount]  = payment.discountAmount.value
            it[reversedById]    = payment.reversedById
            it[idempotencyKey]  = payment.idempotencyKey
            it[notes]           = payment.notes
            it[createdBy]       = payment.createdBy
            it[createdAt]       = payment.createdAt
        } get TransactionPaymentsTable.id

    private fun rowToPayment(row: ResultRow) = TransactionPayment(
        id              = row[TransactionPaymentsTable.id],
        transactionId   = row[TransactionPaymentsTable.transactionId],
        paymentDate     = row[TransactionPaymentsTable.paymentDate],
        accountId       = row[TransactionPaymentsTable.accountId],
        principalAmount = row[TransactionPaymentsTable.principalAmount].toMoney(),
        interestAmount  = row[TransactionPaymentsTable.interestAmount].toMoney(),
        fineAmount      = row[TransactionPaymentsTable.fineAmount].toMoney(),
        discountAmount  = row[TransactionPaymentsTable.discountAmount].toMoney(),
        reversedById    = row[TransactionPaymentsTable.reversedById],
        idempotencyKey  = row[TransactionPaymentsTable.idempotencyKey],
        notes           = row[TransactionPaymentsTable.notes],
        createdBy       = row[TransactionPaymentsTable.createdBy],
        createdAt       = row[TransactionPaymentsTable.createdAt]
    )
}

package br.com.sisgfin.cashflow

import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.FinancialAccountService
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import java.time.LocalDate

data class CashFlowProjectionResult(
    val currentBalance: Money,
    val entries: List<DailyCashFlowEntry>,
    val overdueTransactions: List<Transaction>,
    val overdueTotal: Money,
    val totalCommitted: Money,
    val projectedFinalBalance: Money
)

class CashFlowService(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: FinancialAccountRepository,
    private val accountService: FinancialAccountService
) {
    fun project(accountId: Int?, windowDays: Int): CashFlowProjectionResult {
        val windowEnd = LocalDate.now().plusDays(windowDays.toLong())
        val currentBalance = consolidatedBalance(accountId)
        val allUnpaid = transactionRepository.findUnpaid(windowEnd, accountId)

        val overdue  = allUnpaid.filter { it.status == TransactionStatus.OVERDUE }
        val upcoming = allUnpaid.filter { it.status != TransactionStatus.OVERDUE }

        val overdueOut = outflow(overdue)
        val overdueIn  = inflow(overdue)
        var running = currentBalance - overdueOut + overdueIn

        val entries = upcoming
            .groupBy { it.dueDate.toLocalDate() }
            .keys.sorted()
            .map { date ->
                val dayTxs = upcoming.filter { it.dueDate.toLocalDate() == date }
                val dayOut = outflow(dayTxs)
                val dayIn  = inflow(dayTxs)
                running = running - dayOut + dayIn
                DailyCashFlowEntry(date, dayTxs, dayOut, dayIn, running)
            }

        val totalCommitted = entries.fold(Money.ZERO) { a, e -> a + e.totalOutflow } + overdueOut
        return CashFlowProjectionResult(currentBalance, entries, overdue, overdueOut, totalCommitted, running)
    }

    fun projectWithSimulation(
        accountId: Int?,
        windowDays: Int,
        extras: List<SimulationEntry>
    ): CashFlowProjectionResult {
        val base = project(accountId, windowDays)
        if (extras.isEmpty()) return base

        // Merge real entries and simulated entries, sorted by date
        data class Slot(val date: LocalDate, val real: DailyCashFlowEntry?, val sim: SimulationEntry?)

        val realSlots = base.entries.map { Slot(it.date, it, null) }
        val simSlots  = extras.map { Slot(it.dueDate, null, it) }
        val sorted    = (realSlots + simSlots).sortedWith(compareBy({ it.date }, { if (it.sim != null) 1 else 0 }))

        // Recalculate running balance from scratch (overdues already applied in base)
        var running = base.currentBalance - base.overdueTotal

        val merged = sorted.map { slot ->
            if (slot.sim != null) {
                running = running - slot.sim.amount
                DailyCashFlowEntry(
                    date            = slot.date,
                    transactions    = emptyList(),
                    totalOutflow    = slot.sim.amount,
                    totalInflow     = Money.ZERO,
                    projectedBalance = running,
                    isSimulated     = true,
                    simulationLabel = slot.sim.description
                )
            } else {
                val e = slot.real!!
                running = running - e.totalOutflow + e.totalInflow
                e.copy(projectedBalance = running)
            }
        }

        val totalCommitted = merged.fold(Money.ZERO) { a, e -> a + e.totalOutflow } + base.overdueTotal
        return base.copy(entries = merged, totalCommitted = totalCommitted, projectedFinalBalance = running)
    }

    private fun consolidatedBalance(accountId: Int?): Money =
        if (accountId != null) {
            accountService.calculateBalance(accountId)
        } else {
            accountRepository.findAll().filter { it.isActive }
                .fold(Money.ZERO) { acc, a -> acc + accountService.calculateBalance(a.id) }
        }

    private fun outflow(txs: List<Transaction>): Money =
        txs.filter { it.type == TransactionType.EXPENSE }
            .fold(Money.ZERO) { a, t -> a + (t.paidAmount ?: t.amount) }

    private fun inflow(txs: List<Transaction>): Money =
        txs.filter { it.type == TransactionType.INCOME || it.type == TransactionType.REVERSAL }
            .fold(Money.ZERO) { a, t -> a + (t.paidAmount ?: t.amount) }
}

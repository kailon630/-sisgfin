package br.com.sisgfin.payroll

import br.com.sisgfin.financial.money.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

// Saída bruta do parser — sem consulta ao BD, sem datas de vencimento
data class PayrollRawEntry(
    val matricula: Int,
    val nome: String,
    val cpf: String,       // 11 dígitos normalizados (sem pontuação)
    val funcao: String,
    val adiantamento: Money,
    val liquido: Money,
    val salaryBase: Money, // extraído de "Salário base X.XXX,XX" para detecção de anomalia
    val liquidoCount: Int  // >1 indica férias acumuladas
)

// Entrada enriquecida após lookup no BD — produzida pelo PayrollImportService (8-C)
data class PayrollEntry(
    val raw: PayrollRawEntry,
    val employeeId: Int?,
    val adiantamentoDueDate: LocalDate,
    val liquidoDueDate: LocalDate,
    val employeeFound: Boolean,
    val warningMessage: String?
) {
    val nome: String get() = raw.nome
    val cpf: String get() = raw.cpf
    val funcao: String get() = raw.funcao
    val adiantamento: Money get() = raw.adiantamento
    val liquido: Money get() = raw.liquido
}

data class PayrollImportResult(
    val entries: List<PayrollEntry>,
    val notFoundCount: Int,
    val warnings: List<String>,
    val referenceMonth: YearMonth
)

data class PayrollImportBatch(
    val id: Int = 0,
    val referenceMonth: YearMonth,
    val importedAt: LocalDateTime,
    val importedBy: Int?,
    val totalEmployees: Int,
    val transactionsCreated: Int
)

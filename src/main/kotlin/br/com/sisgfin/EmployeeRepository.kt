package br.com.sisgfin

import br.com.sisgfin.financial.money.toMoney
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class EmployeeRepository {
    fun getAll(): List<Employee> = transaction {
        Employees.selectAll()
            .orderBy(Employees.name to SortOrder.ASC)
            .map { it.toEmployee() }
    }

    fun getById(id: Int): Employee? = transaction {
        Employees.selectAll().where { Employees.id eq id }
            .map { it.toEmployee() }
            .singleOrNull()
    }

    fun getAllActive(): List<Employee> = transaction {
        Employees.selectAll().where { Employees.active eq true }
            .orderBy(Employees.name to SortOrder.ASC)
            .map { it.toEmployee() }
    }

    fun insert(employee: Employee) = transaction {
        Employees.insert {
            it[name]           = employee.name
            it[document]       = employee.document
            it[phone]          = employee.phone
            it[email]          = employee.email
            it[role]           = employee.role
            it[salary]         = employee.salary.value
            it[paymentDay]     = employee.paymentDay
            it[paymentDays]    = employee.paymentDays
            it[employmentType] = employee.employmentType
            it[bankCode]       = employee.bankCode
            it[agencyNumber]   = employee.agencyNumber
            it[agencyDv]       = employee.agencyDv
            it[accountNumber]  = employee.accountNumber
            it[accountDv]      = employee.accountDv
            it[accountType]    = employee.accountType
            it[active]         = employee.active
            it[createdAt]      = employee.createdAt
        } get Employees.id
    }

    fun update(employee: Employee) = transaction {
        Employees.update({ Employees.id eq employee.id }) {
            it[name]           = employee.name
            it[document]       = employee.document
            it[phone]          = employee.phone
            it[email]          = employee.email
            it[role]           = employee.role
            it[salary]         = employee.salary.value
            it[paymentDay]     = employee.paymentDay
            it[paymentDays]    = employee.paymentDays
            it[employmentType] = employee.employmentType
            it[bankCode]       = employee.bankCode
            it[agencyNumber]   = employee.agencyNumber
            it[agencyDv]       = employee.agencyDv
            it[accountNumber]  = employee.accountNumber
            it[accountDv]      = employee.accountDv
            it[accountType]    = employee.accountType
            it[active]         = employee.active
        }
    }

    // Normaliza dígitos para comparar CPF independente de formatação ("254.461.288-69" == "25446128869").
    // Usa getAll() em memória — dataset de funcionários é sempre pequeno (< 500 registros).
    fun findByCpf(cpf: String): Employee? {
        val digits = cpf.replace(Regex("[^0-9]"), "")
        if (digits.length != 11) return null
        return getAll().firstOrNull { emp ->
            emp.document.replace(Regex("[^0-9]"), "") == digits
        }
    }

    private fun ResultRow.toEmployee() = Employee(
        id             = this[Employees.id],
        name           = this[Employees.name],
        document       = this[Employees.document],
        phone          = this[Employees.phone],
        email          = this[Employees.email],
        role           = this[Employees.role],
        salary         = this[Employees.salary].toMoney(),
        paymentDay     = this[Employees.paymentDay],
        paymentDays    = this[Employees.paymentDays],
        employmentType = this[Employees.employmentType],
        bankCode       = this[Employees.bankCode],
        agencyNumber   = this[Employees.agencyNumber],
        agencyDv       = this[Employees.agencyDv],
        accountNumber  = this[Employees.accountNumber],
        accountDv      = this[Employees.accountDv],
        accountType    = this[Employees.accountType],
        active         = this[Employees.active],
        createdAt      = this[Employees.createdAt]
    )
}

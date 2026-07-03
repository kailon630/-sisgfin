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
            it[active]         = employee.active
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
        active         = this[Employees.active],
        createdAt      = this[Employees.createdAt]
    )
}

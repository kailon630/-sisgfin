package br.com.sisgfin

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.suppliers.EntityType
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * T-10: CPF cruzado entre employees e suppliers, com e sem pontuação.
 * T-15: EmploymentType grava .name, exibe .label.
 */
class CrossDocumentValidationTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun supplier(id: Int, document: String) = Supplier(
        id = id, document = document, name = "Fornecedor $id",
        isActive = true, entityType = EntityType.FORNECEDOR
    )

    private fun employee(id: Int, document: String) = Employee(
        id = id, name = "Funcionário $id", document = document,
        phone = "", email = "", role = "Dev",
        salary = Money.fromString("3000.00"), paymentDay = 5, active = true
    )

    // ── T-10: CPF cruzado ─────────────────────────────────────────────────────

    @Test
    fun `EmploymentType CLT grava name CLT e label CLT`() {
        assertEquals("CLT", EmploymentType.CLT.name)
        assertEquals("CLT", EmploymentType.CLT.label)
    }

    @Test
    fun `EmploymentType ESTAGIO grava name ESTAGIO e label com acento`() {
        assertEquals("ESTAGIO", EmploymentType.ESTAGIO.name)
        assertEquals("Estágio", EmploymentType.ESTAGIO.label)
    }

    @Test
    fun `EmploymentType OUTROS grava name OUTROS e label Outros`() {
        assertEquals("OUTROS", EmploymentType.OUTROS.name)
        assertEquals("Outros", EmploymentType.OUTROS.label)
    }

    @Test
    fun `todos EmploymentType entries tem name sem acento e resolvel por name`() {
        EmploymentType.entries.forEach { type ->
            val resolved = EmploymentType.entries.firstOrNull { it.name == type.name }
            assertEquals(type, resolved, "Não resolveu por name: ${type.name}")
        }
    }

    // ── T-10: CPF cruzado via SupplierService ─────────────────────────────────
    // Nota: as validações cruzadas reais vivem nos repositórios e precisam de DB real.
    // Aqui validamos a lógica de normalização e que SupplierService normaliza antes de salvar.

    @Test
    fun `SupplierService normaliza CPF antes de salvar`() {
        val supplierRepo = mockk<SupplierRepository>()
        val auditRepo = mockk<AuditRepository>(relaxed = true)
        val session = mockk<SessionManager>(relaxed = true)
        every { session.currentUser } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        every { supplierRepo.findByDocument("25446128869") } returns null
        val savedSlot = slot<Supplier>()
        every { supplierRepo.insert(capture(savedSlot)) } returns 1

        val service = SupplierService(supplierRepo, auditRepo, session)

        service.save(supplier(0, "254.461.288-69").copy(document = "254.461.288-69"))

        assertEquals("25446128869", savedSlot.captured.document)
    }

    @Test
    fun `EmployeeService normaliza CPF antes de salvar`() {
        val employeeRepo = mockk<EmployeeRepository>()
        val payrollEngine = mockk<br.com.sisgfin.employees.PayrollEngine>(relaxed = true)
        val savedSlot = slot<Employee>()
        every { employeeRepo.insert(capture(savedSlot)) } returns 1

        val service = EmployeeService(employeeRepo, payrollEngine)

        service.save(employee(0, "254.461.288-69"))

        assertEquals("25446128869", savedSlot.captured.document)
    }
}

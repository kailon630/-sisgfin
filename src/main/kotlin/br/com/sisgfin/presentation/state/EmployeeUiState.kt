package br.com.sisgfin.presentation.state

import br.com.sisgfin.Employee

data class EmployeeUiState(
    val isLoading: Boolean = false,
    val employees: List<Employee> = emptyList(),
    val selectedEmployee: Employee? = null,
    val isDialogVisible: Boolean = false,
    val employeeToEdit: Employee? = null,
    val errorMessage: String? = null
)

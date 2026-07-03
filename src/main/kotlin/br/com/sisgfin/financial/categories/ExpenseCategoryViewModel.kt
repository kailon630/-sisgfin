package br.com.sisgfin.financial.categories

import br.com.sisgfin.core.crud.BaseCrudViewModel

class ExpenseCategoryViewModel(
    service: ExpenseCategoryService
) : BaseCrudViewModel<ExpenseCategory>(
    operations  = service,
    emptyFactory = { ExpenseCategory(code = "", name = "") },
    itemFilter  = { item, query ->
        val q = query.lowercase()
        item.code.lowercase().contains(q) ||
        item.name.lowercase().contains(q) ||
        (item.groupName?.lowercase()?.contains(q) == true)
    }
)

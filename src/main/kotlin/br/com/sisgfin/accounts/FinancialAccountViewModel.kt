package br.com.sisgfin.accounts

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountService
import br.com.sisgfin.core.crud.BaseCrudViewModel
import br.com.sisgfin.financial.money.Money

class FinancialAccountViewModel(
    service: FinancialAccountService
) : BaseCrudViewModel<FinancialAccount>(
    operations = service,
    emptyFactory = { FinancialAccount(name = "", initialBalance = Money.ZERO) },
    itemFilter = { item, query ->
        item.name.lowercase().contains(query.lowercase())
    }
)

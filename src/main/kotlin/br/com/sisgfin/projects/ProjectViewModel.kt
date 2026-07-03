package br.com.sisgfin.projects

import br.com.sisgfin.CostCenter
import br.com.sisgfin.CostCenterService
import br.com.sisgfin.core.crud.BaseCrudViewModel

class CostCenterViewModel(
    service: CostCenterService
) : BaseCrudViewModel<CostCenter>(
    operations = service,
    emptyFactory = { CostCenter(code = "", name = "") },
    itemFilter = { item, query ->
        val q = query.lowercase()
        item.name.lowercase().contains(q) || item.code.lowercase().contains(q)
    }
)

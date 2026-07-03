package br.com.sisgfin.suppliers

import br.com.sisgfin.Supplier
import br.com.sisgfin.SupplierService
import br.com.sisgfin.core.crud.BaseCrudViewModel

class SupplierViewModel(
    service: SupplierService
) : BaseCrudViewModel<Supplier>(
    operations = service,
    emptyFactory = { Supplier(document = "", name = "") },
    itemFilter = { item, query ->
        val q = query.lowercase()
        item.name.lowercase().contains(q) ||
            item.document.lowercase().contains(q) ||
            (item.tradeName?.lowercase()?.contains(q) == true)
    }
)

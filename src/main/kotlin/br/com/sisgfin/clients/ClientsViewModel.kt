package br.com.sisgfin.clients

import br.com.sisgfin.Supplier
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.SupplierService
import br.com.sisgfin.core.crud.BaseCrudViewModel
import br.com.sisgfin.core.crud.CrudOperations
import br.com.sisgfin.suppliers.EntityType

class ClientsViewModel(
    private val supplierRepository: SupplierRepository,
    supplierService: SupplierService
) : BaseCrudViewModel<Supplier>(
    operations = object : CrudOperations<Supplier> {
        override fun listAll() = supplierRepository.findClients()
        override fun save(item: Supplier) = supplierService.save(item)
        override fun toggleActive(id: Int) = supplierService.toggleActive(id)
    },
    emptyFactory = { Supplier(document = "", name = "", entityType = EntityType.CLIENTE) },
    itemFilter = { item, query ->
        val q = query.lowercase()
        item.name.lowercase().contains(q) ||
            item.document.lowercase().contains(q) ||
            (item.tradeName?.lowercase()?.contains(q) == true)
    }
)

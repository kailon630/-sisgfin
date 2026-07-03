package br.com.sisgfin.api.routes

import br.com.sisgfin.CostCenterService
import br.com.sisgfin.api.CategoryDto
import br.com.sisgfin.api.CostCenterDto
import br.com.sisgfin.financial.categories.ExpenseCategoryService
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.format.DateTimeFormatter

private val dtFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

fun Route.referenceRoutes(
    categoryService: ExpenseCategoryService,
    costCenterService: CostCenterService
) {
    get("/categories") {
        call.respond(categoryService.listAll().map {
            CategoryDto(
                id          = it.id,
                code        = it.code,
                name        = it.name,
                displayName = it.displayName,
                groupCode   = it.groupCode,
                groupName   = it.groupName,
                isIncome    = it.isIncome,
                isActive    = it.isActive
            )
        })
    }

    get("/cost-centers") {
        call.respond(costCenterService.listAll().map {
            CostCenterDto(
                id          = it.id,
                code        = it.code,
                name        = it.name,
                description = it.description,
                startDate   = it.startDate?.format(dtFmt),
                endDate     = it.endDate?.format(dtFmt),
                isActive    = it.isActive,
                isEncerrado = it.isEncerrado
            )
        })
    }
}

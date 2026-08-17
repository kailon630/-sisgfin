package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.categories.ExpenseCategory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Filtro de categorias por tipo de lançamento (receita/despesa).
 *
 * CF-01: INCOME → apenas isIncome = true
 * CF-02: EXPENSE → apenas isIncome = false
 * CF-03: TRANSFER → todas as categorias
 * CF-04: ADJUSTMENT → todas as categorias
 * CF-05: trocar tipo limpa categoria (categoryId = null → nenhuma legada incluída)
 * CF-06: categoria legada fora do tipo é preservada quando categoryId != null
 */
class CategoryFilterTest {

    private val expenseCat = ExpenseCategory(id = 1, code = "1.1", name = "Vencimentos", isIncome = false)
    private val incomeCat  = ExpenseCategory(id = 74, code = "10.1", name = "Repasse", isIncome = true)
    private val all = listOf(expenseCat, incomeCat)

    // ── CF-01 ─────────────────────────────────────────────────────────────────

    @Test
    fun `CF-01 INCOME retorna apenas categorias de receita`() {
        val result = filterCategoriesForType(all, TransactionType.INCOME, null)
        assertEquals(listOf(74 to "Repasse"), result)
    }

    // ── CF-02 ─────────────────────────────────────────────────────────────────

    @Test
    fun `CF-02 EXPENSE retorna apenas categorias de despesa`() {
        val result = filterCategoriesForType(all, TransactionType.EXPENSE, null)
        assertEquals(listOf(1 to "Vencimentos"), result)
    }

    // ── CF-03 ─────────────────────────────────────────────────────────────────

    @Test
    fun `CF-03 TRANSFER retorna todas as categorias`() {
        val result = filterCategoriesForType(all, TransactionType.TRANSFER, null)
        assertEquals(2, result.size)
    }

    // ── CF-04 ─────────────────────────────────────────────────────────────────

    @Test
    fun `CF-04 ADJUSTMENT retorna todas as categorias`() {
        val result = filterCategoriesForType(all, TransactionType.ADJUSTMENT, null)
        assertEquals(2, result.size)
    }

    // ── CF-05 ─────────────────────────────────────────────────────────────────

    @Test
    fun `CF-05 apos limpar categoryId nao inclui categoria fora do tipo`() {
        // Simula trocar tipo (categoryId = null após o clear)
        val result = filterCategoriesForType(all, TransactionType.INCOME, currentCategoryId = null)
        assertTrue(result.none { it.first == expenseCat.id }, "despesa nao deve aparecer em INCOME sem legado")
    }

    // ── CF-06 ─────────────────────────────────────────────────────────────────

    @Test
    fun `CF-06 categoria legada fora do tipo e preservada quando ainda selecionada`() {
        // Lançamento INCOME com categoryId = 1 (despesa) — dado legado
        val result = filterCategoriesForType(all, TransactionType.INCOME, currentCategoryId = 1)
        assertTrue(result.any { it.first == 1 }, "categoria legada deve aparecer para nao sumir silenciosamente")
        assertTrue(result.any { it.first == 74 }, "categoria correta do tipo tambem aparece")
    }
}

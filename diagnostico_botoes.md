# Diagnóstico de Botões — SisgFin
> Gerado em 2026-07-05. Somente leitura — nenhum arquivo foi alterado.

---

## 1. Botão "Ajuste" quebrado — Row de tipos no painel de transação

**Arquivo:** `financial/transactions/TransactionDetailsPanel.kt` · linhas 248–263  
**Arquivo do enum:** `financial/transactions/TransactionType.kt` · linhas 1–18

### 1a. Enum TransactionType (origem dos rótulos)

```kotlin
// TransactionType.kt:1-18
enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    ADJUSTMENT,
    REVERSAL;

    val displayName: String
        get() = when (this) {
            INCOME     -> "Receita"
            EXPENSE    -> "Despesa"
            TRANSFER   -> "Transferência"
            ADJUSTMENT -> "Ajuste"
            REVERSAL   -> "Estorno"
        }
}
```

### 1b. Container e chips de tipo (o bloco problemático)

```kotlin
// TransactionDetailsPanel.kt:248-263
// Tipo
Text("TIPO", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    TransactionType.values().forEach { t ->
        WsFilterChip(
            selected = type == t,
            onClick = {
                if (type != t) {
                    type = t
                    supplierId = null
                }
            },
            label = { Text(t.displayName) }
        )
    }
}
```

**Observações de layout:**
- O `Row` não tem `Modifier.fillMaxWidth()` nem `FlowRow`/`FlowLayoutArrangement`
- `TransactionType.values()` itera **5 valores**: Receita, Despesa, Transferência, Ajuste, Estorno
- Nenhum `Modifier.weight(...)` nos chips — cada um ocupa o tamanho mínimo do texto
- Com o painel lateral em largura reduzida, os chips "Ajuste" e "Estorno" ficam espremidos ou o texto vai para a vertical por falta de espaço horizontal
- `WsFilterChip` é um `FilterChip` do M3 — não tem `maxLines`, o label apenas passa `{ Text(t.displayName) }` sem `overflow`

---

## 2. Fileira de ações do topo — TransactionsScreen

**Arquivo:** `financial/transactions/TransactionsScreen.kt` · linhas 156–201

```kotlin
// TransactionsScreen.kt:156-201
Row(
    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column {
        Text("Movimentações Financeiras", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Contas a pagar e receber — ciclo operacional",
            style = MaterialTheme.typography.bodyMedium,
            color = WsTextSecondary
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {

        // ── Botão 1: Transferência ─────────────────────────────────────────
        OutlinedButton(                             // M3 RAW — sem wrapper Ws
            onClick = { viewModel.openTransferDialog() },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(36.dp),      // ⚠️ altura 36dp (≠ WsSize.control=40dp)
            border = androidx.compose.foundation.BorderStroke(1.dp, WsBorderLight),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WsTextSecondary),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
        ) {
            Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Transferência", style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp))
        }

        // ── Botão 2: Despesa ───────────────────────────────────────────────
        OutlinedButton(                             // M3 RAW — sem wrapper Ws
            onClick = { openExpensePanel() },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(36.dp),      // ⚠️ altura 36dp (≠ WsSize.control=40dp)
            border = androidx.compose.foundation.BorderStroke(1.dp, WsDanger.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WsDanger),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
        ) {
            Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Despesa", style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp))
        }

        // ── Botão 3: Receita ───────────────────────────────────────────────
        WsButton(                                   // wrapper Ws — usa WsSize.control=40dp
            text = "Receita",
            icon = Icons.Default.Add,
            onClick = { openIncomePanel() }
        )                                           // ⚠️ altura 40dp (diverge dos dois acima)

        // ── Botão 4: Refresh ───────────────────────────────────────────────
        WsIconButton(Icons.Default.Refresh, onClick = { viewModel.load() })
    }
}
```

**Inconsistências identificadas:**
| Botão | Componente | Altura | Border | Cor do texto |
|---|---|---|---|---|
| Transferência | `OutlinedButton` M3 raw | `36.dp` | `WsBorderLight` | `WsTextSecondary` |
| Despesa | `OutlinedButton` M3 raw | `36.dp` | `WsDanger.copy(alpha=0.6f)` | `WsDanger` |
| Receita | `WsButton` | `WsSize.control` = `40.dp` | sem borda | branco sobre `WsAccent` |
| Refresh | `WsIconButton` | `WsSize.icon` = `40.dp` | `WsBorder` no hover | `WsTextSecondary` |

---

## 3. Filtros-pílula — TransactionFilterBar

**Arquivo:** `financial/transactions/TransactionsScreen.kt` · linhas 373–422

```kotlin
// TransactionsScreen.kt:373-422
Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(top = 8.dp)
    // ⚠️ sem fillMaxWidth — sem wrapping — chips podem sair da tela em janelas estreitas
) {
    WsFilterChip(
        selected = listFilter is TransactionListFilter.ActionRequired,
        onClick = { onFilter(TransactionListFilter.ActionRequired) },
        label = { Text("A pagar") }
    )
    WsFilterChip(
        selected = listFilter is TransactionListFilter.All,
        onClick = onClear,
        label = { Text("Todas") }
    )
    WsFilterChip(
        selected = listFilter is TransactionListFilter.DueToday,
        onClick = { onFilter(TransactionListFilter.DueToday) },
        label = { Text("Vence hoje") }
    )
    WsFilterChip(
        selected = listFilter is TransactionListFilter.Overdue,
        onClick = { onFilter(TransactionListFilter.Overdue) },
        label = { Text("Vencidas") }
    )
    WsFilterChip(
        selected = listFilter is TransactionListFilter.Paid,
        onClick = { onFilter(TransactionListFilter.Paid) },
        label = { Text("Pagas") }
    )
    WsFilterChip(
        selected = listFilter is TransactionListFilter.ByType
               && listFilter.type == TransactionType.EXPENSE,
        onClick = { onFilter(TransactionListFilter.ByType(TransactionType.EXPENSE)) },
        label = { Text("Despesas") }
    )
    WsFilterChip(
        selected = listFilter is TransactionListFilter.ByType
               && listFilter.type == TransactionType.INCOME,
        onClick = { onFilter(TransactionListFilter.ByType(TransactionType.INCOME)) },
        label = { Text("Receitas") }
    )
    WsFilterChip(
        selected = listFilter is TransactionListFilter.DuePeriod,
        onClick = {
            val now = LocalDate.now()
            onFilter(TransactionListFilter.DuePeriod(now, now.plusDays(30)))
        },
        label = { Text("30 dias") }
    )
}
```

### Definição do componente WsFilterChip

```kotlin
// DesktopComponents.kt:596-619
@Composable
fun WsFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = label,
        modifier = modifier,
        colors   = FilterChipDefaults.filterChipColors(
            containerColor         = WsSurface,           // inativo: fundo WsSurface
            labelColor             = WsTextSecondary,     // inativo: texto cinza
            selectedContainerColor = WsAccent.copy(alpha = 0.15f),  // ativo: azul 15%
            selectedLabelColor     = WsAccent             // ativo: texto azul
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled             = true,
            selected            = selected,
            borderColor         = WsBorderLight,              // inativo: borda sutil
            selectedBorderColor = WsAccent.copy(alpha = 0.5f) // ativo: borda azul 50%
        )
    )
}
```

**Como o estado "ativo" é aplicado:** via `selected: Boolean` → `FilterChipDefaults.filterChipColors` troca `containerColor` para `WsAccent.copy(0.15f)` e `labelColor` para `WsAccent`. Sem animação explícita — o M3 `FilterChip` tem transição de cor interna.

---

## 4. Inventário completo de variantes de botão

### 4a. WsButton
**Definido em:** `WsControls.kt` · linhas 49–90

```kotlin
// WsControls.kt:49-90
@Composable
fun WsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = WsSize.control,   // padrão: 40dp
) {
    val isEnabled = enabled && !loading
    val interaction = remember { MutableInteractionSource() }

    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = isEnabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(WsRadius.md),        // 12dp
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor         = WsAccent,          // azul primário
            contentColor           = Color.White,
            disabledContainerColor = WsAccent.copy(alpha = 0.4f),
            disabledContentColor   = Color.White.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = WsSpace.lg, vertical = 0.dp),  // h=16dp
        modifier = modifier.height(height).wsPressScale(interaction),
    ) {
        if (loading) {
            WsSpinner(size = 16.dp, stroke = 2.dp, color = Color.White)
            Spacer(Modifier.width(WsSpace.sm))
            Text("Aguarde…", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        } else {
            if (icon != null) {
                Icon(icon, null, Modifier.size(WsSize.iconInner))  // 18dp
                Spacer(Modifier.width(WsSpace.sm))
            }
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
```

**Aparência:** fundo `WsAccent` (azul), texto branco, borda-raio 12dp, altura 40dp, press-scale feedback.

---

### 4b. WsIconButton
**Definido em:** `WsControls.kt` · linhas 97–133

```kotlin
// WsControls.kt:97-133
@Composable
fun WsIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg = when {
        !enabled -> Color.Transparent
        hovered  -> WsElevated
        else     -> Color.Transparent
    }

    Box(
        modifier
            .size(WsSize.icon)                       // 40×40dp
            .clip(RoundedCornerShape(WsRadius.md))   // 12dp
            .background(bg)
            .border(1.dp, WsBorder.copy(alpha = if (hovered) 0.8f else 0.4f), RoundedCornerShape(WsRadius.md))
            .hoverable(interaction, enabled = enabled)
            .wsPressScale(interaction)
            .then(if (enabled) Modifier.androidxClickable(interaction, onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription,
            tint = if (enabled) WsTextSecondary else WsTextDisabled,
            modifier = Modifier.size(WsSize.iconInner),   // 18dp
        )
    }
}
```

**Aparência:** transparente por padrão, fundo `WsElevated` no hover, borda `WsBorder` visível (alpha 0.4→0.8 no hover), 40×40dp, ícone cinza.

---

### 4c. WsOutlinedButton
**Definido em:** `DesktopComponents.kt` · linhas 573–592

```kotlin
// DesktopComponents.kt:573-592
@Composable
fun WsOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    val effectiveColor = if (contentColor == Color.Unspecified) WsTextSecondary else contentColor
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,                        // ⚠️ sem height fixo — herda M3 default (≈48dp)
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),           // ⚠️ 6dp (≠ WsRadius.md=12dp dos outros)
        border = androidx.compose.foundation.BorderStroke(1.dp, WsBorderLight),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = effectiveColor),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        content = content
    )
}
```

**Aparência:** fundo transparente, borda `WsBorderLight`, texto `WsTextSecondary` (padrão), borda-raio 6dp, sem altura fixa (usa padrão M3 ≈48dp).

---

### 4d. WsFilterChip
**Definido em:** `DesktopComponents.kt` · linhas 596–619  
*(código completo na seção 3 acima)*

**Aparência:** fundo `WsSurface` (inativo) / `WsAccent` 15% opacidade (ativo), borda `WsBorderLight` (inativo) / `WsAccent` 50% (ativo), altura determinada pelo M3 `FilterChip` (~32dp).

---

### 4e. OutlinedButton M3 raw — inline em telas (não encapsulado)

Ocorrências no projeto onde `OutlinedButton` do Material3 é chamado diretamente, fora de `WsOutlinedButton`:

| # | Arquivo | Linha | Contexto | height | border | contentColor |
|---|---|---|---|---|---|---|
| 1 | `TransactionsScreen.kt` | 170 | Botão "Transferência" topo | `36.dp` | `WsBorderLight 1dp` | `WsTextSecondary` |
| 2 | `TransactionsScreen.kt` | 183 | Botão "Despesa" topo | `36.dp` | `WsDanger.copy(0.6f) 1dp` | `WsDanger` |
| 3 | `TransactionDetailsPanel.kt` | 203 | Botão "Estornar" ações rápidas | sem height | `WsWarning 1dp` | `WsWarning` (inline no Text) |
| 4 | `StatementScreen.kt` | 61 | Botão "Excel" exportação | sem height | `WsBorderLight 1dp` | `WsTextSecondary` |
| 5 | `DbConfigScreen.kt` | 192 | Botão "Testar conexão" | sem height | `WsBorderLight 1dp` | `WsTextSecondary` |
| 6 | `OfxImportScreen.kt` | 568 | Botão "Ignorar" OFX | sem height | `WsBorderLight 1dp` | `WsTextSecondary` |
| 7 | `OfxImportScreen.kt` | 688 | Botão "Nova importação" resultado | sem height | `WsBorderLight 1dp` | `WsTextSecondary` |

```kotlin
// TransactionDetailsPanel.kt:202-215 — único OutlinedButton sem height e com cor WsWarning
if (TransactionAction.Reverse in actions) {
    OutlinedButton(
        onClick = { showReversalDialog = true },
        border = androidx.compose.foundation.BorderStroke(1.dp, WsWarning)
        // ⚠️ sem shape, sem height, sem contentPadding
    ) {
        Icon(Icons.Default.Undo, null, modifier = Modifier.size(16.dp), tint = WsWarning)
        Spacer(Modifier.width(6.dp))
        Text("Estornar", color = WsWarning)
    }
}
```

```kotlin
// StatementScreen.kt:61-71
OutlinedButton(
    onClick = { viewModel.exportExcel() },
    enabled = state.entries.isNotEmpty(),
    shape = RoundedCornerShape(6.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, WsBorderLight),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = WsTextSecondary)
    // ⚠️ sem height
) {
    Icon(Icons.Outlined.TableChart, null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text("Excel")
}
```

---

### 4f. TextButton M3 raw — inline em diálogos (cancel)

Ocorrências — exclusivamente em `dismissButton` de `AlertDialog`:

| Arquivo | Linha | Label |
|---|---|---|
| `TransactionsScreen.kt` | 629 | "Cancelar" |
| `TransactionsScreen.kt` | 710 | "Cancelar" |
| `TransactionDetailsPanel.kt` | 676 | "Cancelar" |
| `TransactionDetailsPanel.kt` | 739 | "Cancelar" |
| `UserManagementScreen.kt` | 234 | "Cancelar" |
| `SupplierManagementScreen.kt` | 214 | "Cancelar" |
| `EmployeesScreen.kt` | 251, 332 | "Cancelar" |
| `financial/categories/CategoriesScreen.kt` | 239 | "Cancelar" |
| `core/ui/dialogs/ConfirmDialog.kt` | 31 | "Cancelar" |
| `CashFlowScreen.kt` | 545, 585 | "Limpar" / ação de filtro |
| `contracts/ContractsScreen.kt` | 409, 416 | ação de status / "Cancelar" |
| `recurrence/RecurringScreen.kt` | 484 | "Voltar" |

```kotlin
// Padrão repetido em ~12 locais:
dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
```

**Aparência:** fundo transparente, sem borda, texto na cor `contentColor` padrão do M3 (primário do tema = `WsAccent`), altura padrão M3 (≈48dp).

---

## Resumo — Altura dos botões por variante

| Variante | Altura real | Definida por |
|---|---|---|
| `WsButton` | `40dp` (`WsSize.control`) | parâmetro `height` |
| `WsIconButton` | `40dp` (`WsSize.icon`) | `Modifier.size(WsSize.icon)` |
| `WsOutlinedButton` | **~48dp** (M3 default) | não definida no wrapper |
| `WsFilterChip` | **~32dp** (M3 FilterChip) | M3 interno |
| `OutlinedButton` raw — TransactionsScreen topo | `36dp` | `Modifier.height(36.dp)` explícito |
| `OutlinedButton` raw — demais telas | **~48dp** (M3 default) | não definida |
| `TextButton` raw | **~48dp** (M3 default) | não definida |

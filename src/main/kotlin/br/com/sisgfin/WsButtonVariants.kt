package br.com.sisgfin

/*
 * ═══════════════════════════════════════════════════════════════════════════
 *  WsButtonVariants.kt — Sistema de variantes de botão (Onda 2)
 * ───────────────────────────────────────────────────────────────────────────
 *  Resolve o que o diagnostico_botoes.md expôs:
 *   • Fileira do topo com 3 alturas (36/40/48) e 3 estilos misturados
 *   • 7 OutlinedButton crus espalhados (Estornar, Excel, Testar conexão…)
 *   • ~12 TextButton crus nos diálogos (Cancelar)
 *   • Seletor de tipo com 5 valores estourando a largura do painel
 *
 *  Estratégia: UM WsButton com `variant`. Cada botão cru vira uma variante
 *  nomeada. Todos passam a ter altura 40dp e raio 12dp (WsSize/WsRadius).
 *
 *  Depende de WsDesignSystem.kt (WsSize, WsRadius, WsSpace, wsPressScale).
 * ═══════════════════════════════════════════════════════════════════════════
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.financial.transactions.TransactionType

/* ───────────────────────────────────────────────────────────────────────────
 * As variantes. Cada uma tem um papel semântico claro — o olho lê a hierarquia.
 *   PRIMARY   → a ação principal da tela (azul sólido). Uma por tela, idealmente.
 *   SECONDARY → ação neutra de apoio (contorno cinza). Ex: Transferência, Excel.
 *   DANGER    → ação destrutiva/negativa (contorno vermelho). Ex: Despesa, Excluir.
 *   WARNING   → ação de atenção (contorno âmbar). Ex: Estornar.
 *   TERTIARY  → ação de menor peso, sem borda (só texto). Ex: Cancelar nos diálogos.
 * ─────────────────────────────────────────────────────────────────────────── */
enum class WsButtonVariant { PRIMARY, SECONDARY, DANGER, WARNING, TERTIARY }

@Composable
fun WsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: WsButtonVariant = WsButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = WsSize.control,        // 40dp — unificado para TODAS as variantes
) {
    val isEnabled = enabled && !loading
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(WsRadius.md)   // 12dp — unificado

    // cor de conteúdo por variante
    val contentColor = when (variant) {
        WsButtonVariant.PRIMARY   -> Color.White
        WsButtonVariant.SECONDARY -> WsTextSecondary
        WsButtonVariant.DANGER    -> WsDanger
        WsButtonVariant.WARNING   -> WsWarning
        WsButtonVariant.TERTIARY  -> WsTextSecondary
    }

    val commonMod = modifier.height(height).wsPressScale(interaction)
    val contentPad = PaddingValues(horizontal = WsSpace.lg, vertical = 0.dp)

    @Composable
    fun inner() {
        if (loading) {
            WsSpinner(size = 16.dp, stroke = 2.dp, color = contentColor)
            Spacer(Modifier.width(WsSpace.sm))
            Text("Aguarde…", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        } else {
            if (icon != null) {
                Icon(icon, null, Modifier.size(WsSize.iconInner))
                Spacer(Modifier.width(WsSpace.sm))
            }
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }

    when (variant) {
        WsButtonVariant.PRIMARY -> Button(
            onClick = onClick, enabled = isEnabled, interactionSource = interaction,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = WsAccent, contentColor = Color.White,
                disabledContainerColor = WsAccent.copy(alpha = 0.4f),
                disabledContentColor = Color.White.copy(alpha = 0.6f),
            ),
            contentPadding = contentPad, modifier = commonMod,
        ) { inner() }

        WsButtonVariant.TERTIARY -> TextButton(
            onClick = onClick, enabled = isEnabled, interactionSource = interaction,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            contentPadding = contentPad, modifier = commonMod,
        ) { inner() }

        else -> {  // SECONDARY, DANGER, WARNING — todos OutlinedButton com cor de borda própria
            val borderColor = when (variant) {
                WsButtonVariant.SECONDARY -> WsBorderLight
                WsButtonVariant.DANGER    -> WsDanger.copy(alpha = 0.6f)
                WsButtonVariant.WARNING   -> WsWarning.copy(alpha = 0.6f)
                else                      -> WsBorderLight
            }
            OutlinedButton(
                onClick = onClick, enabled = isEnabled, interactionSource = interaction,
                shape = shape,
                border = BorderStroke(1.dp, borderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
                contentPadding = contentPad, modifier = commonMod,
            ) { inner() }
        }
    }
}

/* ───────────────────────────────────────────────────────────────────────────
 * WsTypeSelector — resolve a quebra do "Ajuste"/"Estorno" (Opção B escolhida).
 * Três tipos comuns em grade com ícone; Ajuste e Estorno como links discretos
 * abaixo de uma divisória. Cabe na largura padrão do painel (~340dp) sem quebrar.
 *
 * Substitui o bloco TransactionType.values().forEach { WsFilterChip(...) }
 * em TransactionDetailsPanel.kt.
 * ─────────────────────────────────────────────────────────────────────────── */
@Composable
fun WsTypeSelector(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WsSpace.md)) {
        // Três comuns, cada um ocupa 1/3 com weight — nunca aperta
        Row(horizontalArrangement = Arrangement.spacedBy(WsSpace.sm)) {
            TypeTile(TransactionType.INCOME,   "Receita",       selected, onSelect, Modifier.weight(1f))
            TypeTile(TransactionType.EXPENSE,  "Despesa",       selected, onSelect, Modifier.weight(1f))
            TypeTile(TransactionType.TRANSFER, "Transferência", selected, onSelect, Modifier.weight(1f))
        }
        // Divisória sutil separando comum de raro
        HorizontalDivider(color = WsBorder.copy(alpha = 0.4f))
        // Raros como links discretos
        Row(horizontalArrangement = Arrangement.spacedBy(WsSpace.lg)) {
            RareTypeLink(TransactionType.ADJUSTMENT, "Ajuste de saldo", selected, onSelect)
            RareTypeLink(TransactionType.REVERSAL,   "Estorno",         selected, onSelect)
        }
    }
}

@Composable
private fun TypeTile(
    type: TransactionType,
    label: String,
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSel = selected == type
    val interaction = remember { MutableInteractionSource() }
    val icon = when (type) {
        TransactionType.INCOME   -> Icons.Default.ArrowDownward
        TransactionType.EXPENSE  -> Icons.Default.ArrowUpward
        else                     -> Icons.Default.SwapHoriz
    }
    Column(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(WsRadius.md))
            .background(if (isSel) WsAccent else WsSurface)
            .border(1.dp, if (isSel) WsAccent else WsBorderLight, RoundedCornerShape(WsRadius.md))
            .hoverable(interaction)
            .wsPressScale(interaction)
            .androidxClickableInternal(interaction) { onSelect(type) }
            .padding(vertical = WsSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (isSel) Color.White else WsTextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = if (isSel) Color.White else WsTextSecondary, maxLines = 1)
    }
}

@Composable
private fun RareTypeLink(
    type: TransactionType,
    label: String,
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
) {
    val isSel = selected == type
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .clip(RoundedCornerShape(WsRadius.sm))
            .androidxClickableInternal(interaction) { onSelect(type) }
            .padding(horizontal = WsSpace.sm, vertical = WsSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Add, null,
            tint = if (isSel) WsAccent else WsTextDisabled, modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(WsSpace.xs))
        Text(label, fontSize = 11.sp, color = if (isSel) WsAccent else WsTextSecondary)
    }
}

// clickable interno sem indication (o hover/scale já dá o feedback)
private fun Modifier.androidxClickableInternal(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interaction, indication = null, onClick = onClick,
)

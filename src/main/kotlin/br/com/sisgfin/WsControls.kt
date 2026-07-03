package br.com.sisgfin

/*
 * ═══════════════════════════════════════════════════════════════════════════
 *  WsControls.kt — Componentes-raiz corrigidos (Onda 1 do acabamento)
 * ───────────────────────────────────────────────────────────────────────────
 *  Cada componente aqui resolve um problema da auditoria que se multiplica
 *  por muitas telas. Corrigir aqui = corrigir em toda parte.
 *
 *  Depende de WsDesignSystem.kt (WsSize, WsRadius, WsSpace, WsSpinner...).
 *  Mantém a MESMA assinatura pública dos Ws* antigos + parâmetros novos com
 *  default, para você trocar sem quebrar as chamadas existentes.
 * ═══════════════════════════════════════════════════════════════════════════
 */

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ───────────────────────────────────────────────────────────────────────────
 * PROBLEMA #1 da auditoria — WsButton sem `enabled` e sem estado de loading.
 * Consequência: Login, Salvar, Exportar aceitam duplo-clique durante a coroutine.
 *
 * Correção: `enabled` + `loading`. Quando loading=true, o botão desabilita
 * sozinho e troca o conteúdo por um spinner — não há como disparar 2×.
 * ─────────────────────────────────────────────────────────────────────────── */
@Composable
fun WsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,          // NOVO
    height: androidx.compose.ui.unit.Dp = WsSize.control,  // 40dp — unificado
) {
    val isEnabled = enabled && !loading
    val interaction = remember { MutableInteractionSource() }

    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = isEnabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(WsRadius.md),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = WsAccent,
            contentColor = Color.White,
            disabledContainerColor = WsAccent.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = WsSpace.lg, vertical = 0.dp),
        modifier = modifier
            .height(height)
            .wsPressScale(interaction),
    ) {
        if (loading) {
            WsSpinner(size = 16.dp, stroke = 2.dp, color = Color.White)
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
}

/* ───────────────────────────────────────────────────────────────────────────
 * PROBLEMA #2 — WsIconButton sem hover, sem enabled, sem focus.
 * Correção: hover real via hoverable + press-scale + enabled.
 * ─────────────────────────────────────────────────────────────────────────── */
@Composable
fun WsIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,           // NOVO
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
            .size(WsSize.icon)                       // 40dp — unificado
            .clip(RoundedCornerShape(WsRadius.md))
            .background(bg)
            .border(1.dp, WsBorder.copy(alpha = if (hovered) 0.8f else 0.4f), RoundedCornerShape(WsRadius.md))
            .hoverable(interaction, enabled = enabled)
            .wsPressScale(interaction)
            .then(
                if (enabled) Modifier.androidxClickable(interaction, onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription,
            tint = if (enabled) WsTextSecondary else WsTextDisabled,
            modifier = Modifier.size(WsSize.iconInner),
        )
    }
}

// helper para clickable sem indication redundante (o hover já dá o feedback)
private fun Modifier.androidxClickable(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interaction,
    indication = null,
    onClick = onClick,
)

/* ───────────────────────────────────────────────────────────────────────────
 * PROBLEMA #2 (raiz) — 9 telas com CircularProgressIndicator sem size.
 * Correção: UM loader com tamanho e stroke fixos. Substitua TODOS os
 * CircularProgressIndicator(...) inline por WsLoader() ou WsLoaderFullscreen().
 * ─────────────────────────────────────────────────────────────────────────── */
@Composable
fun WsLoader(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 24.dp,   // tamanho canônico do projeto
    color: Color = WsAccent,
) {
    WsSpinner(modifier = modifier, size = size, stroke = 2.dp, color = color)
}

/** Loader centralizado para o early-return de tela cheia (o padrão das 9 telas). */
@Composable
fun WsLoaderFullscreen(label: String? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WsLoader(size = 28.dp)
            if (label != null) {
                Spacer(Modifier.height(WsSpace.md))
                Text(label, color = WsTextSecondary, fontSize = 13.sp)
            }
        }
    }
}

/* ───────────────────────────────────────────────────────────────────────────
 * PROBLEMA #5.4 — BudgetScreen usa Box aninhado à mão para barra de progresso;
 * Contracts/Reports usam LinearProgressIndicator com raios diferentes (2/3dp).
 * Correção: um único WsProgressBar com raio pill consistente + cor semântica.
 * (Já definido em WsDesignSystem.kt; aqui vai a variante semântica de orçamento.)
 * ─────────────────────────────────────────────────────────────────────────── */
@Composable
fun WsBudgetBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val color = when {
        progress >= 1.0f -> WsDanger
        progress >= 0.8f -> WsWarning
        else             -> WsSuccess
    }
    WsProgressBar(progress = progress, modifier = modifier, height = 8.dp, color = color)
}

/* ───────────────────────────────────────────────────────────────────────────
 * PROBLEMA #8 — EmptyState ausente/inconsistente em 5 telas.
 * Correção: UM composable padrão. Empregue em Employees e Users (ausentes) e
 * troque as versões inline de Dashboard/Contracts/Receivables por este.
 * ─────────────────────────────────────────────────────────────────────────── */
@Composable
fun WsEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().padding(WsSpace.xxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = WsTextDisabled, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(WsSpace.md))
            Text(message, color = WsTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(WsSpace.lg))
                WsButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/* ───────────────────────────────────────────────────────────────────────────
 * PROBLEMA #3 — hover como código morto (isHovered nunca atualizado) em
 * FinancialAccounts, Supplier, User.
 * Correção: um wrapper de linha que LIGA o hover de verdade. Troque os
 * Row(... background(if(isHovered)...)) manuais por este.
 * ─────────────────────────────────────────────────────────────────────────── */
@Composable
fun WsRowHoverable(
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg = when {
        selected -> WsAccent.copy(alpha = 0.12f)
        hovered  -> WsElevated
        else     -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WsRadius.sm))
            .background(bg)
            .hoverable(interaction)                       // <-- o que faltava
            .then(if (onClick != null) Modifier.androidxClickable(interaction, onClick) else Modifier)
            .padding(horizontal = WsSpace.lg, vertical = WsSpace.md),
        verticalAlignment = Alignment.CenterVertically,   // PROBLEMA #4 — alinhamento
        content = content,
    )
}

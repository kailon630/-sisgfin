package br.com.sisgfin

/*
 * ═══════════════════════════════════════════════════════════════════════════
 *  WsDesignSystem.kt — Sistema de design unificado do SisgFin
 * ───────────────────────────────────────────────────────────────────────────
 *  Objetivo: matar o desalinhamento "no olho" na raiz. Todo espaçamento,
 *  raio, altura de controle e elevação sai daqui. Se uma tela parecer torta,
 *  a causa é ela não estar usando estes tokens — não um valor errado.
 *
 *  Ordem de adoção sugerida:
 *   1. Cole este arquivo no pacote br.com.sisgfin
 *   2. Refatore DesktopComponents.kt para consumir WsSize/WsRadius/WsSpace
 *   3. Troque as bordas 1dp de cards/tabelas por Modifier.wsSurface(...)
 *   4. Substitua o LoadingOverlay por WsSkeleton nas listas
 * ═══════════════════════════════════════════════════════════════════════════
 */

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ───────────────────────────────────────────────────────────────────────────
 * 1. RÉGUA — a fonte única de verdade para dimensões
 * ─────────────────────────────────────────────────────────────────────────── */

/** Espaçamento em grid rígido de 4pt. Use SEMPRE em vez de números soltos. */
object WsSpace {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 24.dp
    val xxl = 32.dp
}

/** Escala de raio. Superfícies grandes ficam mais orgânicas (lg/xl). */
object WsRadius {
    val sm = 8.dp    // chips, badges, dots
    val md = 12.dp   // botões, inputs, table rows
    val lg = 16.dp   // cards, KPI tiles, containers de tabela
    val xl = 20.dp   // painel direito, dialogs
}

/**
 * Alturas de controle UNIFICADAS. Este é o coração da correção de alinhamento:
 * input, botão e busca passam todos a 40dp -> baseline alinhada automaticamente.
 * O antigo par 50dp (input) + 36dp (botão) era a causa do desalinhamento.
 */
object WsSize {
    val control   = 40.dp   // input, botão padrão, select, busca
    val controlLg = 48.dp   // botão primário de destaque
    val icon      = 40.dp   // WsIconButton
    val iconInner = 18.dp   // ícone dentro de chip/botão
    val avatar    = 28.dp
}

/* ───────────────────────────────────────────────────────────────────────────
 * 2. ELEVAÇÃO NATIVA — profundidade por luz, não por linha
 *    Resolve o "chapado": sombra suave + borda de highlight quase transparente.
 * ─────────────────────────────────────────────────────────────────────────── */

/**
 * Superfície elevada padrão. Substitui o combo antigo
 * `.border(1.dp, WsBorder).background(WsSurface)` que deixava tudo no mesmo plano.
 *
 * No dark mode a sombra sozinha some (preto sobre quase-preto), por isso
 * combinamos sombra + borda de highlight sutil para desenhar a aresta.
 */
@Composable
fun Modifier.wsSurface(
    shape: Shape = RoundedCornerShape(WsRadius.lg),
    elevation: Dp = 2.dp,
    surface: Color = WsSurface,
    border: Color = WsBorder,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.35f),
        spotColor = Color.Black.copy(alpha = 0.45f),
    )
    .clip(shape)
    .background(surface)
    .border(1.dp, border.copy(alpha = 0.4f), shape)

/** Feedback tátil: o controle "afunda" levemente no clique. */
@Composable
fun Modifier.wsPressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(80),
        label = "wsPressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/* ───────────────────────────────────────────────────────────────────────────
 * 3. COMPONENTES DE ESTADO — o que faltava (progress, spinner, skeleton)
 * ─────────────────────────────────────────────────────────────────────────── */

/**
 * Barra de progresso determinada (0f..1f). Usada em Orçamento e uploads OFX.
 * Cantos totalmente arredondados + animação suave do preenchimento.
 */
@Composable
fun WsProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = WsAccent,
    track: Color = WsElevated,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "wsProgress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(percent = 50))
                .background(color),
        )
    }
}

/**
 * Barra indeterminada — para "carregando sem % conhecido".
 * Um bloco desliza continuamente da esquerda para a direita.
 */
@Composable
fun WsIndeterminateBar(
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    color: Color = WsAccent,
    track: Color = WsElevated,
) {
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val offset by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "indeterminateOffset",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .graphicsLayer { translationX = size.width * (offset / 0.4f) }
                .clip(RoundedCornerShape(percent = 50))
                .background(color),
        )
    }
}

/**
 * Spinner circular leve — para botões em estado de "salvando".
 * Sem depender do CircularProgressIndicator do M3 (que traz padding próprio).
 */
@Composable
fun WsSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    stroke: Dp = 2.dp,
    color: Color = WsAccent,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "spinnerAngle",
    )
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        drawArc(
            color = color.copy(alpha = 0.25f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke.toPx()),
        )
        drawArc(
            color = color,
            startAngle = angle, sweepAngle = 90f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

/**
 * Skeleton com brilho deslizante (shimmer) — substitui o LoadingOverlay
 * "spinner sobre véu" (que é padrão web) enquanto listas carregam.
 * Coloque N destes no lugar das linhas da tabela durante o loading.
 */
@Composable
fun WsSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(WsRadius.sm),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)),
        label = "shimmerX",
    )
    val base = WsElevated
    val highlight = WsBorder.copy(alpha = 0.6f)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(base, highlight, base),
                    startX = x * 300f,
                    endX = (x + 1f) * 300f,
                )
            ),
    )
}

/** Linha de tabela em estado skeleton — solta N num LazyColumn durante o load. */
@Composable
fun WsTableRowSkeleton() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = WsSpace.lg, vertical = WsSpace.md),
        horizontalArrangement = Arrangement.spacedBy(WsSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WsSkeleton(Modifier.height(14.dp).weight(2f))
        WsSkeleton(Modifier.height(14.dp).weight(1f))
        WsSkeleton(Modifier.height(14.dp).weight(1f))
    }
}

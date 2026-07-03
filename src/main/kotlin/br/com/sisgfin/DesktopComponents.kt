package br.com.sisgfin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// ── Item 1: MoneyText ─────────────────────────────────────────────────────────
// Componente padronizado para exibir valores monetários com fonte monospace,
// garantindo alinhamento perfeito em colunas de tabela.

@Composable
fun MoneyText(
    amount: Money,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    large: Boolean = false,
    textAlign: TextAlign = TextAlign.End
) {
    Text(
        text      = MoneyFormatter.format(amount),
        modifier  = modifier,
        style     = if (large) WsMoneyStyleLarge else WsMoneyStyle,
        color     = color,  // Color.Unspecified falls back to WsMoneyStyle.color (= WsTextPrimary)
        textAlign = textAlign
    )
}

// ── Item 2: CrudToolbar com busca real ────────────────────────────────────────

@Composable
fun CrudToolbar(
    title: String,
    subtitle: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    newItemLabel: String,
    onNewItemClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item 2: busca funcional conectada ao viewmodel via onSearchQueryChange
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.width(240.dp).height(40.dp),
                placeholder = {
                    Text(
                        "Filtrar...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WsTextDisabled
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = WsTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = WsTextPrimary),
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = WsAccent,
                    unfocusedBorderColor = WsBorder,
                    focusedContainerColor   = WsBackground,
                    unfocusedContainerColor = WsBackground,
                    cursorColor = WsAccent
                )
            )

            WsButton(label = newItemLabel, icon = Icons.Default.Add, onClick = onNewItemClick)
            WsIconButton(Icons.Default.Refresh, onClick = onRefreshClick)
        }
    }
}

// ── Item 3: WsTableRow com hover real ─────────────────────────────────────────
// Usa Modifier.hoverable + collectIsHoveredAsState() do Compose Desktop.
// Substitui o padrão manual `var isHovered by remember { mutableStateOf(false) }`
// que nunca era atualizado por falta de pointerHoverIcon / hoverable.

@Composable
fun WsTableRow(
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bg = when {
        selected  -> WsAccent.copy(alpha = 0.08f)
        isHovered -> WsElevated
        else      -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .hoverable(interactionSource)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ── Item 6: TopToolbar sem Sync falso ────────────────────────────────────────
// Remove ícone Sync decorativo que implicava sincronização inexistente.
// Adiciona nome/tela atual para contexto quando sidebar está colapsada.

@Composable
fun TopToolbar(username: String, screenTitle: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(WsSurface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Breadcrumb / context
        if (screenTitle.isNotEmpty()) {
            Text(
                screenTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = WsTextPrimary
                )
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Notificações (placeholder funcional)
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Notificações",
                tint = WsTextSecondary,
                modifier = Modifier.size(18.dp)
            )

            VerticalDivider(
                modifier = Modifier.height(24.dp),
                color = WsBorder
            )

            // Avatar + nome do usuário
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(WsAccent.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        username.take(1).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WsAccent
                    )
                }
                Text(
                    text  = username,
                    style = MaterialTheme.typography.bodyMedium.copy(color = WsTextPrimary)
                )
            }
        }
    }
    HorizontalDivider(color = WsBorder)
}

// ── Item 9: WsSelectField visualmente idêntico ao WsTextField ────────────────
// Usa Box + OutlinedTextField readOnly + overlay clickável.
// Mesma altura (50dp), mesmo border radius (6dp) e mesmas cores que WsTextField.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WsSelectField(
    label: String,
    options: List<Pair<Int, String>>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    placeholder: String = "Selecionar...",
    nullable: Boolean = true,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedId }?.second ?: placeholder

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) WsTextSecondary else WsTextDisabled
        )
        Box {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(6.dp),
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = if (enabled) WsTextSecondary else WsTextDisabled,
                        modifier = Modifier.size(20.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor    = if (expanded) WsAccent else WsBorder,
                    disabledContainerColor = if (enabled) WsBackground else WsElevated,
                    disabledTextColor      = if (selectedId == null) WsTextDisabled else WsTextPrimary,
                    disabledTrailingIconColor = WsTextSecondary
                )
            )
            // Overlay invisível captura cliques sem interferir no TextField
            if (enabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = true }
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = WsElevated,
                modifier = Modifier
                    .widthIn(min = 200.dp)
                    .heightIn(max = 300.dp)
            ) {
                if (nullable && selectedId != null) {
                    DropdownMenuItem(
                        text = { Text("— Nenhum —", color = WsTextSecondary) },
                        onClick = { onSelect(null); expanded = false }
                    )
                    HorizontalDivider(color = WsBorder)
                }
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                color = WsTextPrimary
                            )
                        },
                        onClick = { onSelect(id); expanded = false }
                    )
                }
            }
        }
    }
}

// ── Item 10: WsDateField com máscara dd/MM/yyyy e validação em tempo real ─────

private class DateMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(8)
        val out = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('/')
                append(c)
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                offset <= 8 -> offset + 2
                else        -> out.length
            }
            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                offset <= 10 -> offset - 2
                else         -> digits.length
            }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

private val dateMaskFmt = DateTimeFormatter.ofPattern("ddMMyyyy")

@Composable
fun WsDateField(
    label: String,
    value: String,          // Formato "dd/MM/yyyy" ou parcial; internamente usa dígitos
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit  // Devolve dígitos (max 8), ou "" se vazio
) {
    val digits = value.filter { it.isDigit() }.take(8)

    val isError = digits.length == 8 && runCatching {
        LocalDate.parse(digits, dateMaskFmt)
    }.isFailure

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    !enabled -> WsTextDisabled
                    isError  -> WsDanger
                    else     -> WsTextSecondary
                }
            )
            if (isError) {
                Text(
                    "data inválida",
                    style = MaterialTheme.typography.labelMedium,
                    color = WsDanger
                )
            }
        }
        OutlinedTextField(
            value = digits,
            onValueChange = { input ->
                val newDigits = input.filter { it.isDigit() }.take(8)
                onValueChange(newDigits)
            },
            enabled = enabled,
            isError = isError,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            singleLine = true,
            placeholder = {
                Text(
                    "dd/mm/aaaa",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WsTextDisabled
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = if (isError) WsDanger else WsTextDisabled,
                    modifier = Modifier.size(16.dp)
                )
            },
            visualTransformation = DateMaskTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = if (isError) WsDanger else WsAccent,
                unfocusedBorderColor    = if (isError) WsDanger else WsBorder,
                focusedContainerColor   = WsBackground,
                unfocusedContainerColor = WsBackground,
                disabledBorderColor     = WsBorder,
                disabledContainerColor  = WsElevated,
                disabledTextColor       = WsTextSecondary,
                cursorColor             = WsAccent,
                errorBorderColor        = WsDanger,
                errorContainerColor     = WsDanger.copy(alpha = 0.05f)
            )
        )
    }
}

// ── Componentes existentes mantidos ───────────────────────────────────────────

@Composable
fun TableHeaderCell(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text      = text,
        modifier  = modifier,
        style     = MaterialTheme.typography.labelMedium,
        textAlign = textAlign,
        color     = WsTextSecondary
    )
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Inbox, null, modifier = Modifier.size(48.dp), tint = WsTextDisabled)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = WsTextSecondary)
        }
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
            color = WsAccent
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

// ── Item 14: StatusBar com informação dinâmica ────────────────────────────────

@Composable
fun StatusBar(username: String = "", overdueCount: Int = 0) {
    // Atualiza o relógio a cada minuto
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            now = java.time.LocalDateTime.now()
        }
    }

    val timeFmt  = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val dateFmt2 = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(WsBackground)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "${now.format(dateFmt2)} — ${now.format(timeFmt)}",
                style = MaterialTheme.typography.labelMedium,
                color = WsTextSecondary
            )
            if (username.isNotEmpty()) {
                Text(
                    "Usuário: $username",
                    style = MaterialTheme.typography.labelMedium,
                    color = WsTextSecondary
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (overdueCount > 0) {
                Text(
                    "$overdueCount vencido${if (overdueCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = WsDanger
                )
            }
            Text(
                "SisgFin v1.1.0",
                style = MaterialTheme.typography.labelMedium,
                color = WsTextDisabled
            )
        }
    }
}

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
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WsBorderLight),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = effectiveColor),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        content = content
    )
}

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
            containerColor         = WsSurface,
            labelColor             = WsTextSecondary,
            selectedContainerColor = WsAccent.copy(alpha = 0.15f),
            selectedLabelColor     = WsAccent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled             = true,
            selected            = selected,
            borderColor         = WsBorderLight,
            selectedBorderColor = WsAccent.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun WsButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val effectiveContainer = if (containerColor == Color.Unspecified) WsAccent else containerColor
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = effectiveContainer,
            contentColor   = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp))
    }
}

@Composable
fun WsIconButton(icon: ImageVector, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    val effectiveTint = if (tint == Color.Unspecified) WsTextSecondary else tint
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(6.dp))
            .background(WsSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = effectiveTint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun WsTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) WsTextSecondary else WsTextDisabled
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = WsAccent,
                unfocusedBorderColor    = WsBorder,
                focusedContainerColor   = WsBackground,
                unfocusedContainerColor = WsBackground,
                focusedTextColor        = WsTextPrimary,
                unfocusedTextColor      = WsTextPrimary,
                disabledBorderColor     = WsBorder,
                disabledContainerColor  = WsElevated,
                disabledTextColor       = WsTextSecondary,
                cursorColor             = WsAccent
            )
        )
    }
}

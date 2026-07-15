package br.com.sisgfin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
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
import br.com.sisgfin.core.validation.DocumentValidator
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
            val searchInteraction = remember { MutableInteractionSource() }
            val searchFocused by searchInteraction.collectIsFocusedAsState()
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = WsTextPrimary),
                cursorBrush = SolidColor(WsAccent),
                interactionSource = searchInteraction,
                modifier = Modifier.width(240.dp),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(WsBackground)
                            .border(1.dp, if (searchFocused) WsAccent else WsBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = WsTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text("Filtrar...", style = MaterialTheme.typography.bodyMedium, color = WsTextDisabled)
                            }
                            innerTextField()
                        }
                    }
                }
            )

            WsButton(text = newItemLabel, icon = Icons.Default.Add, onClick = onNewItemClick)
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
            .padding(horizontal = WsSpace.lg, vertical = WsSpace.md),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WsSize.control)
                    .clip(RoundedCornerShape(WsRadius.md))
                    .background(if (enabled) WsBackground else WsElevated)
                    .border(1.dp, if (expanded) WsAccent else WsBorder, RoundedCornerShape(WsRadius.md))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedId == null) WsTextDisabled else (if (enabled) WsTextPrimary else WsTextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (enabled) WsTextSecondary else WsTextDisabled,
                    modifier = Modifier.size(20.dp)
                )
            }
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
        val dateInteraction = remember { MutableInteractionSource() }
        val dateFocused by dateInteraction.collectIsFocusedAsState()
        val borderColor = when {
            isError   -> WsDanger
            dateFocused -> WsAccent
            else      -> WsBorder
        }
        BasicTextField(
            value = digits,
            onValueChange = { input ->
                val newDigits = input.filter(Char::isDigit).take(8)
                onValueChange(newDigits)
            },
            enabled = enabled,
            singleLine = true,
            visualTransformation = DateMaskTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) WsTextPrimary else WsTextSecondary
            ),
            cursorBrush = SolidColor(if (isError) WsDanger else WsAccent),
            interactionSource = dateInteraction,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WsSize.control)
                        .clip(RoundedCornerShape(WsRadius.md))
                        .background(
                            when {
                                !enabled -> WsElevated
                                isError  -> WsDanger.copy(alpha = 0.05f)
                                else     -> WsBackground
                            }
                        )
                        .border(1.dp, borderColor, RoundedCornerShape(WsRadius.md))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = if (isError) WsDanger else WsTextDisabled,
                        modifier = Modifier.size(16.dp)
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (digits.isEmpty()) {
                            Text(
                                "dd/mm/aaaa",
                                style = MaterialTheme.typography.bodyLarge,
                                color = WsTextDisabled
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
}

// ── WsDocumentField — campo CPF / CNPJ com máscara e toggle de tipo ──────────

enum class DocumentType(val label: String, val digitCount: Int, val placeholder: String) {
    CPF ("CPF",  11, "000.000.000-00"),
    CNPJ("CNPJ", 14, "00.000.000/0000-00")
}

private class CpfMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(11)
        val out = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 3 || i == 6) append('.')
                if (i == 9) append('-')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 3  -> offset
                offset <= 6  -> offset + 1
                offset <= 9  -> offset + 2
                else         -> minOf(offset + 3, out.length)
            }
            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 3  -> offset
                offset <= 7  -> offset - 1
                offset <= 11 -> offset - 2
                else         -> minOf(offset - 3, digits.length)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

private class CnpjMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(14)
        val out = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 5) append('.')
                if (i == 8) append('/')
                if (i == 12) append('-')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2  -> offset
                offset <= 5  -> offset + 1
                offset <= 8  -> offset + 2
                offset <= 12 -> offset + 3
                else         -> minOf(offset + 4, out.length)
            }
            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2  -> offset
                offset <= 6  -> offset - 1
                offset <= 10 -> offset - 2
                offset <= 15 -> offset - 3
                else         -> minOf(offset - 4, digits.length)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/**
 * Campo de documento com máscara automática e toggle CPF / CNPJ.
 * [value] deve conter apenas dígitos (sem pontuação) — o componente aplica a máscara visualmente.
 * [stateKey] deve ser o ID do registro atual para que o tipo detectado redefina ao trocar de registro.
 * [allowedTypes] com um único elemento oculta o toggle e fixa o tipo.
 */
@Composable
fun WsDocumentField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowedTypes: Set<DocumentType> = setOf(DocumentType.CPF, DocumentType.CNPJ),
    stateKey: Any? = Unit
) {
    val digits = value.filter { it.isDigit() }

    var selectedType by remember(stateKey) {
        mutableStateOf(
            when {
                digits.length == 14 && DocumentType.CNPJ in allowedTypes -> DocumentType.CNPJ
                else -> allowedTypes.first()
            }
        )
    }

    val maxDigits = selectedType.digitCount
    val isComplete = digits.length == maxDigits
    val isError = isComplete && when (selectedType) {
        DocumentType.CPF  -> !DocumentValidator.isValidCpf(digits)
        DocumentType.CNPJ -> !DocumentValidator.isValidCnpj(digits)
    }

    val transformation: VisualTransformation = remember(selectedType) {
        if (selectedType == DocumentType.CPF) CpfMaskTransformation() else CnpjMaskTransformation()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                selectedType.label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    !enabled -> WsTextDisabled
                    isError  -> WsDanger
                    else     -> WsTextSecondary
                }
            )
            if (isError) {
                Text(
                    "${selectedType.label} inválido",
                    style = MaterialTheme.typography.labelMedium,
                    color = WsDanger
                )
            }
            if (allowedTypes.size > 1) {
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DocumentType.entries.filter { it in allowedTypes }.forEach { type ->
                        val selected = type == selectedType
                        Surface(
                            onClick = {
                                if (!selected) {
                                    selectedType = type
                                    onValueChange("")
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            color = if (selected) WsAccent else Color.Transparent,
                            border = if (!selected) BorderStroke(1.dp, WsBorder) else null
                        ) {
                            Text(
                                type.label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else WsTextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val borderColor = when {
            isError -> WsDanger
            focused -> WsAccent
            else    -> WsBorder
        }

        BasicTextField(
            value = digits.take(maxDigits),
            onValueChange = { input ->
                onValueChange(input.filter(Char::isDigit).take(maxDigits))
            },
            enabled = enabled,
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) WsTextPrimary else WsTextSecondary
            ),
            cursorBrush = SolidColor(if (isError) WsDanger else WsAccent),
            interactionSource = interaction,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WsSize.control)
                        .clip(RoundedCornerShape(WsRadius.md))
                        .background(
                            when {
                                !enabled -> WsElevated
                                isError  -> WsDanger.copy(alpha = 0.05f)
                                else     -> WsBackground
                            }
                        )
                        .border(1.dp, borderColor, RoundedCornerShape(WsRadius.md))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
                        if (digits.isEmpty()) {
                            Text(
                                selectedType.placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = WsTextDisabled
                            )
                        }
                        innerTextField()
                    }
                }
            }
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

// WsButton e WsIconButton agora definidos em WsControls.kt (Onda 1)

@Composable
fun WsTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) WsTextSecondary else WsTextDisabled
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) WsTextPrimary else WsTextSecondary
            ),
            cursorBrush = SolidColor(WsAccent),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WsSize.control)
                        .clip(RoundedCornerShape(WsRadius.md))
                        .background(if (enabled) WsBackground else WsElevated)
                        .border(1.dp, if (isFocused) WsAccent else WsBorder, RoundedCornerShape(WsRadius.md))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    innerTextField()
                }
            }
        )
    }
}

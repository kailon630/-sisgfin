package br.com.sisgfin

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Sidebar(
    currentScreen: Screen,
    sessionManager: SessionManager,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val currentUser by sessionManager.currentUser.collectAsState()
    val isAdmin = currentUser?.role == UserRole.ADMIN

    var isExpanded by remember { mutableStateOf(currentScreen is Screen.Dashboard) }
    LaunchedEffect(currentScreen) {
        isExpanded = currentScreen is Screen.Dashboard
    }
    val width by animateDpAsState(targetValue = if (isExpanded) 220.dp else 64.dp)

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(WsSurface)
            .padding(vertical = 12.dp),
        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
    ) {
        // ── Logo ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isExpanded) 12.dp else 0.dp)
                .height(44.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource("icon.png"),
                    contentDescription = "SisgFin",
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit
                )
                if (isExpanded) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "SisgFin",
                        color = WsTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Itens de navegação ───────────────────────────────────────────────
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.weight(1f).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
        ) {
            // Início
            SidebarItem(
                icon     = Icons.Outlined.Dashboard,
                label    = "Dashboard",
                selected = currentScreen is Screen.Dashboard,
                expanded = isExpanded
            ) { onNavigate(Screen.Dashboard) }

            SidebarItem(
                icon     = Icons.AutoMirrored.Outlined.ShowChart,
                label    = "Fluxo de Caixa",
                selected = currentScreen is Screen.CashFlow,
                expanded = isExpanded
            ) { onNavigate(Screen.CashFlow) }

            // ── Item 7: Label de grupo FINANCEIRO ────────────────────────────
            SidebarGroupLabel("FINANCEIRO", isExpanded)

            SidebarItem(
                icon     = Icons.Outlined.AccountBalanceWallet,
                label    = "Movimentações",
                selected = currentScreen is Screen.Transactions,
                expanded = isExpanded
            ) { onNavigate(Screen.Transactions) }

            SidebarItem(
                icon     = Icons.Outlined.Payments,
                label    = "Recebíveis",
                selected = currentScreen is Screen.Receivables,
                expanded = isExpanded
            ) { onNavigate(Screen.Receivables) }

            SidebarItem(
                icon     = Icons.Outlined.Summarize,
                label    = "Painel de Saldos",
                selected = currentScreen is Screen.Balances,
                expanded = isExpanded
            ) { onNavigate(Screen.Balances) }

            SidebarItem(
                icon     = Icons.Outlined.Receipt,
                label    = "Extrato",
                selected = currentScreen is Screen.Statement,
                expanded = isExpanded
            ) { onNavigate(Screen.Statement) }

            SidebarItem(
                icon     = Icons.Outlined.AccountBalance,
                label    = "Contas e Caixas",
                selected = currentScreen is Screen.Accounts,
                expanded = isExpanded
            ) { onNavigate(Screen.Accounts) }

            SidebarItem(
                icon     = Icons.Outlined.FileDownload,
                label    = "Importar OFX",
                selected = currentScreen is Screen.OfxImport,
                expanded = isExpanded
            ) { onNavigate(Screen.OfxImport) }

            SidebarItem(
                icon     = Icons.Outlined.Repeat,
                label    = "Recorrências",
                selected = currentScreen is Screen.Recurring,
                expanded = isExpanded
            ) { onNavigate(Screen.Recurring) }

            SidebarItem(
                icon     = Icons.Outlined.PieChart,
                label    = "Orçamento",
                selected = currentScreen is Screen.Budget,
                expanded = isExpanded
            ) { onNavigate(Screen.Budget) }

            // ── Item 7: Label de grupo CADASTROS ─────────────────────────────
            SidebarGroupLabel("CADASTROS", isExpanded)

            SidebarItem(
                icon     = Icons.Outlined.LocalShipping,
                label    = "Fornecedores",
                selected = currentScreen is Screen.Suppliers,
                expanded = isExpanded
            ) { onNavigate(Screen.Suppliers) }

            SidebarItem(
                icon     = Icons.Outlined.Person,
                label    = "Clientes",
                selected = currentScreen is Screen.Clients,
                expanded = isExpanded
            ) { onNavigate(Screen.Clients) }

            SidebarItem(
                icon     = Icons.Outlined.WorkOutline,
                label    = "Centros de Custo",
                selected = currentScreen is Screen.CostCenters,
                expanded = isExpanded
            ) { onNavigate(Screen.CostCenters) }

            SidebarItem(
                icon     = Icons.Outlined.Category,
                label    = "Plano de Contas",
                selected = currentScreen is Screen.Categories,
                expanded = isExpanded
            ) { onNavigate(Screen.Categories) }

            // ── Item 7: Label de grupo GESTÃO ────────────────────────────────
            SidebarGroupLabel("GESTÃO", isExpanded)

            SidebarItem(
                icon     = Icons.Outlined.Description,
                label    = "Contratos",
                selected = currentScreen is Screen.Contracts,
                expanded = isExpanded
            ) { onNavigate(Screen.Contracts) }

            SidebarItem(
                icon     = Icons.Outlined.Badge,
                label    = "Funcionários",
                selected = currentScreen is Screen.Employees,
                expanded = isExpanded
            ) { onNavigate(Screen.Employees) }

            SidebarItem(
                icon     = Icons.Outlined.UploadFile,
                label    = "Importar Folha",
                selected = currentScreen is Screen.PayrollImport,
                expanded = isExpanded
            ) { onNavigate(Screen.PayrollImport) }

            if (isAdmin) {
                SidebarItem(
                    icon     = Icons.Outlined.People,
                    label    = "Usuários",
                    selected = currentScreen is Screen.UserManagement,
                    expanded = isExpanded
                ) { onNavigate(Screen.UserManagement) }
            }

            SidebarItem(
                icon     = Icons.Outlined.Assessment,
                label    = "Relatórios",
                selected = currentScreen is Screen.Reports,
                expanded = isExpanded
            ) { onNavigate(Screen.Reports) }
        }

        // ── Rodapé ────────────────────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
        ) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = WsBorder
            )

            SidebarItem(
                icon     = Icons.Outlined.Settings,
                label    = "Configurações",
                selected = currentScreen is Screen.Settings,
                expanded = isExpanded
            ) { onNavigate(Screen.Settings) }

            SidebarItem(
                icon     = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                label    = if (isDarkTheme) "Tema Claro" else "Tema Escuro",
                selected = false,
                expanded = isExpanded
            ) { onToggleTheme() }

            SidebarItem(
                icon     = Icons.AutoMirrored.Outlined.Logout,
                label    = "Sair",
                selected = false,
                expanded = isExpanded,
                tint     = WsDanger
            ) { onLogout() }

            Spacer(Modifier.height(4.dp))

            // ── Item 8: Botão de toggle dedicado (Chevron) ───────────────────
            SidebarToggleButton(isExpanded = isExpanded) { isExpanded = !isExpanded }
        }
    }
}

// ── Item 7: Label de seção da sidebar ────────────────────────────────────────

@Composable
private fun SidebarGroupLabel(text: String, expanded: Boolean) {
    if (expanded) {
        Text(
            text = text,
            modifier = Modifier
                .padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 10.sp,
                letterSpacing = 1.sp
            ),
            color = WsTextDisabled
        )
    } else {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = WsBorder
        )
    }
}

// ── Item 8: Botão de toggle com chevron ──────────────────────────────────────

@Composable
private fun SidebarToggleButton(isExpanded: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = if (isExpanded) Alignment.CenterEnd else Alignment.Center
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
            contentDescription = if (isExpanded) "Recolher menu" else "Expandir menu",
            tint = WsTextDisabled,
            modifier = Modifier
                .size(18.dp)
                .then(if (isExpanded) Modifier.padding(end = 12.dp) else Modifier)
        )
    }
}

// ── Item 5 + 8: SidebarItem com indicador sempre visível e tooltip ────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val bgColor      = if (selected) WsElevated else Color.Transparent
    val contentColor = tint ?: if (selected) WsAccent else WsTextSecondary

    // Item 8: tooltip no ícone quando colapsado
    val itemContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (expanded) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
            ) {
                // Item 5: barra indicadora sempre visível quando selecionado
                if (selected) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .background(WsAccent, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(if (expanded) 9.dp else 0.dp))
                } else if (expanded) {
                    Spacer(Modifier.width(12.dp))
                }

                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )

                if (expanded) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        color = if (selected) WsTextPrimary else WsTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }

    if (!expanded) {
        // Item 8: tooltip só aparece quando o menu está colapsado
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            },
            state = rememberTooltipState()
        ) {
            itemContent()
        }
    } else {
        itemContent()
    }
}

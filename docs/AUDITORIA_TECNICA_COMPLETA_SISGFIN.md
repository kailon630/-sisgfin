# Auditoria Técnica e Arquitetural Completa — SisgFin

**Data da auditoria:** 22/05/2026  
**Versão analisada:** pós-Fase 2.5B (Koin + `core/` + migração parcial de ViewModels)  
**Método:** revisão estática de ~73 arquivos Kotlin (~4.700 LOC), migrations Flyway, build Gradle, execução `./gradlew compileKotlin test` (OK).  
**Auditor:** análise automatizada + revisão de código-fonte (sem sessão GUI prolongada para captura visual).

---

# 1. VISÃO GERAL DO PROJETO

## 1.1 O que é o SisgFin hoje

O SisgFin é uma **aplicação desktop monolítica** (único módulo Gradle) em **Kotlin + Compose Desktop 1.7**, com persistência **SQLite + Exposed ORM + Flyway**, orientada a uma estética de **finance workstation** escura. Não é multiplataforma mobile neste repositório; o target explícito é desktop (DMG/MSI/Deb configurados).

## 1.2 Arquitetura atual (estado real)

Arquitetura **híbrida em transição**:

| Camada | Estado | Observação |
|--------|--------|------------|
| **Foundation** | Madura | `core/` (CRUD, erros, Result, UI panel), `di/` (Koin 4) |
| **Domínio cadastral** | Média | Services auditados para Supplier/Account/Project; Employee sem auditoria |
| **Apresentação** | Fragmentada | ~15 screens na raiz do pacote; padrões repetidos manualmente |
| **Domínio financeiro** | Imatura | `financial/money` sólido; ledger/transações/conciliação **inexistentes** |
| **Dados** | Duplicado conceitualmente | Tabelas legadas `accounts`/`transactions` vs `financial_accounts` |

```
┌─────────────────────────────────────────────────────────────┐
│ Main.kt → startKoin → DatabaseFactory.init → Window/App    │
└───────────────────────────┬─────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ App (koinInject) → Login | MainLayout                       │
│   Sidebar + TopToolbar + AnimatedContent(screen)            │
│   + Right Panel (Composable callback, estado local)         │
└───────────────────────────┬─────────────────────────────────┘
                            ▼
        ViewModels (factory Koin) ← Services ← Repositories
                            ▼
                    SQLite (sisgfin.db)
```

## 1.3 Maturidade geral

| Dimensão | Nível (0–5) | Comentário |
|----------|-------------|------------|
| Fundação arquitetural | **4** | Fase 2.5B entregou base reutilizável de verdade |
| Produto funcional | **2.5** | Cadastros operam; motor financeiro e rotas Reports/Transactions são fachada |
| UX desktop nativa | **2** | MenuBar real, painel resize; atalhos e hover incompletos |
| Segurança operacional | **2** | Auth OK para protótipo; autorização não aplicada no domínio |
| Testabilidade | **1.5** | Apenas `MoneyTest`; infra nova sem testes |
| Domínio financeiro | **1** | Money pronto; contabilidade não iniciada |

## 1.4 Qualidade geral (honesta)

- **Código foundation:** qualidade **acima da média** para projeto desktop Kotlin — padrões explícitos, menos “script Compose”.
- **Código de produto (telas):** qualidade **média** — repetição, placeholders, dados fictícios no dashboard.
- **Coerência produto vs visão:** identidade visual **workstation** alcançada em ~60%; comportamento **admin web** em ~40%.

O sistema **não é** um CRUD genérico de tutorial, mas **ainda se comporta** como painel administrativo Compose em fluxos críticos (modais, listas, KPIs decorativos).

---

# 2. ESTRUTURA DE PASTAS E ORGANIZAÇÃO

## 2.1 Árvore resumida

```
SisgFin/
├── build.gradle.kts
├── docs/
│   ├── FASE_2.5B_RELATORIO_ARQUITETURAL.md
│   ├── AUDITORIA_TECNICA_COMPLETA_SISGFIN.md  (este documento)
│   └── screenshots/README.md
├── src/main/
│   ├── kotlin/br/com/sisgfin/
│   │   ├── core/              # foundation (crud, domain, errors, result, ui)
│   │   ├── di/                # Koin modules
│   │   ├── financial/money/   # Money, formatter, rounding
│   │   ├── suppliers|accounts|projects|employees|users|dashboard/  # VMs
│   │   ├── presentation/      # Login VM, BaseViewModel, UiStates legados
│   │   └── [raiz]             # Screens, repos, services, Tables, Theme
│   └── resources/db/migration/  # V1–V5
└── src/test/.../MoneyTest.kt
```

## 2.2 Separação de responsabilidades

**O que está bem separado:**

- Infra CRUD transversal em `core/crud`.
- Auditoria de cadastros financeiros em `AuditedCrudService`.
- Injeção de dependências centralizada em `di/`.

**O que permanece misturado (problema):**

- Telas, repositórios, modelos e schema Exposed na **mesma raiz** `br.com.sisgfin`.
- Lógica de segurança (BCrypt) na **UI** (`UserManagementScreen.kt` linha 258).
- Estado do painel direito no **MainLayout**, não no ViewModel/navegação.

## 2.3 Problemas arquiteturais de organização

1. **Dois “mundos” financeiros:** `Account`/`Transaction` (V1, dashboard) vs `FinancialAccount`/`Supplier`/`Project` (V4).
2. **Pacotes-alvo do roadmap não materializados:** não há `data/`, `auth/`, `shared/` físicos — só `core/` e VMs por módulo.
3. **`presentation/state/EmployeeUiState.kt`:** arquivo legado sem consumidor após migração.
4. **Infra órfã:** `GlobalOverlayHost`, `PanelNavigation`, `ConfirmDialog`, `StatusBar` — criados, pouco ou nada integrados.

## 2.4 Monólito Gradle

Um único artefato simplifica entrega desktop, mas **limita**:

- boundaries enforçados por compilação,
- testes de módulo isolados,
- extração futura de `financial-engine` sem refactor grande.

---

# 3. DESIGN SYSTEM (WS-SYSTEM)

## 3.1 Tokens e paleta (`Colors.kt`)

Paleta **coerente e documentada por níveis**:

- Nível 0–3: `WsBackground` → `WsOverlay` (hierarquia de profundidade clara).
- Texto: primário/secundário/disabled alinhados a GitHub-dark / IDE-like.
- Status: success/danger/warning definidos.

**Ponto forte:** nomes `Ws*` estáveis; aliases `@Deprecated` (`Graphite*`, `TextPrimary`) indicam migração consciente — risco se novos arquivos usarem nomes antigos.

## 3.2 Typography e Material (`Theme.kt`)

- `WorkstationColorScheme` = `darkColorScheme` customizado — **dark-mode-first cumprido**.
- Tipografia: SansSerif, escala reduzida (11–32sp), labels com letter-spacing — adequado a densidade desktop.
- **Limitação:** não há tokens de spacing centralizados (padding 12/16/24 espalhados magic numbers).
- **Limitação:** não há sistema de elevation Material além de bordas `WsBorder` — superfícies usam cor, não sombra (correto para flat workstation, mas não documentado como decisão).

## 3.3 Componentes reutilizáveis (`DesktopComponents.kt`)

| Componente | Uso | Avaliação |
|------------|-----|-----------|
| `WsButton` / `WsTextField` / `WsIconButton` | Alto | Consistente, altura 36dp |
| `TableHeaderCell` / `EmptyState` / `DetailSection` | Médio | Bom para tabelas/painéis |
| `CrudToolbar` | Médio | **Busca fake** — parâmetros ignorados, texto "Filtrar..." fixo |
| `TopToolbar` | Alto | **Command bar decorativa** — "Ctrl+K" sem implementação |
| `StatusBar` | **Zero** | Definida, não usada |

## 3.4 Consistência visual entre módulos

- **Suppliers / Accounts / Projects:** `CrudToolbar` + tabela boxed — **consistentes entre si**.
- **Employees / Users:** toolbar manual — **quebra de padrão**.
- **UserDetailsPanel:** não usa `BaseCrudPanel` — terceiro dialeto de painel.
- **Popups:** `AlertDialog` Material3 — contraste com painel lateral “premium”.

## 3.5 Pontos fortes

- Dark profissional sem gradientes “SaaS marketing”.
- Bordas 1dp + `RoundedCornerShape(8.dp)` repetidos — ritmo visual estável.
- Ícones Outlined na sidebar — linguagem próxima a IDE.

## 3.6 Inconsistências e riscos futuros

- Sem **design tokens** formais (spacing, radius, motion) → deriva visual ao escalar equipe.
- `FilterChip` Material3 em contas/usuários mistura com custom flat.
- KPI cards no dashboard com ícones `TrendingUp/Down` — leitura **dashboard web**, não terminal financeiro.

---

# 4. SIDEBAR E NAVEGAÇÃO

## 4.1 Implementação (`Sidebar.kt`)

- Largura animada **64dp ↔ 220dp** via `animateDpAsState`.
- Logo “S” clicável alterna expansão — padrão colapsável típico de **SaaS sidebar**, não de menu nativo OS.
- Itens com indicador de seleção (barra 3dp quando compacto).
- **Permissão:** item “Usuários” só para `UserRole.ADMIN` — **único gate de UI**.

## 4.2 `Screen` e `NavigationState`

- `Screen` sealed sem parâmetros — navegação plana, sem stack, sem deep link.
- `NavigationState`: `mutableStateOf` + `navigateTo` — suficiente para protótipo, **insuficiente** para fluxos multi-step (ex.: assistente de conciliação).

## 4.3 Rotas e fallbacks (`MainLayout.kt`)

| Rota | Comportamento real |
|------|-------------------|
| Dashboard | OK |
| Accounts | `FinancialAccountsScreen` |
| Transactions | `TransactionsScreen` — **placeholder** |
| Suppliers, Projects, Employees | OK |
| UserManagement | OK (admin UI only) |
| Reports, Settings | **Fallback silencioso → Dashboard** |

**Problema grave de UX:** usuário clica “Relatórios” e vê Dashboard sem feedback — parece bug.

## 4.4 Performance da sidebar

- Recomposição limitada à mudança de `currentScreen` e expansão — **baixo risco**.
- `collectAsState` do usuário — OK.

## 4.5 Expansão futura

- Adicionar itens é trivial (novo `SidebarItem`).
- Sem agrupamento dinâmico, favoritos, ou command palette integrada.
- **Permissões por item** não escalam — só admin/users hardcoded.

## 4.6 Controle de permissões

`SessionManager.hasPermission` existe e **não é referenciado** em nenhuma tela ou service (grep: só definição). Sidebar esconde Usuários, mas **Fornecedores/Contas/Projetos** permanecem para role USER — política de segurança indefinida.

---

# 5. RIGHT PANEL SYSTEM

## 5.1 Arquitetura

Estado em `MainLayout`:

```kotlin
var rightPanelContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
var panelWidth by remember { mutableStateOf(350.dp) }
```

Telas recebem:

- `onShowRightPanel { /* composable do painel */ }`
- `onCloseRightPanel { ... }`

**Acoplamento:** cada screen conhece o contrato do shell; ViewModel `CrudState.isPanelOpen` **não dirige** a visibilidade real do painel.

## 5.2 Resize

- Handle 5dp + `detectDragGestures` — limites **250dp–800dp**.
- Cursor `W_RESIZE_CURSOR` via Skiko — **desktop-native**, ponto forte.

## 5.3 Persistência

- **Nenhuma:** largura e estado aberto/fechado resetam ao trocar de rota ou reiniciar app.
- Não há serialização por módulo/usuário.

## 5.4 Lifecycle

- Painel é Composable armazenado em lambda — recriado na navegação.
- Fechar com **Escape** (`KeyboardShortcuts.isEscape`) — implementado no `MainLayout`.
- Ao navegar pela sidebar: `rightPanelContent = null` — correto.

## 5.5 Responsividade

- `BaseCrudPanel` / painéis antigos usam `BoxWithConstraints` onde aplicável; versões migradas simplificaram layout (menos breakpoints wide/narrow em Supplier).
- Painel estreito (<250dp) bloqueado pelo resize — OK.

## 5.6 Qualidade da implementação

**Nota técnica painel: 7/10**

- Mecanismo resize + animação `AnimatedVisibility` slide — nível workstation.
- Falta sincronização estado VM ↔ shell, foco inicial, persistência, e testes de UI.

## 5.7 Infra não integrada

- `core/ui/navigation/PanelNavigation.kt` — alternativa mais limpa, **não wired**.
- `rememberPanelFocusRequester` — sem uso.

## 5.8 Riscos

- Guardar `@Composable () -> Unit` em `mutableState` pode causar capturas stale se ViewModels mudarem seleção sem `key`.
- Formulários com `remember { mutableStateOf }` sem `key(item.id)` corrigidos em parte na 2.5B — Users ainda usa `remember` sem key em `UserDetailsPanel`.

---

# 6. TABELAS HÍBRIDAS

## 6.1 Padrão atual

Todas as telas CRUD seguem scaffold manual:

1. Header row (`TableHeaderCell` + weights).
2. `LazyColumn` + `items(list)`.
3. Row com `combinedClickable` (single → painel, double → dialog em vários módulos).

**Não existe** componente `DataTable` / `HybridTable` compartilhado.

## 6.2 Densidade

- Padding vertical 12–16dp por linha — densidade **média-alta**, aceitável para desktop.
- Fonte 13–14sp — legível, não ultra-densa estilo Excel.

## 6.3 Hover

Em **5 arquivos** (`SupplierRow`, `EmployeeRow`, etc.):

```kotlin
var isHovered by remember { mutableStateOf(false) }
// isHovered nunca é setado true — sem pointerInput/hoverable
```

**Conclusão:** hover visual **não funciona**; só seleção altera background. Isso reduz sensação desktop premium.

## 6.4 Seleção e double-click

- Seleção visual via `isSelected` comparando IDs — OK.
- Double-click abre popup em Suppliers/Employees/Accounts/Projects; **Users:** `onDoubleClick` **vazio** com comentário — inconsistência.

## 6.5 Keyboard navigation

- **Ausente** em tabelas: sem ↑↓, Enter para abrir, Space para selecionar.
- Escape fecha painel no layout — único atalho global efetivo.

## 6.6 Performance e escalabilidade

- `LazyColumn` adequado para centenas de linhas; repositórios fazem `selectAll()` **sem paginação** — risco com milhares de registros.
- Cada row aloca `remember` para hover morto — overhead pequeno mas desnecessário.

## 6.7 Workstation vs webapp

| Aspecto | Avaliação |
|---------|-----------|
| Layout tabular boxed | IDE-like ✓ |
| LazyColumn scroll | Web-like ✓ |
| Sem grid Excel | Não workstation clássica |
| Hover morto | Webapp incompleto |
| Sem atalhos de teclado na grid | Webapp |

**Veredicto tabelas:** visual **próximo** a admin panel moderno; comportamento **ainda webapp**.

---

# 7. UX DESKTOP

## 7.1 Atalhos (`KeyboardShortcuts.kt`)

| Atalho | Implementado | Ligado na UI |
|--------|--------------|--------------|
| Escape | Sim | Sim (fecha painel) |
| Ctrl+S | Definido | **Não** |
| F5 | Definido | **Não** |
| Ctrl+K | Só texto no TopToolbar | **Não** |

## 7.2 MenuBar nativo (`Main.kt`)

Menu **Arquivo / Financeiro / Relatórios / Ajuda** presente — sinal desktop forte.  
**Todos os itens:** `onClick = { /* TODO */ }` — menu **cosmético**.

## 7.3 TAB flow e foco

- Sem ordem de foco documentada nos formulários de painel.
- `FocusRequester` criado, não aplicado.
- Login usa `WsTextField` padrão Compose — TAB provavelmente funciona por padrão Material, não otimizado.

## 7.4 ENTER

- Não há “Enter salva” global nos painéis.
- Login: depende de botão — não auditado handler Enter explícito.

## 7.5 Ergonomia operacional

- Fluxo clique → painel lateral → salvar: **bom** para edição contextual.
- Fluxo duplo clique → popup: **redundante** e confunde (campos diferentes em Supplier).
- Feedback: `CrudEventEffects` em módulos BaseCrud; **Users sem snackbar bridge**.

## 7.6 Nota UX Desktop: **4.5/10**

MenuBar e resize elevam; atalhos, hover, teclado em grid e command palette ausentes puxam nota para baixo.

---

# 8. MÓDULO DE AUTENTICAÇÃO

## 8.1 Arquitetura

- `LoginScreen` + `LoginViewModel` + `AuthService` + `UserRepository`.
- Pós-login: `SessionManager.login(user)` + navegação Dashboard.
- `LoginViewModel.resetState()` após sucesso — evita reexibir erro.

## 8.2 Fluxo

1. Credenciais → `AuthService.authenticate`.
2. BCrypt `checkpw` contra hash no DB.
3. Usuário inativo → falha.
4. Sucesso → callback com `User` → sessão em memória (`StateFlow`).

## 8.3 Sessão (`SessionManager`)

- `currentUser: StateFlow<User?>`.
- Audit LOGIN/LOGOUT em `audit_logs`.
- `hasPermission` — **não utilizado** no restante do sistema.

## 8.4 Segurança — pontos fortes

- Senhas armazenadas como hash BCrypt (seed e criação de usuário).
- Usuário inativo bloqueado na autenticação.
- Auditoria de login/logout.

## 8.5 Segurança — problemas

| Problema | Severidade |
|----------|------------|
| Seed `admin`/`user` senha `123` | Alta em qualquer deploy real |
| Sem timeout de sessão | Média |
| Sem lockout / rate limit | Média |
| Autorização não enforced em services | Alta |
| USER acessa todos módulos exceto UI Usuários | Alta |
| `AuthService` recebe `auditRepository` não usado | Baixa (código morto) |

## 8.6 Auditoria

- Trilha de login/logout OK.
- Tentativas falhas **não** auditadas.

---

# 9. MÓDULO DE GERENCIAMENTO DE USUÁRIOS

## 9.1 UX

- Tabela densa com role, status, último login — **operacional**.
- Painel lateral com role chips, ativo, reset senha, histórico audit — **acima da média** para admin.
- Sem `CrudToolbar` / busca; sem snackbar de eventos.

## 9.2 Permissões

- Regras de negócio no service: último admin não pode ser rebaixado/desativado — **bom**.
- UI não impede tentativa antes do erro — OK com mensagem de erro.

## 9.3 Auditoria

- `auditRepository.findByEntity("USER", id)` ao selecionar — **diferencial positivo**.
- Histórico limitado ao que está no DB; sem paginação.

## 9.4 Arquitetura

- `UserManagementViewModel` em `users/` — espelha CrudAction/Event mas **não** herda `BaseCrudViewModel`.
- `CrudAction.Search` atualiza query **sem filtrar lista** (diferente do base).

## 9.5 Side panel vs popup

- Popup mode **abandonado** (double-click noop).
- BCrypt na UI ao salvar novo usuário — **violação de camada**:

```kotlin
passwordHash = if (user.id == 0) BCrypt.hashpw(...) else user.passwordHash
```

Deveria estar em `UserManagementService.createUser`.

## 9.6 Nota módulo: **6.5/10** (funcional admin; segurança e consistência arquitetural pendentes)

---

# 10. MÓDULO DE FORNECEDORES

## 10.1 Modelagem (`Supplier` em `FinancialModels.kt`)

- Campos operacionais completos: documento, PIX, banco, contato, notas.
- `Identifiable` + `Activatable` — pronto para infra CRUD.

## 10.2 Arquitetura

- `SupplierViewModel` → `AuditedCrudService` — **aderente** à 2.5B.
- Filtro de busca no VM (nome, documento, tradeName) — **pronto**, UI não conecta toolbar.

## 10.3 UX

- Painel: PIX, contato — **completo**.
- Popup duplo clique: foco bancário (banco/agência/conta) — **inconsistente** com painel.
- `BaseCrudPanel` + dirty state no painel — **bom**.

## 10.4 Escalabilidade

- `selectAll()` sem paginação.
- Sem CNPJ validation, duplicidade documento, anexos contratuais.

## 10.5 Nota: **7/10** como cadastro mestre pré-motor financeiro

---

# 11. MÓDULO DE CONTAS FINANCEIRAS

## 11.1 Modelagem

- `FinancialAccount` com `Money` em `initialBalance` — **preparado monetary**.
- Tipos `BANK | CASH | SAVINGS` — adequado a caixas múltiplos.

## 11.2 Conflito crítico com dashboard

| Conceito | Tabela | Uso |
|----------|--------|-----|
| Conta legada | `accounts` | Dashboard KPIs, seed |
| Conta financeira V4 | `financial_accounts` | Tela “Contas e Caixas” |

**Motor financeiro não pode iniciar** sem unificação ou bridge documentada. Hoje são **dois universos de dados**.

## 11.3 Preparação para ledger

- Saldo inicial em cadastro — insuficiente para ledger (falta chart of accounts, posting rules, multi-currency).
- Auditoria create/update/toggle — bom para compliance de cadastro, não para lançamentos.

## 11.4 Limitações

- Popup stub (“use painel lateral”).
- Sem vínculo com transações reais.
- Sem conciliação bancária.

## 11.5 Nota: **6/10** (modelo bom; integração sistêmica ruim)

---

# 12. MÓDULO DE PROJETOS

## 12.1 Modelagem

- `code`, `name`, `description`, datas opcionais — base para centro de custo.

## 12.2 UI

- Vigência explicitamente **Beta** com texto “próxima versão do motor financeiro” — honestidade boa, produto incompleto.
- Sem orçamento, sem vínculo a lançamentos.

## 12.3 Preparação orçamento / prestação de contas

- Estrutura mínima OK para tagging futura de transações por `project_id` (coluna ainda não existe em `transactions`).
- Relatórios por projeto: **zero** infraestrutura.

## 12.4 Nota: **5.5/10** (cadastro; dimensão financeira ausente)

---

# 13. AUDITORIA DO CÓDIGO

## 13.1 Qualidade geral

- Estilo Kotlin idiomático em services/repos.
- ViewModels financeiros **finos** — bom sinal.
- Compose às vezes **monolítico** (screens 200–340 linhas).

## 13.2 Acoplamento

| De | Para | Grau |
|----|------|------|
| Screens | MainLayout callbacks | Alto |
| Dashboard | Repos legados | Alto |
| User UI | jBCrypt | Alto |
| VMs | Concrete repos (via Koin) | Médio |

## 13.3 Duplicação (quantificada qualitativamente)

- ~5× scaffold tabela+painel (~80–120 linhas cada) → **400–600 linhas** duplicáveis.
- 3× popup+panel forms divergentes.
- Toolbars duplicadas (Users, Employees).

## 13.4 ViewModels

- `BaseCrudViewModel`: **bem estruturado** (action/state/event/IO).
- `init { load() }` em base → carga antecipada mesmo sem visitar tela.
- `BaseViewModel.clear()` — logout **não chama**.

## 13.5 Services

- `AuditedCrudService`: DRY real.
- `EmployeeService`: sem auditoria — inconsistência política.
- `UserManagementService`: regras admin — qualidade boa.

## 13.6 Composição e reutilização

- Foundation **reutilizada** onde migrou (Suppliers, Accounts, Projects, Employees painéis).
- Users e Dashboard **fora** do padrão.
- Infra UI criada e parcialmente órfã.

## 13.7 Testes

- **1** arquivo de teste (`MoneyTest`).
- Zero testes ViewModel/Service/Repository.
- Koin modules não têm `testModule`.

---

# 14. PERFORMANCE

## 14.1 Compose Desktop — riscos

- `AnimatedContent` em `App` e `MainLayout` em toda troca de tela — custo animação desnecessário para power users.
- Múltiplos `collectAsState` por tela — OK.

## 14.2 Listas

- `LazyColumn` OK para listagens moderadas.
- Sem paginação → memória e tempo de query crescem linearmente.

## 14.3 ViewModels factory Koin

- Primeiro inject após login pode disparar **6+ loads IO** paralelos (todos VMs com init load).

## 14.4 Painéis

- `LoadingOverlay` semitransparente sobre painel inteiro — OK.
- Recomposição de formulários com muitos `mutableState` — aceitável.

## 14.5 SQLite

- Arquivo local `data/sisgfin.db` — adequado workstation single-user; não escala multi-usuário concorrente sem migrar engine.

## 14.6 Nota performance percebida: **7/10** em datasets pequenos; **4/10** projetado para 10k+ registros sem paginação

---

# 15. DÍVIDAS TÉCNICAS REAIS

Lista explícita — **sem omissão**:

| # | Item | Tipo | Evidência |
|---|------|------|-----------|
| 1 | MenuBar itens vazios | TODO | `Main.kt` |
| 2 | Busca CrudToolbar não funcional | Placeholder | `DesktopComponents.kt` |
| 3 | Command palette Ctrl+K | Decorativo | `TopToolbar` |
| 4 | Alertas dashboard fictícios | Mock UI | `DashboardScreen.kt` L106–114 |
| 5 | Agenda dashboard fictícia | Mock UI | idem |
| 6 | TransactionsScreen placeholder | Stub | `OtherScreens.kt` |
| 7 | Reports/Settings → Dashboard | Fallback silencioso | `MainLayout.kt` |
| 8 | Account vs FinancialAccount | Débito domínio | V1 vs V4 schema |
| 9 | Hover rows não funciona | Bug/ incompleto | 5 row composables |
| 10 | Popups stub (Account, Project) | Incompleto | popups |
| 11 | Supplier popup ≠ panel campos | Fragilidade UX | dois formulários |
| 12 | Employee sem audit trail | Inconsistência | `EmployeeService` |
| 13 | Permissões não enforced | Segurança | `hasPermission` unused |
| 14 | BCrypt na UI Users | Camada errada | `UserManagementScreen.kt` |
| 15 | Senhas seed 123 | Segurança dev | `DatabaseFactory.kt` |
| 16 | VMs load no init sem escopo tela | Performance | `BaseCrudViewModel` |
| 17 | clear() não no logout | Lifecycle | `SessionManager`/`MainLayout` |
| 18 | Infra overlay/panel nav órfã | Código morto | `core/ui/*` |
| 19 | `EmployeeUiState.kt` legado | Arquivo morto | presentation/state |
| 20 | Money init scale comentado | Precisão | `Money.kt` L14–17 |
| 21 | KPI “totais” só 10 txs | Métrica enganosa | `DashboardUiState` |
| 22 | Testes infra ausentes | Qualidade | só MoneyTest |
| 23 | Paginação ausente | Escala | todos repos `findAll` |
| 24 | Projeto datas Beta | Incompleto | `ProjectsManagementScreen` |

---

# 16. RISCOS ARQUITETURAIS FUTUROS

## 16.1 Motor financeiro / ledger

- **Risco alto:** duplicidade de contas gera lançamentos na entidade errada.
- Ausência de idempotência, posting periods, reversão, multi-ledger.

## 16.2 Escala de dados

- `selectAll()` + Compose full list → gargalo previsível em clientes médios.

## 16.3 Compose Desktop

- Dependência de Skiko/JVM — OK para workstation, mas testes visuais e CI headless são difíceis (screenshots).

## 16.4 Domínio

- Regras financeiras na UI (salário parse `toMoney()` em Employee panel) — tendência a espalhar lógica se não barrar agora.

## 16.5 Equipe / código

- Monólito sem boundaries → regressões ao adicionar motor financeiro paralelo ao CRUD.

## 16.6 Concorrência

- SQLite + Exposed `transaction {}` single-file — segundo processo ou sync cloud **não suportado**.

---

# 17. ADERÊNCIA AO CONCEITO ORIGINAL

Escala **0–10** por eixo conceitual:

| Conceito | Nota | Justificativa técnica |
|----------|------|----------------------|
| JetBrains-like | **6** | Dark dense UI, sidebar ícones, painel lateral resize; falta tool windows, busca global real, keymap |
| Workstation feeling | **5.5** | Shell bom; tabelas e KPIs puxam para admin web |
| Desktop-native | **6** | MenuBar, window, cursor resize; hover/atalhos/menu vazios enfraquecem |
| Dark professional | **8** | Paleta Ws consistente |
| Anti-SaaS | **5** | Sidebar colapsável SaaS, KPI cards, alertas fake SaaS |
| Anti-ERP legado | **7** | Não usa grids cinza anos 2000; mas CRUD modal ainda presente |

**Nota média aderência conceito: 6.2/10**

---

# 18. O QUE AINDA PARECE

## WEBAPP

- `LazyColumn` como lista principal sem keyboard grid.
- `AnimatedContent` fade/slide entre todas as telas.
- KPI cards + alertas estáticos estilo dashboard web.
- Sidebar expand/collapse estilo Notion/Linear.
- Placeholders de busca e Ctrl+K.
- `AlertDialog` para edição rápida.

## MATERIAL DESIGN

- `FilterChip`, `AlertDialog`, `Checkbox`, `CircularProgressIndicator` padrão Material3.
- Tipografia Material mapeada — não fonte custom workstation.
- Botões Material `Button` dentro de `WsButton`.

## ERP LEGADO

- Telas cadastro lista + formulário lateral — padrão ERP modernizado, não quebrado.
- Labels em CAPS nos campos (“NOME / RAZÃO SOCIAL”) — leve vibe ERP brasileiro.
- Status bolinha verde/cinza — comum em ERP.

## CRUD GENÉRICO

- Scaffold repetido 5× sem abstração de lista.
- Popups duplicando painéis.
- Rotas Reports/Settings falsas.
- Módulo Transactions vazio.

---

# 19. O QUE JÁ PARECE

## SOFTWARE PREMIUM

- Paleta dark refinada e bordas sutis.
- Painel contextual com resize e animação slide.
- Tipografia e densidade superiores a CRUD tutorial default.
- Money domain com testes.

## WORKSTATION

- `MenuBar` nativo (estrutura).
- Layout tríptico: sidebar | conteúdo | painel.
- Top command bar visual (mesmo não funcional).
- Branding “SisgFin Workstation” no login.

## FERRAMENTA FINANCEIRA REAL

- `Money` + migração V5 precisão decimal.
- Contas tipadas BANK/CASH/SAVINGS.
- Fornecedor com PIX/dados bancários.
- Auditoria em cadastros auditados.
- **Ainda não:** fluxo de caixa real, ledger, conciliação, DRE.

**Especificidade:** o produto **parece** ferramenta financeira em **identidade**; em **operação financeira** ainda é cadastro + dashboard demo.

---

# 20. SCREENSHOTS OBRIGATÓRIOS

## 20.1 Status desta auditoria

**Screenshots reais NÃO estão anexados neste documento.**

Motivos:

- Ambiente de auditoria sem captura GUI estável (Compose Desktop requer display; `./gradlew run` não produziu captura automática).
- Política: não inventar imagens nem usar placeholders visuais falsos.

## 20.2 Entrega esperada

Instruções e checklist em:

**`docs/screenshots/README.md`**

Arquivos esperados após captura manual local (12 PNG listados no README).

## 20.3 O que cada screenshot deve validar

| Captura | Validação arquitetural |
|---------|------------------------|
| Login | Branding workstation, dark-first |
| Dashboard | Separar KPI real vs mock lateral |
| Sidebar expandida/compacta | Navegação e permissão admin |
| Módulos CRUD | Densidade tabela, painel |
| Right panel + resize | Implementação shell |
| Popup mode | Duplicidade fluxo (problema UX) |
| Hover | Esperado: **sem diferença** até corrigir código |

---

# 21. FLUXO COMPLETO DE NAVEGAÇÃO

## 21.1 Jornada típica (admin)

```
1. Launch → DatabaseFactory.init + Koin
2. Screen.Login → LoginScreen
3. Auth OK → SessionManager.login + navigate Dashboard
4. MainLayout: Sidebar + TopToolbar + content
5. Usuário escolhe módulo → AnimatedContent troca screen
6. Sidebar fecha painel direito (rightPanelContent = null)
7. Em CRUD: clique linha → onShowRightPanel(panel)
8. Escape ou navegar → fecha painel
9. Logout → session clear + Screen.Login (VMs não cleared)
```

## 21.2 Preservação de contexto

| Contexto | Preservado? |
|----------|-------------|
| Seleção na lista | Sim, no ViewModel StateFlow |
| Painel aberto ao trocar módulo | **Não** — resetado |
| Largura do painel | Sim, enquanto mesma sessão MainLayout |
| Scroll da lista | Compose default — parcial |
| Filtro/busca | **Não** — não implementado na UI |

## 21.3 Interação entre módulos

- **Fraca:** módulos isolados; sem cross-link (ex.: ver fornecedor a partir de transação futura).
- Dashboard **não** lê `financial_accounts`.
- Funcionários **não** ligados a folha/transações.

---

# 22. ESCALABILIDADE FUTURA

| Capacidade | Preparação atual | Gap |
|------------|------------------|-----|
| Motor financeiro | Money + audit cadastros | Unificar contas; domain services |
| Ledger | Nenhuma tabela posting | Schema novo + invariantes |
| Relatórios | Rota fake | Query layer + export |
| Anexos | Nenhum | Storage + UI |
| Dashboards analytics | KPI mock | Pipeline dados real |
| Filtros avançados | VM tem Search action | UI + query SQL |
| Command palette | Visual only | Handler global + registry commands |

**Foundation 2.5B acelera cadastros; não resolve ledger.**

---

# 23. NOTA FINAL REAL

Escala **0–10** (10 = referência internacional workstation financeira madura):

| Critério | Nota | Peso mental |
|----------|------|-------------|
| Arquitetura | **6.5** | Foundation boa, periferia legada |
| UX Desktop | **4.5** | Atalhos/hover/menu incompletos |
| Visual | **7.5** | Ws-System forte |
| Escalabilidade | **5.0** | Monólito, sem paginação |
| Performance | **6.5** | OK agora; mal para volume |
| Organização | **5.5** | Pacotes híbridos |
| Domínio Financeiro | **2.5** | Só Money + cadastros |
| Sensação Premium | **6.0** | Visual > comportamento |
| Aderência conceito original | **6.2** | Ver seção 17 |

**Média ponderada (ênfase domínio financeiro): ~5.6/10**  
**Média se avaliar só como “shell CADASTRO desktop dark”: ~6.4/10**

---

# 24. CONCLUSÃO HONESTA

## Hoje o SisgFin parece o quê?

**Resposta técnica:** combinação de **software profissional em formação** + **admin CRUD desktop dark** + **aspiração workstation** ainda não fechada no comportamento.

| Metáfora | Aplica? | Por quê |
|----------|---------|---------|
| CRUD bonito | Parcialmente | Há CRUD repetido e popups; visual acima de CRUD tutorial |
| ERP moderno | Parcialmente | Cadastros + auditoria; sem transações reais integradas |
| Workstation financeira | **Ainda não plenamente** | Falta command layer, teclado, dados financeiros coerentes |
| SaaS desktopizado | **Em elementos** | Sidebar, KPI, placeholders, animações |
| Software profissional real | **Na fundação sim; no produto completo não** | Build OK, patterns OK; rotas vazias, mocks, dual schema |

## Justificativa técnica final

A Fase 2.5B **melhorou substancialmente** a arquitetura interna: Koin, `BaseCrudViewModel`, `AuditedCrudService`, erros e painel base são ativos reais, não cosmética. Isso coloca o projeto em trajetória correta para o Motor Financeiro.

Porém a **superfície do produto** ainda expõe:

- dados e rotas **não financeiras** no dashboard,
- **autorização** não aplicada,
- **UX desktop** pela metade,
- **dois modelos de conta**,
- **screenshots e módulos stub** que um auditor externo classificaria como “protótipo avançado”, não “workstation final”.

**Recomendação ao arquiteto:** aprovar a **direção** da foundation; exigir **gate** antes do motor financeiro: (1) unificação contábil, (2) `CrudListScreen` + remoção popups duplicados, (3) enforcement de permissões, (4) eliminação mocks dashboard, (5) testes mínimos de `AuditedCrudService` e `BaseCrudViewModel`, (6) captura do pacote de screenshots em `docs/screenshots/`.

---

*Fim da auditoria técnica completa — SisgFin, 22/05/2026.*

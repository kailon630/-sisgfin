# SisgFin — Kanban de Implementação

> Mover o card para **Em Andamento** ao iniciar, para **Concluído** ao terminar.
> Itens ordenados por dependência dentro de cada fase.

---

## ✅ Concluído

### Infraestrutura / Foundation
- [x] **CRUD base** — `core/` com `AuditedCrudService`, `BaseCrudViewModel`, `BaseCrudPanel`, Result, erros
- [x] **DI** — Koin 4 configurado com módulos `DatabaseModule`, `RepositoryModule`, `ServiceModule`, `ViewModelModule`
- [x] **Banco de dados** — SQLite + Exposed + Flyway configurados
- [x] **Autenticação / Sessão** — login funcional, `SessionManager` com `UserRole`

### Cadastros
- [x] **RN-09** — Seed do plano de contas TCESP (~100 categorias) via `V10__seed_expense_categories.sql`
- [x] **Fornecedores** — CRUD completo com tela, ViewModel, Service, Repository
- [x] **Contas Financeiras** — CRUD completo
- [x] **Centros de Custo** — CRUD completo (renomeado de "Projetos"; tabela DB preservada como `projects`)
- [x] **Funcionários** — CRUD completo
- [x] **Categorias de Despesa** — CRUD completo
- [x] **Usuários** — CRUD completo com perfis ADMIN / USER

### Motor Transacional (estrutura)
- [x] **RN-13** — State machine com estados terminais (`PAID`, `CANCELED`) — `TransactionStateMachine`
- [x] **RN-15** — Job automático `PENDENTE → VENCIDO` — `OverdueEngine` chamado em `listAll()`
- [x] **RN-33** — Timeline de transação exibida no painel de detalhes — `TransactionDetailsPanel`

### Rastreabilidade
- [x] **RN-11** — Toda escrita registra `userId + timestamp` — `AuditedCrudService`
- [x] **RN-32** — Tabela `audit_logs` imutável — `V3__user_management_and_audit.sql`

---

## 🔄 Em Andamento

_Mova os cards aqui quando começar._

---

## 🔴 Pendente

### Fase 2-A — Corrigir Validações (rápido, alto impacto)

- [x] **RN-01** — Adicionar `UNIQUE` constraint em `suppliers.document` (`V11__supplier_document_unique.sql` + `Tables.kt` + validação no `SupplierService.save()`)
- [x] **RN-03** — Validação de dígitos verificadores de CNPJ e CPF (`DocumentValidator.kt` em `core/validation/` + integrada ao `SupplierService.save()` + 15 testes unitários)
- [x] **RN-16** — `TransactionValidator.validate()`: check `paymentDate >= issueDate` em PAID/PARTIAL; `validatePayment()` recebe `issueDate` e valida também (10 testes unitários)
- [x] **RN-02** — `TransactionService`: `validateSupplier()` chamado em `create()` e `update()`; lança `IllegalArgumentException` com nome do fornecedor se inativo
- [x] **RN-08** — `TransactionValidator`: aviso quando despesa está fora do período do projeto (`dueDate` fora de `project.startDate..endDate`) — registrado como `TimelineEventType.WARNING` + audit (não bloqueia)

### Fase 2-B — Proteções de Delete nos Services

- [x] **RN-07** — `CostCenterService.delete()`: bloquear se existirem transações vinculadas ao centro de custo (`transactionRepository.existsByCostCenterId()`); inativação permanece sempre permitida
- [x] **RN-10** — `ExpenseCategoryService.toggleActive()`: bloquear inativação se existirem transações vinculadas à categoria
- [x] **RN-05** — `FinancialAccountService.toggleActive()`: bloquear inativação se saldo calculado ≠ 0

### Fase 2-C — Completar o Motor Transacional

- [x] **RN-04** — `FinancialAccountService.calculateBalance(accountId)`: `saldo_inicial + Σreceitas_PAID − Σdespesas_PAID` via `TransactionRepository.sumPaid()`
- [x] **RN-17 / RN-18 / RN-19** — Parcelamento automático: `TransactionService.create()` gera filhos mensais quando `installmentTotal > 1`; última parcela absorve arredondamento (FLOOR + resto); `cancel()` faz cascata em filhos `PENDING`/`DRAFT`
- [x] **RN-20 / RN-21** — `TransactionService.createTransfer()`: cria par TRANSFER atômico (origem + destino); `cancel()` cancela o par vinculado em cascata se cancelável
- [x] **RN-14 / RN-22 / RN-23** — `TransactionService.reverseTransaction(id, justification)`: cria lançamento `REVERSAL` com `parentTransactionId` apontando ao original; justificativa obrigatória (RN-22); apenas PAID pode ser estornado (RN-23); bloqueia duplo estorno
- [x] **RN-12** — Controle de acesso real por perfil: `ADMIN` acesso total; `OPERADOR` (renomear `USER`) cria/edita mas não confirma pagamento; verificação nas operações dos Services; migration V12; `Permission.ConfirmPayment` em `recordPayment`, `markAsPaid`, `reverseTransaction`

### Fase 2-D — UI de Lançamentos (completar)

- [x] **Formulário de criação/edição** — campos completos: emissão, vencimento, valor, parcelas, tipo/número documento, fornecedor, centro de custo, categoria, conta, observações; `WsSelectField` para dropdowns; `TransactionsViewModel` carrega todas as listas de referência
- [x] **Modal de baixa rápida** — `PaymentRecordDialog` com validação `dataPagamento >= dataEmissão` (RN-16) em tempo real; bloqueio visual se data inválida
- [x] **Fluxo de estorno** — botão "Estornar" (ícone Undo, cor WsWarning) visível apenas quando `PAID + type≠REVERSAL + canConfirmPayment`; `ReversalDialog` com campo justificativa obrigatório ≥10 chars
- [x] **RN-20/21 UI** — botão "Transferência" na tela de lançamentos; `TransferDialog` com seleção de conta origem/destino, valor, data, descrição, notas; validação local + delegação ao `TransactionService.createTransfer()`

### Fase 3-A — Saldos e Extrato

- [x] **RN-06** — Tipo `INVESTMENT` adicionado ao `FinancialAccountType`; campo `investmentBroker` no modelo + V13 migration; rendimentos registrados como `ADJUSTMENT` entram na fórmula de saldo (RN-04 atualizado: `+ adjustment`)
- [x] **Painel de saldos** — `BalancesScreen` com 3 tiles totalizadores (saldo, a pagar, vencido) + tabela por conta com saldo atual, contagem de pendentes/vencidos e data do último pagamento; acessível pela sidebar; recarregável
- [x] **Extrato por conta** — `StatementScreen` com barra de filtros (conta, período, tipo, projeto, categoria), tabela cronológica com colunas débito/crédito/saldo acumulado, banner de saldo abertura e rodapé totalizador; exportação XLSX (Apache POI) e PDF (Apache PDFBox) salvos em `~/Documents/SisgFin/` e abertos automaticamente

### Fase 3-B — Orçamento por Rubrica

- [x] **Entidade `BudgetItem`** — modelo + migration: `projectId`, `categoryId`, `monthlyAmount`, `annualAmount`
- [x] **RN-24** — Realizado: soma automática de lançamentos `PAID` vinculados ao projeto + categoria no período
- [x] **RN-25** — Saldo disponível = `annualAmount − realizado`
- [x] **RN-26** — Exibir saldo disponível da rubrica em tempo real no formulário de lançamento
- [x] **RN-27** — Aviso no formulário ao ultrapassar orçamento da rubrica (não bloqueia, apenas alerta)
- [x] **RN-28** — Orçamento de projeto encerrado é somente leitura

### Fase 4 — Relatórios

- [x] **Livro Diário (RN-29)** — `ReportsScreen` tab; lista todos os lançamentos `PAID` de um período; descrição automática no padrão TCESP (`"PAGO A, [CREDOR] CF [TIPO_DOC] [NÚMERO]"`); exportação PDF (A4 landscape, PDFBox) + Excel (Apache POI)
- [x] **Balancete (RN-30)** — `ReportsScreen` tab; comparativo por rubrica: dotação mensal / anual / realizado / saldo / % utilização; filtro por mês ou acumulado; exportação PDF + Excel
- [x] **Demonstrativo Financeiro** — terceira aba em `ReportsScreen`; agrupa receita × despesa por categoria AUDESP e grupo; subtotais por grupo; total geral; exportação PDF + Excel
- [x] **Comprovante individual PDF (RN-31)** — botão "Comprovante" em `TransactionDetailsPanel` (só para `PAID`); `ReportsExporter.receiptPdf()` em A4 portrait com campos em duas colunas, linhas de assinatura e rodapé; aberto automaticamente após geração

### Fase 5 — Produção

- [x] **Dashboard com dados reais** — `DashboardViewModel` migrado para `FinancialAccountRepository` + `FinancialAccountService` + `TransactionRepository`; KPIs: saldo consolidado, receita/despesa do mês, vencidos (count + total), a vencer 7 dias (count + total); coluna lateral com saldos por conta, lista de vencidos e a vencer; tabela de últimas 15 liquidações
- [ ] **Backup automático** — cópia do arquivo `sisgfin.db` em intervalo configurável (ex: ao fechar o app)
- [ ] **Instalador** — empacotar MSI (Windows) e DEB (Linux) via Compose Desktop packaging

---

### Fase 6-A — Fluxo de Caixa Projetado ⬅ *prioridade máxima (cliente solicitou)*

> Objetivo: o operador abre o sistema e vê, em segundos, se consegue pagar os boletos de seg–qua sem faltar verba para a folha de quinta. Hoje isso é feito na mão.

- [x] **`CashFlowProjection` — modelo de dados** — `DailyCashFlowEntry` + `CashFlowUiState` em `cashflow/CashFlowModels.kt`; calculado em memória, nunca persistido
- [x] **`CashFlowService.project(accountId, days)`** — `TransactionRepository.findUnpaid(windowEnd, accountId?)` retorna `OVERDUE` sempre + `PENDING`/`PARTIAL`/`SCHEDULED` até `windowEnd`; `CashFlowService.project()` agrupa por `dueDate`, acumula `runningBalance` separando OVERDUE como bloco inicial
- [x] **`CashFlowViewModel`** — expõe `CashFlowUiState` via `StateFlow`; `setWindowDays()` / `setAccount()` / `load()`; padrão `CoroutineScope(SupervisorJob() + Dispatchers.Main)` + `runCatching`
- [x] **`CashFlowScreen` — tela principal** — tabela com barra lateral colorida por saúde do dia; colunas Data / Compromissos / Saídas / Entradas / Saldo Projetado; linha vermelha quando `projectedBalance < 0`; chips 7/14/30 dias; dropdown de conta; seção retrátil de atrasados
- [x] **Indicador de "teto seguro"** — `StatusBadge` tile: verde *"Saldo suficiente"* / vermelho *"Saldo insuficiente"* comparando `currentBalance ≥ totalCommitted`
- [x] **Integração no Dashboard** — card "Projeção — Próximos Dias" na sidebar do Dashboard; mostra até 3 dias com compromissos (nome do dia, contagem, saída do dia, saldo projetado acumulado) + badge final verde/vermelho com saldo projetado a 7 dias; `DashboardViewModel` injeta `CashFlowService` via Koin

---

### Fase 6-B — Simulação de Impacto ("E se?")

> Objetivo: antes de assumir um compromisso (ex: contratar serviço por R$800 com prazo de 14 dias), ver como isso afeta o fluxo projetado — sem criar o lançamento oficial.

- [x] **`SimulationEntry` — modelo** — `data class SimulationEntry(description, amount, dueDate, accountId?)` em `CashFlowModels.kt`; existe apenas em memória; `isSimulating` e `simulationDelta` calculados como propriedades de `CashFlowUiState`
- [x] **`CashFlowService.projectWithSimulation(accountId, days, extras)`** — injeta `SimulationEntry` como `DailyCashFlowEntry(isSimulated=true, simulationLabel=…)` na lista ordenada por data; recalcula `runningBalance` do zero mesclando reais + simulados; `CashFlowViewModel.load()` chama automaticamente `projectWithSimulation` quando `simulationEntries` não está vazio e armazena `baseProjectedBalance` para o delta
- [x] **Painel "Simular Compromisso"** — botão "Simular" toggle na toolbar da `CashFlowScreen`; painel lateral animado (320dp) com formulário Descrição / Valor / Vencimento (dd/MM/yyyy) / Conta; `addSimulation()` / `removeSimulation()` / `clearSimulations()`; linhas simuladas na tabela em âmbar com ícone `Science`; botão "Criar lançamento real" chama `commitSimulation()` → `TransactionService.create()` + remove da simulação
- [x] **Badge de impacto** — `SimulationImpactBadge` exibido quando `isSimulating`: mostra saldo base → saldo com simulação e delta colorido (verde se melhora, vermelho se piora)

---

### Fase 6-C — Geração Automática de Lançamentos por Funcionário

> Objetivo: cadastrar uma vez o funcionário com valor e dia(s) de pagamento; o sistema gera automaticamente os lançamentos no contas a pagar todo mês — sem lançar na mão.

- [x] **Expandir modelo `Employee`** — `paymentDays: String?` (ex: `"5,20"`) + `employmentType: String?` + enum `EmploymentType(CLT/PJ/ESTAGIO/OUTROS)`; `V15__employee_payment_days.sql` adiciona as colunas em `employees` e `employee_id` em `financial_transactions`; `effectivePaymentDays()` parseia e retorna lista de dias — vazio = sem auto-geração; formulário na `EmployeesScreen` atualizado com seletor de vínculo e campo de dias automáticos com preview dos dias configurados
- [x] **`PayrollEngine`** — `generateForMonth(yearMonth)` itera todos os funcionários ativos com `effectivePaymentDays()` não vazio; deduplicação via `TransactionRepository.existsPaymentForEmployee(employeeId, dueDate)` (range do dia inteiro); usa a primeira conta ativa como conta debitada; `generateForEmployee(id)` gera mês corrente + próximo (chamado pelo `EmployeeService.save()` e `toggleActive()` ao reativar)
- [x] **Campo `employeeId` em `financial_transactions`** — FK nullable para `employees`; incluso na `V15__employee_payment_days.sql`; `TransactionRepository` atualizado com `employeeId` em insert/update/rowToTransaction + `findNextPendingForEmployee()` + `existsPaymentForEmployee()`
- [x] **Gatilho de geração** — `EmployeeService.save()`/`toggleActive()` chama `PayrollEngine.generateForEmployee()`; `Main.kt` dispara `generateForMonth(now)` + `generateForMonth(now+1)` em `CoroutineScope(Dispatchers.IO)` na abertura do app
- [x] **UI de confirmação** — `BaseCrudViewModel.onSaveSuccess()` hook + `EmployeeViewModel.onSaveSuccess()` lê `EmployeeService.lastPayrollResult` e emite `CrudEvent.ShowSnackbar` com quantidade de lançamentos gerados
- [x] **Tela de Funcionários — indicador de lançamentos** — coluna "PRÓX. PGTO" na tabela; `EmployeeViewModel.nextPaymentDates: StateFlow<Map<Int, LocalDate?>>` carregado via `TransactionRepository.findNextPendingForEmployee()`; mostra data formatada dd/MM/yyyy ou "—" para funcionários sem auto-geração

---

### UX/UI — Melhorias de Interface (levantamento 14 sugestões)

> Sessão de análise UX com foco em softwares nativos de gestão financeira; 14 sugestões levantadas e implementadas.

- [x] **Tipografia monetária** — `WsMoneyStyle` / `WsMoneyStyleLarge` (monospace, SemiBold/Bold, tracking negativo); composable `MoneyText(amount, large, color)` substituindo `Text(MoneyFormatter.format(...))` em toda a UI
- [x] **Campo de busca real** — `CrudToolbar` usa `OutlinedTextField` visível na toolbar (não ícone-abre-campo); atalho `Ctrl+F` mantido
- [x] **`WsTableRow` com hover correto** — `hoverable(MutableInteractionSource) + collectIsHoveredAsState()` resolve o bug de hover nunca atualizar; substituído em `EmployeesScreen`, `DashboardScreen`, `TransactionsScreen`
- [x] **Painel lateral compacto** — largura inicial `400.dp.coerceAtMost(contentAreaWidth * 0.6f)` (era 50% da tela)
- [x] **Indicador de seleção sempre visível** — barra azul 3dp no lado esquerdo do item selecionado na sidebar, mesmo quando colapsada
- [x] **TopToolbar com breadcrumb** — ícone Sync removido; subtítulo exibe o nome da tela atual (ex: "Movimentações")
- [x] **Labels de grupo na sidebar** — `SidebarGroupLabel("FINANCEIRO" / "CADASTROS" / "GESTÃO")` expandido = texto, colapsado = `HorizontalDivider`
- [x] **Botão toggle dedicado** — `SidebarToggleButton` com `ChevronLeft/ChevronRight` no rodapé da sidebar
- [x] **Tooltip nos itens colapsados** — `TooltipBox + PlainTooltip` envolve cada `SidebarItem` quando colapsado
- [x] **`WsSelectField` visual consistente** — `OutlinedTextField(readOnly, enabled=false)` + overlay `Box.matchParentSize().clickable`; mesmo tamanho e padding do `WsTextField`
- [x] **`WsDateField` com máscara** — `DateMaskTransformation : VisualTransformation`; entrada só dígitos, exibe `dd/MM/yyyy`; borda vermelha se data inválida
- [x] **Acessibilidade de cores** — `WsTextSecondary` → `#A0A7B1` (4.6:1), `WsWarning` → `#E6A817` (4.8:1), novo `WsInfo = #58A6FF`
- [x] **KPI tiles com ícone watermark** — `Icon` 36dp no canto superior direito com `alpha = 0.18f`; `heightIn(min = 96.dp)` em vez de altura fixa
- [x] **StatusBar com relógio dinâmico** — `LaunchedEffect { while(true) { delay(60_000L); now = LocalDateTime.now() } }`; mostra overdueCount via `TransactionRepository`

#### UX — Ajustes em Movimentações (TransactionsScreen)

- [x] **Filtro padrão `ActionRequired`** — `TransactionListFilter.ActionRequired` (PENDING + OVERDUE + PARTIAL, ASC); ViewModel e serviço inicializam com esse filtro; chip "A pagar" adicionado como primeira opção; chip "Pendentes" removido (coberto pelo novo filtro)
- [x] **`+ Despesa` e `+ Receita`** — substituem "Nova Transação"; `OutlinedButton` vermelho para Despesa, `WsButton` verde para Receita; `TransactionsViewModel.openNewExpense()` / `openNewIncome()` via `BaseCrudViewModel.openWithItem(T)`
- [x] **Tabela agrupada por seção temporal** — quando filtro `ActionRequired` está ativo: `LazyColumn` com `stickyHeader` para grupos "Vencidos / Hoje / Amanhã / Esta semana / Próximas"; badge colorido no header; filtros específicos mantêm tabela plana

---

### Fase 6-D — Importação de Extrato OFX

> Objetivo: o operador importa o extrato bancário mensal (.ofx) direto no sistema — o banco (BB, conta 501145-0) já entrega os dados; não precisa mais lançar manualmente cada recebimento de PIX ou cada boleto pago. Arquivos de referência em `docs/` (jan, fev e mar 2026).

#### 6-D.1 — Parser OFX

- [x] **`OfxParser`** — parser próprio para OFX SGML versão 102; lê linha a linha com máquina de estados em blocos `<STMTTRN>...</STMTTRN>`; extrai `TRNTYPE`, `DTPOSTED`, `TRNAMT`, `FITID`, `CHECKNUM`, `MEMO`; suporta tags com e sem fechamento XML; `parse(File)` usa `windows-1252`, `parse(String)` para testes; transações com campo obrigatório ausente são silenciosamente descartadas
- [x] **`OfxStatement`** + **`OfxTransaction`** + **`OfxTrnType`** — em `ofx/OfxModels.kt`; `amount: Money` preserva sinal original (negativo = saída); `isInflow: Boolean` calculado; `XFER` sinalizado pelo sinal do amount
- [x] **Testes unitários do parser** — 12 testes em `OfxParserTest.kt`: DEP positivo, DEBIT negativo, XFER positivo/negativo, encoding W-1252 com TRANSFERÊNCIA/ANTECIPAÇÃO via arquivo temporário, FITID duplicado (ambos incluídos — dedup é do serviço), FITID vazio ignorado, TRNAMT ausente ignorado, tags com fechamento XML, metadados do extrato, arquivo OFX real de janeiro/2026 (1241 transações) — 12/12 passando

#### 6-D.2 — Modelo e Persistência

- [x] **Tabela `ofx_imports`** — `V17__ofx_imports.sql`; colunas: `id`, `account_id` (FK financial_accounts), `filename`, `bank_id`, `acct_id`, `dt_start`, `dt_end`, `imported_at`, `imported_by`, `total_records`, `new_records`, `duplicate_records`; índice `ix_ofx_imports_account_id`
- [x] **Campo `ofx_fitid` em `financial_transactions`** — `V16__transaction_ofx_fitid.sql`; coluna `ofx_fitid VARCHAR(64) NULL`; índice único parcial `(account_id, ofx_fitid) WHERE ofx_fitid IS NOT NULL`; campo adicionado em `FinancialTransactionsTable`, `Transaction`, `TransactionRepository` (insert + rowToTransaction); `TransactionRepository.existsByFitId(accountId, fitId)` para deduplicação eficiente
- [x] **`OfxImportRepository`** — `insert(OfxImport): Int`; `existsByFitId(accountId, fitId): Boolean` (delega para `financial_transactions`); `findByAccount(accountId)` e `findAll()` para histórico; registrado no Koin `repositoryModule`; `OfxImport` entidade de domínio em `ofx/OfxImport.kt`; `OfxImportsTable` em `ofx/OfxImportsTable.kt`; `OfxImportResult` adicionado em `OfxModels.kt`

#### 6-D.3 — Serviço de Importação

- [x] **`OfxImportService.import(file, accountId, userId)`** — fluxo completo em `ofx/OfxImportService.kt`:
  1. Parse via `OfxParser` — falha de parse retorna `OfxImportResult` com errorCount=1 sem abortar
  2. Validação do ACCTID por comparação de dígitos (aviso em `warnings`, não bloqueia importação)
  3. Por `OfxTransaction`: `OfxImportRepository.existsByFitId()` → duplicata = skip + duplicateCount++
  4. Mapeamento `DEP→INCOME`, `DEBIT→EXPENSE`, `XFER/OTHER` pelo sinal do `amount`
  5. `Transaction` construída com `status=PAID`, `paymentDate=dueDate=issueDate=dateTime`, `paidAmount=amount.abs()`, `ofxFitId=fitId`
  6. `TransactionService.createFromOfx()` — novo método dedicado que aceita PAID direto (sem enforcement de PENDING), sem parcelamento, timeline `OFX_IMPORT` + audit `TRANSACTION_CREATED`
  7. `OfxImportRepository.insert()` registra log de importação ao final
  8. Retorna `OfxImportResult(newCount, duplicateCount, errorCount, errors, warnings)` — `warnings` inclui aviso de ACCTID divergente
- [x] **Deduplicação idempotente** — reimportar o mesmo arquivo não cria duplicatas; resultado informa quantos já existiam; `TimelineEventType.OFX_IMPORT` adicionado; `OfxParser` e `OfxImportService` registrados no Koin `serviceModule`

#### 6-D.4 — UI de Importação

- [x] **`OfxImportScreen`** — tela em `ofx/OfxImportScreen.kt`; wizard de 3 etapas: **(1) SelectFile** — card centralizado com `FileDialog` nativo (`java.awt.FileDialog`, bloqueia thread IO), seletor de conta e mensagem de erro inline; **(2) Preview** — chips de resumo (banco/conta OFX/período/total/duplicatas), seletor de conta com recalculação de duplicatas ao trocar, tabela das primeiras 20 transações com tipo/FITID/memo/valor, botão "Importar N lançamentos" desabilitado sem conta selecionada; **(3) Done** — cards de estatística (importados/já existiam/erros), lista de avisos e erros, botões "Nova importação" e "Ver no extrato"
- [x] **Seletor de conta com auto-seleção** — `OfxImportViewModel.parseFile()` compara dígitos do `ACCTID` com `account.accountNumber`; se houver correspondência única, pré-seleciona a conta; `selectAccount()` recalcula `duplicateCount` via `OfxImportRepository.countDuplicates()` (single query com `inList`)
- [x] **Feedback pós-importação** — `DoneStep` mostra `newCount / duplicateCount / errorCount` em cards coloridos; warnings de ACCTID e erros individuais exibidos; botão "Ver no extrato" navega para `Screen.Statement`
- [x] **Histórico de importações** — `ImportHistoryTable` na etapa SelectFile; carregado via `OfxImportRepository.findAll()`; colunas: arquivo, data/hora, conta, período, novos, duplicatas; `OfxImportViewModel` registrado como `factory` no Koin; `Screen.OfxImport` adicionado; sidebar item "Importar OFX" no grupo FINANCEIRO; título "Importar OFX" no `TopToolbar`

#### 6-D.5 — Conciliação Manual Básica

> Os lançamentos OFX chegam como `PAID` (já aconteceram no banco). Pode haver lançamentos `PENDING` criados manualmente que correspondem ao mesmo movimento. Esta etapa resolve isso.

- [x] **Detecção de possíveis conciliações** — `OfxImportService.import()` rastreia `fitId → dbId` dos lançamentos criados; ao final do loop, chama `TransactionService.findPendingCandidates(accountId, absAmount, date)` (busca PENDING/OVERDUE com amount exato e dueDate ±3 dias, excluindo lançamentos com `ofxFitId != null`); candidatos listados em `OfxImportResult.candidates`
- [x] **`ReconcileStep` + `ConciliationCandidateCard`** — nova etapa do wizard exibida quando `result.hasCandidates`; lista todos os pares com cards lado a lado (coluna OFX: memo/data/valor/FITID; coluna Manual: descrição/vencimento/valor/status); botões "Vincular" (verde) e "Ignorar" (outline) por card; estado `handledIndices: Map<Int, Boolean>` controlado pelo ViewModel; badges de resultado (✓ Vinculado / ✗ Ignorado) após ação; botão "Concluir"/"Pular restantes" navega para `Step.Done`
- [x] **`TransactionService.reconcile(manualTxId, ofxTxId, ofxFitId)`** — requer `Permission.ConfirmPayment`; atualiza o lançamento manual com `status=PAID`, `paymentDate` da entrada OFX, `reconciledWithFitId=ofxFitId`; desativa o lançamento OFX (bypass da state machine, que não permite PAID→CANCELED em operação normal); registra `TimelineEventType.RECONCILED` no manual e `CANCELED` no OFX; auditoria em ambos
- [x] **Campo `reconciledWith`** — `V18__transaction_reconciled_with.sql` adiciona `reconciled_with_fitid VARCHAR(64) NULL` em `financial_transactions`; coluna em `FinancialTransactionsTable`; campo `reconciledWithFitId: String?` em `Transaction`; mapeado em `insert()`, `update()` e `rowToTransaction()`; permite futuramente filtrar conciliados vs não-conciliados no extrato

---

### Fase 7-A — Recorrência Automática

> Objetivo: o operador cadastra uma vez uma despesa ou receita recorrente (aluguel, assinatura, mensalidade de associado) e o sistema gera automaticamente os lançamentos mês a mês — sem prazo fixo, sem lançar na mão. Segue o mesmo padrão do `PayrollEngine` já existente.

#### 7-A.1 — Modelo e Persistência

- [ ] **`RecurrenceInterval` enum** — `recurrence/RecurrenceInterval.kt`; valores: `DIARIO`, `SEMANAL`, `QUINZENAL`, `MENSAL`, `BIMESTRAL`, `TRIMESTRAL`, `SEMESTRAL`, `ANUAL`; campo `displayName` para UI
- [ ] **Entidade `RecurrenceTemplate`** — `recurrence/RecurrenceTemplate.kt`; campos: `id`, `description`, `amount: Money`, `type: TransactionType`, `interval: RecurrenceInterval`, `dayOfMonth: Int`, `accountId`, `supplierId?`, `categoryId?`, `costCenterId?`, `documentType?`, `notes?`, `startsAt: LocalDate`, `endsAt: LocalDate?`, `isActive`, `createdBy?`, `createdAt`, `updatedAt`
- [ ] **`RecurrenceTemplatesTable`** — `recurrence/RecurrenceTemplatesTable.kt` + `V19__recurrence_templates.sql`; FK para `financial_accounts`, `suppliers`, `expense_categories`, `projects`
- [ ] **Campo `recurrence_template_id` em `financial_transactions`** — `V20__transaction_recurrence_fk.sql`; coluna `INTEGER NULL`; atualizar `FinancialTransactionsTable`, `Transaction` (campo `recurrenceTemplateId: Int?`), `TransactionRepository` (`insert`, `update`, `rowToTransaction`)
- [ ] **`RecurrenceTemplateRepository`** — CRUD completo + `findAllActive()` + `existsGeneratedFor(templateId: Int, dueDate: LocalDate): Boolean` (consulta `financial_transactions` por `recurrence_template_id` + janela do dia para deduplicação)

#### 7-A.2 — Motor de Geração

- [ ] **`RecurrenceEngine.generateAhead(monthsAhead: Int = 2)`** — `recurrence/RecurrenceEngine.kt`; itera todos os templates ativos; calcula as próximas datas até `now + monthsAhead`; para cada data: `existsGeneratedFor()` → pula se já existe; cria `Transaction(status=PENDING, recurrenceTemplateId=template.id)` via `TransactionService.createFromRecurrence()` (novo método sem enforcement de parcelamento e sem `PAID` forçado); registra `TimelineEventType.RECURRENCE_GENERATED` na timeline
- [ ] **`RecurrenceEngine.generateForTemplate(id)`** — sobrecarga individual; chamada imediatamente ao salvar/reativar um template
- [ ] **Cálculo de datas por intervalo** — `RecurrenceDateCalculator.kt`; respeita `dayOfMonth` mas ajusta para último dia do mês quando necessário (ex: dia 31 em fevereiro → 28/29); edge cases: dia 29/30/31 em meses curtos
- [ ] **Gatilhos de geração** — `Main.kt`: dispara `generateAhead(2)` em `CoroutineScope(Dispatchers.IO)` na abertura (mesmo padrão do `PayrollEngine`); `RecurrenceTemplateService.save()` e `toggleActive()` chamam `generateForTemplate(id)` ao ativar
- [ ] **Testes unitários `RecurrenceEngineTest`** — geração mensal correta; deduplicação (não gera duplicata); bimestral/semestral; edge case dia 31 em meses curtos; `endsAt` respeitado; `generateAhead(0)` não gera nada; template inativo ignorado

#### 7-A.3 — UI

- [ ] **Tela `RecurringScreen`** — `recurrence/RecurringScreen.kt`; lista de templates com colunas: Descrição / Tipo / Intervalo / Próx. Vencimento / Conta / Status; badge colorido por tipo (Receita/Despesa); botões "+ Recorrência Despesa" e "+ Recorrência Receita"; sidebar item "Recorrências" no grupo FINANCEIRO
- [ ] **Painel de detalhes do template** — `RecurringDetailsPanel.kt`; campos completos editáveis; seção "Histórico Gerado" listando as últimas transações geradas pelo template (filtro `recurrenceTemplateId`); badge do próximo vencimento a gerar
- [ ] **Toggle "Recorrente" no `TransactionDetailsPanel`** — checkbox ou switch "Tornar recorrente" que expande seção com: intervalo (`WsSelectField`), dia do mês, data de encerramento (opcional); ao salvar com recorrência ativa, cria `RecurrenceTemplate` e preenche `recurrenceTemplateId` na transação; visível apenas na criação (não na edição de transação já gerada)
- [ ] **Cancelamento de recorrência** — no painel do template: botão "Pausar" (desativa template, preserva gerados); botão "Cancelar Futuras" (`WsButton` com cor `WsDanger`): desativa template + cancela todos `PENDING/DRAFT` com `recurrenceTemplateId` igual (cascata controlada); confirmação via `ConfirmDialog` antes de executar

---

### ✅ Fase 7-B — Contratos

> Objetivo: registrar formalmente contratos de prestação de serviço ou fornecimento (com contratado, vigência e valor), vincular lançamentos ao contrato e acompanhar quanto já foi consumido do valor total contratado. Contratos recorrentes geram lançamentos automaticamente via integração com a Fase 7-A.

#### 7-B.1 — Modelo e Persistência

- [x] **`ContractStatus` enum** — `contracts/ContractStatus.kt`; valores: `VIGENTE`, `ENCERRADO`, `SUSPENSO`, `CANCELADO`; `displayName` e `color` para badge na UI
- [x] **Entidade `Contract`** — `contracts/Contract.kt`; campos: `id`, `number: String` (ex: "CT-001/2025"), `description`, `contractorId: Int` (FK suppliers), `type: TransactionType` (INCOME ou EXPENSE), `totalValue: Money`, `startDate: LocalDate`, `endDate: LocalDate?`, `status: ContractStatus`, `notes?`, `recurrenceTemplateId: Int?` (FK opcional), `createdBy?`, `createdAt`, `updatedAt`
- [x] **`ContractsTable`** — `contracts/ContractsTable.kt` + `V21__contracts.sql`; FK para `suppliers`; índice em `status`
- [x] **Campo `contract_id` em `financial_transactions`** — `V22__transaction_contract_fk.sql`; coluna `INTEGER NULL`; atualizar `FinancialTransactionsTable`, `Transaction` (campo `contractId: Int?`), `TransactionRepository` (`insert`, `update`, `rowToTransaction`, `existsPendingByContract(contractId): Boolean`)
- [x] **Campo `contract_id` em `recurrence_templates`** — adicionar em `V21` (ou migration própria se V21 já migrou); `RecurrenceTemplate` + `RecurrenceTemplatesTable` atualizados; quando preenchido, transações geradas pelo engine recebem `contractId` automaticamente

#### 7-B.2 — Serviço e Regras

- [x] **`ContractRepository`** — CRUD + `findAll()`, `findByStatus(status)`, `findByContractor(supplierId)`, `sumConsumed(contractId): Money` (soma `paidAmount` de transações PAID vinculadas), `existsPendingTransactions(contractId): Boolean`
- [x] **`ContractService`** — `save()` com validação (número único, `endDate >= startDate`); `updateStatus()`: bloqueia ENCERRADO/CANCELADO se `existsPendingTransactions()`; `getExecutionSummary(contractId)`: retorna `totalValue`, `consumed`, `remaining`, `percentUsed`; `delete()`: bloqueia se existirem transações vinculadas
- [x] **`ContractViewModel`** — expõe `ContractUiState` via `StateFlow`; herdando `BaseCrudViewModel`; carrega lista de suppliers para o selector; chama `getExecutionSummary()` ao selecionar contrato

#### 7-B.3 — UI

- [x] **`ContractsScreen`** — `contracts/ContractsScreen.kt`; tabela com colunas: Nº / Objeto / Contratado / Valor Total / Consumido / Saldo / Status / Vigência; badge colorido por `ContractStatus`; barra de progresso de consumo embutida na célula "Consumido" (cor varia: verde < 80%, amarelo 80-99%, vermelho >= 100%); filtro por status
- [x] **Painel de detalhes do contrato** — campos completos editáveis; seção "Execução" com tile `totalValue` / tile `consumed` / tile `remaining` / percentual; lista das últimas 10 transações vinculadas com data, descrição e valor; botão "Vincular recorrência" abre dropdown de templates existentes ou cria novo
- [x] **Seletor de contrato no `TransactionDetailsPanel`** — `WsSelectField` "Contrato (opcional)" listando contratos `VIGENTE` do tipo correspondente (EXPENSE mostra contratos EXPENSE, INCOME mostra INCOME); ao selecionar, auto-preenche `supplierId` com o `contractorId` do contrato selecionado (pode ser sobrescrito)
- [x] **Sidebar item "Contratos"** — no grupo GESTÃO, entre Funcionários e Usuários; `Screen.Contracts` adicionado em `Screen.kt`; roteamento em `MainLayout.kt`
- [x] **Alerta de extrapolação de contrato** — `ContractService.validateAmount()`: aviso (não bloqueia) ao criar transação cujo valor fará `consumed > totalValue`; exibido como badge âmbar no `TransactionDetailsPanel` (análogo ao aviso de orçamento RN-27 já existente)

---

### Fase 7-C — Clientes e Recebíveis

> Objetivo: registrar clientes (contraparte de receitas), vincular transações INCOME a clientes e ter uma tela de contas a receber com aging — espelho do que já existe para despesas/fornecedores.

#### 7-C.1 — Modelo: Parceiros Comerciais

- [ ] **`EntityType` enum** — `suppliers/EntityType.kt`; valores: `FORNECEDOR`, `CLIENTE`, `AMBOS`; `displayName` para UI
- [ ] **`V23__supplier_entity_type.sql`** — adiciona coluna `entity_type VARCHAR(20) NOT NULL DEFAULT 'FORNECEDOR'` em `suppliers`; `UPDATE suppliers SET entity_type = 'FORNECEDOR'` para dados existentes
- [ ] **Modelo `Supplier` atualizado** — campo `entityType: EntityType = EntityType.FORNECEDOR`; `Suppliers` table com a nova coluna; `SupplierRepository`: `findByType(type)`, `findSuppliers()` (FORNECEDOR+AMBOS), `findClients()` (CLIENTE+AMBOS); `findAll()` sem filtro para compatibilidade
- [ ] **`SupplierService` atualizado** — proteger `save()`: não permite remover papel CLIENTE se existirem transações INCOME vinculadas (e vice-versa para FORNECEDOR)

#### 7-C.2 — Tela de Clientes

- [ ] **`ClientsScreen`** — `clients/ClientsScreen.kt`; reutiliza exatamente os composables de `SupplierManagementScreen` (sem duplicar lógica); diferença: título "Clientes", `viewModel.load()` chama `findClients()`; painel de detalhes com campo `entityType` pré-selecionado em CLIENTE
- [ ] **Tela de Fornecedores ajustada** — `SupplierManagementScreen`: `SupplierViewModel.load()` passa a chamar `findSuppliers()` em vez de `findAll()` para filtrar apenas FORNECEDOR/AMBOS; chip "Tipo" na toolbar com filtro Todos/Fornecedor/Ambos
- [ ] **`ClientsViewModel`** — `clients/ClientsViewModel.kt`; herda `BaseCrudViewModel<Supplier, SupplierUiState>`; delega ao mesmo `SupplierService` com `loadItems()` chamando `repository.findClients()`
- [ ] **Sidebar item "Clientes"** — grupo CADASTROS, após Fornecedores; `Screen.Clients` em `Screen.kt`; roteamento em `MainLayout.kt`

#### 7-C.3 — Campo Cliente nas Transações

- [ ] **`TransactionDetailsPanel` — label dinâmico** — campo contraparte (`supplierId`) exibe "Fornecedor" quando `type == EXPENSE` e "Cliente" quando `type == INCOME`; lista de opções carregada com `findSuppliers()` para EXPENSE e `findClients()` para INCOME; ao trocar o `type` no formulário, a lista recarrega e a seleção é limpa
- [ ] **Livro Diário e relatórios** — coluna "Credor/Cliente" nos relatórios já existentes: para INCOME, exibe o nome do cliente vinculado (mesmo campo `supplierId`, só muda o label no cabeçalho)

#### 7-C.4 — Tela de Contas a Receber

- [ ] **`ReceivablesScreen`** — `receivables/ReceivablesScreen.kt`; filtra transações `type=INCOME` com status `PENDING/OVERDUE/PARTIAL`; agrupadas por cliente (nome ou "Sem cliente"); colunas: Cliente / Descrição / Vencimento / Valor / Status / Dias em atraso; linha em vermelho quando `OVERDUE`
- [ ] **`ReceivablesViewModel`** — carrega via `TransactionRepository.findReceivables()` (nova query: `type=INCOME AND status IN (PENDING,OVERDUE,PARTIAL)`); agrupa por `supplierId`; calcula `daysOverdue = max(0, today - dueDate)`
- [ ] **Tiles de aging no topo da `ReceivablesScreen`** — 4 tiles: "A vencer" / "1–30 dias" / "31–60 dias" / "61+ dias"; valores calculados no `ReceivablesViewModel`; cores: cinza / amarelo / laranja / vermelho
- [ ] **Card de Recebíveis no Dashboard** — tile "Recebíveis Vencidos" com total em atraso e contagem (análogo ao tile "Vencidos" já existente para despesas); `DashboardViewModel` injeta query de recebíveis via `TransactionRepository`
- [ ] **Sidebar item "Recebíveis"** — grupo FINANCEIRO, após Movimentações; `Screen.Receivables` em `Screen.kt`; roteamento em `MainLayout.kt`

---

## Legenda

| Símbolo | Significado |
|---------|-------------|
| ✅ Concluído | Implementado e funcional |
| 🔄 Em Andamento | Iniciado nesta sessão / sprint |
| 🔴 Pendente | Ainda não começado |
| RN-XX | Código da regra de negócio em `REQUISITOS_SISGFIN.md` |

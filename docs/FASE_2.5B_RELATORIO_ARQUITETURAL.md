# Relatório Técnico — Fase 2.5B — Architectural Hardening

**Projeto:** SisgFin  
**Data:** 22/05/2026  
**Escopo:** Endurecimento arquitetural (sem funcionalidades de negócio do Motor Financeiro)

---

## 1. Estrutura arquitetural criada

A fase introduziu uma camada **foundation** explícita, separada do código legado na raiz do pacote `br.com.sisgfin`:

```
br.com.sisgfin/
├── core/
│   ├── crud/           # BaseCrudViewModel, CrudAction, CrudEvent, CrudState, CrudUiState, CrudResult, CrudOperations
│   ├── domain/         # Identifiable, Activatable, MutableEntityRepository, AuditedCrudService
│   ├── errors/         # AppError, ErrorClassifier, AppLogger
│   ├── result/         # Result<T> (Success | Error | Validation)
│   └── ui/
│       ├── panel/      # BaseCrudPanel
│       ├── loading/    # LoadingOverlay
│       ├── notifications/ # CrudEventEffects (snackbar bridge)
│       ├── dialogs/    # ConfirmDialog
│       ├── overlays/   # GlobalOverlayHost, OverlayRegistry
│       ├── keyboard/   # KeyboardShortcuts
│       ├── focus/      # rememberPanelFocusRequester
│       └── navigation/ # PanelNavigationState
├── di/
│   ├── AppModules.kt
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt
│   ├── ServiceModule.kt
│   └── ViewModelModule.kt
├── suppliers/          # SupplierViewModel
├── accounts/           # FinancialAccountViewModel
├── projects/           # ProjectViewModel
├── employees/          # EmployeeViewModel
├── users/              # UserManagementViewModel
├── dashboard/          # DashboardViewModel
├── financial/money/    # (pré-existente, mantido)
└── presentation/       # LoginViewModel, BaseViewModel, UiStates legados
```

**Princípio:** `core` concentra contratos e comportamento transversal; módulos de domínio (`suppliers`, `accounts`, etc.) concentram ViewModels especializados; `di` centraliza composição.

---

## 2. Organização final de pacotes

| Pacote | Responsabilidade |
|--------|------------------|
| `core.crud` | Máquina de estados/ações/eventos CRUD |
| `core.domain` | Contratos de entidade e serviço auditado |
| `core.errors` | Erros tipados + logging (java.util.logging) |
| `core.result` | Result pattern operacional |
| `core.ui.*` | Infraestrutura visual workstation |
| `di` | Módulos Koin |
| `suppliers/accounts/projects/employees/users/dashboard` | ViewModels por bounded context |
| Raiz `br.com.sisgfin` | Models, repositories, screens, tema (migração física futura) |

**Pendente (planejado, não executado nesta fase):** mover `*Repository.kt`, `*Screen.kt`, `Tables.kt` para `data/`, `auth/`, `shared/`. A estrutura-alvo do prompt foi **iniciada** via pacotes de ViewModel e `core/`, sem big-bang move de 30+ arquivos para reduzir risco de regressão.

---

## 3. Estratégia de DI adotada

- **Framework:** Koin 4.0.0 (`koin-core` + `koin-compose`)
- **Escopos:**
  - `single` — repositories, services, `SessionManager`, `NavigationState`, `DatabaseFactory` (referência)
  - `factory` — ViewModels (nova instância por resolução; adequado a desktop sem Android Lifecycle)
- **Inicialização:** `startKoin { modules(appModules) }` em `Main.kt`, após `DatabaseFactory.init()`
- **Consumo na UI:** `koinInject<T>()` em `App.kt` — eliminação do bloco `remember { }` como composition root
- **Proibições respeitadas:** sem service locator manual, sem singletons espalhados em objetos globais

**Benefícios imediatos:** wiring testável, expansão por módulos (`financialModule`, `authModule` futuros), preparação para perfis/ambientes via `koinApplication { }` adicional.

---

## 4. Como Koin foi integrado

1. Dependências adicionadas em `build.gradle.kts`.
2. Módulos criados conforme estrutura obrigatória do prompt.
3. `Main.kt` chama `startKoin` antes de renderizar `App()`.
4. `App.kt` resolve dependências via `koinInject()` e repassa ViewModels ao `MainLayout` (prop drilling mantido temporariamente no layout, não no composition root).
5. ViewModels financeiros e de RH migrados para pacotes dedicados com registro em `ViewModelModule.kt`.

**Compose Desktop:** não há `koinViewModel()` com lifecycle Android; `factory` + `koinInject()` é o padrão correto para esta stack.

---

## 5. CRUD infrastructure criada

### 5.1 Contratos

| Artefato | Papel |
|----------|-------|
| `CrudState<T>` | Estado persistente (lista, seleção, loading, painel, dialog, busca, dirty, erro) |
| `CrudUiState<T>` | Projeção para UI (compatível com telas antigas) |
| `CrudAction<T>` | Intenções do usuário (Select, OpenPanel, Save, Search, Refresh, etc.) |
| `CrudEvent` | Efeitos transitórios (Snackbar, Error, ConfirmDialog, OperationSuccess) |
| `CrudOperations<T>` | Contrato de serviço (listAll, save, toggleActive) |
| `BaseCrudViewModel<T>` | Orquestração: IO em `Dispatchers.IO`, emissão de eventos, tratamento de erro |

### 5.2 Serviço auditado unificado

`AuditedCrudService<T>` consolida create/update/toggle + `AuditRepository` + `SessionManager`, substituindo ~120 linhas triplicadas em `FinancialServices.kt`.

### 5.3 Repositório tipado

`MutableEntityRepository<T>` implementado por `SupplierRepository`, `FinancialAccountRepository`, `ProjectRepository`.

### 5.4 Painel base

`BaseCrudPanel` padroniza header, subtítulo, indicador dirty, erro inline, footer save/cancel, `LoadingOverlay`.

---

## 6. Event/Action architecture

**Separação aplicada:**

```
UI → CrudAction → BaseCrudViewModel → CrudState (persistente)
                              ↓
                         CrudEvent (transitório) → CrudEventEffects → Snackbar
```

- **Estado persistente** não contém mensagens de snackbar nem flags de navegação one-shot.
- **Eventos** são consumidos em `LaunchedEffect` via `SharedFlow` (buffer 8).
- **UserManagementViewModel** segue o mesmo padrão de `CrudAction`/`CrudEvent`, com estado composto (`CrudState` + audit logs separados) por requisitos específicos de auditoria por entidade.

---

## 7. Refatorações realizadas

| Área | Mudança |
|------|---------|
| `App.kt` | Removidos 30+ `remember { }`; DI via Koin |
| `Main.kt` | Bootstrap Koin |
| `FinancialServices.kt` | Três classes → três linhas de herança `AuditedCrudService` |
| `FinancialViewModels.kt` | **Removido**; substituído por ViewModels em pacotes de módulo |
| `EmployeeViewModel` | Migrado para `employees/`, estende `BaseCrudViewModel` |
| `UserManagementViewModel` | Migrado para `users/`, actions/events + `Result` |
| `DashboardViewModel` | Migrado para `dashboard/`, erros via `AppLogger` |
| Models | `Identifiable` / `Activatable` em entidades CRUD |
| Painéis laterais | Suppliers, Accounts, Projects, Employees → `BaseCrudPanel` |
| `MainLayout` | Escape via `KeyboardShortcuts` |
| Erros | `AppLogger` (JUL), sem `println` |

---

## 8. ViewModels migrados

| ViewModel | Pacote novo | Base | Serviço |
|-----------|-------------|------|---------|
| `SupplierViewModel` | `suppliers` | `BaseCrudViewModel` | `SupplierService` |
| `FinancialAccountViewModel` | `accounts` | `BaseCrudViewModel` | `FinancialAccountService` |
| `ProjectViewModel` | `projects` | `BaseCrudViewModel` | `ProjectService` |
| `EmployeeViewModel` | `employees` | `BaseCrudViewModel` | `EmployeeService` |
| `UserManagementViewModel` | `users` | `BaseViewModel` + CrudAction/Event | `UserManagementService` |
| `DashboardViewModel` | `dashboard` | `BaseViewModel` | Repos diretos (legado dashboard) |
| `LoginViewModel` | `presentation.viewmodel` | — | `AuthService` (inalterado) |

**Registrados em Koin:** todos os acima + dependências encadeadas.

---

## 9. Redução de duplicação obtida

| Antes | Depois | Estimativa |
|-------|--------|------------|
| 3× ViewModel CRUD (~55 linhas) | 1× `BaseCrudViewModel` + 3× classe ~15 linhas | **~120 linhas** |
| 3× Service auditado (~44 linhas) | 1× `AuditedCrudService` + 3× declaração | **~100 linhas** |
| 4× painel com header/footer manual | `BaseCrudPanel` | **~80 linhas/ painel** |
| DI manual 30 linhas `remember` | 5 módulos Koin + 8 `koinInject` | Composição centralizada |
| try/catch + `errorMessage` disperso | `ErrorClassifier` + `AppLogger` + `Result` | Padronização |

**Duplicação remanescente (honesta):**

- Telas `*Screen.kt` ainda repetem scaffold de tabela (não extraído para `CrudListScreen` genérico).
- Popups (`SupplierPopup`, `EmployeePopup`) coexistem com painel lateral (fluxo duplo).
- `UserManagementScreen` e toolbar de Employees não usam `CrudToolbar` unificado.
- `EmployeeUiState.kt` legado não removido (não referenciado pelo novo VM).

---

## 10. Problemas arquiteturais resolvidos

1. **DI inexistente** → Koin com módulos nomeados.
2. **Composition root no Compose** → `remember` eliminado de `App.kt`.
3. **Triplicação CRUD financeiro** → `BaseCrudViewModel` + `AuditedCrudService`.
4. **Mistura estado/efeito** → `CrudState` vs `CrudEvent`.
5. **Erros ad hoc** → `AppError` + classificação + logging estruturado.
6. **Retornos inconsistentes** → `Result<T>` em ViewModels migrados.
7. **Painéis sem padrão** → `BaseCrudPanel` nos módulos principais.
8. **Acoplamento de auditoria nos services** → centralizado em `AuditedCrudService`.

---

## 11. Riscos restantes

| Risco | Severidade | Descrição |
|-------|------------|-----------|
| ViewModels `factory` sem escopo de tela | Média | Instâncias novas a cada `koinInject` se `App` recompor; na prática `App` é estável, mas logout não chama `clear()` |
| `init { load() }` em todos VMs registrados | Média | Telas não visitadas ainda disparam load ao primeiro inject |
| Conflito de nome `Result` | Baixa | `kotlin.Result` vs `core.result.Result` — exige imports explícitos |
| Prop drilling no `MainLayout` | Baixa | 6 ViewModels passados manualmente |
| Popups + painel | Média | UX inconsistente e campos divergentes (ex.: Supplier popup com dados bancários, painel com PIX) |
| Dashboard acoplado a repos legados | Média | `AccountRepository` vs `FinancialAccountRepository` ainda confusos conceitualmente |

---

## 12. Limitações atuais

- **Sem testes unitários** para `BaseCrudViewModel`, `AuditedCrudService`, Koin modules (apenas `MoneyTest` pré-existente passa).
- **Busca na toolbar** ainda placeholder (`searchQuery = ""`); infraestrutura `CrudAction.Search` existe mas UI não conectada.
- **Paginação** preparada em `CrudState` conceitualmente, não implementada.
- **Reorganização física completa** de pacotes (`data/`, `auth/`) não realizada.
- **GlobalOverlayHost** criado mas não integrado ao `MainLayout`.
- **ConfirmDialog** via `CrudEvent.ConfirmDialog` não wired na UI.
- **Login** permanece fora do padrão CRUD (correto para o domínio).

---

## 13. Impactos futuros positivos

1. **Motor Financeiro:** novos agregados (ledger, transações) plugam `CrudOperations` ou serviços de domínio dedicados sem reinventar loading/erro/painel.
2. **Testes:** módulos Koin permitem `startKoin { modules(testModule) }` com mocks de `CrudOperations`.
3. **Multi-ambiente:** `serviceModule` pode bifurcar por propriedade Gradle (`dev`, `prod`).
4. **Features transversais:** snackbar, confirmação e overlay têm ponto único de extensão.
5. **Auditoria:** padrão único para entidades mestras; novas entidades só definem `entityType` e lambdas de cópia.

---

## 14. Antes vs Depois

### Arquitetura antiga

```
Main → App (remember × 30)
         ├── Repositories (new each composition)
         ├── Services (manual wire)
         └── ViewModels (God init load, duplicate CRUD)
                └── Screens (copy-paste table + panel + popup)
```

**Características:** service locator Compose, estado misturado (`errorMessage` + dialog flags), services financeiros copy-paste, sem contratos de domínio, sem logging padronizado.

### Arquitetura nova

```
Main → startKoin(appModules) → DatabaseFactory.init()
         └── App (koinInject)
                ├── SessionManager / NavigationState
                ├── ViewModels (module packages)
                │     └── BaseCrudViewModel / CrudAction / CrudEvent
                └── MainLayout → Screens
                       └── BaseCrudPanel + CrudEventEffects
```

**Características:** DI explícita, serviços auditados herdam base, erros tipados, UI foundation para workstation, ViewModels por bounded context.

---

## 15. Dívidas técnicas restantes

1. Extrair `CrudListScreen<T>` parametrizável (colunas, row, empty state).
2. Eliminar popups duplicados ou unificar com painel lateral.
3. Escopo de ViewModel por navegação + `clear()` no logout.
4. Mover arquivos de dados/auth para pacotes finais.
5. Renomear/clarificar `AccountRepository` (dashboard legado) vs `FinancialAccountRepository`.
6. Implementar testes: `AuditedCrudService`, `BaseCrudViewModel`, regras `UserManagementService`.
7. Conectar busca real ao `CrudToolbar`.
8. Integrar `GlobalOverlayHost` e `ConfirmDialog` nos fluxos de exclusão/desativação.
9. Lazy load de dados por tela (não no `init` global do VM).
10. Adicionar interfaces de repositório injetáveis (hoje classes concretas).

---

## 16. Avaliação HONESTA

### O sistema agora está arquiteturalmente saudável?

**Parcialmente sim.** A fundação (`core`, `di`, padrão CRUD, erros, Result) está em nível **enterprise-grade** para um produto desktop em evolução. O código legado na raiz do pacote e as telas repetitivas ainda impedem classificar o projeto como “totalmente consolidado”.

### Ainda existem gargalos?

**Sim:**

- Composição de listas/painéis ainda manual por tela.
- Lifecycle de ViewModel não alinhado à navegação.
- Domínio financeiro real (ledger, transações) ainda não existe — o dashboard usa tabelas legadas `Accounts`/`Transactions`.
- Testes de arquitetura ausentes.

### O sistema está pronto para o Motor Financeiro?

**Pronto para iniciar, não para implementar o motor sem mais uma camada.**

Recomendação: começar o Motor Financeiro com:

1. Novo pacote `financial/engine/` (ou módulo Gradle separado).
2. Serviços que **não** herdam `AuditedCrudService` quando a regra for transacional/contábil (não CRUD simples).
3. Manter `BaseCrudViewModel` apenas para cadastros mestres.
4. Resolver ambiguidade `Account` legado vs `FinancialAccount` **antes** de lançamentos contábeis.

**Veredicto:** A Fase 2.5B cumpriu o objetivo de **endurecer** a plataforma. O SisgFin deixou de ser apenas “app modular com Compose” e passou a ter **infraestrutura de plataforma** reutilizável. A saúde arquitetural é **boa na fundação**, **média na periferia (UI screens)**, e **dependente de disciplina** na próxima fase para não reintroduzir duplicação no motor financeiro.

---

## Verificação de build

```
./gradlew compileKotlin test
BUILD SUCCESSFUL
```

Testes existentes (`MoneyTest`) passam; novos testes de infraestrutura ficam como dívida explícita (Seção 15).

---

*Documento gerado como entrega obrigatória da Fase 2.5B — Architectural Hardening.*

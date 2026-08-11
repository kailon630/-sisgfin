# R2 — Dados bancários e remessa

## 1. Resumo executivo

Funcionários e fornecedores têm modelos bancários **diferentes** (códigos estruturados COMPE vs texto livre). Remessa bancária existe **somente** para folha (`PayrollBankExporter` + `Employee`); sem dados bancários o funcionário é **omitido** da remessa, não gera linha inválida. `BankList` é usado só no cadastro de funcionários. Na base: 1 funcionário ativo com banco/conta preenchidos; 1 fornecedor ativo sem banco/conta.

## 2. Estado atual

### 2.1 Estrutura dos campos bancários

#### `employees` (migração V25)

| Campo Kotlin | Coluna SQL | Tipo | Nullable | Formato esperado |
|---|---|---|---|---|
| `bankCode` | `bank_code` | VARCHAR(3) | YES | código COMPE 3 dígitos (`BankList`) |
| `agencyNumber` | `agency_number` | VARCHAR(10) | YES | número da agência |
| `agencyDv` | `agency_dv` | VARCHAR(2) | YES | dígito verificador; UI formata `agência-DV` |
| `accountNumber` | `account_number` | VARCHAR(20) | YES | número da conta |
| `accountDv` | `account_dv` | VARCHAR(2) | YES | DV; formata `conta-DV` |
| `accountType` | `account_type` | VARCHAR(2) DEFAULT `'CS'` | YES | `CS` / `CC` / `CP` |

`Employee.hasBankingData` exige `bankCode`, `agencyNumber` e `accountNumber` não vazios.

#### `suppliers` (V4 + modelo)

| Campo Kotlin | Coluna SQL | Tipo | Nullable | Formato esperado |
|---|---|---|---|---|
| `bank` | `bank` | VARCHAR(50) | YES | texto livre |
| `agency` | `agency` | VARCHAR(20) | YES | texto livre |
| `account` | `account` | VARCHAR(20) | YES | texto livre |
| `pixKey` | `pix_key` | VARCHAR(100) | YES | texto livre |

UI de fornecedor: campos livres, **sem** `BankList`.

### 2.2 `PayrollBankExporter.kt`

O exporter **não lê** entidade do banco: recebe `List<RemessaEntry>` já montada e escreve XLSX (CPF, agência, conta, valor).

Montagem em `PayrollImportViewModel`: para cada entrada, carrega `Employee`; se `emp.hasBankingData`, inclui na remessa; senão, nome vai para lista de faltantes (`missingBankData`).

Se `bankCode` / `agencyNumber` / `accountNumber` nulos ou vazios → **pula a linha** (não entra no arquivo); não lança exceção; não gera linha com campos vazios via esse caminho.

### 2.3 Remessa para fornecedor

**Não existe.** Nenhum exporter/serviço gera remessa a partir de `Supplier`. Campos bancários do fornecedor ficam só no cadastro. Confirmação explícita: remessa bancária hoje é exclusiva do fluxo de folha (funcionários).

### 2.4 Uso de `BankList.kt`

Usado em `EmployeesScreen.kt` (seleção de banco COMPE). **Não** importado nas telas de fornecedor/cliente.

## 3. Resultados das queries

> Ajuste: em `employees` a flag é `active`, não `is_active`. Query executada com `WHERE active = true`.

**Funcionários ativos:**

| total | com_banco (`bank_code`) | com_conta (`account_number`) |
|------:|------------------------:|-----------------------------:|
| 1 | 1 | 1 |

**Fornecedores ativos:**

| total | com_banco (`bank`) | com_conta (`account`) |
|------:|-------------------:|----------------------:|
| 1 | 0 | 0 |

## 4. Achados

| # | Severidade | Achado | Arquivo:linha | Impacto |
|---|-----------|--------|---------------|---------|
| 1 | MÉDIO | Modelos bancários divergentes (funcionário estruturado × fornecedor texto livre) | `Tables.kt` / V25 vs V4 | Impede reutilizar remessa de folha para AP |
| 2 | ALTO | Sem caminho de remessa para pagar fornecedor | ausência de exporter Supplier | Pagamento a fornecedor via arquivo bancário não é suportado |
| 3 | MÉDIO | Funcionário sem dados bancários é silencioso na remessa (só lista de faltantes no VM) | `PayrollImportViewModel` ~260–272 | Risco operacional de omitir pagamento se operador não notar faltantes |
| 4 | BAIXO | Fornecedor ativo na base sem nenhum dado bancário | query R2 | Cadastro incompleto para eventual uso futuro |

## 5. Incertezas

- Não foi exercitado o fluxo UI completo de exportação com `missingBankData` (apenas código).
- Formato exato exigido pelo banco (BB) além do template XLSX do exporter não foi validado contra manual externo.
- Volume de dados local é mínimo; taxas de preenchimento em produção desconhecidas.

# T-21 — Sanitização de description e notes das transações

## Contexto

Após T-19 (sanitização de `employees.name`, `employees.role`, `employees.email`,
`suppliers.name`) identificamos que os campos livres de `financial_transactions`
também podiam conter caracteres de controle provenientes de fontes externas:
arquivos OFX do Banco do Brasil (linha-ending `\r\n`), planilhas Excel e edição
direta pelo usuário via campos de texto livre.

## Campos afetados

| Campo            | Função              | Regra aplicada          |
|------------------|---------------------|-------------------------|
| `description`    | Linha única, TCESP  | `clean()`               |
| `document_type`  | Linha única         | `clean()`               |
| `document_number`| Linha única         | `clean()`               |
| `notes`          | Observação livre    | `cleanPreserveNewlines()`|

`notes` preserva `\n` e `\t` porque observações multilinhas são legítimas.
`ofx_fitid` **não foi alterado** — é chave de deduplicação e já estava limpo no banco.

## Implementação (4 BLOCOs)

### BLOCO 1 — TransactionService (`9b02d1c`)
Helper privado `sanitize(tx: Transaction): Transaction` aplicado em 7 pontos de entrada:
`create()`, `update()`, `createTransfer()`, `reverseTransaction()`, `createFromOfx()`,
`createFromPayrollImport()`, `createFromRecurrence()`.
`duplicate()` e `generateInstallments()` são cobertos transitivamente via `create()`.

### BLOCO 2 — OfxParser (`929da8a`)
`TextSanitizer.clean()` aplicado a FITID (na leitura da tag) e a MEMO (no fechamento
`</STMTTRN>`). `lineSequence()` já divide linhas em `\r`, `\n` e `\r\n`, então o
principal risco no parser é NBSP (U+00A0) e zero-width, não CR puro.

### BLOCO 3 — Migração V36 (`a427cec`)
UPDATE preventivo nos 4 campos de `financial_transactions`. Esperado 0 linhas afetadas
(banco estava limpo à época da escrita). `notes` preserva `\n` via lógica de dois
`replace()` antes do `regexp_replace()`.

### BLOCO 4 — Documentação (este commit)
Relatório + KANBAN atualizado + tag `t21-sanitizacao-transacoes`.

## Testes adicionados

| Suite                     | Testes  | O que cobrem                                              |
|---------------------------|---------|-----------------------------------------------------------|
| `TransactionSanitizeTest` | TSAN-01…11 | create/update/payroll: CR, CRLF, LF preservado, null, idempotente |
| `OfxParserTest` (SAN)     | OFX-SAN-01…04 | CRLF no OFX, NBSP em MEMO, FITID dedup, idempotente  |

## Decisões

- **`\r` em MEMO no parser**: `lineSequence()` trata `\r` como separador de linha,
  então CR puro já é neutralizado antes do sanitizer. O sanitizer protege contra
  NBSP, zero-width e C1 que não são separadores.
- **`ofx_fitid` na migração**: excluído por ser chave de deduplicação; zero linhas
  sujas confirmadas antes da implementação.
- **`calculateBalance` e `transaction_payments`**: não tocados em nenhum bloco.

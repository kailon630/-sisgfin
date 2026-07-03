# Captura de screenshots — Auditoria SisgFin

Os screenshots **não foram gerados automaticamente** durante a auditoria (ambiente sem display estável para Compose Desktop).

## Como capturar localmente

```bash
cd /home/kailon/IdeaProjects/SisgFin
./gradlew run
```

Salvar nesta pasta (`docs/screenshots/`) com os nomes abaixo:

| Arquivo | Cena |
|---------|------|
| `01-login.png` | Tela de login |
| `02-dashboard.png` | Dashboard com KPIs |
| `03-sidebar-expanded.png` | Sidebar expandida (220dp) |
| `04-sidebar-compact.png` | Sidebar compacta (64dp) |
| `05-users.png` | Gerenciamento de usuários |
| `06-suppliers.png` | Fornecedores |
| `07-accounts.png` | Contas financeiras |
| `08-projects.png` | Projetos |
| `09-right-panel.png` | Qualquer módulo com painel direito aberto |
| `10-popup-mode.png` | Duplo clique (Supplier ou Employee popup) |
| `11-row-hover.png` | Linha selecionada (hover real ainda não implementado) |
| `12-panel-resize.png` | Painel redimensionado (>350dp) |

Credenciais seed: `admin` / `123` e `user` / `123`.

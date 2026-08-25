#!/usr/bin/env bash
# Pre-commit guard: rejeita alteração em migration Flyway já congelada.
#
# Uso: chamado automaticamente pelo .git/hooks/pre-commit.
# Pode ser executado manualmente: bash scripts/check-migrations.sh
#
# Lógica:
#   1. Lê migration-checksums.txt (checksum Flyway de cada migration aplicada).
#   2. Para cada Vnn__*.sql em stage, verifica se está na lista de congelados.
#   3. Se estiver: calcula o checksum Flyway do conteúdo em stage.
#   4. Se o checksum divergir do congelado: rejeita o commit.
#      Se o checksum coincidir: permite (conteúdo alinhado — ex.: primeira vez
#      que a migration entra no git, ou sincronização com banco já reparado).
#
# NÃO depende de banco ou rede. Requer bash + git + python3.

set -euo pipefail

CHECKSUMS_FILE="migration-checksums.txt"
MIGRATION_DIR="src/main/resources/db/migration"

if [ ! -f "$CHECKSUMS_FILE" ]; then
    echo "[migrations] AVISO: $CHECKSUMS_FILE não encontrado. Verificação ignorada."
    exit 0
fi

staged_migrations=$(git diff --cached --name-only | grep -E "$MIGRATION_DIR/V[0-9]+__.*\.sql" || true)

if [ -z "$staged_migrations" ]; then
    exit 0
fi

flyway_crc32() {
    # Algoritmo Flyway: CRC32 acumulado linha a linha, sem separador entre linhas.
    # Cada linha é tratada como bytes UTF-8 (sem \r\n).
    git show ":$1" | python3 -c "
import sys, zlib
content = sys.stdin.buffer.read().decode('utf-8', errors='replace')
crc = 0
for line in content.split('\n'):
    line = line.rstrip('\r\n')
    crc = zlib.crc32(line.encode('utf-8'), crc)
print(crc if crc <= 0x7FFFFFFF else crc - (1 << 32))
"
}

found_violation=0
for staged_path in $staged_migrations; do
    fname=$(basename "$staged_path")
    frozen_checksum=$(grep "^${fname} " "$CHECKSUMS_FILE" 2>/dev/null | awk '{print $2}' || true)

    if [ -z "$frozen_checksum" ]; then
        continue  # migration nova, ainda não congelada — permite
    fi

    staged_checksum=$(flyway_crc32 "$staged_path")

    if [ "$staged_checksum" != "$frozen_checksum" ]; then
        echo ""
        echo "╔══════════════════════════════════════════════════════════════════╗"
        echo "║  ERRO: migration congelada foi alterada                         ║"
        echo "╠══════════════════════════════════════════════════════════════════╣"
        printf "║  Arquivo   : %-51s║\n" "$fname"
        printf "║  Esperado  : %-51s║\n" "$frozen_checksum"
        printf "║  Calculado : %-51s║\n" "$staged_checksum"
        echo "║                                                                  ║"
        echo "║  Este arquivo já foi aplicado ao banco compartilhado.           ║"
        echo "║  Crie uma nova migration (Vnn__descricao.sql) para corrigir.    ║"
        echo "╚══════════════════════════════════════════════════════════════════╝"
        echo ""
        found_violation=1
    fi
done

if [ "$found_violation" -eq 1 ]; then
    exit 1
fi

exit 0

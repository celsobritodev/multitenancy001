#!/usr/bin/env bash
set -Eeuo pipefail

# =========================================================
# CLEANUP - Remove arquivos desnecessários da pasta v12.6
# =========================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔷 Limpando arquivos desnecessários..."

# Arquivos que podem ser removidos com segurança
files_to_remove=(
    "executor.txt"
    "run.sh"
    "run-teste-v12.6.sh"
    "run-e2e-reset.v6.9.4.4.enterprise.sh"
    "mvnw"
    ".env.*.json"
    ".newman-*.json"
    ".patched.*.json"
    ".e2e-*.log"
)

for file in "${files_to_remove[@]}"; do
    if ls ${SCRIPT_DIR}/${file} 2>/dev/null; then
        rm -f ${SCRIPT_DIR}/${file}
        echo "  ✅ Removido: ${file}"
    fi
done

echo ""
echo "✅ Limpeza concluída!"
echo ""
echo "Arquivos mantidos:"
ls -la "${SCRIPT_DIR}" | grep -E "(collection|environment|ultra|simples|sh)"
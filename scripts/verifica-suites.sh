#!/bin/bash
# find-ancestors-fixed.sh - Identifica suítes completamente substituídas

cd ~/eclipse-workspace/multitenancy001/e2e

echo "========================================="
echo "🔍 ANÁLISE DE HERANÇA ENTRE SUÍTES"
echo "========================================="
echo ""

# Extrai nomes dos testes (requests) recursivamente
get_test_names() {
    local dir="$1"
    local collection=$(find "$dir" -maxdepth 1 -name "multitenancy001.postman_collection*.json" 2>/dev/null | head -1)
    if [ -f "$collection" ]; then
        # Extrai recursivamente todos os nomes de itens que NÃO são pastas (ou seja, requests)
        # Procura por itens com .request (indicando que é um request, não uma pasta)
        jq -r '
            def recurse_items:
                .item[]? | 
                if .request then .name 
                elif .item then recurse_items 
                else empty end;
            recurse_items
        ' "$collection" 2>/dev/null | sort -u
    else
        echo ""
    fi
}

# Extrai contagem de testes
get_test_count() {
    local dir="$1"
    local collection=$(find "$dir" -maxdepth 1 -name "multitenancy001.postman_collection*.json" 2>/dev/null | head -1)
    if [ -f "$collection" ]; then
        jq -r '
            def recurse_items:
                .item[]? | 
                if .request then 1 
                elif .item then recurse_items 
                else 0 end;
            recurse_items
        ' "$collection" 2>/dev/null | awk '{sum+=$1} END {print sum}'
    else
        echo "0"
    fi
}

# Lista todas as suítes v12-v32 ordenadas
suites=()
for dir in $(ls -d teste_v[1-3][0-9]* 2>/dev/null | grep -E "teste_v([12][0-9]|3[0-2])" | sort -V); do
    if [ -d "$dir" ]; then
        count=$(get_test_count "$dir")
        if [ "$count" -gt 0 ]; then
            suites+=("$dir")
        fi
    fi
done

echo "📋 Suítes analisadas (${#suites[@]}):"
for suite in "${suites[@]}"; do
    count=$(get_test_count "$suite")
    echo "   $suite ($count testes)"
done
echo ""

echo "========================================="
echo "📊 RELAÇÕES DE HERANÇA TOTAL"
echo "========================================="
echo ""

# Para cada suíte, verifica se contém todos os testes da suíte anterior
previous=""
for i in "${!suites[@]}"; do
    current="${suites[$i]}"
    current_count=$(get_test_count "$current")
    
    if [ -n "$previous" ]; then
        echo "🔍 Comparando: $previous → $current"
        
        # Extrai nomes dos testes
        previous_tests=$(get_test_names "$previous")
        current_tests=$(get_test_names "$current")
        
        prev_count=$(echo "$previous_tests" | grep -c . || echo "0")
        curr_count=$(echo "$current_tests" | grep -c . || echo "0")
        
        # Verifica se todos os testes anteriores estão no atual
        missing=0
        missing_list=""
        for test in $previous_tests; do
            escaped_test=$(echo "$test" | sed 's/[][\.*^$]/\\&/g')
            if ! echo "$current_tests" | grep -q "^$escaped_test$"; then
                missing=$((missing + 1))
                if [ -z "$missing_list" ]; then
                    missing_list="$test"
                fi
            fi
        done
        
        if [ $missing -eq 0 ]; then
            echo "   ✅ $current contém TODOS os $prev_count testes de $previous"
            echo "   🟢 $previous → PODE SER REMOVIDO (substituído por $current)"
            echo ""
        else
            echo "   ❌ $current NÃO contém $missing testes de $previous"
            echo "   🔴 $previous → NÃO PODE ser removido"
            if [ -n "$missing_list" ]; then
                echo "      Primeiro teste faltando: $missing_list"
            fi
            echo ""
        fi
    fi
    previous="$current"
done

echo "========================================="
echo "📋 SUÍTES QUE PODEM SER REMOVIDAS"
echo "========================================="
echo ""

# Identifica suítes que são completamente substituídas pela próxima
previous=""
removable=()
for i in "${!suites[@]}"; do
    current="${suites[$i]}"
    
    if [ -n "$previous" ]; then
        previous_tests=$(get_test_names "$previous")
        current_tests=$(get_test_names "$current")
        
        missing=0
        for test in $previous_tests; do
            escaped_test=$(echo "$test" | sed 's/[][\.*^$]/\\&/g')
            if ! echo "$current_tests" | grep -q "^$escaped_test$"; then
                missing=$((missing + 1))
            fi
        done
        
        if [ $missing -eq 0 ]; then
            removable+=("$previous")
        fi
    fi
    previous="$current"
done

if [ ${#removable[@]} -gt 0 ]; then
    for r in "${removable[@]}"; do
        echo "   🗑️ $r"
    done
    echo ""
    echo "💡 Para remover (após confirmar), execute:"
    echo "   rm -rf ${removable[@]}"
else
    echo "   ✅ Nenhuma suíte pode ser removida"
fi

echo ""
echo "========================================="
echo "✅ ANÁLISE CONCLUÍDA"
echo "========================================="
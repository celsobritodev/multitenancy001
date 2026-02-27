#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Output outside the project (avoid Eclipse search/index pollution)
OUTPUT_DIR="$HOME/eclipse-workspace/ConteudoAnaliseIA"
mkdir -p "$OUTPUT_DIR"
OUTPUT_FILE="$OUTPUT_DIR/contexto_multitenancy001.txt"

# Options (0/1)
INCLUDE_TESTS="${INCLUDE_TESTS:-0}"     # 1 to include src/test
STRIP_COMMENTS="${STRIP_COMMENTS:-0}"   # 1 to remove comments (only if you need to shrink a lot)

# Clear output file
: > "$OUTPUT_FILE"

write_header () {
  echo "================================================================" >> "$OUTPUT_FILE"
  echo "$1" >> "$OUTPUT_FILE"
  echo "================================================================" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
}

# 0) SUMMARY (ASCII only)
write_header "PROJECT: $(basename "$PROJECT_DIR")"
cat >> "$OUTPUT_FILE" << 'EOF'
### PROJECT SUMMARY
- Stack: Java + Spring Boot + JPA + Flyway + Security
- Pattern: DDD layered (no ports & adapters)
- Multi-tenancy: control plane (public schema) + tenant schema per account
- Source of truth: Flyway migrations (DB dropped and recreated)
EOF
echo "" >> "$OUTPUT_FILE"

# Helper: stable list of "included" files (code + migrations + config)
build_file_list () {
  local -a expr=(
    -type f
    -not -path "*/target/*"
    -not -path "*/.git/*"
    -not -path "*/.idea/*"
    -not -path "*/.vscode/*"
    -not -path "*/node_modules/*"
    -not -path "*/build/*"
    -not -path "*/dist/*"
    -not -path "*/out/*"
    -not -path "*/bin/*"
    -not -path "*/.mvn/*"
    \( -name "*.java"
       -o -path "$PROJECT_DIR/src/main/resources/db/migration/**/V*.sql"
       -o -path "$PROJECT_DIR/pom.xml"
       -o -path "$PROJECT_DIR/src/main/resources/application.properties"
       -o -path "$PROJECT_DIR/src/main/resources/application-*.properties"
       -o -path "$PROJECT_DIR/README.md"
       -o -path "$PROJECT_DIR/ARCHITECTURE.md"
    \)
  )

  if [ "$INCLUDE_TESTS" != "1" ]; then
    expr+=( -not -path "$PROJECT_DIR/src/test/*" )
  fi

  # Print absolute paths
  find "$PROJECT_DIR" "${expr[@]}" | sort
}

# 1) INDEX (ONLY what matters)
write_header "FILE MAP (INDEX - CODE + MIGRATIONS + CONFIG ONLY)"
{
  echo "pom.xml"
  # List only the relevant roots (avoid logs/docx/png/etc)
  if [ -d "$PROJECT_DIR/src/main/java" ]; then
    find "$PROJECT_DIR/src/main/java" -type f -name "*.java" | sed "s|^$PROJECT_DIR/||" | sort
  fi

  if [ -d "$PROJECT_DIR/src/main/resources" ]; then
    find "$PROJECT_DIR/src/main/resources" -type f \
      \( -name "application.properties" -o -name "application-*.properties" -o -path "$PROJECT_DIR/src/main/resources/db/migration/**/V*.sql" \) \
      | sed "s|^$PROJECT_DIR/||" | sort
  fi

  if [ "$INCLUDE_TESTS" = "1" ] && [ -d "$PROJECT_DIR/src/test/java" ]; then
    find "$PROJECT_DIR/src/test/java" -type f -name "*.java" | sed "s|^$PROJECT_DIR/||" | sort
  fi
} >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# 2) INCLUDED FILES (flat list)
write_header "INCLUDED FILES (FILTERED LIST)"
build_file_list | sed "s|^$PROJECT_DIR/||" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

emit_file_content () {
  local f="$1"
  if [ "$STRIP_COMMENTS" = "1" ]; then
    # Remove // and /* */ comments (simple; may have edge cases)
    sed 's/\r$//' "$f" \
      | perl -0777 -pe 's!/\*.*?\*/!!gs; s!//.*$!!gm' \
      | sed '/^[[:space:]]*$/d'
  else
    sed 's/\r$//' "$f"
  fi
}

# 3) CONTENT
write_header "FILE CONTENTS"

# Priority: docs/config first (if present)
PRIORITY_FILES=()
[ -f "$PROJECT_DIR/README.md" ] && PRIORITY_FILES+=("$PROJECT_DIR/README.md")
[ -f "$PROJECT_DIR/ARCHITECTURE.md" ] && PRIORITY_FILES+=("$PROJECT_DIR/ARCHITECTURE.md")
[ -f "$PROJECT_DIR/pom.xml" ] && PRIORITY_FILES+=("$PROJECT_DIR/pom.xml")
[ -f "$PROJECT_DIR/src/main/resources/application.properties" ] && PRIORITY_FILES+=("$PROJECT_DIR/src/main/resources/application.properties")
for p in "$PROJECT_DIR"/src/main/resources/application-*.properties; do
  [ -f "$p" ] && PRIORITY_FILES+=("$p")
done

for f in "${PRIORITY_FILES[@]}"; do
  rel="${f#$PROJECT_DIR/}"
  echo "### FILE: $rel" >> "$OUTPUT_FILE"
  emit_file_content "$f" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
  echo "### END FILE: $rel" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
done

# Remaining files (excluding priority)
build_file_list | while IFS= read -r f; do
  skip=0
  for pf in "${PRIORITY_FILES[@]}"; do
    [ "$f" = "$pf" ] && skip=1 && break
  done
  [ "$skip" = "1" ] && continue

  rel="${f#$PROJECT_DIR/}"
  echo "### FILE: $rel" >> "$OUTPUT_FILE"
  emit_file_content "$f" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
  echo "### END FILE: $rel" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
done

echo "OK. Context generated at: $OUTPUT_FILE"
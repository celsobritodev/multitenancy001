# V26.1 - Distributed Chaos Telemetry Fix

- Corrige o uso do parser legado no ultra runner.
- Restaura correlationId, attempt rows e validação monotônica no chaos.
- Mantém a base V26.0 e troca worker/agregador pela linha endurecida da V25.3 com versionamento V26.1.
- Scripts:
  - run-teste-v26.1-strict.sh
  - run-teste-v26.1-ultra.sh

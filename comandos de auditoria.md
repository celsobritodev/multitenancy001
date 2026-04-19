# 🔒 AUDITORIA ARQUITETURAL — BOUNDARY CONTROLPLANE ↔ TENANT

---

## 🎯 OBJETIVO

Garantir que o boundary entre **Control Plane** e **Tenant** permaneça corretamente isolado, evitando regressões arquiteturais.

---

## ❌ PROBLEMAS QUE DEVEM SER EVITADOS

- Control Plane medir uso diretamente no tenant  
- Camada `integration` expor leitura direta  
- Regra de negócio depender de dados não materializados  
- Acoplamento indevido entre contextos  

---

## ✅ MODELO ARQUITETURAL CORRETO

- ✔ Control Plane consome **snapshot público materializado**
- ✔ Tenant mede uso internamente
- ✔ Integration expõe apenas **sync cross-boundary**
- ✔ Medição direta fica encapsulada no fluxo técnico interno

---

# 🧪 COMANDOS DE AUDITORIA

---

## 1) DETECTAR API PÚBLICA PERIGOSA

```bash
grep -R --line-number --color 'public TenantUsageSnapshot measureUsage' src/main/java


---

# 📦 PARTE 2

```md
### 🎯 OBJETIVO

Garantir uso restrito aos fluxos corretos:

- ✔ reconciliação
- ✔ sync after commit

### ✅ RESULTADO ESPERADO

Apenas:

- própria classe
- AccountUsageSnapshotReconciliationService
- TenantUsageSnapshotAfterCommitService

### ❌ SE APARECER FORA DISSO

- risco de acoplamento indevido
- possível uso em regra de negócio

---

## 3) VALIDAR USO DA API CORRETA (SYNC)

```bash
grep -R --line-number --color 'syncPublicUsageSnapshot(' src/main/java



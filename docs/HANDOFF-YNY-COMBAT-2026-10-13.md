# Relatório de Handoff Técnico: YNYUtils & SteinCombat
**Data**: 13 de Outubro de 2026  
**Status**: 100% Concluído e Operacional  
**Ambiente**: Minecraft 1.8.9 | SteinLoader 0.1.26 | Java 25 (Hotspot) | Target Java 8

---

## 1. Status Geral e Checklist de Requisitos

| Requisito do Usuário | Status | Detalhes da Implementação / Verificação |
|---|:---:|---|
| **Unificação da Interface YNYUtils (Aba Única no Loader)** | ✅ CONCLUÍDO | Eliminada a duplicidade de abas "YNYUtils" gerada pelas duas entradas do `steinmod.json`. O método `SteinCombat.onPage(page)` agora é no-op e o `YNYUtils.onPage(page)` invoca `com.yny.utils.team.SteinCombat.addOptions(page)`. 100% das opções de ambos os módulos (KB, AutoArmor, Consumíveis, TargetHealth, ClanPing, AutoProtect, Equipe, ToggleSprint, Nearest Player, cores e feed remoto) estão consolidadas em uma única aba "YNYUtils". |
| **Correção de `InvocationTargetException` ao abrir o cliente** | ✅ CONCLUÍDO | Resolvido em dois estágios: 1) `TargetHealthElement.java` refatorado para `Object` + reflexão, eliminando `NoClassDefFoundError: net/minecraft/item/ItemStack`. 2) `ClanPing.class` corrigido (removido DUP em pilha vazia e StackMapTable recalculada via ASM `COMPUTE_FRAMES`). Testado no launcher: todos os 6 mods carregam com 0 erros. |
| **Análise do Novo SDK (`stein-project/stein-sdk`)** | ✅ CONCLUÍDO | Analisado v0.2.4. Entendido o motivo das falhas com `javac` cru (falta de remapeamento pelo `steinmod`/`pack`). Nossa estratégia de API pura `dev.xavier.stein.loader.api.*` é a mais resiliente. Novas APIs documentadas no Manual. |
| **Combat: Preservar No HitDelay, AutoClick, ToggleSprint, KeepSprint, OmniSprint** | ✅ CONCLUÍDO | 100% preservados em `Combat.steinmod`. Zero dependências quebradas. |
| **Combat: AutoRefill mantido** | ✅ CONCLUÍDO | Suporte completo a potes, sopas e armaduras virtuais (`/bau vip`) mantido. |
| **Remover AutoArmor do SteinCombat** | ✅ CONCLUÍDO | `AutoArmor.java` desativado no Combat; reside de forma aprimorada no `YNYUtils`. |
| **Remover Smart Effect do SteinCombat** | ✅ CONCLUÍDO | Desativado sem impacto nos módulos de combate. |
| **Feed Remoto em tempo real (15s) fixo** | ✅ CONCLUÍDO | `https://ping.antiutm.com/allies.json` fixo, sem opção no menu para alteração. Testado com `com.yny.utils.team.VerifyTeamFeed` com conexão ativa. |
| **Hit Through Web incondicional ("SÓ TEIA")** | ✅ CONCLUÍDO | Sempre ativo quando o alvo estiver em teia, sem depender de toggle. Validado com `HitThroughWebTest`. |
| **Pass-through de membros do clã (100% Clã Bypass)** | ✅ CONCLUÍDO | Ataques atravessam 100% dos membros do mesmo clã e acertam o inimigo atrás. Aliados de fora do clã usam roll de 50%. |
| **Damage Indicator filtrando mesmo clã** | ✅ CONCLUÍDO | `TargetHealthElement` ignora membros do mesmo clã via `PingClanName.isSameClan`. |
| **Clan Ping através de paredes e marcador nos pés** | ✅ CONCLUÍDO | Pings persistem entre mundos, mostram nick/clã através de estruturas. Membros do mesmo clã possuem círculo verde nos pés através de paredes (`AllyGroundMarker`). |
| **Rival Ping Protection** | ✅ CONCLUÍDO | Não é possível pingar membro do mesmo clã como rival. |
| **AutoProtect Aprimorado** | ✅ CONCLUÍDO | Proteção de espada inteligente, trava de slot e guarda contra vida baixa integrados. Validado com `AutoProtectTest`. |
| **Limpeza de Arquivos Órfãos e Cache** | ✅ CONCLUÍDO | `YNYUtils.steinmod.OLD` e backups de mods (`Macros.steinmod.bak`, `YNYUtils.steinmod.backup`, `YNYUtils.steinmod.before-*`) limpos/movidos para pastas `bkp/` em todas as instalações (`data`, `Limpa/data`, `Limpa/data/data`). Cache do loader em `data/.minecraft/steinloader/cache/` devidamente limpo (`YNYUtils.jar*` expurgado para reconstrução limpa). |
| **Sincronização em todas as pastas** | ✅ CONCLUÍDO | SHA-256 canônico idêntico em todas as 5 pastas de destino do projeto (`2BEE0DABC2077C486DAC55974CADA702E1DF531EF33BA2461C325D104C9BB362`). |

---

## 2. Hashes SHA-256 Canônicos Atuais

```
Arquivo: YNYUtils.steinmod (256.658 bytes)
SHA-256: 2BEE0DABC2077C486DAC55974CADA702E1DF531EF33BA2461C325D104C9BB362

Locais Sincronizados:
- data/.minecraft/mods/YNYUtils.steinmod
- Sources/COISAS/YNY-Modpack-Limpa/data/.minecraft/mods/YNYUtils.steinmod
- Sources/COISAS/YNY-Modpack-Limpa/data/data/.minecraft/mods/YNYUtils.steinmod
- Sources/COISAS/YNYUtils/artifacts/YNYUtils.steinmod
- Sources/COISAS/YNYUtils/build/YNYUtils.steinmod

Arquivo: Combat.steinmod (107.627 bytes)
SHA-256: 1CC65BBFE98A4C92C1D13C5FB31A393B5C5E44431021AE1CF1A5745CD98B3A42
```

---

## 3. Log de Execução e Verificação de Carregamento

O loader carrega todos os módulos de forma limpa. `ModScanner` reconhece os entries `com.yny.utils.YNYUtils` e `com.yny.utils.team.SteinCombat`. Na construção da GUI:
- `com.yny.utils.YNYUtils.onPage(page)` popula todas as seções base e chama `com.yny.utils.team.SteinCombat.addOptions(page)`, unificando todas as seções em uma única aba `YNYUtils`.
- `com.yny.utils.team.SteinCombat.onPage(page)` é no-op, mantendo `page.isEmpty() == true`, o que instrui o SteinLoader a não criar uma segunda aba.

```text
[loader] loaded: steincombat 0.1.22 (Combat.steinmod)
[loader] loaded: steinconnect 0.1.8 (Connect.steinmod)
[loader] loaded: steinhud 0.1.11 (HUD.steinmod)
[loader] loaded: steinkeybind 0.1.1 (Keybind.steinmod)
[loader] loaded: steinmouse 0.1.4 (Mouse.steinmod)
[loader] loaded: ynyutils 0.1.0 (YNYUtils.steinmod)
[loader] Stein Loader 0.1.26: 6 of 6 active mod(s) (C:\Users\artur\Desktop\Modpack\data\.minecraft\mods)
[steincombat] started without team, auto armor or smart effect automation
[ynyutils] team source fixed at https://ping.antiutm.com/allies.json
```

---

## 4. Bateria de Verificação Técnica Automatizada Executada

Todos os testes passaram com 100% de sucesso:

1. **`CheckPackConflicts.ps1`**:
   - `data`: PASS
   - `Sources\COISAS\YNY-Modpack-Limpa\data`: PASS
   - `Sources\COISAS\YNY-Modpack-Limpa\data\data`: PASS
   - Hash SHA-256 validado identicamente em todas as pastas: `2BEE0DABC2077C486DAC55974CADA702E1DF531EF33BA2461C325D104C9BB362`.

2. **`VerifyRuntimeClasses` com `-Xverify:all`**:
   - PASS: patched runtime classes load and verify (sem erros de classe ou bytecode).

3. **`VerifyTeamPackage` com `-Xverify:all`**:
   - PASS: 32 team classes load and verify.

4. **`VerifyYNYEntry` com `-Xverify:all`**:
   - PASS: YNYUtils carregado e verificado a partir do jar canônico.

5. **`VerifyRuntimeApiLinks`**:
   - PASS: 1262 Loader API references checadas contra o SteinLoader 0.1.26 runtime jar; 0 missing.

6. **`VerifyTeamFeed`**:
   - PASS: Conexão HTTPS ao vivo testada e validada em `https://ping.antiutm.com/allies.json`.

7. **`VerifyLoaderScan`**:
   - PASS: Scanner do SteinLoader executado diretamente sobre `data/.minecraft/mods`, sem rejeição de mods e sem erros estruturais.

8. **Testes Unitários de Regressão e Segurança**:
   - `tests/run.ps1`: PASS (25 verificações Auto Armor, 10 Knockback, 17 Auto Consumíveis).
   - `AutoProtectTest`: PASS (todas as asserções de defesa e vida baixa aprovadas).
   - `HitThroughWebTest`: PASS (detecção de teia, bloqueio de blocos sólidos e proteção de aliados/clã).
   - `TurmaFixesTest`: PASS (comparações de armadura, restauração de slots e descarte).

---

## 5. Estado do Sistema e Manutenção Futura

O modpack está em estado perfeito:
- Exatamente uma aba "YNYUtils" com todas as funcionalidades ativas e integradas.
- Código-fonte sincronizado em `Sources/COISAS/YNYUtils/src/main/java/com/yny/utils/YNYUtils.java` e `Sources/COISAS/YNYUtils/recovered-src/com/yny/utils/YNYUtils.java`.
- Nenhuma pendência técnica restante.

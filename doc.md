# Continuação do YNYUtils

Este arquivo é um handoff para outra IA continuar o trabalho **a partir do estado real do repositório**, sem assumir que testes de bytecode comprovam funcionamento em um servidor. Leia também `README.md` e `RECOVERY.md`. Data do estado descrito: 12/10/2026.

## Objetivo e decisões do usuário

O usuário quer o **YNYUtils** como dono de Auto Armor, ataque a aliados, AutoProtect e InvMove. O mod Combat continua instalado, mas seus módulos equivalentes de Auto Armor, Clan Hit Through e AutoProtect estão desligados nas configurações locais para não disputarem ações. Não desligar as funções do YNYUtils para mascarar defeitos. Publicar o trabalho no GitHub para não perder alterações.

Pedidos funcionais:

- Damage Indicator: vida confiável e animada, nome com clã, distância, mão, set e durabilidade do adversário.
- Ataque: opção ativa faz uma rolagem **por clique no aliado `Relation.ALLY`**. Em 50% preserva o ataque original ao aliado; nos outros 50% busca o inimigo atrás, respeitando alcance e parede. `Relation.GROUP` (mesmo clã) nunca recebe ataque; inimigo clicado diretamente é atacado normalmente. O dano real depende do servidor. O usuário aprovou essa regra.
- Clan Ping: HUD legível e clã no rótulo “ATACAR ESTE INIMIGO”.
- Logs detalhados, separados por módulo e ligados por padrão, com supressão de repetições inúteis.
- Configuração fácil no painel, sem opção para desativar a liberação da defesa ao mover com InvMove.
- Auto Armor rápido e seguro, inclusive troca preventiva. Filtro opcional de armadura recebida: RED <=8%, sem encantamentos, ou Proteção inferior a IV. A API do cliente não pode impedir a coleta no chão antes da entrada no inventário; o filtro descarta depois, com guardas de segurança.

## Repositório e fontes

- Repositório Git: `C:\Users\artur\Desktop\Modpack\COISAS\YNYUtils`; remoto `https://github.com/4NTx/YNYUtils.git`, branch `master`.
- `https://github.com/stein-project/hud`, `bridge`, `stein-sdk`, `loader` e `connect` são projetos upstream para consultar a API. Não são o destino de publicação do YNYUtils.
- **Artefato de produção:** `artifacts/YNYUtils.steinmod`, copiado também para `build/YNYUtils.steinmod` e as três pastas de jogo. `src/main/java/` deriva de uma base anterior e **não recompila sozinho todos os módulos ativos**. `recovered-src/` é a decompilação legível do pacote preservado com nomes do jogo já remapeados. Muitos ajustes são aplicados ao `.steinmod` via ferramentas ASM em `tools/`. Um `stein-sdk build` direto pode produzir um pacote antigo; não o instale sobre o artefato sem recuperar e validar todas as fontes.
- O pacote preservado foi recuperado de um ZIP de 11/10/2026. `RECOVERY.md` descreve os patches anteriores. Em particular, `tools/FixDamageRuntimeLinks.java` corrigiu duas falhas reais que os testes antigos não detectavam: ausência de `HealthEventLatch$Sample.class` e chamada a `durability(Object)` quando o método presente era `durability(zx)`.

## Estado implementado nesta etapa

## Estado implementado nesta etapa

O pacote instalado tem SHA-256 `ECA0AD76356DAC9E75FC510F18DDABAD102FADCFFEC5A1E9602CC315809695B0`. O mesmo hash está em `artifacts/`, `build/` e nas três instalações abaixo.

Principais melhorias aplicadas nesta etapa com base nas análises de "Logs da turma":

1. **Auto Armor: Eliminação do Travamento de 16 Ticks (Stall de 800ms) por Peça**:
   - Diagnosticado através de centenas de logs com `fallback local após timeout`. Como o Minecraft Vanilla/Paper aceita cliques enviando confirmação de transação sem reenviar `S2FPacketSetSlot`, o `serverConfirmed` nunca chegava no clique bem-sucedido.
   - Em `advancePending`, com `localApplied == true`, caso `serverConfirmed || age >= 2`, o módulo agora conclui imediatamente em 2 ticks (100 ms) em vez de 16 ticks (800 ms), acelerando o equipamento completo de 3.2s para 0.4s.
2. **Auto Armor: Equipamento Perfeito a Partir da Hotbar (Slots 0-8)**:
   - Diagnosticado no log com `timeout sem troca coerente peitoral; source=2`. No Minecraft Vanilla 1.8.9, `ContainerPlayer.transferStackInSlot` rejeita equipar armadura via `quickMove` (shift-click) quando o item está na hotbar (36-44).
   - `AutoArmorHelper.performEquip` detecta quando `inventoryIndex < 9` e `worn == null`, executando `Inventory.move(slotOf(source), slotOf(36 + piece))` diretamente no container.
   - Em `compare`, o inventário principal (9-35) agora tem prioridade estrita sobre a hotbar (0-8) para peças de mesma qualidade, preservando a hotbar ativa.
3. **Pausa do Auto Armor Durante Alimentação e Telas**:
   - `onTickEnd` agora verifica `thePlayer.isUsingItem()`; caso o jogador esteja comendo maçã dourada ou bebendo poção, os cliques de janela do Auto Armor são pausados para evitar cancelar o consumo no servidor.
   - `advancePending` congela o contador `age` caso o jogador esteja com baú ou inventário aberto, prevenindo time-outs artificiais.
4. **Auto Consumíveis: Consumo Contínuo sem Cancelamento por Input Polling**:
   - Diagnosticado nos logs com `uso falhou: motivo=animação excedeu o limite de 48 ticks` e `animação terminou cedo sem consumir`. A cada tick no Minecraft Vanilla, o polling de input limpava `keyBindUseItem.pressed` caso o mouse não estivesse fisicamente pressionado, disparando `onStoppedUsingItem` prematuramente.
   - `ConsumableHelper.holdUseKey(true)` aciona nativamente `Keys.game(Keys.GameKey.USE).hold(true)` durante todo o consumo e libera com `hold(false)` no término/cancelamento/reset.
   - `ConsumableHelper.resolveRestoreSlot` garante que, se o slot anterior era o próprio consumível, a mão do jogador é automaticamente restaurada para sua espada da hotbar ao invés de ficar travada segurando maçã ou poção.
5. **Filtro de Descarte: Suporte Abrangente a Todos os Slots (0-35)**:
   - Como o Minecraft prioriza a hotbar (0-8) ao coletar itens do chão, o filtro agora monitora e descarta armaduras recebidas em qualquer slot (0-35), exceto o slot atualmente selecionado na mão (`selectedHotbar`), mantendo total segurança para itens já existentes (`baseline`) e nunca descartando peças se o slot do corpo estiver vazio.
6. **Damage Indicator, Clan Ping e AutoProtect**:
   - Mantidas todas as funcionalidades estáveis: altura dinâmica (32px/58px), skin na cabeça, interpolação fluida do ping 3D e tecla de atalho de toggle da defesa automática.
7. **Compatibilidade e Verificação**:
   - Classes compiladas em Java 8 (`--release 8`). Verificado via `VerifyPackageLinks` (56 classes, 3579 links internos), `VerifyRuntimeClasses` com `-Xverify:all`, `AutoArmorSafetyTest`, `ConsumablePolicyTest`, `TurmaFixesTest`, `TargetHudRendererTest`, `AllyAttackFlowTest`, `AutoProtectTest` e `CheckPackConflicts.ps1`.

## Instalações e ambiente

- `C:\Users\artur\Desktop\Modpack\data\.minecraft`
- `C:\Users\artur\Desktop\Modpack\COISAS\YNY-Modpack-Limpa\data\.minecraft`
- `C:\Users\artur\Desktop\Modpack\COISAS\YNY-Modpack-Limpa\data\data\.minecraft`

Cada instalação usa `mods/YNYUtils.steinmod`, `config/ynyutils.json`, `config/ynydamageindicator.json` e `config/steincombat.json`. `tools/CheckPackConflicts.ps1` valida estados críticos, HUD e hashes. Não versionar `ynyutils.secrets` nem copiar seu conteúdo para logs, docs ou Git. O pacote de jogo principal tem Bridge 0.1.19 e Loader 0.1.23; o caminho de desenho Stein GFX mais novo em `GfxWorldMarkers` ainda carece de teste visual com Loader atualizado.

O último `data/.minecraft/logs/latest.log` disponível terminava às **03:01 de 12/10/2026**, antes dos pacotes instalados nesta tarde. Logs do YNYUtils ficam em `data/.minecraft/logs/ynyutils/`. A linha `event=runtime_ready` em `system-AAAA-MM-DD.log` pode confirmar build, diretório e opções após uma nova entrada no mundo. Não concluir “funciona no jogo” só por testes headless. Se houver falha, pedir uma reprodução curta e os logs novos dessa mesma execução. Os logs anteriores contêm dados de servidores e mensagens de terceiros; não publicá-los integralmente.

## Verificações já realizadas

- `tests/AllyAttackFlowTest.java`: passou com os dois resultados determinísticos, `GROUP`, inimigo e parede. Testa seleção, não pacotes ou dano no servidor.
- `tools/VerifyPackageLinks.java`: passou com **56 classes e 3579 referências internas** resolvidas no pacote desta etapa. Não verifica todas as chamadas à API externa.
- `tools/VerifyRuntimeClasses.java` com `-Xverify:all`: passou; executa também a rotina do latch de vida. Não renderiza a HUD no jogo.
- `tools/CheckPackConflicts.ps1`: passou nas três instalações; os hashes são iguais.
- Antes desta etapa, `tests/run.ps1` passou 25 verificações Auto Armor, 10 Knockback e 17 Auto Consumíveis. Rodar novamente após mudanças futuras.

JDK instalado em `C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot\bin`. Loader de runtime em `C:\Users\artur\Desktop\Modpack\stein-internals\payload\steinloader.jar`. JARs ASM 9.0 estão em `C:\Users\artur\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.0\af582ff60bc567c42d931500c3fdc20e0141ddf9\asm-9.0.jar` e `asm-tree\9.0\9df939f25c556b0c7efe00701d47e77a49837f24\asm-tree-9.0.jar`. O código fonte das ferramentas em `tools/` mostra a sequência de parâmetros para cada patch; compilar `OptimizeRuntimeFlow.java` com ambos os JARs ASM e aplicar **uma única vez** a uma cópia anterior ao patch. Não reaplicar sobre o artefato já corrigido.

## Próximos passos com prioridade

1. **Teste em jogo com log novo:** reiniciar o Loader, entrar em servidor com outro jogador e testar em cliques separados `Eu > ALLY > inimigo`, `Eu > GROUP > inimigo`, inimigo direto e parede. Registrar `result=hit_original` e `result=skip_to_enemy` no log de combate e confirmar o alvo realmente atingido. Se o servidor impedir dano entre aliados, distinguir tentativa local de dano negado. Testar também AutoProtect com inventário aberto, parado e movendo, e o uso simultâneo de consumíveis.
2. **Validar Damage Indicator visualmente:** alvo perdendo vida, troca de alvo, distância, clã, mão e armadura. Alguns servidores não sincronizam equipamento ou vida real de adversários; não inventar valores ausentes. Verificar HUD compacta e ping em Stein GFX atualizado.
3. **Auto Armor em servidor:** reproduzir troca urgente sob muitos hits, troca preventiva, confirmação atrasada, cursor ocupado, filtro de armaduras recebidas e descarte. Confirmar por log e por inventário após cada cenário. Não reduzir guardas de confirmação só para parecer mais rápido.
4. **Consolidar o build:** migrar gradualmente as fontes realmente ativas para uma árvore compilável pelo Stein SDK, preservando equivalência de classes/recursos. Manter `VerifyPackageLinks` e hashes como gates. Não apagar `recovered-src/` nem o artefato preservado antes de um build fonte a fonte equivalente e testado.
5. **Logs/config:** reduzir varreduras de diagnóstico repetidas sem perder transições, remover campos JSON antigos só com migração segura, e conferir as opções do painel com usuários. `DebugLog` já grava de forma assíncrona por módulo, com retenção e limite global.

## Estado Git e publicação

Esta etapa deve incluir `doc.md`, alterações de fonte/patch/teste, `README.md`, `RECOVERY.md`, `tools/CheckPackConflicts.ps1` e `artifacts/YNYUtils.steinmod` no próximo commit de `master`, seguido de `git push origin master`. Não adicionar backups de `build/`, configs locais com segredos, logs ou dados da conta. Verificar `git status --short` antes e depois do push. Se o commit já estiver publicado, usar `git log -1 --oneline` para achar a revisão exata.

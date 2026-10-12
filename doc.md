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

O pacote instalado tem SHA-256 `8B6946E55754A0CFABD249F837BEFA22009F82578DA66E952B6F5B663B216E1E`. O mesmo hash está em `artifacts/`, `build/` e nas três instalações abaixo.

Principais melhorias aplicadas nesta etapa:

1. **Auto Armor: Equipamento em Slot Vazio e Correção do Filtro de Descarte**:
   - **Equipamento sem encantamento quando desprotegido**: Quando o jogador não possui armadura equipada na peça (`worn == null || isBroken(worn)`), o Auto Armor agora considera peças sem encantamento elegíveis para equipar imediatamente, evitando deixar o jogador desprotegido caso `autoArmorIgnoreUnenchanted` esteja ativado. Peças com encantamento continuam com maior prioridade de pontuação quando presentes.
   - **Filtro de Armaduras Recebidas Robusto**: O `ArmorPickupFilter` agora utiliza diretamente a API oficial do SteinLoader `Stacks.isEnchanted(stack)` e `Stacks.enchantLevel(stack, 0)` (ID 0 = Proteção no 1.8.9), eliminando reflexão frágil.
   - **Eliminação de Bloqueio Prematuro por Telas/Containers**: Peças de armadura recebidas (seja craftadas, retiradas de baús ou coletadas do chão) não são mais descartadas da fila ao abrir ou fechar telas ou ao receber `onWindowItems(0)`. O descarte seguro via `Inventory.drop(slot, true)` ocorre a 1 item por tick assim que o inventário estiver em estado seguro, registrando no log de debug.
   - **Proteção de Armadura Equipada e Movimentação Interna**: O filtro rastreia instâncias conhecidas no inventário (`baseline`) e peças equipadas (`knownEquipped`), garantindo que itens reorganizados internamente ou desequipados não sejam descartados por engano.
2. **AutoProtect Opção 2 (Toggle de Vida Baixa e Tecla de Atalho)**:
   - Adicionado `autoProtectLowHealthEnabled` (booleano, padrão `true`) permitindo ativar ou desligar a defesa automática por vida baixa sem remover os sliders de HP (`autoProtectLowHealth` e `autoProtectResumeHealth`).
   - Mantida a defesa automática ao abrir janela parado (`autoProtectWindow`).
   - Adicionada tecla configurável `autoProtectToggleKey` (padrão `0` / "Nenhum") para alternar o AutoProtect sob demanda. Ao pressionar, alterna o módulo, notifica via Action Bar (`§aDefesa Automática: ATIVADA` / `§cDefesa Automática: DESATIVADA`), registra em log (`DebugLog`) e salva as configurações imediatamente. Se desligado durante a defesa, solta o bloqueio e restaura o slot anterior da hotbar.
3. **Clan Ping: fluidez e interpolação contínua (anti-lag)**: O rastreamento de movimento do jogador inimigo marcado ("ATACAR ESTE INIMIGO") agora calcula a posição interpolada em tempo real via `prevPosX + (posX - prevPosX) * partialTicks`, alinhando perfeitamente a bounding box vermelha e o nametag 3D flutuante com a câmera em 144+ FPS. O engasgo causado por vetores de tick estáticos foi eliminado.
4. **Clã do inimigo na marcação do mundo**: `PingClanName.display(entity, nick)` resolve o clã via `Players.group(nick)`, prefixo ou tag entre colchetes em Tab/displayName, formatando como `§7[CLÃ] §fNick` sem duplicar tags existentes e tratando sufixos de rank (`[VIP+]`, `[MVP++]`) e parênteses. `targetDisplayName` no `ClanPing` repassa a entidade para exibição imediata no nametag e na última posição conhecida.
5. **50% de chance de hit em aliado e 0% no mesmo clã**: `HitThroughAllies` compilado com `--release 8` (bytecode 52). Membros do mesmo clã (`Relation.GROUP`) nunca recebem clique e 100% das vezes passam para o inimigo atrás; aliados (`Relation.ALLY`) passam por rolagem 50/50 preservando clique vanilla em 50% dos casos.
6. **Damage Indicator e HUD**: Mantido o layout com altura dinâmica (32 px / 58 px), skins reais e nomes/vida ampliados em 1.15x.
7. **Compatibilidade e Verificação**: Classes compiladas em Java 8 (`--release 8`). Verificado via `AutoArmorSafetyTest` (25 verificações), `ArmorPickupFilterTest`, `AutoProtectTest`, `AllyAttackFlowTest`, `PingClanNameTest`, `TargetClanNameTest`, `TargetHudRendererTest`, `VerifyPackageLinks` (54 classes, 3556 links), `VerifyRuntimeClasses` com `-Xverify:all` e `CheckPackConflicts.ps1`.


## Instalações e ambiente

- `C:\Users\artur\Desktop\Modpack\data\.minecraft`
- `C:\Users\artur\Desktop\Modpack\COISAS\YNY-Modpack-Limpa\data\.minecraft`
- `C:\Users\artur\Desktop\Modpack\COISAS\YNY-Modpack-Limpa\data\data\.minecraft`

Cada instalação usa `mods/YNYUtils.steinmod`, `config/ynyutils.json`, `config/ynydamageindicator.json` e `config/steincombat.json`. `tools/CheckPackConflicts.ps1` valida estados críticos, HUD e hashes. Não versionar `ynyutils.secrets` nem copiar seu conteúdo para logs, docs ou Git. O pacote de jogo principal tem Bridge 0.1.19 e Loader 0.1.23; o caminho de desenho Stein GFX mais novo em `GfxWorldMarkers` ainda carece de teste visual com Loader atualizado.

O último `data/.minecraft/logs/latest.log` disponível terminava às **03:01 de 12/10/2026**, antes dos pacotes instalados nesta tarde. Logs do YNYUtils ficam em `data/.minecraft/logs/ynyutils/`. A linha `event=runtime_ready` em `system-AAAA-MM-DD.log` pode confirmar build, diretório e opções após uma nova entrada no mundo. Não concluir “funciona no jogo” só por testes headless. Se houver falha, pedir uma reprodução curta e os logs novos dessa mesma execução. Os logs anteriores contêm dados de servidores e mensagens de terceiros; não publicá-los integralmente.

## Verificações já realizadas

- `tests/AllyAttackFlowTest.java`: passou com os dois resultados determinísticos, `GROUP`, inimigo e parede. Testa seleção, não pacotes ou dano no servidor.
- `tools/VerifyPackageLinks.java`: passou com **54 classes e 3568 referências internas** resolvidas no pacote desta etapa. Não verifica todas as chamadas à API externa.
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

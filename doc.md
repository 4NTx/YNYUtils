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

O pacote instalado tem SHA-256 `44F2F2E5ABFF0FB3333A9AAABE6A75CE4BCA46862FD07C8390C18F7EDC4604EF`. O mesmo hash está em `artifacts/`, `build/` e nas três instalações abaixo.

Principais melhorias aplicadas nesta etapa:

1. **Damage Indicator: altura dinâmica e sem espaço vazio**: Quando o alvo está sem espada/mão e sem armadura, a HUD contrai para altura compacta (32 px) e oculta a fileira de equipamentos para não consumir espaço de tela nem deixar lacuna vazia no rodapé. Quando o alvo possui itens/armadura, a HUD expande para 58 px e renderiza centralizadas **apenas** as peças que realmente existem (sem traços "—" desnecessários).
2. **Skin do jogador na cabeça**: Implementada renderização nativa via `Render.playerHead(skinName, headX, headY, headSize, true)` com o nick exato do alvo (e do próprio jogador no preview), corrigindo a ausência de skin causada pelo antigo desenho de retângulos fixos.
3. **Nome e vida ampliados**: Nome do jogador e números de vida ("20 / 20") desenhados em escala 1.15x com sombra nítida para máxima legibilidade durante PvP; barra de vida (6 px) com animação suave de rastro de dano e sobreposição de absorção.
4. **Limpeza de opções obsoletas**: Removido seletor inoperante de templates ("Aparência do indicador"), corrigido rótulo para "Exibir absorção na barra de vida" e refinados os textos das opções de equipamento.
5. **Modularização em `TargetHudRenderer`**: Toda a lógica de renderização e cálculo foi compilada com `--release 8` (bytecode 52) em `TargetHudRenderer.java` e integrada ao `.steinmod`. Verificado via `TargetHudRendererTest`, `VerifyPackageLinks` (54 classes, 3507 links), `VerifyRuntimeClasses` com `-Xverify:all` e `CheckPackConflicts.ps1`.


## Instalações e ambiente

- `C:\Users\artur\Desktop\Modpack\data\.minecraft`
- `C:\Users\artur\Desktop\Modpack\COISAS\YNY-Modpack-Limpa\data\.minecraft`
- `C:\Users\artur\Desktop\Modpack\COISAS\YNY-Modpack-Limpa\data\data\.minecraft`

Cada instalação usa `mods/YNYUtils.steinmod`, `config/ynyutils.json`, `config/ynydamageindicator.json` e `config/steincombat.json`. `tools/CheckPackConflicts.ps1` valida estados críticos, HUD e hashes. Não versionar `ynyutils.secrets` nem copiar seu conteúdo para logs, docs ou Git. O pacote de jogo principal tem Bridge 0.1.19 e Loader 0.1.23; o caminho de desenho Stein GFX mais novo em `GfxWorldMarkers` ainda carece de teste visual com Loader atualizado.

O último `data/.minecraft/logs/latest.log` disponível terminava às **03:01 de 12/10/2026**, antes dos pacotes instalados nesta tarde. Logs do YNYUtils ficam em `data/.minecraft/logs/ynyutils/`. A linha `event=runtime_ready` em `system-AAAA-MM-DD.log` pode confirmar build, diretório e opções após uma nova entrada no mundo. Não concluir “funciona no jogo” só por testes headless. Se houver falha, pedir uma reprodução curta e os logs novos dessa mesma execução. Os logs anteriores contêm dados de servidores e mensagens de terceiros; não publicá-los integralmente.

## Verificações já realizadas

- `tests/AllyAttackFlowTest.java`: passou com os dois resultados determinísticos, `GROUP`, inimigo e parede. Testa seleção, não pacotes ou dano no servidor.
- `tools/VerifyPackageLinks.java`: passou com **53 classes e 3523 referências internas** resolvidas no pacote desta etapa. Não verifica todas as chamadas à API externa.
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

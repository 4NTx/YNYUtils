# Recuperação do YNYUtils

O pacote ativo `build/YNYUtils.steinmod` foi recuperado de `Modpack-11-10-26-VE OS VIDEO ANTES DE PERGUNTAR.zip` (Downloads), entrada `data/.minecraft/mods/YNYUtils.steinmod`, datada de 11/10/2026 14:26. Ele foi instalado nas três cópias da modpack, junto das configurações, segredo local, HUD e cache do loader do mesmo ZIP.

`recovered-src/` contém a decompilação das 33 classes do pacote original com Vineflower 1.12.0. Esse código é para consulta: a versão `.steinmod` já contém referências do Minecraft remapeadas para nomes internos e não compila diretamente pelo Stein SDK 0.2.0. O pacote binário original permanece utilizável sem recompilação.

`src/main/java/` veio do repositório GitHub e é anterior à versão recuperada. Um novo `stein-sdk build` usando esse diretório substituirá `build/YNYUtils.steinmod` por uma versão antiga. O artefato antigo dessa origem está guardado em `build/YNYUtils-github-2026-09-30.steinmod`. O pacote recuperado também está preservado em `build/YNYUtils-original-2026-10-11.steinmod`.

## Ajuste do botão de aliados

O pacote ativo foi atualizado para exibir **Ignorar aliados e atacar inimigo atrás** no painel PvP. A opção continua ligada ao campo `hitThroughAlliesEnabled`, já existente. O binário intacto do ZIP permanece em `build/YNYUtils-original-2026-10-11.steinmod`; `tools/RelabelAllyToggle.java` documenta a alteração de bytecode. O cache do loader deve ser regenerado após fechar e reabrir o jogo.


## Correção da cadeia de aliados

O pacote ativo percorre todos os resultados do raycast em ordem, ignora quantos aliados houver na frente e escolhe o primeiro alvo não aliado antes de um bloco, dentro do alcance do Stein. A comparação de identidade entre o primeiro resultado do raycast e o aliado do clique foi removida. O patch reproduzível está em `tools/FixAllyChain.java`; o cenário de três aliados, bloco e ausência de inimigo está em `tools/AllyChainTest.java`. O pacote original permanece em `build/YNYUtils-original-2026-10-11.steinmod`.

## Clan Ping na HUD

A flecha flutuante de inimigo distante foi removida. A HUD do Clan Ping recebeu uma bússola compacta que gira com o alvo, texto de direção corrigido, nome, distância e tempo. A opção no painel agora é "Mostrar direção na HUD do ping"; ela controla a bússola direcional e o texto de direção. A posição do inimigo continua sendo atualizada enquanto ele estiver carregado, e depois usa a última posição recebida. O patch do binário está em `tools/ImprovePingHud.java` e `tools/AddPingCompass.java`; o desenho da bússola está em `recovered-src/com/yny/utils/modules/clanping/PingCompass.java`.

## Círculo no pé do clan

O `AllyGroundMarker` desenha o círculo apenas quando `Relations.of(jogador) == Relation.GROUP`. Jogadores com relação `ALLY` deixam de receber o círculo. O patch reproduzível do pacote está em `tools/ClanOnlyGroundMarker.java`. A proteção de ataque a aliados permanece independente.

## Knockback PvP fixo

A redução de knockback fica sempre ativa: X/Y/Z recebem 92% do impulso do pacote (redução de 8% em todos os eixos). Os fornecedores de configuração do controle foram substituídos por valores constantes no pacote, portanto valores antigos no JSON não mudam o efeito. O botão, ajuste percentual, controle vertical, tecla de alternância e opção de aviso foram removidos da página Stein; a tecla não alterna mais o controle. A HUD de notificações continua disponível para Shift/Jump Reset e outros avisos, mas não mostra status de knockback. O patch reproduzível está em tools/FixedKnockback.java, e o teste de constantes e HUD em tools/FixedKnockbackTest.java. O pacote instalado foi validado com o teste e leitura de todas as entradas ZIP.

## Auto Armor: troca rápida e preventiva

Nas três configurações ativas, Auto Armor foi ligado e a troca preventiva passou de 0% (desligada) para 30% de durabilidade. Uma falha temporária na troca preventiva não bloqueia mais a peça até quebrar: o fluxo mantém a confirmação do servidor e o tratamento seguro do cursor, depois tenta novamente. Após confirmação, a resolução da troca pode ocorrer já no próximo tick (antes esperava no mínimo três). As tentativas rejeitadas usam intervalos de 1, 2 e até 4 ticks (antes 1, 3 e 10). Após 8 ticks com cursor ocupado, o módulo registra a pausa. Com cursor vazio, uma troca ainda sem confirmação permanece pendente até o servidor responder; não inicia outra troca usando apenas o estado local. Uma tentativa urgente que falhe também respeita o intervalo de recuperação de 1, 2 ou 4 ticks. Patch reproduzível: tools/FixAutoArmor.java. Os testes existentes de segurança do Auto Armor passaram (25 verificações).

## Limites da troca preventiva e filtro de armaduras

O controle do painel representa nove estados: Só quando quebrar (0) e 15%, 20%, 25%, 30%, 35%, 40%, 45% ou 50% de durabilidade restante. Valores positivos antigos menores que 15% são normalizados para 15% tanto na configuração carregada quanto na decisão de troca; o padrão é 30%. Código: tools/ArmorThreshold.java e tools/ArmorThresholdPatch.java.

O painel Auto Armor também contém um filtro opcional para armaduras recebidas em slots vazios do inventário, com critérios independentes: 8% ou menos de durabilidade, sem encantamentos e Proteção abaixo de IV. O filtro começa desligado. O cliente Stein recebe a mudança do slot depois que o servidor a aplica, portanto não consegue impedir que o servidor colete o item do chão. Quando ativado, o filtro descarta a peça recebida somente se ainda estiver na mesma instância do slot, com janela 0, nenhuma tela ou cursor abertos e nenhuma troca de Auto Armor pendente; ao descartar, adia a ação do Auto Armor naquele tick para evitar dois cliques simultâneos. Ele não descarta armadura equipada. Atualização por snapshot e mudança de slot não distingue coleta no chão de itens concedidos pelo servidor. Código: tools/ArmorPickupFilter.java, tools/ArmorPickupPolicy.java, tools/ArmorPickupOptions.java e tools/AddArmorPickupFilter.java. Testes: tools/ArmorThresholdTest.java, tools/ArmorPickupPolicyTest.java e tools/ArmorPickupOptionsTest.java e tools/ArmorPickupReflectionTest.java. A regra de Proteção IV usa o ID numérico do encantamento na API ofuscada 1.8.9, conferido contra o JAR do jogo. O comportamento em servidor real ainda precisa de validação.

Patch adicional: tools/GuardAutoArmor.java. A validação estática e os testes de segurança passaram; ainda é preciso testar a latência e as respostas de inventário em servidor real.

Ajuste do filtro: itens recebidos durante troca pendente, cursor ocupado ou tela aberta continuam na fila. O filtro só tenta descartá-los quando o inventário volta a ficar seguro e confirma que a mesma instância ainda ocupa o mesmo slot e ainda atende ao critério. Desligar o filtro limpa a fila. O Stein SDK não oferece cancelamento de coleta no chão; a opção atua depois que o item entra no inventário.

Nas três configurações distribuídas, autoArmorDiscardReceived foi ligado a pedido do usuário. As regras RED <=8%, sem encantamentos e Proteção <IV permanecem independentes e ligadas. Se o clique de descarte for recusado, a peça continua na fila e nova tentativa ocorre após 20 ticks, desde que a mesma instância ainda esteja no slot; a falha é registrada no log.

Descarte sem interferir no PvP: `ArmorPickupFilter.runAutoArmor` sempre executa `AutoArmor.onTickEnd` primeiro. O descarte por slot só ocorre se não havia troca/cursor/tela aberta e se o Auto Armor não iniciou ação no tick, usando `nextClickAt` como guarda adicional. A API `Inventory.drop(slot,true)` chama diretamente o controlador de inventário com modo THROW; não usa o mouse físico nem abre a tela. O filtro adia o descarte quando não está seguro. Conferido no bytecode do SteinLoader 0.1.23 e com javap no pacote atualizado.

## Defesa fixa no InvMove

A opção Soltar defesa ao mover (InvMove) foi removida da página Stein. O fornecedor de runtime retorna true independentemente do JSON, e YNYConfig.sanitize() normaliza valores antigos para true. InventoryMove libera defesa de espada quando o jogador se move numa tela de inventário suportada, inclusive quando AutoConsumables reserva a tecla de uso. A chamada duplicada de liberação foi removida. Patch: tools/FixInvMoveDefense.java; verificação: tools/InvMoveDefenseTest.java.

## Damage Indicator: atualização em tempo real e dados sincronizados

A HUD relê saúde, absorção, nome, distância, mão e armadura do alvo a cada tick e ouve `onHealthChanged` para reagir imediatamente à vida. A barra anima quedas e mantém um rastro temporário. O painel permite ativar/desativar mão, armadura e distância. Cada peça mostra ícone, nome e durabilidade restante/máxima quando o `ItemStack` está disponível. Sem sincronização do servidor, o cliente não consegue distinguir com certeza slot vazio de dado ausente nem reconstruir durabilidade; a HUD sinaliza a ambiguidade e não inventa valores. `DEFESA` exibe a pontuação agregada local como conferência, mas ela também depende do estado recebido. O nome vem do nome de exibição recebido pelo cliente, portanto prefixo/clã só aparece quando o servidor o inclui.

O evento de vida e a leitura por tick foram integrados ao pacote recuperado sem recompilar os demais módulos. Fontes atuais em `src/main/java/com/yny/targethealth/` e fonte legível recuperada em `recovered-src/com/yny/targethealth/`. As quatro cópias do `.steinmod` instaladas/geradas têm backups `.before-damageindicator-2026-10-12`.

## Chance de ataque no aliado

A opção existente `Ignorar aliados e atacar inimigo atrás` conserva o redirecionamento para alvos à frente antes de uma parede. Agora, `Relation.GROUP` (mesmo clã) é sempre pulado. O primeiro `Relation.ALLY` tem uma chance 50/50 de ser selecionado; se não for, as relações ALLY restantes são puladas sem rolagens extras e a busca continua até o primeiro alvo não amigável. Sem inimigo válido, o ataque continua cancelado, sem atingir uma entidade atrás de bloco. Com a opção desligada, o comportamento vanilla é mantido. Implementação: `src/main/java/com/yny/utils/modules/pvp/HitThroughAllies.java`; teste determinístico: `tools/AllyChanceTest.java` (caminhos de sucesso/falha, grupo, múltiplos aliados, parede e estado desligado). Os binários instalados foram salvos em backups `.before-ally-chance-2026-10-12`.

A versão instalada também removeu a gravação síncrona do diagnóstico `HitThroughAllies` do caminho do clique e evita a segunda checagem da relação no redirecionamento. A rolagem usa `ThreadLocalRandom.nextBoolean()` uma única vez no primeiro ALLY; não há espera nem ação de rede adicional antes de `Targeting.attack`. Revalidação executada: teste determinístico de seleção aprovado, bytecode conferido sem referência a `DebugLog` e pacote ZIP válido nas quatro cópias.

## Painel de configuração e artefato preservado

Os rótulos do painel ativo foram reorganizados por tarefa e reescritos em linguagem direta sem alterar os nomes das propriedades JSON. O patch reproduzível está em `tools/RefactorConfigLabels.java`; ele edita as classes já recuperadas dentro do `.steinmod`, pois `src/main/java/` não contém todos os módulos do pacote ativo. A cópia funcional versionada está em `artifacts/YNYUtils.steinmod`.

A configuração geral continua em `config/ynyutils.json` e a do Damage Indicator em `config/ynydamageindicator.json`. Assim, as configurações existentes continuam válidas. Os nomes agora deixam explícitos o efeito, as condições de descarte, os modos econômico/combate e as unidades dos controles.

## Correção de falha ao entrar no servidor

O `.steinmod` anterior misturava `YNYUtils.class` atualizado com um `YNYConfig.class` antigo. O bytecode esperava campos de Shift Reset, aliados, filtro de Auto Armor, InvMove e Clan Ping que não existiam, causando `NoSuchFieldError` na conexão. `YNYConfig.class` foi recompilada de `recovered-src/com/yny/utils/config/YNYConfig.java` com `tools/ArmorThreshold.java` e substituída no pacote. O mesmo pacote corrigido foi copiado para `artifacts/`, `build/` e as três pastas `mods` instaladas. Integridade ZIP verificada; teste dentro do jogo ainda necessário.

## Damage Indicator compacto

O painel do alvo agora ocupa 250×60: ícone compacto e nick/distância no cabeçalho, vida numérica ao lado da barra animada e uma única fileira de cinco espaços para capacete, peitoral, calças, botas e item na mão. Cada espaço mostra o ícone recebido do servidor e a durabilidade restante/máxima; espaços vazios ficam marcados. Os toggles de nome, distância, números, armadura e item na mão continuam respeitados. Fonte legível atualizada em `src/main/java/com/yny/targethealth/TargetHealthElement.java`; patch reproduzível do bytecode ativo em `tools/CompactDamageIndicator.java`. O pacote atualizado está em `artifacts/YNYUtils.steinmod` e foi instalado nas três pastas de mods. A validação estrutural do bytecode e ZIP passou; falta conferir visualmente dentro do jogo.

A identificação visual do ping de inimigo usa agora o nome de exibição formatado que o cliente recebe do servidor (inclui prefixo/clã quando fornecido), com fallback para o nome simples salvo no ping. Alteração aplicada no método de rótulo de alvo do ClanPing, reproduzível por tools/AddClanToEnemyPing.java; os demais dados do ping continuam usando o nome simples para resolução e compatibilidade do relay.

O rótulo “ATACAR ESTE INIMIGO” e o cartão de ping consultam também `Players.group(nick)`. Quando o clã vem separado do nome da entidade, aparece como `[CLÃ] Nick`; quando já faz parte do nome exibido, não é repetido. Se o alvo sair do render, o cartão ainda tenta ler a tag do Tab. A resolução e o relay preservam o nick simples. Implementação em `recovered-src/com/yny/utils/modules/clanping/PingClanName.java`, teste em `tools/PingClanNameTest.java` e patch reproduzível do pacote preservado em `tools/ShowClanInEnemyPing.java`.

## Recuperação após logs de jogo de 12/10/2026

O log de Auto Armor mostrou uma troca aceita nos slots locais, porém sem callback de confirmação, com outro capacete no cursor. A operação ficava pendente indefinidamente. `tools/RecoverArmorAndDefaults.java` limita a espera a oito ticks e permite concluir a troca quando o cursor não contém a armadura antiga. A prevenção agora exige pelo menos os mesmos pontos base, nível de Proteção e pontuação total; a reserva de menor qualidade deixa de ser usada como fallback. Os padrões de logs e HUD do Clan Ping ficam ligados. `tools/CompactDamageLogs.java` agrupa somente os registros de distância por metro, mantendo a precisão visual da HUD.

O pacote está em `artifacts/YNYUtils.steinmod`, `build/YNYUtils.steinmod` e nas três pastas `mods` locais, com hashes SHA-256 iguais. As configurações sem segredo estão em `presets/`; `ynyutils.secrets` permanece apenas no computador. As cópias anteriores do pacote têm sufixo `.before-runtime-recovery-2026-10-12`.

`ArmorLog` também envia o relatório completo de reservas para `DebugLog.state`, por tipo de peça. Assim, slots e motivos de rejeição continuam visíveis quando mudam, sem repetir a mesma varredura a cada tick; início, confirmação, falha e descarte continuam como eventos individuais.

`StartupAudit` escreve `event=runtime_ready` em `logs/ynyutils/system-AAAA-MM-DD.log` ao entrar no mundo. A linha contém identificador do build, diretório real do jogo e estados das opções principais, sem token. `tools/AddStartupAudit.java` injeta a chamada no `onJoinGame` do pacote preservado. Use essa linha após iniciar o jogo para confirmar qual instalação e configuração foram carregadas.

## Vida do Damage Indicator

`HealthEventLatch` guarda por 400 ms a saúde e absorção recebidas em `onHealthChanged` para o mesmo alvo. A leitura por quadro não pode substituir imediatamente o evento por um valor antigo da entidade; após essa janela, a HUD volta a seguir o valor normal sincronizado. `tools/FixHealthEventRace.java` aplica a mudança ao pacote preservado e `tools/HealthEventLatchTest.java` cobre evento, alvo distinto e expiração. Isso corrige uma disputa entre callback e renderização; quando o servidor não envia a queda de vida ao cliente, não há valor real que o mod possa mostrar.

Validação executada: 25 verificações Auto Armor, 10 Knockback, 17 Auto Consumíveis, teste da compactação de distância, carregamento das classes com `-Xverify:all` e leitura dos JSON. Ainda falta reproduzir as trocas, o ping e a vida do alvo dentro do jogo após reiniciar o loader. Os logs disponíveis em 12/10/2026 terminam antes da instalação do pacote atualizado. Servidores podem omitir ou falsear vida, armadura, durabilidade e grupo dos outros jogadores; nesses casos a interface só pode mostrar os dados recebidos pelo cliente.

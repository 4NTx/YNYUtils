# Reach: sincronização, seleção e diagnóstico

## Código efetivamente inspecionado

Minecraft 1.8.9, mappings do SDK em `minecraft-named-open.jar`, API local
`stein-api-sources.jar`, Stein Loader 0.1.15 e Stein Bridge 0.1.12 da instalação.
Não há Forge EventBus, Mixin, ASM ou transformação de bytecode no YNYUtils.
O uso de membros Minecraft/Netty segue a autorização do projeto para acesso normal,
como no Knockback Control. O SDK compila/remapeia esses membros.

## Vanilla e momento do clique

`Minecraft.runTick` chama `EntityRenderer.getMouseOver(1.0F)` antes dos loops
de entrada. Isso apagava a seleção que o YNYUtils aplicava apenas no overlay.
Depois de mouse/teclado, os loops de `KeyBinding.isPressed()` consomem ataques e
interações em `Minecraft.clickMouse`/`rightClickMouse`.

Na Bridge instalada, os wrappers de `Mouse.next()` e `Keyboard.next()` notificam
o evento anterior antes de consultar o próximo, inclusive no encerramento do loop.
Por isso, os callbacks oficiais `SteinMod.onMouseInput` e `onKeyInput` permitem
sincronizar a seleção antes desses cliques. Essa ordem foi verificada no bytecode
da Bridge, não inferida somente pelo nome dos callbacks. Não usamos polling de
botões físicos: os controles vanilla continuam funcionando quando remapeados.

O vanilla consulta blocos usando `PlayerControllerMP.getBlockReachDistance`
(4,5 survival/5 creative), procura entidades colidíveis e não espectadoras e
aplica o limite de contato 3,0 survival ou 6,0 creative. A caixa consultada tem
somente a margem vanilla de `getCollisionBorderSize`; a hitbox não é modificada.
O centro (`getDistanceToEntity`) não define esse limite.

O clique consome `Minecraft.objectMouseOver` nos métodos normais do controller;
o servidor ainda decide se aceita a interação e aplica dano. Não produzimos
ataques extras, nem falsificamos posição ou packets.

## Seleção e otimização

- OFF/3,0: nenhuma seleção customizada nem busca adicional. Ao mudar a configuração,
  só restauramos o snapshot vanilla se `objectMouseOver` ainda for exatamente nosso
  objeto. Um resultado novo do vanilla/outro mod nunca é sobrescrito na restauração.
- Creative, spectator, GUI, câmera diferente do jogador e mundo ausente não recebem
  alcance customizado. O creative mantém 6,0 vanilla, inclusive em ON/3,0.
- Se o vanilla já selecionou uma entidade, reutilizamos esse resultado sem busca.
- Para seleção estendida, usamos olhos, vetor da mira, caixa, borda de colisão,
  alvo elegível mais próximo e obstrução por blocos. Ignoramos a montaria quando
  os olhos não estiverem dentro dela. Empates entre entidade e bloco favorecem bloco.
- Reutilizamos o impacto BLOCK vanilla. Se ele foi descartado como MISS, consultamos
  blocos novamente para não depender de informação perdida no resultado vanilla.
- O raycast de input usa partialTicks=1; o de preview usa o valor real do quadro.
  `Targeting.raycast(double)` não permite escolher partialTicks e o provider instalado
  usa o do render; ele também não filtra a montaria. Por isso a seleção usa diretamente
  a geometria Minecraft, em um helper isolado, sem inventar um hook no SDK.
- Cache limitado à fase de input, alcance, câmera, posição/rotação e identidade do
  resultado base. Invalidado a cada tick/quadro/configuração; não conserva alvo entre
  ticks nem deixa referências de mundo no modo OFF.
- O diagnóstico lê o mesmo hitVec usado para o clique; não lança outro raio.
- HUDs não consultam entidades nem executam raycasts. Textos do diagnóstico são
  formatados apenas quando chegam eventos. Largura da notificação PvP é cacheada,
  com invalidação em recarga de recursos/fontes e mudança do modo Unicode.

## Limitação de interação segurada

Os callbacks de input garantem a seleção dos cliques consumidos após eventos.
`rightClickMouse` também pode repetir uso enquanto a tecla está segurada, em ticks
sem qualquer evento de input. A Bridge atual não expõe callback entre o raycast
inicial desses ticks e essa repetição. Neles, a interação segue a seleção vanilla
daquele tick: não afirmamos que interações estendidas seguradas sejam contínuas.
Resolver isso integralmente pede um callback oficial após `getMouseOver` no tick,
antes de consumir ataques/usos. Nenhuma API fictícia ou contorno foi adicionado.

## Diagnóstico visual temporário

Enquanto Custom Reach está ligado, quatro linhas no canto superior esquerdo mostram
alvo na mira, distância dos olhos ao primeiro ponto de contato, limite configurado
e se o resultado está selecionado. Se a entidade estiver além do slider, uma busca
somente diagnóstica pode identificá-la até a distância vanilla de blocos (4,5 no
survival) e marcá-la fora do limite. Um bloco que cruza a mira aparece como obstrução.
Essa busca de leitura não muda o alvo usado pelo clique, o slider ou os packets.
Ao desligar Custom Reach, o overlay some. A medição é até a caixa de colisão: ela
não é a distância ao centro do aldeão e não comprova que o servidor aceitou um golpe.

## Diagnóstico de ataques (OFF por padrão)

`SteinMod.onAttackEntity` captura dados imutáveis na thread do jogo, sem cancelar.
Um observador Netty passivo acompanha somente `C02PacketUseEntity.Action.ATTACK`
correspondente e mostra o diagnóstico quando a promise de escrita local tem sucesso.
Isso não é confirmação do servidor nem garantia de entrega TCP/aplicação de dano.
Callbacks `onEntityHurt`/`onHealthChanged` observam dano no mesmo alvo dentro de 1s:
o texto expressamente não atribui esse dano ao nosso golpe (outras causas existem).

Não altera mensagens, ordem, conteúdo, frequência ou destinos de packets. A thread
Netty não consulta jogadores/mundo/fontes: lê apenas flags e metadata imutável.
A fila tem limite 32 e descarta excesso. Tentativas expiram; dados entre conexões
não se misturam. O observador é removido quando o diagnóstico está OFF/desconectado.
Os callbacks de ataque/dano sempre devolvem false, mantendo o fluxo vanilla.

O HUD `ynyutils.attack-diagnostics` é registrado no editor do Stein, separadamente
de `ynyutils.knockback-status` (posição/escala e notificações antigas preservadas).
Habilite o elemento no editor caso esteja oculto. Não substitui Reach Debug, removido.

## Verificação

Build JDK 25 pelo Stein SDK. `tests/run.ps1` executa testes headless contra as classes
Minecraft mapeadas reais com dublês de jogador/mundo. A alocação sem construtor usada
nesse harness serve somente para evitar inicialização de janela/OpenGL e não faz
parte do mod. Esses testes não provam aceitação do servidor nem timing em jogo.
Incluem restauração e isolamento entre mundos, filtros/obstrução, cache, limites,
reuso do vanilla, observer OFF, preservação de packets, promises normais/void do
Netty 4.0, escrita falha e distinção entre ataque solicitado/escrito e dano observado.

Teste manual em survival no servidor autorizado:

1. Compare OFF e ON/3,0 em um alvo cujo contato seja maior que 3,0; nenhum ataque
   estendido deve ocorrer. Lembre que a distância ao centro não é a distância ao contato.
2. ON/3,9: clique em alvo com contato entre 3,0 e 3,9. Remapeie ataque para outra
   tecla e repita. Teste mirando enquanto você e o alvo se movimentam.
3. Coloque um bloco entre os jogadores: nunca deve escolher o alvo através dele.
4. Alterne ON/OFF rapidamente e reduza para 3,0: não deve sobrar seleção estendida.
5. Teste duas entidades alinhadas, alvo removido, spectator, montaria, creative,
   reconexão e mudança de dimensão. No creative, 6,0 vanilla é preservado.
6. Ative diagnóstico no Panel e no editor HUD: compare contato/limite e pacote
   escrito. Se outro jogador causar dano, a mensagem não deve afirmar autoria.
7. Teste clique direito separado e uso segurado; a limitação acima é conhecida.
8. Desative diagnóstico, teste KB/keybinds/notificações e recarregue recursos F3+T.

Arquivos: CustomReach (estado/sincronização), EntityReachRaycast e ReachGeometry
(seleção/geometria), AttackDiagnostics (observação), AttackDiagnosticsHud (texto),
PvpStatusHud (cache de medidas), YNYUtils (callbacks/registro/Panel), YNYConfig (flag),
tests (regressões), README e este relatório (uso, evidências e limites).

# YNYUtils

Base modular client-side para Minecraft 1.8.9 com Stein Loader/Stein SDK.

O ponto de entrada é `com.yny.utils.YNYUtils`, implementando exclusivamente
`SteinMod`. A organização inicial reserva `core`, `modules`, `config` e `ui`
para recursos que tenham uma necessidade concreta. Compila com Stein SDK 0.2.0
e requer a biblioteca Stein Loader 0.1.16 (instalador 0.1.23 ou mais recente).

As preferências existentes permanecem em `config/ynyutils.json` e
`config/ynydamageindicator.json`. Ambos usam `ModConfig` do SDK: valores
inválidos voltam ao padrão campo a campo, e a gravação é atômica e agrupada.

## Knockback Control

Intercepta somente `S12PacketEntityVelocity` destinado ao jogador local,
antes de o manipulador vanilla aplicar `motionX`, `motionY` e `motionZ`.
O Panel oferece ativação, uma tecla selecionável para alternar o módulo e
velocidade recebida entre 93% e 100% (padrão: 100%). O valor mostra também a
redução correspondente.

Opcionalmente, `Manter impulso vertical vanilla` aplica o percentual apenas
em X/Z; Y permanece exatamente como veio no pacote. O `Jump Reset` é
independente da redução: pode ficar ON com `Reduzir Knockback` OFF, caso em
que o S12 inteiro permanece vanilla. `Pulinho ao receber KB no chão` é
experimental e fica desligado por padrão: depois de um S12 local
com impulso horizontal e vertical positivo, tenta um pulso de pulo normal
somente se o jogador ainda estiver no chão. Não cria pulo no ar. Não atua
em água, lava, teia, escadas, montarias, agachado ou com uma tela aberta;
respeita a tecla de pulo manual e tem intervalo mínimo de 600 ms. Não gera
ataques ou pacotes artificiais.

`Logs detalhados KB/Capira/Pot` fica OFF por padrão. Quando ligado, registra
em `logs/ynyutils-debug.log` os componentes vanilla/aplicados de cada S12
local, a decisão do Jump Reset e os motivos de Auto Capira/Auto Pot esperarem,
não encontrarem item na hotbar ou falharem no uso. O arquivo tem rotação
limitada a aproximadamente 1 MB; desligue o diagnóstico depois do teste.

O interceptor é instalado uma vez por conexão, ao entrar no mundo. Mudanças
seguidas de configuração ou keybind são agrupadas pelo SDK.

Ao ativar ou desativar o Knockback Control, uma notificação temporária aparece.
Ela pode ser movida, escalada ou ocultada pelo editor de HUD do Stein Loader.
Ela também pode ser ligada ou desligada em `Mostrar notificação do KB` no Panel.

O recurso Ataque Longo (Reach), incluindo diagnóstico, foi removido do mod
ativo. O arquivo de trabalho anterior foi preservado localmente fora deste
repositório para desenvolvimento posterior.

O relatório técnico está em
[`docs/knockback-control-compatibility.md`](docs/knockback-control-compatibility.md).

## YNY Damage Indicator

Integrado ao mesmo `.steinmod`, com uma seção própria no Panel e um HUD
independente para nome, vida, absorção e alvos distantes. Mantém o ID
`ynydamageindicator.target` e continua usando
`config/ynydamageindicator.json`, preservando posição e preferências anteriores.
Não altera seleção/alcance de ataque, cliques ou packets. O `.steinmod`
YNYDamageIndicator separado não deve ficar ativo junto com YNYUtils para evitar
registrar duas cópias do mesmo HUD.
O alvo distante é consultado por `Targeting.raycast(query)` do SDK, com o
alcance visual e a opção de ignorar folhagem já configurados.

## Auto Armor

Permanece ativado até você desligar, inclusive se acabar o estoque. Por padrão,
repõe peças quebradas/vazias; a reposição preventiva pode ser habilitada com um
limite de durabilidade restante (0% mantém o modo somente após quebrar). Nunca faz
uma troca preventiva por uma peça de qualidade inferior.

A escolha compara pontos de armadura, Proteção e encantamentos especializados,
Espinhos, Inquebrável e durabilidade. É possível definir um material preferido;
se não houver reserva dele, usa a melhor alternativa disponível. A opção
`Ignorar armaduras sem encantamentos` restringe as reservas a peças encantadas.
Na troca preventiva, exige mais durabilidade restante e não reduz os pontos base
de armadura. Uma peça realmente quebrada no slot sempre tem prioridade sobre o
limite preventivo; reservas danificadas, mas ainda utilizáveis, também podem ser
equipadas. `Usar reservas danificadas se a armadura quebrar` controla esse caso
(ligado por padrão); desligado, nesses casos só aceita reservas com 100% de
durabilidade. Itens literalmente destruídos não são equipáveis no Minecraft.
A troca observa os slots locais e os callbacks oficiais do servidor;
se uma confirmação não chegar, libera a operação após um timeout limitado e tenta de
novo com intervalo progressivo, sem deixar o módulo preso por vários segundos. Se ativado,
o descarte da peça antiga só ocorre depois que uma reserva válida foi equipada e
confirmada, somente quando a peça antiga não possui encantamentos e só com cursor
vazio; essa opção de descarte rotineiro está OFF por padrão. Sem reserva válida,
não descarta nada e mantém a peça atual no slot. Peças encantadas antigas são
preservadas nesse fluxo normal; o último recurso para cursor preso é separado.

Usa a API de inventário do Stein, sem abrir telas nem mexer nos controles de
movimento. O botão `Ligar/desligar Auto Armor`, a tecla selecionável, preferências
de equipamento e notificações ficam no Panel/HUD do Stein. Desativado por padrão;
as preferências são salvas na configuração.

Durante uma troca preventiva, se o servidor devolver a peça antiga ao cursor,
o módulo tenta guardá-la com o inventário fechado e o slot livre. Ele espera
os callbacks de cursor e destino e limita as tentativas. A opção de último recurso
(ligada por padrão) descarta somente a peça antiga identificada em callback do
servidor, após três devoluções sem sucesso ou oito ticks sem slot livre; exige
troca confirmada e nova peça equipada. Nunca descarta um cursor de origem incerta.
Mudanças manuais no inventário desabilitam esse descarte para a troca em curso.
O diagnóstico registra apenas transições e falhas, sem escrita a cada tick.
`logs/ynyutils-debug.log` e o arquivo anterior `ynyutils-debug.1.log` são
limitados a aproximadamente 1 MB cada; logs antigos acima desse limite são
descartados na próxima escrita.
`tests/AutoArmorSafetyTest.java` cobre confirmações atrasadas/fora de ordem,
cursor ocupado, janela aberta e destino incerto.

## Auto Consumíveis

O Panel permite ligar/desligar Auto Consumíveis, escolher tecla e notificação,
e configurar capira/maçã dourada e poções bebíveis de força/velocidade nos
modos econômico ou hard. A capira tem prioridade; só são usados itens na
hotbar. Ao iniciar, o mod sincroniza o slot com o servidor, usa o caminho
vanilla e restaura o slot anterior depois. Ele solta a tecla assim que observa
o primeiro consumo, evitando iniciar um segundo item. A queda na pilha é
provisória: a confirmação exige observar o efeito no jogador. Se o efeito não
aparecer em até dois segundos, registra a falha e aplica uma pausa antes de
tentar novamente. Telas abertas e uso manual suspendem a automação.

## Compilação

Requer JDK 25, Stein SDK 0.2.0 e Stein Loader instalado no jogo com a
biblioteca 0.1.16 ou mais recente. Para preparar os mappings e compilar:

```powershell
java -jar ..\stein-sdk-tool\v0.2.0\stein-sdk.jar setup --mc ..\data\.minecraft
java -jar ..\stein-sdk-tool\v0.2.0\stein-sdk.jar build
```

Regressões headless, depois do build: `powershell -File tests\run.ps1`.
Os dublês de teste não são incluídos no `.steinmod`.

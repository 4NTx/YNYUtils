# YNYUtils

Mod client-side para Minecraft 1.8.9 com Stein Loader.

A versão funcional preservada está em `artifacts/YNYUtils.steinmod`. `recovered-src/` guarda a decompilação legível dessa versão; `src/main/java/` ainda deriva de uma base anterior e não representa sozinho todos os módulos ativos. Consulte `RECOVERY.md` antes de reconstruir o pacote.

O ponto de entrada é `com.yny.utils.YNYUtils`, implementando exclusivamente
`SteinMod`. A organização inicial reserva `core`, `modules`, `config` e `ui`
para recursos que tenham uma necessidade concreta. Compila com Stein SDK 0.2.0
e requer a biblioteca Stein Loader 0.1.16 (instalador 0.1.23 ou mais recente).

As preferências existentes permanecem em `config/ynyutils.json` e
`config/ynydamageindicator.json`. Ambos usam `ModConfig` do SDK: valores
inválidos voltam ao padrão campo a campo, e a gravação é atômica e agrupada.
## Painel de configuração

As opções do painel estão agrupadas por tarefa: **Ataque e aliados**, **Knockback: escolha Shift Reset ou Jump Reset**, **Armadura e inventário**, **Consumíveis: maçãs e poções**, **Damage Indicator** e **Clan Ping**. Os rótulos dizem o efeito; ciclos mostram o comportamento de cada modo, e controles numéricos exibem unidade ou resultado.

- **Ataque e aliados:** a opção documenta a chance de 50% no primeiro aliado e que o mesmo grupo/clã é sempre ignorado.
- **Knockback:** Shift Reset e Jump Reset são mutuamente exclusivos; ativar um desativa o outro.
- **Armadura e inventário:** reposição, material, reservas, filtro de armaduras recebidas e movimento com inventário aberto ficam juntos. As opções de descarte indicam a condição e a salvaguarda.
- **Consumíveis:** maçãs e poções têm modos independentes. Os modos diferenciam economia e combate, com tempos em segundos.
- **Damage Indicator:** nome, vida, absorção, alvo, distância, mão, armadura e aparência ficam na seção do indicador.
- **Clan Ping:** atalhos, duração, alcance, notificações e autorização ficam agrupados. O token é guardado no armazenamento de segredos do Stein.

Os nomes das propriedades JSON foram preservados para manter as configurações existentes. Preferências gerais ficam em `config/ynyutils.json`; as do Damage Indicator ficam em `config/ynydamageindicator.json`.

## Knockback e resets

O Knockback Control do pacote ativo aplica 92% do impulso recebido ao jogador local. O painel deixa os resets juntos: Shift Reset e Jump Reset são mutuamente exclusivos; ligar um desliga o outro. O Jump Reset só tenta pular após impulso recebido enquanto o jogador ainda está no chão e respeita a janela de estado definida pelo módulo.

Os logs detalhados ficam ligados por padrão. A gravação é assíncrona e separada por módulo em `logs/ynyutils/`; arquivos com mais de 24 horas são removidos e o total é limitado a 32 MiB. O gravador descarta eventos se a fila limitada encher e registra um resumo de descartes.

O interceptor é instalado uma vez por conexão, ao entrar no mundo. Mudanças
seguidas de configuração ou keybind são agrupadas pelo SDK.

Ao ativar ou desativar os resets de knockback, uma notificação temporária pode aparecer.
Ela pode ser movida, escalada ou ocultada pelo editor de HUD do Stein Loader.
As notificações dos módulos podem ser ocultadas nas opções de HUD correspondentes.

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
movimento. O botão `Ligar/desligar Auto Armor`, a tecla selecionável, preferências`r`nde equipamento e notificações ficam no Painel/HUD do Stein. O estado e as`r`npreferências são salvos na configuração.

Durante uma troca preventiva, se o servidor devolver a peça antiga ao cursor,
o módulo tenta guardá-la com o inventário fechado e o slot livre. Ele espera
os callbacks de cursor e destino e limita as tentativas. A opção de último recurso
(ligada por padrão) descarta somente a peça antiga identificada em callback do
servidor, após três devoluções sem sucesso ou oito ticks sem slot livre; exige
troca confirmada e nova peça equipada. Nunca descarta um cursor de origem incerta.
Mudanças manuais no inventário desabilitam esse descarte para a troca em curso.
Os registros de Auto Armor ficam em `logs/ynyutils/auto-armor-AAAA-MM-DD.log`; retenção e limite global são aplicados pelo gravador assíncrono.
`tests/AutoArmorSafetyTest.java` cobre confirmações atrasadas/fora de ordem,
cursor ocupado, janela aberta e destino incerto.

## Auto Consumíveis

O Panel permite ligar/desligar Auto Consumíveis, escolher tecla e notificação,
e configurar capira/maçã dourada e poções bebíveis de força/velocidade nos
modos econômico ou combate, independentemente para maçãs e poções. No econômico,
um hit real ou perda de vida arma o uso; o item só é consumido depois
que seu efeito termina e se a janela de PvP ainda estiver ativa. A simples
expiração dos corações de absorção não conta como hit. Ao confirmar o consumo,
os hits anteriores são considerados atendidos: sem novo dano, não há nova dose.
No modo combate,
o item é usado quando o efeito está ausente ou próximo do fim, somente na
janela configurada após atacar um jogador ou receber um hit. A capira tem prioridade; só são usados itens na
hotbar. Ao iniciar, o mod sincroniza o slot com o servidor, usa o caminho
vanilla e restaura o slot anterior depois. Ele solta a tecla assim que observa
o primeiro consumo, evitando iniciar um segundo item. A queda na pilha é
provisória: a confirmação exige observar o efeito no jogador. Se o efeito não
aparecer em até dois segundos, registra a falha e aplica uma pausa antes de
tentar novamente. Telas abertas e uso manual suspendem a automação.

## Compilação

Requer JDK 25, Stein SDK 0.2.0 e Stein Loader instalado no jogo com a
biblioteca 0.1.16 ou mais recente. Atenção: `src/main/java/` ainda não reconstrói todos os módulos ativos. Leia `RECOVERY.md`; a versão funcional preservada está em `artifacts/YNYUtils.steinmod`. Não substitua esse artefato por um build direto até a recuperação das fontes estar completa.

Para preparar os mappings e compilar:

```powershell
java -jar ..\stein-sdk-tool\v0.2.0\stein-sdk.jar setup --mc ..\data\.minecraft
java -jar ..\stein-sdk-tool\v0.2.0\stein-sdk.jar build
```

Regressões headless, depois do build: `powershell -File tests\run.ps1`.
Os dublês de teste não são incluídos no `.steinmod`.

O indicador atualiza vida pelo evento `onHealthChanged` e também a relê a cada tick; anima a barra e mantém um rastro breve quando a vida cai. Item na mão, quatro peças da armadura, durabilidade, distância e nome exibido pelo cliente são relidos enquanto o alvo está selecionado. Ícones, nomes e durabilidade vêm dos `ItemStack`s que o cliente recebeu. Se o servidor não sincronizar um slot, a HUD indica que ele está vazio ou sem dados; não estima durabilidade. `DEFESA` mostra o valor agregado disponível no cliente como conferência, que também depende da sincronização do servidor.

## Ataque e aliados

Quando `50% no primeiro aliado; grupo/clã sempre ignorado` está ativo, o mesmo clã (`GROUP`) é sempre ignorado. Para o primeiro aliado de outra relação (`ALLY`) no raio antes de uma parede, o cliente faz uma rolagem de 50%: se passar, ataca esse aliado; caso contrário, segue até o primeiro alvo não amigável. Aliados adicionais não geram novas rolagens. Com a opção desligada, o ataque vanilla é mantido.

A rolagem usa `ThreadLocalRandom.nextBoolean()` uma vez no primeiro ALLY, sem espera nem ação de rede. O evento e o resultado são registrados de forma assíncrona no log de combate.

# YNYUtils

Base modular client-side para Minecraft 1.8.9 com Stein Loader/Stein SDK.

O ponto de entrada é `com.yny.utils.YNYUtils`, implementando exclusivamente
`SteinMod`. A organização inicial reserva `core`, `modules`, `config` e `ui`
para recursos que tenham uma necessidade concreta e hooks oficiais do SDK.

## Knockback Control

Intercepta somente `S12PacketEntityVelocity` destinado ao jogador local,
antes de o manipulador vanilla aplicar `motionX`, `motionY` e `motionZ`.
O Panel oferece ativação, uma tecla selecionável para alternar o módulo e
velocidade recebida entre 93% e 100% (padrão: 100%). O valor mostra também a
redução correspondente.

O interceptor é instalado uma vez por conexão, ao entrar no mundo. Mudanças
seguidas de configuração ou keybind são agrupadas e gravadas uma vez, um segundo
após a última alteração.

Ao ativar ou desativar o Knockback Control, uma notificação temporária aparece.
Ela pode ser movida, escalada ou ocultada pelo editor de HUD do Stein Loader.
Ela também pode ser ligada ou desligada em `Mostrar notificação do KB` no Panel.

## Custom Reach

Quando ligado, substitui apenas a seleção local de uma entidade na linha da
mira, entre 3,0 e 3,9 blocos. O alvo precisa estar antes de qualquer bloco; o
clique e a interação continuam usando os métodos vanilla e o servidor continua
responsável por validar o alcance. Não altera hitboxes, movimento, velocity ou
pacotes de movimento.

OFF e ON com 3,0 não executam raycast adicional nem substituem a seleção vanilla.
Ao desligar/reduzir para 3,0, a seleção anterior é restaurada imediatamente, somente
se ainda pertencer ao YNYUtils. No criativo, preserva o alcance vanilla de 6 blocos.
Cliques de mouse ou teclas remapeadas sincronizam a seleção com o tick do jogo;
a prévia usa o partialTicks do quadro. Reutiliza resultados vanilla e evita
consultas repetidas dentro da mesma fase de input.

`Diagnóstico de ataques` é opcional (padrão OFF) e usa um HUD próprio do Stein.
Mostra alvo, distância dos olhos até o contato, limite e escrita local do pacote
de ataque. Dano observado não confirma autoria nem aceitação do servidor.
Não é o antigo Reach Debug: nada é desenhado continuamente ao apontar um alvo.

Detalhes, limitações e testes: [`docs/reach-improvements.md`](docs/reach-improvements.md).
O relatório técnico está em
[`docs/knockback-control-compatibility.md`](docs/knockback-control-compatibility.md).

## Auto Armor

Permanece ativado até você desligar, inclusive se acabar o estoque: volta a equipar
quando uma peça quebra e surge uma reserva compatível na bolsa/hotbar.
Prioriza maior proteção e, em empate, melhor durabilidade. Usa a API de inventário
do Stein, sem abrir telas nem mexer nos controles de movimento. Faz no máximo uma
troca a cada dois ticks e espera antes de tentar de novo se a confirmação demorar.
Desativado por padrão; o botão `Ligar/desligar Auto Armor`, a tecla selecionável e
a notificação ficam no Panel/HUD do Stein. A preferência é salva na configuração.

## Compilação

Requer JDK 25 e o Stein SDK preparado para Minecraft 1.8.9:

```powershell
java -jar ..\stein-sdk-tool\stein-sdk.jar build
```

Regressões headless, depois do build: `powershell -File tests\run.ps1`.
Os dublês de teste não são incluídos no `.steinmod`.

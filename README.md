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

Permanece ativado até você desligar, inclusive se acabar o estoque. Por padrão,
repõe peças quebradas/vazias; a reposição preventiva pode ser habilitada com um
limite de durabilidade restante (0% mantém o modo somente após quebrar). Nunca faz
uma troca preventiva por uma peça de qualidade inferior.

A escolha compara pontos de armadura, Proteção e encantamentos especializados,
Espinhos, Inquebrável e durabilidade. É possível definir um material preferido;
se não houver reserva dele, usa a melhor alternativa disponível. A opção
`Ignorar armaduras sem encantamentos` restringe as reservas a peças encantadas.
Na troca preventiva, exige mais durabilidade restante e não reduz os pontos base
de armadura. A troca observa os slots locais e os callbacks oficiais do servidor;
se uma confirmação não chegar, libera a operação em até oito ticks e tenta de
novo com intervalo progressivo, sem deixar o módulo preso por vários segundos. Se ativado,
o descarte da peça antiga só ocorre depois dessa confirmação, somente quando ela
não possui encantamentos e só com cursor vazio; por padrão o descarte está OFF.
Peças encantadas antigas são sempre preservadas.

Usa a API de inventário do Stein, sem abrir telas nem mexer nos controles de
movimento. O botão `Ligar/desligar Auto Armor`, a tecla selecionável, preferências
de equipamento e notificações ficam no Panel/HUD do Stein. Desativado por padrão;
as preferências são salvas na configuração.

## Compilação

Requer JDK 25 e o Stein SDK preparado para Minecraft 1.8.9:

```powershell
java -jar ..\stein-sdk-tool\stein-sdk.jar build
```

Regressões headless, depois do build: `powershell -File tests\run.ps1`.
Os dublês de teste não são incluídos no `.steinmod`.

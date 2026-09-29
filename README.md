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

## Reach Debug

Ferramenta somente de diagnóstico. Quando ativada, mostra no HUD o alvo que o
Minecraft já está apontando, a distância até a hitbox, o alcance vanilla atual e
o alcance configurado para diagnóstico (3,0 a 3,9 blocos). Ela não modifica
raycast, alcance, hitbox, ataque, pacotes ou posições.
O relatório técnico está em
[`docs/knockback-control-compatibility.md`](docs/knockback-control-compatibility.md).

## Compilação

Requer JDK 25 e o Stein SDK preparado para Minecraft 1.8.9:

```powershell
java -jar ..\stein-sdk-tool\stein-sdk.jar build
```

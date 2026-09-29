# YNYUtils

Base modular client-side para Minecraft 1.8.9 com Stein Loader/Stein SDK.

O ponto de entrada é `com.yny.utils.YNYUtils`, implementando exclusivamente
`SteinMod`. A organização inicial reserva `core`, `modules`, `config` e `ui`
para recursos que tenham uma necessidade concreta e hooks oficiais do SDK.

## Knockback Control

Intercepta somente `S12PacketEntityVelocity` destinado ao jogador local,
antes de o manipulador vanilla aplicar `motionX`, `motionY` e `motionZ`.
O Panel oferece ativação e percentual entre 7% e 100% (padrão: 100%).
O relatório técnico está em
[`docs/knockback-control-compatibility.md`](docs/knockback-control-compatibility.md).

## Compilação

Requer JDK 25 e o Stein SDK preparado para Minecraft 1.8.9:

```powershell
java -jar ..\stein-sdk-tool\stein-sdk.jar build
```

# Knockback Control — compatibilidade com o Stein SDK

## Estado

`SUPPORTED VIA MINECRAFT NETTY PIPELINE`

## Fluxo vanilla 1.8.9 verificado

```text
S12PacketEntityVelocity.processPacket(INetHandlerPlayClient)
  -> NetHandlerPlayClient.handleEntityVelocity(S12PacketEntityVelocity)
  -> WorldClient.getEntityByID(packet.getEntityID())
  -> Entity.setVelocity(
       packet.getMotionX() / 8000.0,
       packet.getMotionY() / 8000.0,
       packet.getMotionZ() / 8000.0)
  -> Entity.motionX / motionY / motionZ
```

`EntityPlayerSP` herda `setVelocity` por `AbstractClientPlayer`,
`EntityPlayer` e `EntityLivingBase`, até `Entity`.

## Implementação adotada

O Stein SDK continua sendo usado para carregamento, ciclo de vida, Panel e
configuração. O módulo instala um `ChannelInboundHandler` Netty imediatamente
antes de `packet_handler`, o manipulador vanilla. Ele altera somente os campos
de um `S12PacketEntityVelocity` cujo `entityID` seja o do jogador local.

O pacote então segue normalmente para `NetHandlerPlayClient`, que divide os
valores por 8000 e chama `Entity.setVelocity`. Assim, 100% mantém os inteiros
originais; 50% usa 0,50; e 7% usa 0,07, com arredondamento ao inteiro do pacote.

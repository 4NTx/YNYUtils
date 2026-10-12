# Stein API e configuração do pack

Referências oficiais consultadas em 12/10/2026:

- [Loader: eventos dos mods](https://github.com/stein-project/loader/blob/main/src/main/java/dev/xavier/stein/loader/api/SteinMod.java)
- [Loader: inventário](https://github.com/stein-project/loader/blob/main/src/main/java/dev/xavier/stein/loader/api/Inventory.java)
- [HUD: alvo](https://github.com/stein-project/hud/blob/main/src/main/java/dev/xavier/stein/hud/TargetHud.java)
- [HUD: seleção do alvo](https://github.com/stein-project/hud/blob/main/src/main/java/dev/xavier/stein/hud/TargetTracker.java)

O evento `onHealthChanged` recebe os valores de vida que o servidor sincronizou. A leitura a cada tick melhora a atualização quando há um pacote, mas não revela vida que o servidor esconde ou falsifica. O evento `onEntityHurt` confirma a animação de dano sem informar quanto de vida caiu. Equipamento e durabilidade do adversário também dependem dos itens que o servidor enviou ao cliente.

O evento `onSlotChanged` ocorre depois que a alteração do inventário já foi aplicada. Ele cobre coleta no chão, cliques e itens concedidos pelo servidor, sem informar qual deles causou a mudança. Por isso o filtro de armaduras recebidas pode descartar com segurança depois da entrada no inventário, mas não impedir a coleta. `Inventory.drop` envia um clique de descarte; `Inventory.resync` pode pedir ao servidor o conteúdo real da janela se houver suspeita de divergência.

O HUD oficial do Stein também tem um elemento de alvo. Para usar o Damage Indicator do YNYUtils sem dois painéis de alvo, em `config/steinloader-hud.json`, dentro de `elements`, usar:

```json
"steinhud.target": { "enabled": false },
"ynydamageindicator.target": { "enabled": true }
```

Manter as outras propriedades de posição e escala existentes em cada objeto. Em `config/ynydamageindicator.json`, deixar `enabled`, `showHeldItem`, `showArmorDurability` e `showDistance` como `true` para exibir os dados pedidos. Essas chaves não contêm credenciais.

Em 12/10/2026, a pasta principal já tinha o Damage Indicator do YNYUtils visível. As duas cópias em `COISAS/YNY-Modpack-Limpa/data/.minecraft` e `COISAS/YNY-Modpack-Limpa/data/data/.minecraft` tinham o elemento desligado no layout, apesar de o módulo estar ligado. Os dois layouts foram corrigidos localmente e as três opções de dados foram explicitadas nas configurações dessas cópias. As pastas do pack ficam fora deste repositório; estas instruções preservam a alteração no GitHub sem publicar configurações locais ou segredos.

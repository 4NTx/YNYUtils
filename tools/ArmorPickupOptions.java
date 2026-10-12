package com.yny.utils.modules.player;
import com.yny.utils.config.YNYConfig;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
public final class ArmorPickupOptions {
 private ArmorPickupOptions(){}
 public static Page add(Page page,YNYConfig config,ModConfig<YNYConfig> store){
  return page
   .option(Option.toggle("Ativar filtro de armaduras recebidas",()->config.autoArmorDiscardReceived,value->{config.autoArmorDiscardReceived=value;store.saveSoon();}))
   .option(Option.toggle("Descartar recebida com 8% ou menos de durabilidade",()->config.autoArmorDiscardRed8,value->{config.autoArmorDiscardRed8=value;store.saveSoon();}))
   .option(Option.toggle("Descartar recebida sem encantamentos",()->config.autoArmorDiscardUnenchanted,value->{config.autoArmorDiscardUnenchanted=value;store.saveSoon();}))
   .option(Option.toggle("Descartar recebida com Proteção abaixo de IV",()->config.autoArmorDiscardBelowP4,value->{config.autoArmorDiscardBelowP4=value;store.saveSoon();}));
 }
}

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Applies the user-facing config labels to the recovered active Stein package. */
public final class RefactorConfigLabels {
    private static final Map<String, String> LABELS = labels();
    private static Map<String, String> labels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("PvP - Aliados", "Ataque e aliados");
        m.put("Reset de KB — escolha apenas UM modo", "Knockback: escolha Shift Reset ou Jump Reset");
        m.put("Auto Armor e InvMove", "Armadura e inventário");
        m.put("Auto Consumíveis - Cap e poções", "Consumíveis: maçãs e poções");
        m.put("Damage Indicator", "Damage Indicator: vida e equipamento do alvo");
        m.put("Clan Ping", "Clan Ping: marcar e localizar");
        m.put("Ignorar aliados e atacar inimigo atrás", "50% no primeiro aliado; grupo/clã sempre ignorado");
        m.put("Shift Reset (não combinar com Jump Reset)", "Usar Shift Reset (desativa Jump Reset)");
        m.put("Jump Reset (não combinar com Shift Reset)", "Usar Jump Reset (desativa Shift Reset)");
        m.put("Logs detalhados PvP (KB/Capira/Hit)", "Registrar detalhes de combate e consumíveis");
        m.put("Ativar Auto Armor", "Ativar troca automática de armadura");
        m.put("Ativar InvMove em inventários", "Mover-se enquanto o inventário está aberto");
        m.put("Material preferido", "Material preferido para reposição");
        m.put("Troca preventiva abaixo de", "Repor armadura abaixo desta durabilidade");
        m.put("Ignorar reservas sem encantamentos", "Usar somente reservas encantadas");
        m.put("Usar reservas danificadas se a armadura quebrar", "Aceitar reserva danificada se a peça quebrar");
        m.put("Descartar antiga sem encantamentos após troca confirmada", "Descartar peça antiga sem encantamentos após confirmar troca");
        m.put("Último recurso: descartar peça presa no cursor", "Último recurso: descartar peça antiga presa no cursor");
        m.put("Descartar armaduras recebidas", "Ativar filtro de armaduras recebidas");
        m.put("Descartar RED (8% ou menos)", "Descartar recebida com 8% ou menos de durabilidade");
        m.put("Descartar sem encantamentos", "Descartar recebida sem encantamentos");
        m.put("Descartar abaixo de Proteção IV", "Descartar recebida com Proteção abaixo de IV");
        m.put("Ativar Auto Consumíveis", "Ativar uso automático de maçãs e poções");
        m.put("Quando usar maçã dourada", "Quando usar maçãs douradas");
        m.put("Econômico: novo dano + efeito expirado", "Econômico: após dano, quando o efeito acabar");
        m.put("Combate: renovar perto do fim em PvP", "Combate: renovar efeito perto do fim");
        m.put("Prioridade de maçã", "Qual maçã escolher");
        m.put("Intervalo mínimo entre maçãs (segurança)", "Pausa mínima entre usos de maçã");
        m.put("Quando usar poções", "Quando usar poções de força/velocidade");
        m.put("Usar poção de força", "Permitir poção de força");
        m.put("Usar poção de velocidade", "Permitir poção de velocidade");
        m.put("Combate: renovar quando restarem", "Combate: renovar efeito quando restarem");
        m.put("Combate: tempo após último hit", "Combate: janela após último hit");
        m.put("Ativar indicador", "Exibir Damage Indicator");
        m.put("Mostrar nome do alvo", "Exibir nome do alvo");
        m.put("Mostrar vida em números", "Exibir vida atual e máxima");
        m.put("Mostrar absorção", "Exibir corações de absorção");
        m.put("Mostrar item na mão e durabilidade", "Exibir item da mão e durabilidade");
        m.put("Mostrar set e durabilidade das peças", "Exibir armaduras e durabilidade");
        m.put("Mostrar distância", "Exibir distância até o alvo");
        m.put("Mostrar alvos distantes", "Buscar alvos além da mira");
        m.put("Alcance visual", "Alcance máximo da busca visual");
        m.put("Ignorar folhagem", "Ver através de folhagem");
        m.put("Ignorar armor stands", "Ignorar manequins (armor stands)");
        m.put("Ignorar aliados e grupo", "Não exibir aliados nem grupo/clã");
        m.put("Estilo da HUD", "Aparência do indicador");
        m.put("Cor de destaque", "Cor de destaque da HUD");
        m.put("Cor de vida dinâmica", "Mudar cor conforme a vida");
        m.put("Cor da vida", "Cor fixa da barra de vida");
        m.put("Opacidade do fundo", "Transparência do fundo");
        m.put("Tecla: Ir aqui", "Atalho: marcar local");
        m.put("Tecla: Atacar inimigo", "Atalho: marcar inimigo");
        m.put("Clique da rodinha: ping rápido", "Usar clique do meio para ping rápido");
        m.put("Som ao receber Clan Ping", "Tocar som ao receber ping");
        m.put("Mostrar direção na HUD do ping", "Exibir direção e distância do ping");
        m.put("Tecla: despingar meu ping", "Atalho: remover meu ping");
        m.put("Mostrar HUD do Clan Ping", "Exibir cartão do Clan Ping");
        m.put("Duração: pingar aqui", "Duração do ping de local");
        m.put("Duração: pingar inimigo", "Duração do ping de inimigo");
        m.put("Alcance visual do Clan Ping", "Alcance para encontrar alvo/local");
        m.put("Cooldown entre pings", "Pausa mínima entre pings");
        m.put("Escala dos marcadores", "Tamanho dos marcadores");
        m.put("Autorização de líder", "Token para enviar pings do clã");
        m.put("Status do relay", "Estado da conexão e envio");
        return m;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.steinmod output.steinmod");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        AtomicInteger replacements = new AtomicInteger();
        try (ZipFile source = new ZipFile(input.toFile());
             ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] bytes;
                try (InputStream stream = source.getInputStream(entry)) { bytes = stream.readAllBytes(); }
                if (entry.getName().equals("com/yny/utils/YNYUtils.class")
                        || entry.getName().equals("com/yny/targethealth/TargetHealthMod.class")
                        || entry.getName().equals("com/yny/utils/modules/player/ArmorPickupOptions.class")) {
                    ClassWriter writer = new ClassWriter(0);
                    new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                String signature, String[] exceptions) {
                            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                            return new MethodVisitor(Opcodes.ASM9, delegate) {
                                @Override public void visitLdcInsn(Object value) {
                                    String replacement = value instanceof String ? LABELS.get(value) : null;
                                    if (replacement != null) { replacements.incrementAndGet(); super.visitLdcInsn(replacement); }
                                    else super.visitLdcInsn(value);
                                }
                            };
                        }
                    }, 0);
                    bytes = writer.toByteArray();
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime()); target.putNextEntry(copy); target.write(bytes); target.closeEntry();
            }
        }
        if (replacements.get() < 30) throw new IllegalStateException("too few labels changed: " + replacements.get());
        System.out.println("Config labels refactored: " + replacements.get());
    }
}

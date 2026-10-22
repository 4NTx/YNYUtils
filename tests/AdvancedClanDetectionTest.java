package tests;

import com.yny.utils.modules.clanping.PingClanName;

public final class AdvancedClanDetectionTest {
    public static void main(String[] args) {
        System.out.println("=== Running Advanced Clan Detection Tests ===");

        // 1. Suffix clan tags (placed after the nickname)
        expect("YNY", PingClanName.extractPrefixBracketTag("Jogador [YNY]", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("§a[VIP] §fJogador §e[YNY]", "Jogador"));
        expect("ABC", PingClanName.extractPrefixBracketTag("Nick [ABC]", "Nick"));
        expect("ABC", PingClanName.extractPrefixBracketTag("[VIP+] Nick [ABC]", "Nick"));
        expect("TREM", PingClanName.extractPrefixBracketTag("[MVP++] Nick [TREM]", "Nick"));

        // 2. Variable tag lengths: 2 to 6 characters (not just 3!)
        expect("FX", PingClanName.extractPrefixBracketTag("[FX] Jogador", "Jogador"));
        expect("TR", PingClanName.extractPrefixBracketTag("Jogador [TR]", "Jogador"));
        expect("GO", PingClanName.extractPrefixBracketTag("[GO] Jogador", "Jogador"));
        expect("YNYY", PingClanName.extractPrefixBracketTag("[YNYY] Jogador", "Jogador"));
        expect("DARK", PingClanName.extractPrefixBracketTag("Jogador [DARK]", "Jogador"));
        expect("TITAN", PingClanName.extractPrefixBracketTag("Jogador [TITAN]", "Jogador"));
        expect("ALPHA", PingClanName.extractPrefixBracketTag("[ALPHA] Jogador", "Jogador"));

        // 3. Various enclosure formats: [], (), {}, <>, «»
        expect("YNY", PingClanName.extractPrefixBracketTag("(YNY) Jogador", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("Jogador (YNY)", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("{YNY} Jogador", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("Jogador {YNY}", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("<YNY> Jogador", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("Jogador <YNY>", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("«YNY» Jogador", "Jogador"));

        // 4. Separators without brackets: |, •, ▪, -
        expect("YNY", PingClanName.extractPrefixBracketTag("YNY | Jogador", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("Jogador | YNY", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("Jogador • YNY", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("YNY • Jogador", "Jogador"));
        expect("YNY", PingClanName.extractPrefixBracketTag("Jogador - YNY", "Jogador"));

        // 5. Verification that ranks are still ignored even with suffix tags
        expect("YNY", PingClanName.extractPrefixBracketTag("[VIP] [Lutador2x] Jogador [YNY]", "Jogador"));

        // 6. Verification that duel tags are never misidentified as clan tags
        if (PingClanName.extractPrefixBracketTag("[x1] Jogador [x1]", "Jogador") != null) {
            throw new AssertionError("Duel tags [x1] must not be returned as clan tag");
        }

        // 7. Test outgoing chat command detection (/clan entrar <tag>, /clan sair)
        PingClanName.processOutgoingChat("/clan entrar TdP");
        expect("TDP", PingClanName.getDetectedSelfClan());

        PingClanName.processOutgoingChat("/clan sair");
        if (PingClanName.getDetectedSelfClan() != null) {
            throw new AssertionError("Clan should be cleared after /clan sair");
        }

        // 8. Test incoming chat SimpleClans notifications
        PingClanName.processIncomingChat("§6[Clans] §aVocê entrou no clan TdP.");
        expect("TDP", PingClanName.getDetectedSelfClan());

        PingClanName.processIncomingChat("§6[Clans] §cVocê saiu do clan.");
        if (PingClanName.getDetectedSelfClan() != null) {
            throw new AssertionError("Clan should be cleared after leaving clan");
        }

        // 9. Test that enemy chat and kill messages do NOT poison detected clan (MNP bug fix)
        PingClanName.processIncomingChat("[MNP] Inimigo: Artur vem x1");
        if (PingClanName.getDetectedSelfClan() != null) {
            throw new AssertionError("Enemy chat [MNP] must NOT set detected clan!");
        }

        PingClanName.processIncomingChat("[MNP] Inimigo matou Artur");
        if (PingClanName.getDetectedSelfClan() != null) {
            throw new AssertionError("Enemy kill message [MNP] must NOT set detected clan!");
        }

        // 10. Test that other players entering a clan does NOT change detected clan
        PingClanName.processIncomingChat("[Clans] Pedrinho entrou no clan MNP.");
        if (PingClanName.getDetectedSelfClan() != null) {
            throw new AssertionError("Other player joining clan [MNP] must NOT set detected clan!");
        }

        // 11. Test leader server tracking and onDisconnect cleanup
        PingClanName.setLeaderServer("mush.com.br/gladiador");
        expect("mush.com.br/gladiador", PingClanName.getLeaderServer());

        PingClanName.setDetectedSelfClan("YNY");
        expect("YNY", PingClanName.getDetectedSelfClan());

        PingClanName.onDisconnect();
        if (PingClanName.getLeaderServer() != null) {
            throw new AssertionError("Leader server must be null after onDisconnect");
        }
        if (PingClanName.getDetectedSelfClan() != null) {
            throw new AssertionError("Detected self clan must be null after onDisconnect");
        }

        System.out.println("PASS: All Advanced Clan Detection tests passed successfully!");
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected [" + expected + "], got [" + actual + "]");
        }
    }
}

# Migração Combat → YNYUtils

O pacote ativo YNYUtils.steinmod inclui agora duas entradas do Stein Loader: o YNYUtils preservado e a Equipe. A Equipe usa as classes transferidas do SteinCombat sob o pacote com.yny.utils.team.

A lista é lida de https://ping.antiutm.com/allies.json ao entrar no servidor e novamente a cada 15 segundos. A rede roda em segundo plano, com atualização a cada 15 segundos e sem usar cache HTTP. Em falha, permanece a última lista válida salva em config/ynyutils-team.json. O URL é imposto no código ao carregar e salvar; o painel mostra apenas o estado e o botão de atualização. Aliados, clãs aliados, inimigos, clãs inimigos e seções por servidor aceitam as chaves inglesas e portuguesas suportadas pelo Feed original.

O painel Equipe conserva as cores, a identificação de clãs, a ocultação/transparência de aliados, a lista somente leitura e as HUDs de jogadores próximos com os mesmos IDs salvos anteriormente. O YNYUtils original mantém a decisão e o redirecionamento de ataques para que um clique não gere dois ataques.

A CombatApi pública do Combat consulta as relações compartilhadas pelo Loader para preservar o uso por outros mods e pelo AutoProtect do Combat. A escolha de armaduras do Auto Refill continua configurável no painel do Combat. No Hit Delay, Autoclick, Toggle Sprint, Keep Sprint, Omni Sprint, Auto Refill e o AutoProtect do Combat permanecem disponíveis. A automação Auto Armor, o Smart Effect e as relações/listas do Combat não são inicializados. O SmartEffect permanece apenas como controlador interno da defesa do AutoProtect do Combat quando esse recurso for ligado.

Fonte transferida: SteinCombat 0.1.22, MPL 2.0. A licença e o aviso estão em team-source/ e no pacote final. Loader instalado: 0.1.33; os dois artefatos novos exigem pelo menos 0.1.25.

Backups dos mods instalados antes da troca:
- YNYUtils/build/YNYUtils-before-team-migration-2026-10-13.steinmod
- SteinCombat/build/Combat-before-team-migration-2026-10-13.steinmod

Compilação: executar spotlessApply build em SteinCombat e em YNYUtils/team-source, depois tools/Merge-YNYTeam.ps1 sobre o pacote YNYUtils preservado. O diretório COISAS/data é uma junção local para data, usada pelo SDK ao empacotar o subprojeto.

Verificações: build completo dos dois projetos, verificação Java de 32 classes de Equipe e das classes recuperadas do YNYUtils, integridade dos arquivos originais do YNYUtils, hashes idênticos entre candidatos e cópias instaladas. A consulta real ao URL, o parse do JSON, a compilação e o carregamento das 32 classes foram verificados. A interface e o comportamento em servidor ainda requerem uma partida após reiniciar o Loader.
## Correção de carregamento (2026-10-13)

O patch de HitThroughWeb em tools/PatchClanFeatures.java agora insere um frame F_SAME no destino do primeiro IFEQ de onAttackEntity. O arquivo já empacotado foi corrigido por tools/FixAttackStackMap.java. O pacote anterior está em build/YNYUtils-before-stackmap-fix-2026-10-13.steinmod; o instalado anteriormente também foi preservado em build/YNYUtils-installed-before-stackmap-fix-2026-10-13.steinmod.

Após recompilar o patch de origem e mesclar a Equipe, o pacote final deve conter StackMapTable com frame no offset 16 de onAttackEntity. A verificação de carregamento deve pôr o .steinmod antes do diretório do verificador no classpath; tools/VerifyYNYEntry.java imprime a origem exata da classe carregada. O pacote instalado passou por essa verificação e pelas 32 classes da Equipe.

As duas cópias legadas em COISAS/YNY-Modpack-Limpa também receberam somente a correção do frame, preservando seus manifests. Backup: build/YNYUtils-clean-pack-before-stackmap-fix-2026-10-13.steinmod.

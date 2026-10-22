# 🎮 Guia Rápido de Teclas e Configurações — Modpack YNY

> **Resumo prático e direto ao ponto:** este guia explica exatamente qual tecla apertar, o que cada função faz na prática e como deixar seu jogo configurado com segurança máxima.

---

## ⚔️ Teclas Principais de Combate e PvP

### `[ R ]` — Auto Refill (Reabastecimento Automático)
* **O que faz:** Mantém sua hotbar e seu inventário sempre abastecidos sem você precisar abrir baús manualmente.
* **Modo atual:** **"Manter Estoque"** — só precisa ativar uma única vez e ter itens guardados no seu baú. Sempre que sair algum item da hotbar (gastou capira, quebrou espada, usou poção), ele repõe na hora sozinho.
* **Armaduras:** Mantém sempre **3 sets completos** no seu inventário.
* **Devolução inteligente:** Se tiver peças gastas/usadas no inventário, ele **devolve automaticamente ao `/bau vip`**.
* **100% em Segundo Plano:** **NÃO abre a tela do baú na sua cara!** O PvP não é interrompido, seu boneco não trava e você continua batendo e andando normalmente.
* *Configuração opcional:* Se quiser refilar apenas ao clicar em vez do modo automático contínuo, configure no menu pressionando `[INSERT]`.

---

### `[ B ]` — `/bau vip` Rápido
* **O que faz:** Envia o comando `/bau vip` no chat instantaneamente.
* **Onde fica:** Já configurado no **Macro Keybind**, funcional e com proteção anti-flood de pacotes.

---

### `[ P ]` — Auto Armor (Troca Automática de Armadura)
* **O que faz:** Troca qualquer peça de armadura (Capacete, Peitoral, Calça ou Bota) no instante em que ela **quebra**.
* **Troca preventiva (antes de quebrar):** Se quiser que troque antes de quebrar (ex: ao chegar em 15% ou 20% de durabilidade), abra o menu no `[INSERT]` ➔ vá em **YNYUtils** ➔ **Auto Armor** e regule a barra.
* **Sem bugs:** Proteção de cursor ativa — não trava item na mão nem joga peças valiosas no chão.

---

### `[ ↑ ]` (Seta para Cima) — Auto Consumíveis (Capira & Poções)
* **O que faz:** Liga e desliga o consumo automático inteligente de:
  * 🍏 **Maçã Dourada / Capira (Notch)**
  * ⚔️ **Poção de Força II**
  * ⚡ **Poção de Velocidade II**
* **Modo Econômico (Economiza seus recursos):**
  * **Perdeu vida?** Come a maçã na hora.
  * **Não perdeu vida?** NÃO come e não gasta recurso à toa.
  * **Está em combate (PvP)?** Come/renova as poções e capira assim que o efeito expirar.
  * **NÃO está em PvP?** Se o efeito acabar e você estiver fora de combate, ele **não renova à toa**, economizando todos os seus potes.

---

### `[ M ]` — Mob Aura (Matar Mobs & Abrir Tesouros)
* **O que faz:** Ataca automaticamente os monstros ao redor.
* **Desempenho:** Bate em **2 alvos simultâneos** com velocidade cravada de **12 CPS**.
* **Anti-KB em Monstros:** Você **não leva knockback** de **Guardian** nem de **Wither** (e reduz/anula o empurrão de outros monstros).
* **Segurança comprovada:** Testado abrindo mais de **50 tesouros** sem levar nenhum ban (use por sua conta e risco).
* **Foco em Boss:** Ao se aproximar de um **Guardian** ou **Wither**, o Mob Aura foca e tritura esses monstros primeiro, ignorando zumbis e esqueletos secundários.
* **Pausa Automática contra Jogadores:** Se qualquer **player inimigo** chegar perto, o Mob Aura **pausa imediatamente** para não bugar seus hits no PvP e não dividir a mira com monstros. *(Dá pra desativar essa proteção no menu Insert se quiser bater em tudo junto)*.

---

### `[ N ]` — Hitar ou Ignorar Pig Zombie (Zombie Pigman)
* **O que faz:** Liga ou desliga se o Mob Aura deve bater nos Pig Zombies do Nether.
* **Padrão (DESLIGADO):** Ele **ignora 100% dos Pig Zombies**, não batendo neles.
* **Por que isso é excelente?**
  * Ao abrir livros de tesouro no Nether, você **não chama a horda inteira de Pigman** pra cima de você.
  * O Mob Aura foca nos Guardians, Withers e outros bichos perigosos primeiro, poupando **muita capira**.
  * Quando terminar de limpar a área, aperte `[N]` e mate os Pig Zombies se quiser.
* **Anti-Morte Inteligente:** Se algum Pig Zombie já estiver irritado/agressivo contra você, o Mob Aura **bate nele na hora** mesmo com a opção desligada para proteger sua vida.
* **Comandos rápidos no chat:** Digite `/pig` ou `.pig` para ligar/desligar na hora.

---

### `[ V ]` — Modo Foco (PvP Focus Mode)
* **O que faz:** Redireciona e foca seus ataques automaticamente no adversário mais frágil.
* **Critério de Foco:** Prioriza quem estiver com **menos vida** ou **sem capacete** (ou com peça de armadura ausente há mais de 1 segundo).
* **Comandos de chat úteis:**
  * `/focus on` / `/focus off` ➔ Liga ou desliga.
  * `/focus elmo` ➔ Foca exclusivamente quem estiver sem capacete.
  * `/focus tempo 1` ➔ Ajusta o tempo de espera sem peça (padrão: 1s).

---

### `[ G ]` — ToggleSprint (Correr Automático)
* **O que faz:** Mantém a corrida sempre ativa. Basta apertar e segurar `W` que o personagem corre na velocidade máxima sem precisar de duplo clique nem segurar a tecla `Control`.

---

## 🛠️ Menus e Telas de Configuração

### `[ INSERT ]` — Painel Geral de Configurações
* Pressione a tecla **INSERT** para abrir o menu com todos os módulos:
  * **YNYUtils:** Ajustes de Auto Armor, Auto Consumíveis, Ping de Clã, Mob Aura, Foco.
  * **SteinCombat:** Autoclicker, ToggleSprint, Auto Refill.
  * **Knockback:** Piso de segurança travado em 90% (redução máxima de 10% — sem 0KB ilegal).

### `[ SHIFT DIREITO ]` *(Abaixo do Enter)* — Editor de HUD
* Abre o editor visual da tela:
  * Clique e arraste para posicionar onde quiser: vida do oponente mirado, aviso de armadura própria quebrada, status do clã, contador de CPS e bússola.

---

## ⚡ Dica de Ouro: Autoclicker Anti-Ban

No menu de Autoclicker (abra no `[INSERT]` ou aperte `[ F ]`):
* Configure para o perfil: **Baiano Single Click 32/33**.
* **Vantagens:**
  * Não gera avisos de hack na moderação do servidor.
  * Não toma alerta de CPS no Anti-Cheat.
  * Entrega hits consistentes e não trava os menus nem o clique do mouse no inventário.

---

## 📌 Outros Atalhos Úteis

| Tecla | Função |
| :---: | :--- |
| **`F`** | Liga / Desliga o Autoclicker |
| **`C`** | Zoom suave de luneta |
| **`' ou ~`** *(Til)* | Menu do Macro Keybind para editar atalhos |
| **`Scroll (MMB)`** | Botão do meio da mira: dá Ping inteligente de Clã (marca inimigo ou local no bloco) |
| **`H`** | Alterna modo fantasma/invisível para aliados e clã em confrontos de massa |

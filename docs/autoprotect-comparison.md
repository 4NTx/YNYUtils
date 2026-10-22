# AutoProtect: comparação com SteinCombat

O YNYUtils já controla a tecla de uso junto com AutoConsumables e libera a defesa quando o InvMove se desloca em uma tela. Também mantém dois limites de vida: entra no limite baixo e sai no limite de recuperação.

O AutoProtect do SteinCombat acrescenta duas salvaguardas úteis:

1. A defesa por vida baixa só começa durante combate: dano nos últimos 60 ticks ou inimigo a até 6 blocos. O YNYUtils atual defende ao cruzar o limite mesmo sem combate. Um filtro opcional de combate evitaria selecionar a espada fora de perigo.
2. Se o jogador troca manualmente o slot da hotbar enquanto bloqueia, o SteinCombat suspende a automação por 60 ticks. O YNYUtils restaura o slot anterior ao liberar a defesa, mas pode voltar a escolher a espada no tick seguinte. Uma pausa após intervenção manual evitaria disputar o controle com o jogador.

Não copiei a gestão da tecla do Smart Effect. Ela também controla comida e poções, enquanto o YNYUtils já tem AutoConsumables e sua própria reserva da tecla. Copiá-la criaria dois controladores concorrentes.

A migração da Equipe usa a proteção e o redirecionamento de ataque existentes no YNYUtils. A entrada de Equipe não envia um segundo ataque ao clicar em membro do clã.
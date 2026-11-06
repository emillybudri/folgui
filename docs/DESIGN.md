# Design do Folgui

## Princípios

1. **Responda em um olhar.** A tela inicial diz o que é hoje antes de qualquer outra coisa.
2. **Cor é significado.** Cada cor quer dizer uma coisa só, no app inteiro.
3. **Ícone e rótulo curto.** Até duas palavras. Explicação só aparece quando há um problema.
4. **Mostre, não descreva.** O círculo colorido do dia e o anel da pessoa substituem frases.
5. **O que se usa mais fica mais perto.** Criar evento é o botão central; a IA fica sempre no topo.

## Cores

| Cor | Significado | Fundo | Texto | Forte |
|---|---|---|---|---|
| Violeta | Ação e evento | `#ECE7FF` | `#3A1FB8` | `#6246EA` |
| Verde | Folga, livre | `#D3F5E3` | `#0A5238` | `#0B8F5A` |
| Coral | Trabalho, ocupado | `#FFE0DA` | `#8C2412` | `#D9432C` |
| Azul | Férias | `#D9ECFF` | `#0B4A80` | `#1F78D6` |
| Índigo | Sono | `#E4E2FB` | `#2F2B7A` | `#4A44B5` |
| Âmbar | Atenção, feriado | `#FFEDBF` | `#634500` | `#A86F00` |

Violeta nunca indica um estado do dia. Os valores do modo escuro estão em `Tema.kt`. Texto sobre os fundos tem contraste de pelo menos 7:1.

### Cores de grupo

As 11 cores de grupo são as da Google Agenda (`CorGrupo` em `Grupos.kt`) e identificam o grupo, não um estado do dia. Por isso aparecem só como um ponto ou faixa ao lado do nome, nunca como fundo de cartão ou de texto, e sempre com o nome da cor para leitores de tela.

## Navegação

Hoje · Agenda · **+** · Eventos · Pessoas. A barra da IA e o perfil ficam fixos no topo das abas.

## Componentes

- **Cartões:** brancos, sem borda, com sombra leve e cantos de 24dp.
- **Dias:** círculos na tela inicial, com a cor do estado e anel violeta em hoje.
- **Pessoas:** avatar com anel na cor do que a pessoa está fazendo agora.
- **Semana:** blocos em tons suaves, para o texto continuar legível.
- **Marcar com alguém:** o dia é pintado pelo dia inteiro, nunca por um horário suposto. "Melhores dias" vem antes do calendário, e a hora começa vazia.
- **Feriado:** a escala não sabe se a empresa abre, então o app pergunta "Você trabalhará neste dia?".
- **Entrada:** splash, login (Google ou e-mail) e, só então, o cadastro da rotina.
- **Etapas do cadastro:** círculo com ícone e número embaixo, sem nome visível.

## Textos

- Títulos são substantivos: "Horário", não "Qual é o horário?".
- Disponibilidade só aparece em um evento quando há conflito.
- Sem texto de ajuda fixo: se a tela precisa de explicação, a tela deve mudar.

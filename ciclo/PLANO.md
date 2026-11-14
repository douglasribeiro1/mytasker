# Ciclo Dados: plano de melhorias

Como funciona hoje: o app mantém uma notificação fixa que muda de texto ("Desligar" / "Ativar").
Duas rotinas da Samsung com "Notificação recebida" leem essa palavra e desligam ou ligam os dados móveis.
Quem executa a ação é a Samsung, que tem permissão de sistema. O app só decide **quando**.

## Fase 1: robustez (aprovada)

- [ ] **Voltar sozinho depois de reiniciar o celular.** Hoje o ciclo para até o app ser aberto.
- [ ] **Conferir se a rotina obedeceu.** Se 1 min após "Ativar" os dados continuarem desligados,
      reenviar o sinal. Se ainda falhar, mostrar um aviso.
- [ ] **Pular o ciclo conectado ao Wi-Fi**, porque desligar os dados aí não economiza nada.
- [ ] **Pular o ciclo carregando.**
- [ ] **"Pausar por 1 hora"**, pela tela do app e pela notificação.
- [ ] **Botão no painel rápido** para ligar ou pausar o ciclo.

## Fase 2: períodos por horário (aprovada)

Lista com quantos períodos você quiser. Cada período tem:
- nome, dias da semana, início e fim (pode atravessar a meia-noite);
- comportamento:
  - ciclo com tempos próprios;
  - dados sempre desligados *(em aberto: vale com a tela acesa?)*;
  - pausado (internet normal).

Fora dos períodos, valem os tempos padrão. Se dois períodos se sobrepõem, vale o que estiver mais acima
na lista, e a lista pode ser reordenada.

## Fase 3: resumo do dia (aprovada)

- "Hoje: 6h20 sem dados móveis", com os últimos 7 dias.

## Fase 4: motor de automações (em discussão)

A notificação vira um **controle remoto genérico das rotinas da Samsung**.
A Samsung executa qualquer ação que uma rotina sabe fazer: Wi-Fi, Bluetooth, modo de som,
Não perturbe, economia de energia, Always On Display, brilho, sincronização, 5G etc.
O app fornece os **gatilhos que a Samsung não tem**:

- ciclos ("X min ligado, Y min desligado");
- espera depois de apagar a tela;
- temporizador ("por 1 hora");
- vários horários por dia;
- combinações (tela apagada **e** horário **e** bateria abaixo de X%);
- estado que o app enxerga: tela, Wi-Fi conectado, carregando, nível de bateria.

Cada automação teria:
- nome;
- par de palavras (ligar/desligar), geradas pelo app para nunca uma conter a outra;
- gatilho e condições;
- instrução pronta de quais rotinas criar.

A economia de dados vira só uma automação pré-pronta entre várias.

Várias automações simultâneas seriam **uma notificação por automação**, agrupadas para não poluir.

### Pontos a testar antes de construir

- A Samsung reage a várias notificações do mesmo app ao mesmo tempo?
- A Samsung reage a notificações **agrupadas**?
- A Samsung reage a uma notificação que só **mudou de texto**? Isso já funciona com uma só.
- Existe limite de rotinas na Samsung?

### Limites

- O app **não cria** as rotinas: você cria uma vez, seguindo a instrução que o app mostra.
- O app só confere o resultado do que ele consegue ler: dados, Wi-Fi, Bluetooth, modo de som,
  Não perturbe e economia de energia.

## Em aberto

1. "Dados sempre desligados" vale com a tela acesa?
2. Apagar o MyTasker antigo (Shizuku) do repositório ou manter?
3. Quais automações além dos dados você usaria de fato? Isso define a ordem.

# Pulso (antigo Ciclo Dados): plano de melhorias

Como funciona hoje: o app mantém uma notificação fixa que muda de texto ("Desligar" / "Ativar").
Duas rotinas da Samsung com "Notificação recebida" leem essa palavra e desligam ou ligam os dados móveis.
Quem executa a ação é a Samsung, que tem permissão de sistema. O app só decide **quando**.

## Fase 1: robustez (aprovada)

- [x] **Voltar sozinho depois de reiniciar o celular.** Hoje o ciclo para até o app ser aberto.
- [x] **Conferir se a rotina obedeceu.** Se 1 min após "Ativar" os dados continuarem desligados,
      reenviar o sinal. Se ainda falhar, mostrar um aviso.
- [x] **Pular o ciclo conectado ao Wi-Fi**, porque desligar os dados aí não economiza nada.
- [x] **Pular o ciclo carregando.**
- [x] **"Pausar por 1 hora"**, pela tela do app e pela notificação.
- [x] **Botão no painel rápido** para ligar ou pausar o ciclo.

## Fase 2: períodos por horário (aprovada)

Lista com quantos períodos você quiser. Cada período tem:
- nome, dias da semana, início e fim (pode atravessar a meia-noite);
- comportamento:
  - ciclo com tempos próprios;
  - economia o tempo todo (vale com a tela acesa ou apagada);
  - pausado (internet normal).

Fora dos períodos, valem os tempos padrão. Se dois períodos se sobrepõem, vale o que estiver mais acima
na lista, e a lista pode ser reordenada.

## Fase 3: resumo do dia (aprovada)

- "Hoje: 6h20 sem dados móveis", com os últimos 7 dias.

## Fase 4: motor de automações (descartada)

As rotinas da Samsung já cobrem foco, bateria baixa e noite. O Pulso ficou genérico:
a rotina faz o que você quiser (dados, Wi-Fi...) com os sinais "Desligar" e "Ativar".

## Feito também

- [x] Nome e ícone novos (Pulso). O pacote é o mesmo, então instala por cima.
- [x] Tutorial passo a passo dentro do app. Abre na primeira vez.
- [x] MyTasker antigo (Shizuku) removido do repositório.
- [x] Horários: "Economia o tempo todo" vale com a tela acesa ou apagada.
- [x] Resumo do dia e dos últimos 7 dias.
- [x] Tempo mínimo de 1 minuto; a conferência dos dados acontece na metade da fase.
- [x] Sem economia durante chamadas (ligação comum, WhatsApp e outros apps de voz) e com o roteador Wi-Fi ligado.
- [x] Notificação apagada volta sozinha; botão "Pausar / retomar" em cada notificação.
- [x] Tela de diagnóstico: permissões, bateria, rotinas, estado do celular e teste das rotinas de dados.
- [x] Versões publicadas em Releases (link direto do APK) e aviso de versão nova dentro do app.
- [x] Visual novo azul/roxo, seguindo o tema claro/escuro do celular, com barra inferior.

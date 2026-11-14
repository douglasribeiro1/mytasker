# Pulso

App Android que avisa as **rotinas da Samsung (Modos e Rotinas)** na hora certa, para economizar bateria desligando os dados móveis (ou o que você quiser) com a tela apagada.

Não precisa de root, Shizuku nem computador.

## Por que assim

Um app comum não pode desligar os dados móveis no Android. As rotinas da Samsung podem, porque são do sistema.
O que falta a elas são gatilhos como "15 min sim, 2 min não depois que a tela apaga".

O Pulso mantém uma **notificação fixa** e troca o texto dela:
- **"Desligar"**: economia. A sua rotina desliga os dados (e o Wi-Fi, se quiser).
- **"Ativar"**: normal. A sua rotina religa.

As rotinas usam a condição **"Notificação recebida"**, com o app Pulso e a palavra.

## Configuração

O tutorial dentro do app (cartão "Como funciona") tem o passo a passo completo. Em resumo:

1. Instale o APK, permita as notificações e deixe a bateria do Pulso **sem restrições**.
2. Em Modos e Rotinas, crie duas rotinas:
   - SE notificação do Pulso com **"Desligar"** → Dados móveis **desligado**.
   - SE notificação do Pulso com **"Ativar"** → Dados móveis **ligado**.
3. Ligue o Pulso. Em Avançado, use "Enviar desligar" e "Enviar ligar" para testar.

As palavras podem ser trocadas, desde que uma não esteja dentro da outra ("Desligar" contém "ligar").

## O que ele faz

- **Ciclo padrão:** tela apaga → espera (3 min) → economia (15 min) → normal (2 min) → repete.
- **Horários:** quantos você quiser, com dias, início e fim (pode atravessar a meia-noite). Cada um pode ser:
  - economia o tempo todo, mesmo com a tela acesa;
  - ciclo com tempos próprios;
  - sem ciclo.

  Se dois horários se sobrepõem, vale o de cima.
- Acender a tela, estar no Wi-Fi ou carregando volta ao normal. As três opções são configuráveis.
- **Pausar 1 hora:** pelo app, pela notificação ou pelo botão do painel rápido.
- **Conferência:** 1 min depois de voltar ao normal, confere se os dados voltaram. Se não voltaram, reenvia o sinal e, se ainda falhar, avisa.
- **Respeita você:** se você mesmo desligou os dados, o Pulso não religa.
- **Retoma sozinho** depois de reiniciar o celular ou atualizar o app.
- **Resumo:** tempo em economia hoje e nos últimos 7 dias.
- **Histórico** de tudo o que fez, em Avançado.

O código fica na pasta `ciclo/`. O pacote continua `com.douglasribeiro.ciclodados` para instalar por cima das versões anteriores.

## Atualizações sem reinstalar

O Android só instala por cima se a chave de assinatura for a mesma e o `versionCode` for maior. O `versionCode` sobe sozinho a cada build no GitHub Actions. Para a chave, faça uma vez:

1. No GitHub, vá em Settings > Secrets and variables > Actions > New repository secret.
   Crie `KEYSTORE_PASSWORD` com uma senha forte, de 12 caracteres ou mais.
   Guarde essa senha também num gerenciador de senhas.
2. Na aba Actions, rode "Gerar chave de assinatura (rodar uma vez)".
3. Baixe o artifact `chave-mytasker`, copie o texto de `KEYSTORE_B64.txt` e crie o secret `KEYSTORE_B64` com ele.
   Guarde `release-backup.jks` fora do GitHub.
4. Apague o artifact da execução.

## Build automático

O workflow `.github/workflows/build.yml` compila a cada push na `main` e também pode ser disparado manualmente. O APK sai no artifact `pulso-release`.

## Segurança e privacidade

- Não pede acesso à internet.
- Só lê o estado da rede e dos dados móveis.
- Usa alarmes, notificação e mantém o processador acordado por alguns segundos em cada troca.
- Não lê suas notificações nem a tela.

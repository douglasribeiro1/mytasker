# MyTasker

App Android que, com a tela apagada, desliga dados móveis e/ou Wi-Fi em ciclos (por exemplo 15 minutos desligado e 2 minutos ligado para sincronizar), para economizar bateria. Usa o [Shizuku](https://shizuku.rikka.app/), então não precisa de root.

## Requisitos

- Android 8.0 ou mais novo.
- Shizuku instalado e iniciado. No Android 11 ou mais novo, ele inicia pela depuração sem fio, sem computador.

## Como usar

1. Instale o Shizuku e inicie-o.
2. Instale o APK do MyTasker.
3. Abra o MyTasker e toque em "Pedir autorização ao Shizuku".
4. Ajuste os intervalos e toque em "Salvar intervalos".
5. Ative "Ciclo ativo".
6. Nas configurações do Android, em Bateria, deixe o MyTasker em "Sem restrições".

## Como funciona

- Você escolhe no app o que o ciclo pode desligar: dados móveis, Wi-Fi ou os dois.
- Tela apagada: espera o tempo de segurança (padrão 3 minutos), para não cortar uma conversa ou um carregamento.
- Depois desliga só o que estiver ligado naquele momento. O que você deixou desligado continua desligado.
- Espera o tempo "desligado", religa o que desligou pelo tempo "ligado", e repete.
- Tela acesa: cancela o ciclo e religa na hora o que o ciclo tinha desligado.
- Os tempos usam alarmes do Android que funcionam mesmo em sono profundo (Doze).

## Atualizações sem reinstalar

O Android só instala por cima se a chave de assinatura for a mesma e o `versionCode` for maior. O `versionCode` sobe sozinho a cada build no GitHub Actions. Para a chave, faça uma vez:

1. No GitHub: Settings > Secrets and variables > Actions > New repository secret. Nome `KEYSTORE_PASSWORD`, valor uma senha forte (12 caracteres ou mais). Guarde essa senha também num gerenciador de senhas.
2. Aba Actions > "Gerar chave de assinatura (rodar uma vez)" > Run workflow.
3. Ao terminar, baixe o artifact `chave-mytasker`. Abra `KEYSTORE_B64.txt`, copie todo o texto e crie o secret `KEYSTORE_B64` com ele. Guarde `release-backup.jks` fora do GitHub (por exemplo no Google Drive), como backup.
4. Apague o artifact da execução (ícone de lixeira).
5. Rode Actions > Build APK. Desinstale o app antigo uma última vez e instale este APK. Daqui para frente, é só instalar por cima.

Se perder a chave e a senha, as próximas versões terão que ser instaladas do zero.

## Build automático no GitHub Actions

O workflow `.github/workflows/build.yml` compila o APK a cada push na `main` e também pode ser disparado manualmente. O APK aparece como artifact `mytasker-release`. Sem os secrets, o APK sai sem assinatura.

## Segurança

- O MyTasker não pede acesso à internet. Pede só permissões de estado de rede/Wi-Fi, alarmes, notificação e manter o processador acordado alguns segundos.
- O serviço que roda com privilégio de shell só executa comandos fixos (`svc data`, `cmd wifi`/`svc wifi`, `settings get global mobile_data`). Ele não recebe comandos de fora.
- O Shizuku dá ao app autorizado privilégios de shell (ADB). Autorize só o MyTasker.
- Depois de iniciar o Shizuku, desligue a depuração sem fio se não precisar dela. O Shizuku continua rodando.

## Limitações conhecidas

- Se o aparelho não aceitar os comandos, a notificação mostra "Falha ao desligar" ou "Falha ao ligar".
- Sem root, o Shizuku precisa ser iniciado de novo depois de cada reinício do celular.
- Desligar o Wi-Fi faz o celular reconectar à rede a cada ciclo, o que leva alguns segundos.

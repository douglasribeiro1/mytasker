# MyTasker

App Android que alterna os dados móveis em ciclos, por exemplo 15 minutos desligados e 2 minutos ligados, para economizar bateria. Com a tela acesa, os dados voltam na hora. Usa o [Shizuku](https://shizuku.rikka.app/), então não precisa de root.

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

- Tela apagada: desliga os dados móveis, espera o intervalo "desligado" e liga por o intervalo "ligado". Repete.
- Tela acesa: liga os dados na hora.
- Se o celular estiver no Wi-Fi, o ciclo não desliga os dados.

## Atualizações sem reinstalar

O Android só instala uma nova versão por cima da antiga se tiver a mesma chave de assinatura. Por isso:

- Use sempre o mesmo `release.jks` e o mesmo `keystore.properties`. Faça backup deles fora do GitHub. Se perder a chave, as próximas versões terão que ser instaladas do zero.
- Aumente `versionCode` em `app/build.gradle.kts` a cada versão publicada.

## Compilar

Com o Android Studio, abra a pasta do projeto. Para o APK assinado, coloque `release.jks` e `keystore.properties` na raiz do projeto e rode `Build > Generate Signed App Bundle / APK`. Ou, no terminal:

```
./gradlew assembleRelease
```

O arquivo `keystore.properties` tem este formato:

```
storeFile=release.jks
storePassword=...
keyAlias=mytasker
keyPassword=...
```

Nunca faça commit de `*.jks` ou `keystore.properties`. O `.gitignore` já ignora os dois.

## Limitações conhecidas

- O ciclo usa o comando `svc data enable|disable`. Se o seu aparelho não aceitar o comando, o app mostra "Falha no comando svc data".
- Sem root, o Shizuku precisa ser iniciado de novo depois de cada reinício do celular.
- O intervalo pode variar um pouco, porque o Android pode adiar tarefas com a tela apagada.

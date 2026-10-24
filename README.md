# NaPlaca ⚽

App Android para acompanhar **jogos de futebol ao vivo**, do Girabola à Liga dos Campeões, com alertas de golos, tabelas, marcadores e estatísticas.

Funciona **só com o GitHub e serviços gratuitos**: sem servidor, sem Firebase, sem chaves pagas. O código, a compilação (GitHub Actions) e a distribuição do APK (GitHub Releases) ficam todos no GitHub.

## Funcionalidades

| | |
|---|---|
| **Jogos** | Jogos do dia por liga, ao vivo com minuto e resultado, filtro por data (calendário), *Ao vivo*, *Minhas equipas* e *Seguidos*. Atualização automática a cada 30 s durante os jogos e *pull-to-refresh*. |
| **Detalhe do jogo** | Lances (golos, cartões, substituições), estatísticas (posse, remates, cantos…), escalações e suplentes, forma recente, partilha e atalho para as estatísticas da Bantubet. |
| **Ligas** | Classificação, calendário e resultados, melhores marcadores e assistências. A estrela escolhe que ligas aparecem em *Jogos*. |
| **Equipas** | Perfil, forma (V/E/D), próximos jogos e resultados. O coração adiciona aos favoritos. |
| **Explorar** | Rankings de jogadores por liga (golos, assistências, golos por jogo) com barras de intensidade. |
| **Bantubet** | O centro de estatísticas [statistics.bantubet.co.ao](https://statistics.bantubet.co.ao/) dentro da app. |
| **Alertas** | Notificações de golo, cartão vermelho, início, intervalo e fim, e um lembrete antes do jogo. Equipas favoritas são seguidas automaticamente. Inclui horas de silêncio e histórico de alertas. |
| **Perfil** | Perfil local (nome, ligas, tema claro/escuro) e **cópia de segurança em JSON** (exportar/importar) para mudar de telemóvel. |
| **Offline** | Os últimos dados ficam em cache e aparecem logo ao abrir, mesmo sem rede. |

## Fontes de dados (gratuitas)

- **ESPN**: API pública usada pelo site espn.com, sem chave. Fornece resultados ao vivo, lances, estatísticas, escalações, tabelas e marcadores das ligas europeias, das competições CAF/UEFA, do Brasileirão e do Mundial.
- **TheSportsDB**: chave de teste gratuita `123`. Fornece o **Girabola** (jogos, resultados e tabela). Na chave gratuita a tabela e as listas vêm reduzidas e não há lances ao minuto.
- **Bantubet Statistics**: este site não tem API pública. A app abre-o numa WebView, tal como no navegador, e não extrai dados dele.

O NaPlaca não é afiliado à ESPN, à TheSportsDB nem à Bantubet. Estas APIs não são oficiais e podem mudar. A conversão de cada fonte está isolada em `EspnMapper` e `SportsDbMapper`, e é testada com respostas reais guardadas em `app/src/test/resources`.

## Como funcionam os alertas sem servidor

O `LiveMatchWorker` (WorkManager) corre de 15 em 15 minutos. Quando há um jogo seguido a decorrer ou prestes a começar, reagenda-se **a cada minuto**. Em cada execução pede o estado atual à ESPN ou à TheSportsDB, compara-o com o último estado guardado (`MatchDiff`) e mostra **notificações locais**. Se o Android restringir a app em segundo plano, desative a otimização de bateria para o NaPlaca.

## Instalar

1. Abra [Releases](../../releases) e descarregue o `NaPlaca-x.y.z.apk` mais recente.
2. No telemóvel, permita "instalar apps desconhecidas" para o navegador ou o gestor de ficheiros.
3. Abra o APK.

Cada push também gera um APK de debug nos *artifacts* do workflow **Android CI**.

## Publicar uma versão

```bash
git tag v0.1.0
git push origin v0.1.0
```

O workflow **Release** compila, assina e publica o APK numa GitHub Release. Também o pode correr à mão em *Actions → Release → Run workflow*.

### Assinatura (recomendado)

Sem chave própria, cada release é assinada com uma chave temporária e o Android não deixa atualizar por cima da versão anterior. Para resolver isso:

```bash
keytool -genkeypair -v -keystore naplaca.jks -alias naplaca -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 naplaca.jks   # copie o resultado
```

Em *Settings → Secrets and variables → Actions*, crie:

| Secret | Valor |
|---|---|
| `NAPLACA_KEYSTORE_BASE64` | o resultado do `base64` |
| `NAPLACA_KEYSTORE_PASSWORD` | palavra-passe da keystore |
| `NAPLACA_KEY_ALIAS` | `naplaca` |
| `NAPLACA_KEY_PASSWORD` | palavra-passe da chave |

Guarde o `naplaca.jks` num sítio seguro. **Nunca** o adicione ao repositório (o `.gitignore` já o exclui).

## Desenvolvimento

Requisitos: JDK 17 e Android SDK 35 (ou Android Studio).

```bash
./gradlew testDebugUnitTest   # testes unitários (mappers, alertas, datas)
./gradlew lintDebug
./gradlew assembleDebug       # app/build/outputs/apk/debug/
```

Estrutura (`app/src/main/java/ao/naplaca/app`):

```
domain/          modelos (Match, StandingRow, Leader…) e catálogo de ligas
data/remote/     Retrofit (EspnApi, SportsDbApi) e mappers JSON → domínio
data/local/      Room (favoritos, jogos seguidos, cache, histórico) e DataStore (perfil)
data/repository/ FootballRepository (cache-first) e FavoritesRepository
alerts/          LiveMatchWorker, MatchDiff, Notifier, AlertScheduler
backup/          exportar/importar perfil em JSON
ui/              Jetpack Compose + Material 3 (ecrãs em ui/screens)
```

Para adicionar uma liga, junte uma entrada a `domain/Leagues.kt` (o *slug* da ESPN, como `ned.1`, ou o id da TheSportsDB).

## Próximos passos

- Widget no ecrã inicial com os jogos da equipa favorita.
- Mais desportos: basquetebol (NBA, BAI) e outros.
- Distribuição pelo F-Droid ou pelo Obtainium (gratuitos).

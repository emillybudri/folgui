# Folgui

App Android para quem trabalha em escala: mostra de um jeito simples quais dias são de trabalho e de folga, ajuda a marcar compromissos sem cair no trabalho nem no sono e encontra as folgas em comum com a família e os amigos.

## O que ele faz

- **Escalas:** semanal, 12x36, 24x48, 2x2, 4x2, 5x1, 6x1 ou um ciclo personalizado, com férias, trocas de plantão e folgas extras.
- **Agenda em quatro visões:** mês (com o turno em cada dia), semana (hora a hora), lista e ano.
- **Horário de sono:** nada é sugerido nesse trecho, e marcar algo ali pede confirmação.
- **Compromissos:** criar, editar, remarcar com sugestões de horários livres e excluir, com lembrete. Tudo que é marcado no Folgui vai para a Google Agenda e continua igual lá. Os criados na Google Agenda aparecem no app e abrem nela para edição.
- **Conta (Google ou e-mail):** o login vem antes do cadastro e guarda escala, atividades, pessoas e eventos na conta. Tudo volta ao entrar em outro celular.
- **Segundo emprego ou faculdade:** cada atividade tem nome e escala própria e conta nos horários ocupados.
- **Assistente com IA (Gemini):** fica na barra do topo e aceita texto ou áudio. Mostra o que entendeu, confere o pedido contra a escala e só altera algo depois da confirmação.

## Requisitos da atividade

| Requisito | Onde está |
| --- | --- |
| Kotlin e Jetpack Compose | Todo o app, sem layouts XML |
| Navegação entre telas | `NavHost` e `NavController` em [FolguiApp.kt](app/src/main/java/com/example/folgui/FolguiApp.kt) |
| DataStore | Perfil, escala e preferências em [FolguiApp.kt](app/src/main/java/com/example/folgui/FolguiApp.kt) |
| SQLite | [BancoHelper.kt](app/src/main/java/com/example/folgui/BancoHelper.kt) e [FolguiRepository.kt](app/src/main/java/com/example/folgui/FolguiRepository.kt) |
| API REST | BrasilAPI (feriados) e Open-Meteo (previsão do tempo) em [ApiRepository.kt](app/src/main/java/com/example/folgui/ApiRepository.kt) |
| Notificações locais | [AlarmScheduler.kt](app/src/main/java/com/example/folgui/AlarmScheduler.kt), [ReminderReceiver.kt](app/src/main/java/com/example/folgui/ReminderReceiver.kt) e [NotificationHelper.kt](app/src/main/java/com/example/folgui/NotificationHelper.kt) |

## Demonstração

[Vídeo do app funcionando (56 s)](docs/demo.mp4), gravado com dados fictícios. Música: Uplifting Corporate, de Soundrider/Dope (CC BY 3.0).

## Design

Cada cor tem um só significado, e as telas usam ícones e rótulos curtos. As regras estão em [docs/DESIGN.md](docs/DESIGN.md).

## Como rodar

1. Copie `.env.example` para `.env` e preencha a chave da [API do Gemini](https://aistudio.google.com/apikey).
2. Abra o projeto no Android Studio, ou rode:

   ```
   ./gradlew :app:assembleDebug
   ```

Testes: `./gradlew :app:testDebugUnitTest`

Para o login, crie um projeto no Firebase com Authentication (Google e e-mail) e Firestore, publique as regras com `firebase deploy --only firestore` e coloque o `google-services.json` em `app/`. Sem esse arquivo, o app abre sem pedir login.

O `GEMINI_MODEL` é o modelo principal. Se ele estiver lotado ou sem cota, o app usa outros modelos automaticamente.

## Sobre a chave da API

O `.env` e os APKs ficam fora do Git, porque a chave vai embutida no APK e pode ser extraída dele. Para distribuir o app a outras pessoas, o certo é colocar as chamadas ao Gemini atrás de um servidor próprio.

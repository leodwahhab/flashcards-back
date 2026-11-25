# Flashcards API

API REST (Spring Boot) de um aplicativo de flashcards com **repetição espaçada no estilo Anki**: notas que geram
cards, baralhos aninhados, agendador SM-2 com passos de aprendizado, fila de estudo com limites diários, desfazer,
estatísticas, importação/exportação (CSV e backup JSON) e mídia.

## Avisos importantes

- **Rotas legadas removidas.** `/flashcards`, `/groups`, `/sessions` e `/study/flashcards*` **não existem mais**. O
  modelo antigo (flashcard + grupo + nível de domínio) foi substituído por note/card, baralho e estado SM-2. A tabela de
  equivalências está em [`docs/fluxos-de-negocio.md`](docs/fluxos-de-negocio.md#8-migração-dos-dados-legados-v4).
- **A migração V4 é destrutiva.** Ela converte os dados legados e **remove** as tabelas `flashcard`, `flashcard_group`,
  `flashcard_group_assignment` e `study_session` (o histórico de sessões não é migrável e é perdido). **Faça backup do
  banco antes de subir esta versão sobre um banco existente.**
- **`JWT_SECRET` é obrigatório.** Sem ele (ou com o segredo de exemplo que já esteve no repositório) a aplicação não
  inicia. Veja [como gerar](#variáveis-de-ambiente).
- A `V3__insert_admin.sql` é uma migration **local, fora do controle de versão** (apenas desenvolvimento); nada depende
  dela.

## Funcionalidades

- **Autenticação** JWT stateless: cadastro, login (com limite de tentativas), troca de senha e exclusão de conta
  (apaga todos os dados e os arquivos de mídia).
- **Baralhos aninhados** (`Pai::Filho`, até 8 níveis) e **opções de baralho** reutilizáveis (limites diários, passos,
  facilidade, intervalos, leech…).
- **Notes** `BASIC`, `BASIC_REVERSED`, `CLOZE` e `TYPE_IN`, com geração automática de cards, **detecção de duplicados**
  e **tags** (renomear/mesclar).
- **Agendador SM-2** (estilo Anki) com estados `NEW → LEARNING → REVIEW → RELEARNING`, lapsos, *leech*, suspender,
  enterrar irmãos e *reset*.
- **Fila de estudo** ordenada, com limites por baralho, **dia lógico** (vira às 4h, fuso configurável), previsão de
  intervalos por botão, controle de concorrência (`version`) e **desfazer**.
- **Busca de cards** com filtros combináveis e **ações em lote** (suspender, bandeira, mover, tags, reset, excluir).
- **Estatísticas**: hoje, panorama, previsão, mapa de calor, retenção e sequência de estudo.
- **Mídia** (imagem/áudio, tipo validado pelo conteúdo), **importação CSV**, **exportação CSV** e **backup/restauração
  JSON**.
- Erros em `application/problem+json`, CORS configurável e Swagger UI.

## Stack

- **Java 21**, **Spring Boot 3.5** (Web, Data JPA, Security, Validation)
- **PostgreSQL** + **Flyway** (migrations) — H2 apenas nos testes de API
- **JJWT** (JWT HS256), **springdoc-openapi** (Swagger UI), **Apache Commons CSV**, **Lombok**
- Testes: JUnit 5, Spring Boot Test/MockMvc, Spring Security Test, Testcontainers (PostgreSQL)

## Como rodar

Pré-requisitos: JDK 21 e Docker (para o PostgreSQL local).

```bash
# 1. Banco de dados (PostgreSQL definido em compose.yaml: db "flashcards", usuário/senha "postgres")
docker compose up -d

# 2. Segredo do JWT (obrigatório): Base64 de pelo menos 32 bytes aleatórios
export JWT_SECRET="$(openssl rand -base64 48)"

# 3. Aplicação (o Flyway cria/migra o esquema na inicialização)
./mvnw spring-boot:run
```

No PowerShell, sem `openssl`:

```powershell
$b = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
$env:JWT_SECRET = [Convert]::ToBase64String($b)
.\mvnw.cmd spring-boot:run
```

Alternativa: com `DOCKER_COMPOSE_ENABLED=true`, a integração `spring-boot-docker-compose` sobe o `compose.yaml`
automaticamente ao iniciar a aplicação. O `compose.yaml` lê `DATABASE_USER`, `DATABASE_PASSWORD` e usa `DATABASE_URL`
como **nome do banco** — não defina `DATABASE_URL` com uma URL JDBC no mesmo shell em que roda `docker compose`.

Depois de subir:

| O quê | Onde |
|---|---|
| API | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI (JSON) | `http://localhost:8080/v3/api-docs` |

O Swagger/OpenAPI é **público por padrão**; em produção defina `app.docs.public=false`.

### Primeiros passos (curl)

```bash
# cadastro (já devolve o token)
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"minhasenha"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

# baralho padrão
DECK=$(curl -s localhost:8080/api/v1/decks -H "Authorization: Bearer $TOKEN" | sed 's/.*"id":\([0-9]*\).*/\1/')

# criar uma nota
curl -s -X POST localhost:8080/api/v1/notes -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"noteType\":\"BASIC\",\"deckId\":$DECK,\"front\":\"capital da França\",\"back\":\"Paris\"}"

# fila de estudo e resposta (rating 1..4 = errei, difícil, bom, fácil)
curl -s localhost:8080/api/v1/study/queue -H "Authorization: Bearer $TOKEN"
curl -s -X POST localhost:8080/api/v1/study/cards/1/answer -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"rating":3,"timeMs":4200}'
```

## Configuração

### Variáveis de ambiente

Todas são lidas em `src/main/resources/application.yml`.

| Variável | Padrão | Finalidade |
|---|---|---|
| `JWT_SECRET` | **obrigatória** | Segredo HMAC do JWT: Base64 com **≥ 32 bytes** decodificados. Gere com `openssl rand -base64 48`. |
| `JWT_EXPIRATION` | `86400000` (24 h) | Validade do token em ms |
| `JWT_ALLOW_INSECURE_DEFAULT` | `false` | Aceita o segredo público de exemplo (só para dev/testes; nunca em produção) |
| `DATABASE_URL` | `postgresql://localhost:5432/flashcards` | URL **sem** o prefixo `jdbc:` (a aplicação o acrescenta) |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `postgres` / `postgres` | Credenciais do banco |
| `DATABASE_DRIVER` | `org.postgresql.Driver` | Driver JDBC |
| `HIBERNATE_DIALECT` | `org.hibernate.dialect.PostgreSQLDialect` | Dialeto do Hibernate |
| `SHOW_SQL` | `false` | Loga o SQL gerado |
| `FLYWAY_ENABLED` | `true` | Roda as migrations na inicialização |
| `DOCKER_COMPOSE_ENABLED` | `false` | Sobe o `compose.yaml` automaticamente |
| `APP_STUDY_ZONE` | `UTC` | Fuso que delimita o dia lógico (ex.: `America/Sao_Paulo`) |
| `APP_STUDY_DAY_START_HOUR` | `4` | Hora (0..23) em que o dia lógico começa |
| `APP_MEDIA_DIR` | `./data/media` | Diretório dos arquivos de mídia (`<dir>/<userId>/<uuid>.<ext>`) |
| `APP_MEDIA_MAX_BYTES` | `10485760` (10 MB) | Tamanho máximo de upload de mídia **e** do CSV de importação |
| `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` | — | Opcionais, devem vir juntas. Criam o usuário inicial na subida (com baralho padrão) se o e-mail ainda não existir; nunca alteram um usuário existente. Não commite a senha. |

### Propriedades `app.*`

Além do `application.yml`, as propriedades abaixo podem ser definidas em `application.yml`/`-D`/variável de ambiente
equivalente (`app.cors.allowed-origins` → `APP_CORS_ALLOWED_ORIGINS`).

| Propriedade | Padrão | Descrição |
|---|---|---|
| `app.study.zone` | `UTC` | Fuso do dia lógico de estudo |
| `app.study.day-start-hour` | `4` | Início do dia lógico (limites diários, vencimento das revisões, estatísticas) |
| `app.media.dir` | `./data/media` | Onde os arquivos de mídia são gravados |
| `app.media.max-bytes` | `10485760` | Tamanho máximo por arquivo (upload e CSV) |
| `app.cors.allowed-origins` | `http://localhost:3000,http://localhost:5173` | Origens permitidas (lista separada por vírgulas) |
| `app.docs.public` | `true` | Swagger UI/OpenAPI sem autenticação; use `false` em produção |
| `app.login.max-failures` | `10` | Falhas de login permitidas na janela, por e-mail + IP |
| `app.login.window-minutes` | `15` | Duração da janela do limite de login (excedido → 429 com `Retry-After`) |

### CORS

Permitidas as origens de `app.cors.allowed-origins`, os métodos `GET, POST, PUT, PATCH, DELETE, OPTIONS` e os cabeçalhos
`Authorization, Content-Type, Accept`; `Retry-After` e `Content-Disposition` são expostos. Sem credenciais (a API usa
Bearer, não cookies). Para um front-end em outra origem, inclua-a em `app.cors.allowed-origins`.

### Observações de produção

- Defina `JWT_SECRET` forte e `app.docs.public=false`.
- O limite de tentativas de login é **em memória e por instância**; com várias réplicas use um limitador no proxy ou
  um repositório compartilhado. Atrás de proxy, configure `server.forward-headers-strategy` para o IP correto.
- Os arquivos de mídia ficam em disco local: persista/replique `app.media.dir` e inclua-o nos seus backups (o backup
  JSON da API **não** leva os arquivos).

## Testes

```bash
./mvnw test
```

- Os testes de **API** (MockMvc) rodam em **H2 em memória** (`src/test/resources/application.yml`: `ddl-auto=create-drop`,
  Flyway desligado) com relógio controlável (`MutableClock`) — não precisam de Docker.
- Os testes de **migração** (`MigrationPostgresTest`) e de **exclusão de conta em cascata**
  (`AccountDeletionCascadePostgresTest`) usam **PostgreSQL real via Testcontainers** e exigem **Docker**; sem Docker eles
  são ignorados (`disabledWithoutDocker`).

## Estrutura do projeto

```
com.example.flashcards_api
├── common/        Clock/auditoria, AppProperties, tratamento de erros (problem+json), paginação, Texts
├── auth/          cadastro, login, JWT, segurança/CORS, rate limit, conta, OpenAPI
├── deck/          baralhos, opções de baralho, contagens diárias, padrões do usuário
├── tag/           tags (renomear, mesclar, excluir)
├── note/          notes, geração de cards, duplicados
├── card/          cards, busca, ações em lote, cloze e renderização
├── scheduler/     Sm2Scheduler (puro), dia lógico, previsão de botões
├── review/        review_log e Rating
├── study/         fila de estudo, resposta e desfazer
├── stats/         estatísticas
├── media/         upload/download de imagem e áudio
└── importexport/  CSV (import/export) e backup JSON
src/main/resources/db/migration   V1, V2 (legado), V4 (remodelagem), V5 (mídia)
```

Cada módulo segue `Controller → Service → Repository` com DTOs em `record`. Todo acesso é filtrado por usuário e um
recurso de outro usuário responde 404.

## Documentação

- [`docs/arquitetura.md`](docs/arquitetura.md) — modelo de domínio, esquema do banco, SM-2, fila, segurança, decisões e desvios.
- [`docs/fluxos-de-negocio.md`](docs/fluxos-de-negocio.md) — fluxos de negócio com diagramas, regras, erros e glossário.
- [`docs/api.md`](docs/api.md) — referência de todos os endpoints (e Swagger UI em `/swagger-ui.html`).

## Roadmap

- Sincronização *offline-first* para clientes móveis (USN / `updated_at` por entidade, resolução de conflitos).
- FSRS como segundo algoritmo, selecionável por baralho.
- Bônus de atraso e *fuzz* no SM-2.
- Tipos de nota customizados e templates (HTML/CSS); importação/exportação `.apkg`.
- *Refresh token* e revogação de tokens.
- Recuperação de senha e verificação de e-mail.
- Baralhos compartilhados (e papéis/organizações).
- Backup assíncrono (incluindo mídia).
- Armazenamento de mídia em S3 (ou compatível).
- Rate limit de login distribuído.

## Deploy com Docker (VM única)

`compose.prod.yaml` sobe Postgres, a API e o Caddy (HTTPS automático via Let's Encrypt). Só as portas 80/443 ficam expostas.

```bash
cp .env.prod.example .env.prod     # preencha DOMAIN, senhas e JWT_SECRET; o arquivo é ignorado pelo git
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
```

O `DOMAIN` precisa apontar (registro A) para o IP da VM antes da primeira subida. Dados em volumes Docker (`pgdata`, `media`): faça backup deles.

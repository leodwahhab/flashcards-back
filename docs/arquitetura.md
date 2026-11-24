# Arquitetura e contrato da remodelagem (estilo Anki)

Documento de referência da remodelagem. Descreve o modelo de domínio, o esquema do banco, as regras do
agendador SM-2, a fila de estudo e os endpoints. Os módulos foram implementados em paralelo a partir deste contrato.

## 1. Stack e convenções

- Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL (H2 nos testes de API), JWT (jjwt).
- Pacote raiz `com.example.flashcards_api`, organizado **por funcionalidade** (`auth`, `deck`, `note`, `card`, ...).
- Cada módulo tem: entidade(s), repositório, serviço (`@Transactional`), controller, DTOs (`record`).
- Escopo por usuário: toda entidade de domínio tem `userId` (coluna `user_id`, FK `ON DELETE CASCADE`);
  todo acesso é filtrado por `userId` (`findByIdAndUserId`). Recurso de outro usuário responde **404**.
- Datas e horas: `java.time.Instant` / `timestamptz` (UTC). Nunca `LocalDateTime`.
  O tempo vem sempre de um `java.time.Clock` injetado (bean em `common`), nunca de `Instant.now()` direto.
- Erros: `application/problem+json` (RFC 7807, `ProblemDetail`) com lista `errors` para erros de campo.
  Exceções de domínio em `common`: `NotFoundException` (404), `ConflictException` (409),
  `BusinessRuleException` (422). Validação de bean (`@Valid`) também responde 422.
- Paginação: parâmetros `page` (0-based) e `size` (padrão 20, máximo 100); resposta `PageResponse<T>`
  (`items`, `page`, `size`, `totalElements`, `totalPages`). Ordenação só por campos permitidos (whitelist).
- Documentação: Javadoc em português em toda classe pública e método público de serviço; comentários só onde o
  "porquê" não é óbvio. DTOs documentados com `@Schema` do springdoc quando agregar valor.
- Prefixo da API: `/api/v1` (o contrato mudou de forma incompatível com a versão anterior; ver seção 8).
- Configuração própria em `@ConfigurationProperties("app")` (`AppProperties`):
  `app.study.zone` (padrão `UTC`), `app.study.day-start-hour` (padrão `4`),
  `app.media.dir` (padrão `./data/media`), `app.media.max-bytes` (padrão 10 MB).

## 2. Modelo de domínio

```
user ─1:N─ deck (parent_id, options_id) ─N:1─ deck_options
user ─1:N─ note (note_type, front, back, image_url) ─N:M─ tag
note ─1:N─ card (deck_id, ordinal, estado de agendamento)
card ──────< review_log (um registro por resposta; base de estatísticas e do "desfazer")
```

- **Note** guarda o conteúdo. **Card** é o que se estuda e guarda o estado de agendamento.
  Um note gera 1..N cards conforme o tipo.
- Tipos de nota (enum fixo `NoteType`; tipos customizados ficam como melhoria futura):

| Tipo | Campos usados | Cards gerados | Pergunta / Resposta |
|---|---|---|---|
| `BASIC` | front, back | 1 (ordinal 0) | front / back |
| `BASIC_REVERSED` | front, back | 2 (ordinal 0 e 1) | 0: front→back, 1: back→front |
| `CLOZE` | front (texto com `{{c1::resp}}`, `{{c2::resp::dica}}`), back (extra, opcional) | 1 por número de cloze distinto (ordinal = n-1) | pergunta oculta o cloze n (`[...]` ou `[dica]`), resposta o revela; extra aparece na resposta |
| `TYPE_IN` | front, back | 1 | front / back; o cliente pede a digitação e compara |

- `image_url` é opcional em qualquer tipo. Um note precisa de `front` não vazio **ou** `image_url`.
- Tags: texto livre, normalizado (trim, minúsculas, espaços viram `-`, máx. 100 caracteres), único por usuário.
- Baralhos aninhados: `parent_id`; caminho completo `Pai::Filho`; profundidade máxima 8; nome único entre irmãos
  (sem diferenciar maiúsculas). Um card pertence a **um** baralho.
- Opções de baralho (`deck_options`): conjunto nomeado reutilizável; todo baralho aponta para um.

## 3. Esquema do banco (migration `V4__anki_remodel.sql`)

A V3 local (`insert_admin`) não faz parte do repositório; a V4 não depende dela.

Convenções: `BIGSERIAL` como PK, `timestamptz`, `ON DELETE CASCADE` nas FKs de `user_id`.

**`"user"`**: converter `created_at`/`updated_at` para `timestamptz`; normalizar `email` para `lower(trim(email))`.

**`deck_options`**: `id`, `user_id`, `name`, `is_default boolean`,
`new_per_day int=20`, `reviews_per_day int=200`,
`learning_steps varchar(100)='1 10'` (minutos, separados por espaço), `relearning_steps varchar(100)='10'`,
`graduating_interval_days int=1`, `easy_interval_days int=4`,
`starting_ease int=2500` (permil), `easy_bonus int=130`, `hard_interval int=120`,
`interval_modifier int=100`, `lapse_new_interval int=0` (percentual do intervalo mantido após lapso),
`minimum_interval int=1`, `maximum_interval int=36500`, `leech_threshold int=8`,
`bury_siblings boolean=true`, `created_at`, `updated_at`. Único `(user_id, lower(name))`.

**`deck`**: `id`, `user_id`, `parent_id` (FK `deck`, cascade), `name varchar(255)`, `options_id` (FK `deck_options`, restrict),
`created_at`, `updated_at`. Índice único `(user_id, COALESCE(parent_id,0), lower(name))`.

**`note`**: `id`, `user_id`, `note_type varchar(20)` (CHECK nos 4 valores), `front text`, `back text`,
`image_url varchar(2048)`, `guid varchar(36)` único, `dup_key varchar(64)` (hash do `front` normalizado: sem HTML,
minúsculas, espaços colapsados), `created_at`, `updated_at`. CHECK `front` não vazio ou `image_url` não nulo.
Índice `(user_id, note_type, dup_key)`.

**`tag`**: `id`, `user_id`, `name varchar(100)`; único `(user_id, name)`.
**`note_tag`**: `(note_id, tag_id)` PK, ambas FKs cascade, índice em `tag_id`.

**`card`**: `id`, `user_id`, `note_id` (cascade), `deck_id` (cascade), `ordinal int`,
`state varchar(12)` (`NEW|LEARNING|REVIEW|RELEARNING`, CHECK), `position bigint` (ordem de introdução dos novos),
`due_at timestamptz not null`, `interval_days int=0`, `ease_permille int=2500`, `reps int=0`, `lapses int=0`,
`learning_step int=0`, `last_review_at timestamptz null`, `suspended boolean=false`, `buried_until timestamptz null`,
`flag smallint=0` (0..7), `version bigint` (`@Version`), `created_at`, `updated_at`.
Único `(note_id, ordinal)`. Índices `(user_id, deck_id, state, due_at)` e `(note_id)`.

**`review_log`**: `id`, `user_id` (cascade), `card_id bigint` (**sem FK**: o histórico sobrevive à exclusão do card),
`deck_id bigint`, `rating smallint` (1..4), `state_before`, `state_after`, `interval_before`, `interval_after`,
`ease_before`, `ease_after`, `due_before`, `due_after`, `reps_before`, `lapses_before`, `lapses_after`, `learning_step_before`,
`last_review_before`, `time_ms int`, `reviewed_at timestamptz`. As colunas `*_before` permitem desfazer.
Índices `(user_id, reviewed_at)` e `(card_id)`.

### Migração dos dados legados

Na ordem:
1. `deck_options` padrão (`is_default=true`, nome `Padrão`) e baralho `Padrão` para **todo** usuário.
2. Cada `flashcard_group` vira um `deck` (raiz) com as opções padrão do usuário.
3. Cada `flashcard` vira 1 `note` `BASIC` (`front = coalesce(text_content,'')`, `back = answer`, `image_url`,
   `guid = gen_random_uuid()`) + 1 `card` ordinal 0. Cards sem `last_shown_date` ficam `NEW`; os demais ficam `REVIEW`
   com `interval_days` 1 / 6 / 21 conforme `dominance_level` low / medium / high, `reps = attempts_correct + attempts_incorrect`,
   `last_review_at = last_shown_date`, `due_at = now()`.
4. Um card que estava em vários grupos fica no baralho do grupo de **menor id**; os outros grupos viram tags
   `grupo-<nome normalizado>` do note. Card sem grupo vai para o baralho `Padrão`.
5. Remover `flashcard_group_assignment`, `flashcard_group`, `flashcard` e `study_session`
   (`study_session` guardava só contagens enviadas pelo cliente e não é migrável para `review_log`).
   Usar coluna temporária `legacy_flashcard_id` em `note` para o mapeamento e removê-la ao fim.

## 4. Agendador SM-2 (`scheduler`)

Classe pura e sem I/O: `Sm2Scheduler.schedule(card, rating, options, now, dayStartFn) -> SchedulingResult` e
`preview(card, options, now, ...) -> Map<Rating, ...>` (intervalos mostrados em cada botão).
Notas: `Rating` = `AGAIN(1)`, `HARD(2)`, `GOOD(3)`, `EASY(4)`. Intervalos em dias inteiros. `ease` em permil (2500 = 250%).
`mod = interval_modifier/100`. Sem fuzz (determinístico, facilita teste).

**Fim do dia lógico**: o dia começa em `day_start_hour` no fuso `app.study.zone`. Cards em `REVIEW` vencem no
início do dia lógico de `hoje + intervalo`; cards em `LEARNING`/`RELEARNING` vencem no instante exato (`now + passo`).

| Estado | AGAIN | HARD | GOOD | EASY |
|---|---|---|---|---|
| `NEW` | vai a `LEARNING` passo 0, vence em `passo[0]` | `LEARNING` passo 0, vence em `passo[0]` (se houver 2+ passos, média de `passo[0]` e `passo[1]`; se 1 passo, `passo[0]*1,5`) | próximo passo, ou gradua se já era o último | gradua com `easy_interval_days` |
| `LEARNING` (passo i) | passo 0 | repete o passo i | passo i+1, ou gradua | gradua com `easy_interval_days` |
| `REVIEW` | lapso (abaixo) | `max(interval+1, round(interval*hard_interval/100*mod))`, ease −150 (mín. 1300) | `max(hard'+1, round(interval*ease/1000*mod))` | `max(good'+1, round(interval*ease/1000*easy_bonus/100*mod))`, ease +150 |
| `RELEARNING` (passo i) | passo 0 | repete o passo i | passo i+1, ou volta a `REVIEW` | volta a `REVIEW` |

- **Graduar** = `state=REVIEW`, `interval = graduating_interval_days` (ou `easy_interval_days` no EASY),
  `ease = starting_ease` na primeira graduação, `learning_step = 0`.
- **Lapso** (AGAIN em `REVIEW`): `lapses+1`, `ease = max(1300, ease−200)`,
  `interval = max(minimum_interval, round(interval*lapse_new_interval/100))`; se houver passos de reaprendizado, vai a
  `RELEARNING` passo 0 vencendo em `relearning_steps[0]`; senão permanece `REVIEW` com o novo intervalo.
  Se `lapses >= leech_threshold`: suspender o card e adicionar a tag `leech` ao note.
- Todo intervalo é limitado a `[minimum_interval, maximum_interval]`. Toda resposta incrementa `reps` e define `last_review_at`.
- **Fora do escopo (melhorias)**: bônus por atraso, fuzz, FSRS.

## 5. Fila de estudo (`study`)

Dia lógico = `[início, início+24h)` conforme seção 4. Para cada card **usa-se a opção do próprio baralho do card**.
Um card é elegível se `suspended=false` e (`buried_until` nulo ou `<= now`).

Ordem de entrega: (1) `LEARNING`/`RELEARNING` com `due_at <= now`, por `due_at`; (2) `REVIEW` com `due_at <` início do próximo
dia lógico, por `due_at`, respeitando `reviews_per_day − revisões já feitas hoje no baralho`;
(3) `NEW` por `position`, respeitando `new_per_day − novos já introduzidos hoje no baralho`
(introduzido hoje = `review_log` com `state_before=NEW` no dia lógico). `deckId` opcional inclui subbaralhos.

- **Enterrar irmãos**: após responder, se `bury_siblings`, os outros cards do mesmo note ficam com `buried_until` = início do
  próximo dia lógico (apenas os que não estão em `LEARNING`/`RELEARNING`).
- **Concorrência**: `card.version` (`@Version`); conflito responde 409.
- **Desfazer**: `POST /study/undo` reverte a última resposta do usuário (restaura o card a partir das colunas `*_before`
  do `review_log`, remove o log, remove o enterramento dos irmãos e a tag `leech`/suspensão se foram causados por ela).

## 6. Endpoints (`/api/v1`)

Todos exigem JWT, exceto `auth/register`, `auth/login` e a documentação.

- **auth**: `POST /auth/register`, `POST /auth/login`, `POST /auth/change-password`, `DELETE /account` (corpo com senha).
- **decks**: `GET /decks` (árvore com contagens `new/learning/review` de hoje), `POST /decks`, `GET /decks/{id}`,
  `PATCH /decks/{id}` (`name`, `parentId`, `optionsId`), `DELETE /decks/{id}` (remove subbaralhos e cards; notes sem cards são removidos).
- **deck-options**: `GET /deck-options`, `POST /deck-options`, `GET|PUT|DELETE /deck-options/{id}` (não remove padrão nem em uso).
- **tags**: `GET /tags` (com contagem), `PATCH /tags/{id}` (renomear; se já existir, mescla), `DELETE /tags/{id}`.
- **notes**: `POST /notes` (`noteType`, `deckId`, `front`, `back`, `imageUrl`, `tags[]`, `allowDuplicate`; 409 se duplicado),
  `GET /notes/{id}`, `PATCH /notes/{id}` (regerar cards de cloze), `DELETE /notes/{id}`, `GET /notes/{id}/cards`, `GET /notes/duplicates`.
- **cards**: `POST /cards/search` (texto, `deckId`+subbaralhos, tags, estado, suspenso, flag, vencimento, lapsos; paginado, ordenável),
  `GET /cards/{id}`, `PATCH /cards/{id}` (`suspended`, `flag`, `deckId`), `POST /cards/bulk` (ação sobre `ids[]`:
  `SUSPEND`, `UNSUSPEND`, `FLAG`, `MOVE`, `ADD_TAG`, `REMOVE_TAG`, `RESET` (volta a `NEW`), `DELETE`).
- **study**: `GET /study/queue?deckId=&limit=` (contagens + cards com pergunta/resposta renderizadas e intervalos previstos por botão),
  `POST /study/cards/{id}/answer` (`rating`, `timeMs`), `POST /study/undo`.
- **stats**: `GET /stats/today`, `/stats/overview?deckId=`, `/stats/forecast?days=30`, `/stats/heatmap?from=&to=`,
  `/stats/retention?days=30`, `/stats/streak`.
- **media**: `POST /media` (multipart; imagem/áudio; tipos e tamanho validados), `GET /media/{id}`, `DELETE /media/{id}`.
- **import/export**: `POST /import/csv` (multipart: `file`, `deckId`, `noteType`, `delimiter`),
  `GET /export/csv?deckId=`, `GET /export/backup` (JSON completo do usuário).

## 7. Melhorias futuras (fora do escopo desta entrega)

- Sincronização offline-first para clientes móveis (USN / `updated_at` por entidade, resolução de conflitos).
- FSRS como segundo algoritmo, selecionável por baralho; bônus de atraso e fuzz no SM-2.
- Tipos de nota customizados e templates HTML/CSS; importação/exportação `.apkg`.
- Refresh token e revogação, recuperação de senha e verificação de e-mail (exigem infraestrutura de e-mail), rate limit no login.
- Baralhos compartilhados, modo multiusuário/organização, papéis (admin).

## 8. Incompatibilidades com a versão anterior

Os endpoints `/flashcards`, `/groups`, `/sessions` e `/study/flashcards*` foram removidos. Equivalências:
flashcard → note + card, grupo → deck, `DominanceLevel` → estado SM-2, `StudySession` → `review_log`.

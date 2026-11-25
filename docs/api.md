# Referência da API

Referência de todos os endpoints. Para o comportamento (regras, fluxos, diagramas) veja
[`fluxos-de-negocio.md`](fluxos-de-negocio.md); para a arquitetura, [`arquitetura.md`](arquitetura.md).

- **Base**: `/api/v1`. Corpo e resposta em JSON (`application/json`), exceto upload (multipart) e downloads.
- **Documentação interativa**: Swagger UI em `/swagger-ui.html` e OpenAPI em `/v3/api-docs`. São **públicos por
  padrão** (`app.docs.public=true`); em produção use `app.docs.public=false` para exigir autenticação.
- **Autenticação**: `Authorization: Bearer <token>` (JWT obtido em `/auth/register` ou `/auth/login`). Na coluna
  "Auth": **pública** = sem token; **JWT** = token obrigatório.
- **Escopo**: cada recurso é do usuário do token; recurso de outro usuário responde **404**.
- **Datas**: ISO-8601 em UTC (`2026-11-24T12:00:00Z`). Campos `Instant` nos corpos seguem o mesmo formato.
- **Campos omitidos**: onde a tabela diz "opcional", o campo pode ser omitido ou `null`.
- Todo endpoint JSON autenticado pode responder, além dos erros listados: **401** (token ausente/inválido) e **500**.
  Erros de corpo malformado/tipo de parâmetro inválido são **400**; falhas de validação são **422**.

## Formato de erro (`application/problem+json`)

Todos os erros seguem a RFC 7807. Erros de validação trazem a lista `errors`:

```json
{
  "type": "about:blank",
  "title": "Erro de validação",
  "status": 422,
  "detail": "Um ou mais campos são inválidos",
  "errors": [
    { "field": "password", "message": "tamanho deve ser entre 6 e 72" }
  ]
}
```

Regra de negócio violada:

```json
{ "type": "about:blank", "title": "Regra de negócio violada", "status": 422,
  "detail": "Profundidade máxima de baralhos é 8" }
```

Nota duplicada (409, com o id do note existente):

```json
{ "type": "about:blank", "title": "Conflito", "status": 409,
  "detail": "Já existe uma nota com a mesma frente (id 12)", "existingNoteId": 12 }
```

Importação CSV inválida (422, `errors` por linha em vez de por campo):

```json
{ "type": "about:blank", "title": "Importação inválida", "status": 422,
  "detail": "O arquivo tem 1 linha(s) inválida(s); nada foi importado",
  "errors": [ { "row": 3, "message": "A frente (front) está vazia" } ] }
```

Não autenticado (401): `title` "Não autenticado", `detail` "Token ausente, inválido ou expirado". Excesso de tentativas
de login (429): cabeçalho `Retry-After` (segundos). A mensagem exata dos textos pode mudar; programe contra `status` e,
nos erros de campo, `errors[].field`.

| Status | Quando |
|---|---|
| 400 | Corpo ausente/JSON malformado; tipo de parâmetro inválido (ex. `id` não numérico); enum/rating desconhecido |
| 401 | Token ausente, inválido ou expirado; credenciais de login incorretas |
| 404 | Recurso inexistente **ou de outro usuário** |
| 409 | Duplicidade (e-mail, nome entre irmãos, nota duplicada), versão desatualizada, integridade, restauração em conta não vazia |
| 413 | Upload/CSV acima de `app.media.max-bytes` |
| 422 | Validação de campos e violação de regra de negócio |
| 429 | Limite de tentativas de login |
| 500 | Erro inesperado (sem detalhes internos) |

## Paginação

Onde há paginação (`POST /cards/search`): `page` (base 0, padrão 0), `size` (1..100, padrão 20) e `sort`
(`campo[,asc|desc]`, só campos permitidos). Fora das faixas → 422. Resposta:

```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
```

---

## Índice

1. [Auth e conta](#1-auth-e-conta) — 4 endpoints
2. [Baralhos](#2-baralhos-decks) — 5
3. [Opções de baralho](#3-opções-de-baralho-deck-options) — 6
4. [Tags](#4-tags) — 3
5. [Notes](#5-notes) — 6
6. [Cards](#6-cards) — 4
7. [Estudo](#7-estudo-study) — 3
8. [Estatísticas](#8-estatísticas-stats) — 6
9. [Mídia](#9-mídia-media) — 3
10. [Importação e exportação](#10-importação-e-exportação) — 4

Total: 44 endpoints.

---

## 1. Auth e conta

| Método | Caminho | Auth | Corpo | Resposta | Erros |
|---|---|---|---|---|---|
| POST | `/auth/register` | pública | `email` (obrigatório, e-mail válido), `password` (obrigatório, 6..72 caracteres e ≤ 72 bytes UTF-8) | **201** `{token}` | 409 e-mail já cadastrado (sem diferenciar maiúsculas); 422 |
| POST | `/auth/login` | pública | `email` (obrigatório), `password` (obrigatório) | **200** `{token}` | 401 credenciais inválidas; 422; **429** (`Retry-After`) após 10 falhas em 15 min por e-mail + IP |
| POST | `/auth/change-password` | JWT | `currentPassword` (obrigatório), `newPassword` (obrigatório, 8..72) | **204** | 422 (senha atual incorreta, nova inválida) |
| DELETE | `/account` | JWT | `password` (obrigatório) | **204** | 422 senha incorreta |

`DELETE /account` apaga a conta e todos os dados (inclusive os arquivos de mídia). O e-mail é normalizado (`trim` +
minúsculas) no cadastro e no login.

---

## 2. Baralhos (`decks`)

Objeto **DeckNode**:

```json
{ "id": 1, "parentId": null, "name": "Matemática", "fullName": "Matemática", "optionsId": 1,
  "newCount": 20, "learningCount": 0, "reviewCount": 35, "children": [ { "...": "DeckNode" } ] }
```

`newCount`/`learningCount`/`reviewCount` são as contagens de **hoje**, já incluindo subbaralhos e respeitando os limites
diários. `children` ordenados por nome.

| Método | Caminho | Auth | Corpo / parâmetros | Resposta | Erros |
|---|---|---|---|---|---|
| GET | `/decks` | JWT | — | **200** `DeckNode[]` (raízes, com filhos aninhados) | — |
| POST | `/decks` | JWT | `name` (obrigatório, ≤ 255, sem `::`), `parentId` (opcional), `optionsId` (opcional; padrão = opções padrão do usuário) | **201** `DeckNode` | 404 pai/opções inexistentes; 409 nome repetido entre irmãos; 422 nome inválido ou profundidade > 8 |
| GET | `/decks/{id}` | JWT | — | **200** `DeckNode` (com subárvore) | 404 |
| PATCH | `/decks/{id}` | JWT | `name`, `parentId` (**`null` explícito move para a raiz**; omitido não altera), `optionsId` — todos opcionais | **200** `DeckNode` | 404; 409 nome repetido; 422 ciclo, profundidade ou nome inválido |
| DELETE | `/decks/{id}` | JWT | — | **204** (remove subbaralhos, cards e notes que ficarem sem cards) | 404; 422 se for o `Padrão` e único raiz |

---

## 3. Opções de baralho (`deck-options`)

Objeto **DeckOptionsResponse**:

```json
{ "id": 1, "name": "Padrão", "isDefault": true, "newPerDay": 20, "reviewsPerDay": 200,
  "learningSteps": "1 10", "relearningSteps": "10", "graduatingIntervalDays": 1, "easyIntervalDays": 4,
  "startingEase": 2500, "easyBonus": 130, "hardInterval": 120, "intervalModifier": 100,
  "lapseNewInterval": 0, "minimumInterval": 1, "maximumInterval": 36500, "leechThreshold": 8,
  "burySiblings": true, "createdAt": "2026-11-24T12:00:00Z", "updatedAt": "2026-11-24T12:00:00Z" }
```

Corpo de criação/atualização (**DeckOptionsRequest**), com as faixas:

| Campo | Faixa / regra |
|---|---|
| `name` | **obrigatório**, ≤ 255, único por usuário (sem diferenciar maiúsculas) |
| `newPerDay`, `reviewsPerDay` | 0..9999 |
| `learningSteps` | minutos separados por espaço (`"1 10"`), 1..40320 cada, crescentes, 1..10 passos |
| `relearningSteps` | idem, pode ser `""` |
| `graduatingIntervalDays` | 1..365 |
| `easyIntervalDays` | 1..365 e ≥ `graduatingIntervalDays` |
| `startingEase` | 1300..5000 |
| `easyBonus` | 100..1000 |
| `hardInterval` | 50..1000 |
| `intervalModifier` | 10..1000 |
| `lapseNewInterval` | 0..100 |
| `minimumInterval` | 1..36500 |
| `maximumInterval` | 1..36500 e ≥ `minimumInterval` |
| `leechThreshold` | 1..99 |
| `burySiblings` | booleano |

Todos exceto `name` são opcionais: no `POST` assumem o padrão; no `PUT` mantêm o valor atual.

| Método | Caminho | Auth | Corpo | Resposta | Erros |
|---|---|---|---|---|---|
| GET | `/deck-options` | JWT | — | **200** `DeckOptionsResponse[]` (por nome) | — |
| POST | `/deck-options` | JWT | `DeckOptionsRequest` | **201** | 409 nome repetido; 422 |
| GET | `/deck-options/{id}` | JWT | — | **200** | 404 |
| PUT | `/deck-options/{id}` | JWT | `DeckOptionsRequest` | **200** | 404; 409 nome repetido; 422 |
| DELETE | `/deck-options/{id}` | JWT | — | **204** | 404; 409 em uso por baralhos; 422 é o conjunto padrão |
| POST | `/deck-options/{id}/default` | JWT | — | **200** (o conjunto passa a ser o padrão) | 404 |

---

## 4. Tags

**TagResponse**: `{ "id": 3, "name": "verbos-irregulares", "noteCount": 12 }`.

| Método | Caminho | Auth | Corpo | Resposta | Erros |
|---|---|---|---|---|---|
| GET | `/tags` | JWT | — | **200** `TagResponse[]` (por nome) | — |
| PATCH | `/tags/{id}` | JWT | `name` (obrigatório; normalizado; ≤ 100; não vazio) | **200** `TagResponse`; se o nome já existir, **mescla** na existente e devolve esta | 404; 422 |
| DELETE | `/tags/{id}` | JWT | — | **204** (remove a tag e as associações; notes permanecem) | 404 |

Normalização: `trim`, minúsculas, espaços viram `-`.

---

## 5. Notes

**NoteResponse**:

```json
{ "id": 7, "noteType": "BASIC_REVERSED", "front": "casa", "back": "house", "imageUrl": null,
  "guid": "6f1c...", "tags": ["vocabulario"], "cards": [ { "...": "CardSummary" } ],
  "createdAt": "2026-11-24T12:00:00Z", "updatedAt": "2026-11-24T12:00:00Z" }
```

Valores de `noteType`: `BASIC`, `BASIC_REVERSED`, `CLOZE`, `TYPE_IN`.

| Método | Caminho | Auth | Corpo / parâmetros | Resposta | Erros |
|---|---|---|---|---|---|
| POST | `/notes` | JWT | `noteType` (obrigatório), `deckId` (obrigatório), `front` (≤ 50000; obrigatório se não houver `imageUrl`), `back` (≤ 50000; obrigatório exceto em `CLOZE`), `imageUrl` (≤ 2048), `tags` (≤ 100 itens), `allowDuplicate` (padrão `false`) | **201** `NoteResponse` com `cards` | 404 baralho; **409** duplicado (`existingNoteId`); 422 (conteúdo inválido para o tipo; CLOZE sem lacuna; tag inválida) |
| GET | `/notes/duplicates` | JWT | — | **200** `DuplicateGroup[]` (até 200 grupos) | — |
| GET | `/notes/{id}` | JWT | — | **200** `NoteResponse` | 404 |
| PATCH | `/notes/{id}` | JWT | `front`, `back`, `imageUrl` (`""` remove), `tags` (substitui a lista), `allowDuplicate` — todos opcionais; tipo e baralho não mudam | **200** `NoteResponse` (em CLOZE, cards regenerados: números novos criam cards; removidos só apagam cards nunca estudados) | 404; 409 duplicado; 422 |
| DELETE | `/notes/{id}` | JWT | — | **204** (remove o note e os cards; mantém o `review_log`) | 404 |
| GET | `/notes/{id}/cards` | JWT | — | **200** `CardSummary[]` (por ordinal) | 404 |

**DuplicateGroup**: `{ "noteType": "BASIC", "dupKey": "<sha256>", "notes": [ { "id", "noteType", "front", "back", "imageUrl", "createdAt" } ] }`.

Cloze: `{{c1::resposta}}` ou `{{c2::resposta::dica}}`, números 1..99.

---

## 6. Cards

**CardSummary**:

```json
{ "id": 21, "noteId": 7, "deckId": 1, "noteType": "BASIC", "ordinal": 0, "state": "REVIEW",
  "dueAt": "2026-11-25T04:00:00Z", "intervalDays": 6, "easePermille": 2500, "reps": 3, "lapses": 0,
  "suspended": false, "flag": 0, "question": "casa", "tags": ["vocabulario"] }
```

`question` é um trecho (até 120 caracteres, sem HTML) da pergunta renderizada. `state`: `NEW`, `LEARNING`, `REVIEW`,
`RELEARNING`.

**CardDetail**: os campos do resumo (exceto `question` como trecho) mais `position`, `learningStep`, `lastReviewAt`,
`buriedUntil`, `question` e `answer` **completas e renderizadas**, `imageUrl`, `createdAt`, `updatedAt`.

| Método | Caminho | Auth | Corpo / parâmetros | Resposta | Erros |
|---|---|---|---|---|---|
| POST | `/cards/search` | JWT | Todos opcionais, combinados com E: `text` (≤ 500; sem diferenciar maiúsculas, em `front`/`back`; `%` e `_` são literais), `deckId`, `includeSubdecks` (padrão `true`), `tags` (≤ 50; **todas** devem estar no note), `states` (≤ 4; qualquer um), `suspended`, `flag` (0..7), `dueBefore` (instante, estritamente antes), `minLapses` (≥ 0), `noteType`, `page`, `size`, `sort` (`id`, `dueAt` padrão, `createdAt`, `lapses`, `interval`; `campo[,asc\|desc]`) | **200** `PageResponse<CardSummary>` | 404 `deckId` inexistente; 422 paginação/ordenação/tag inválida |
| GET | `/cards/{id}` | JWT | — | **200** `CardDetail` | 404 |
| PATCH | `/cards/{id}` | JWT | `suspended`, `flag` (0..7), `deckId` — todos opcionais | **200** `CardDetail` | 404 card/baralho; 422 `flag` fora da faixa |
| POST | `/cards/bulk` | JWT | `ids` (obrigatório, 1..500; repetidos ignorados), `action` (obrigatório), `flag` (0..7; obrigatório em `FLAG`), `deckId` (obrigatório em `MOVE`), `tag` (≤ 200; obrigatória em `ADD_TAG`/`REMOVE_TAG`) | **200** `{ "affected": n }` | 404 se **qualquer** id/baralho não for do usuário (nada é alterado); 400 `action` desconhecida; 422 parâmetros |

Ações de `bulk`: `SUSPEND`, `UNSUSPEND`, `FLAG`, `MOVE`, `ADD_TAG`, `REMOVE_TAG` (atuam nos **notes** dos cards), `RESET`
(volta a `NEW` e ao fim da fila), `DELETE` (remove cards e notes que ficarem sem cards). A operação é atômica.

---

## 7. Estudo (`study`)

| Método | Caminho | Auth | Corpo / parâmetros | Resposta | Erros |
|---|---|---|---|---|---|
| GET | `/study/queue` | JWT | `deckId` (opcional; inclui subbaralhos), `limit` (1..200, padrão 50) | **200** `QueueResponse` | 404 baralho; 422 `limit` |
| POST | `/study/cards/{id}/answer` | JWT | `rating` (obrigatório: `1..4` ou `AGAIN`/`HARD`/`GOOD`/`EASY`), `timeMs` (0..600000, padrão 0), `version` (opcional) | **200** `AnswerResponse` | 400 rating desconhecido; 404 card; **409** `version` desatualizada ou concorrência; 422 card suspenso/enterrado, `rating` ausente, `timeMs` fora da faixa |
| POST | `/study/undo` | JWT | — | **200** `QueueCard` (card restaurado) | 404 sem respostas a desfazer ou card excluído |

**QueueResponse**:

```json
{
  "counts": { "newCount": 20, "learningCount": 1, "reviewCount": 35 },
  "cards": [
    { "cardId": 21, "noteId": 7, "deckId": 1, "state": "NEW", "noteType": "BASIC", "ordinal": 0,
      "question": "casa", "answer": "house", "imageUrl": null, "dueAt": "2026-11-24T12:00:00Z", "version": 0,
      "intervals": {
        "again": { "label": "1m",  "intervalDays": 0, "dueAt": "2026-11-24T12:01:00Z" },
        "hard":  { "label": "6m",   "intervalDays": 0, "dueAt": "2026-11-24T12:05:30Z" },
        "good":  { "label": "10m", "intervalDays": 0, "dueAt": "2026-11-24T12:10:00Z" },
        "easy":  { "label": "4d",  "intervalDays": 4, "dueAt": "2026-11-28T04:00:00Z" } } }
  ]
}
```

`counts` já respeitam os limites diários e **não** dependem de `limit`. Ordem de `cards`: aprendizado vencido →
revisões do dia → novos. Rótulos: `<1m`, `10m`, `1.5h`, `1d`..`29d`, `1mo`.., `1y`..

**AnswerResponse**:

```json
{ "cardId": 21, "state": "LEARNING", "dueAt": "2026-11-24T12:10:00Z", "intervalDays": 0, "ease": 2500,
  "reps": 1, "lapses": 0, "suspended": false, "leech": false, "version": 1,
  "nextIntervals": { "again": {}, "hard": {}, "good": {}, "easy": {} } }
```

(`nextIntervals` tem o mesmo formato de `intervals`.) `leech = true` indica que **esta** resposta suspendeu o card.

---

## 8. Estatísticas (`stats`)

Todos `GET`, **JWT**. Definições exatas das métricas em [`fluxos-de-negocio.md`](fluxos-de-negocio.md#6-estatísticas).

| Caminho | Parâmetros | Resposta | Erros |
|---|---|---|---|
| `/stats/today` | — | `{ date, reviews, correct, again, newCards, timeMs, accuracyPercent }` | — |
| `/stats/overview` | `deckId` (opcional) | `{ newCards, learning, young, mature, relearning, suspended, buried, totalCards, totalNotes, averageEasePercent, averageIntervalDays, totalLapses }` | 404 baralho |
| `/stats/forecast` | `days` (1..365, padrão 30), `deckId` | `{ days, totalDue, items: [ { date, due, cumulative } ] }` | 404; 422 `days` |
| `/stats/heatmap` | `from`, `to` (`yyyy-MM-dd`; padrão: `to` = hoje, `from` = `to − 364`; janela máx. 366 dias), `deckId` | `{ from, to, totalReviews, activeDays, maxCount, items: [ { date, count } ] }` (só dias com respostas) | 404; 400 data inválida; 422 janela |
| `/stats/retention` | `days` (1..365, padrão 30), `deckId` | `{ days, from, to, totalReviews, matureReviews, passed, retentionPercent, daily: [ { date, reviews, passed, retentionPercent } ] }` | 404; 422 `days` |
| `/stats/streak` | — | `{ currentStreak, longestStreak, totalDaysStudied, lastStudyDate }` | — |

Exemplo (`/stats/today`):

```json
{ "date": "2026-11-24", "reviews": 42, "correct": 36, "again": 6, "newCards": 10, "timeMs": 215000,
  "accuracyPercent": 85.7 }
```

---

## 9. Mídia (`media`)

| Método | Caminho | Auth | Corpo / parâmetros | Resposta | Erros |
|---|---|---|---|---|---|
| POST | `/media` | JWT | `multipart/form-data` com a parte `file` (PNG, JPEG, GIF, WebP, MP3, OGG, WAV, M4A) | **201** `{ id, url, contentType, size, originalName }` (`url` = `/api/v1/media/{id}`) | 413 acima de `app.media.max-bytes`; 422 vazio, tipo não permitido ou tipo declarado diferente do conteúdo |
| GET | `/media/{id}` | JWT | — | **200** bytes do arquivo (`Content-Type` detectado, `Content-Disposition: inline`, `Cache-Control: private, max-age=86400`, `X-Content-Type-Options: nosniff`) | 404 |
| DELETE | `/media/{id}` | JWT | — | **204** (remove linha e arquivo) | 404 |

O tipo é decidido pelos *magic bytes*. Para usar a mídia num note, coloque a `url` em `imageUrl`.

---

## 10. Importação e exportação

| Método | Caminho | Auth | Corpo / parâmetros | Resposta | Erros |
|---|---|---|---|---|---|
| POST | `/import/csv` | JWT | `multipart/form-data`: `file` (CSV UTF-8, obrigatório); `deckId` (padrão: baralho padrão), `noteType` (padrão `BASIC`), `delimiter` (`,`, `;`, `tab` ou `auto`), `hasHeader` (padrão: detectar), `skipInvalid` (padrão `false`) | **200** `{ imported, skippedDuplicates, errors: [ { row, message } ], duplicateRows: [ n ], cardsCreated, decksCreated }` | 404 baralho; 413 arquivo grande; 422 arquivo vazio/binário/não UTF-8/malformado, > 10 000 linhas, linhas inválidas sem `skipInvalid` (com `errors`) |
| POST | `/import/backup` | JWT | corpo `application/json` = arquivo de `GET /export/backup` | **200** `{ deckOptions, decks, notes, cards, reviewLogs }` | 409 conta já tem notes/cards; 422 JSON inválido, versão ≠ 1, referências inconsistentes (nada é gravado) |
| GET | `/export/csv` | JWT | `deckId` (opcional; inclui subbaralhos) | **200** `text/csv; charset=UTF-8` (`flashcards.csv`), cabeçalho `deck,noteType,front,back,tags,imageUrl` | 404 baralho |
| GET | `/export/backup` | JWT | — | **200** `application/json` (`flashcards-backup.json`): `{ version, exportedAt, deckOptions, decks, notes, cards, reviewLogs }` | — |

Colunas do CSV de importação (cabeçalho, sem diferenciar maiúsculas): `front` (obrigatória), `back`, `tags` (separadas
por espaço), `deck` (caminho `A::B`), `noteType`, `imageUrl`. Sem cabeçalho: `front, back, tags, deck`. O backup **não**
inclui os arquivos de mídia.

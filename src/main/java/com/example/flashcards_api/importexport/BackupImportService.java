package com.example.flashcards_api.importexport;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.ConflictException;
import com.example.flashcards_api.common.Texts;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.deck.UserDefaultsService;
import com.example.flashcards_api.importexport.BackupModel.CardData;
import com.example.flashcards_api.importexport.BackupModel.DeckData;
import com.example.flashcards_api.importexport.BackupModel.DeckOptionsData;
import com.example.flashcards_api.importexport.BackupModel.NoteData;
import com.example.flashcards_api.importexport.BackupModel.ReviewLogData;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.review.ReviewLog;
import com.example.flashcards_api.review.ReviewLogRepository;
import com.example.flashcards_api.tag.Tag;
import com.example.flashcards_api.tag.TagRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Restauração de um backup JSON ({@link BackupExportService}) em uma conta <b>vazia</b> (sem notes nem cards).
 * O arquivo é lido por streaming, elemento a elemento, e tudo ocorre em uma transação: qualquer erro desfaz tudo.
 * <p>
 * Regras: todos os ids são remapeados; {@code guid} dos notes é regenerado (é único no banco inteiro, e o backup
 * pode ser restaurado em outra conta do mesmo servidor); {@code created_at}/{@code updated_at} voltam a ser os da
 * restauração (vêm da auditoria); o estado de agendamento dos cards e os registros de revisão são preservados.
 * Opções e baralhos que já existam na conta destino com o mesmo nome (como o {@code Padrão}) são reaproveitados e,
 * no caso das opções, sobrescritas pelos valores do backup. Revisões de cards que não estão mais no backup (excluídos)
 * são mantidas com {@code cardId = 0}, pois contam para as estatísticas.
 */
@Service
@RequiredArgsConstructor
public class BackupImportService {

    private final ImportExportRepository importExportRepository;
    private final CardRepository cardRepository;
    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;
    private final DeckRepository deckRepository;
    private final DeckOptionsRepository deckOptionsRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final UserDefaultsService userDefaultsService;
    private final ObjectMapper objectMapper;

    /**
     * Quantidades restauradas.
     *
     * @param deckOptions conjuntos de opções do backup
     * @param decks       baralhos do backup
     * @param notes       notes criados
     * @param cards       cards criados
     * @param reviewLogs  registros de revisão criados
     */
    public record Result(int deckOptions, int decks, int notes, int cards, int reviewLogs) {
    }

    /**
     * Restaura o backup lido do fluxo.
     *
     * @param userId dono (a conta que recebe os dados)
     * @param in     JSON do backup
     * @return quantidades restauradas
     * @throws ConflictException     se a conta já tiver notes ou cards
     * @throws BusinessRuleException se o JSON for inválido, de versão desconhecida ou inconsistente
     */
    @Transactional
    public Result restore(Long userId, InputStream in) {
        if (importExportRepository.countByUserId(userId) > 0 || cardRepository.countByUserId(userId) > 0) {
            throw new ConflictException("O backup só pode ser restaurado em uma conta sem notes nem cards");
        }
        Restorer restorer = new Restorer(userId, userDefaultsService.ensureDefaults(userId).options());
        try (JsonParser p = objectMapper.getFactory().createParser(in)) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                throw new BusinessRuleException("Backup inválido: esperado um objeto JSON");
            }
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                String field = p.currentName();
                p.nextToken();
                switch (field) {
                    case "version" -> restorer.version(p.getIntValue());
                    case "deckOptions" -> each(p, DeckOptionsData.class, restorer::options);
                    case "decks" -> {
                        List<DeckData> decks = new ArrayList<>();
                        each(p, DeckData.class, decks::add);
                        restorer.decks(decks);
                    }
                    case "notes" -> each(p, NoteData.class, restorer::note);
                    case "cards" -> each(p, CardData.class, restorer::card);
                    case "reviewLogs" -> each(p, ReviewLogData.class, restorer::reviewLog);
                    default -> p.skipChildren();
                }
            }
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("Backup inválido: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new BusinessRuleException("Backup ilegível: " + e.getMessage());
        }
        return restorer.result();
    }

    private <T> void each(JsonParser p, Class<T> type, Consumer<T> consumer) throws IOException {
        if (p.currentToken() != JsonToken.START_ARRAY) {
            throw new BusinessRuleException("Backup inválido: esperada uma lista");
        }
        while (p.nextToken() != JsonToken.END_ARRAY) {
            consumer.accept(objectMapper.readValue(p, type));
        }
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new BusinessRuleException("Backup inconsistente: " + what);
        }
        return value;
    }

    /** Estado da restauração: mapas de ids antigos para novos. */
    private final class Restorer {
        private final Long userId;
        private final Map<Long, Long> optionsIds = new HashMap<>();
        private final Map<Long, Long> deckIds = new HashMap<>();
        private final Map<Long, Long> noteIds = new HashMap<>();
        private final Map<Long, Long> cardIds = new HashMap<>();
        private final Map<String, Long> tagIds = new HashMap<>();
        private final Map<String, DeckOptions> existingOptions = new HashMap<>();
        private boolean versionSeen;
        private int optionsCount;
        private int deckCount;
        private int noteCount;
        private int cardCount;
        private int logCount;

        Restorer(Long userId, DeckOptions defaultOptions) {
            this.userId = userId;
            for (DeckOptions o : deckOptionsRepository.findAllByUserId(userId)) {
                existingOptions.put(o.getName().toLowerCase(Locale.ROOT), o);
            }
            existingOptions.putIfAbsent(defaultOptions.getName().toLowerCase(Locale.ROOT), defaultOptions);
        }

        Result result() {
            if (!versionSeen) {
                throw new BusinessRuleException("Backup inválido: campo 'version' ausente");
            }
            return new Result(optionsCount, deckCount, noteCount, cardCount, logCount);
        }

        void version(int version) {
            if (version != BackupModel.VERSION) {
                throw new BusinessRuleException("Versão de backup não suportada: " + version);
            }
            versionSeen = true;
        }

        private void requireVersion() {
            if (!versionSeen) {
                throw new BusinessRuleException("Backup inválido: 'version' deve ser o primeiro campo");
            }
        }

        void options(DeckOptionsData d) {
            requireVersion();
            require(d.id(), "opções sem id");
            String key = require(d.name(), "opções sem nome").toLowerCase(Locale.ROOT);
            DeckOptions target = existingOptions.get(key);
            boolean created = target == null;
            if (created) {
                target = new DeckOptions();
                target.setUserId(userId);
                target.setName(d.name());
            }
            target.setNewPerDay(d.newPerDay());
            target.setReviewsPerDay(d.reviewsPerDay());
            target.setLearningSteps(require(d.learningSteps(), "passos de aprendizado"));
            target.setRelearningSteps(require(d.relearningSteps(), "passos de reaprendizado"));
            target.setGraduatingIntervalDays(d.graduatingIntervalDays());
            target.setEasyIntervalDays(d.easyIntervalDays());
            target.setStartingEase(d.startingEase());
            target.setEasyBonus(d.easyBonus());
            target.setHardInterval(d.hardInterval());
            target.setIntervalModifier(d.intervalModifier());
            target.setLapseNewInterval(d.lapseNewInterval());
            target.setMinimumInterval(d.minimumInterval());
            target.setMaximumInterval(d.maximumInterval());
            target.setLeechThreshold(d.leechThreshold());
            target.setBurySiblings(d.burySiblings());
            target = deckOptionsRepository.save(target);
            existingOptions.put(key, target);
            optionsIds.put(d.id(), target.getId());
            optionsCount++;
        }

        /** Restaura baralhos garantindo pais antes dos filhos (a lista do arquivo pode vir em qualquer ordem). */
        void decks(List<DeckData> decks) {
            requireVersion();
            List<DeckData> pending = new ArrayList<>(decks);
            while (!pending.isEmpty()) {
                int before = pending.size();
                pending.removeIf(d -> {
                    if (d.parentId() != null && !deckIds.containsKey(d.parentId())) {
                        return false;
                    }
                    deck(d);
                    return true;
                });
                if (pending.size() == before) {
                    throw new BusinessRuleException("Backup inconsistente: baralhos com pai inexistente ou ciclo");
                }
            }
        }

        private void deck(DeckData d) {
            require(d.id(), "baralho sem id");
            String name = require(d.name(), "baralho sem nome");
            Long optionsId = require(optionsIds.get(d.optionsId()), "baralho aponta para opções inexistentes");
            Long parentId = d.parentId() == null ? null : deckIds.get(d.parentId());
            Deck deck = deckRepository.findByUserIdAndParentIdAndNameIgnoreCase(userId, parentId, name)
                    .orElseGet(() -> {
                        Deck created = new Deck();
                        created.setUserId(userId);
                        created.setParentId(parentId);
                        created.setName(name);
                        return created;
                    });
            deck.setOptionsId(optionsId);
            deck = deckRepository.save(deck);
            deckIds.put(d.id(), deck.getId());
            deckCount++;
        }

        void note(NoteData d) {
            requireVersion();
            require(d.id(), "note sem id");
            Note note = new Note();
            note.setUserId(userId);
            note.setNoteType(require(d.noteType(), "note sem tipo"));
            note.setFront(d.front() == null ? "" : d.front());
            note.setBack(d.back() == null ? "" : d.back());
            note.setImageUrl(d.imageUrl());
            if (d.tags() != null) {
                for (String raw : d.tags()) {
                    String name = Texts.normalizeTag(raw);
                    if (name.isEmpty()) {
                        continue;
                    }
                    if (name.length() > Texts.MAX_TAG_LENGTH) {
                        throw new BusinessRuleException("Backup inconsistente: tag longa demais");
                    }
                    Long id = tagIds.computeIfAbsent(name, n -> tagRepository.findByUserIdAndName(userId, n)
                            .orElseGet(() -> tagRepository.save(new Tag(userId, n))).getId());
                    note.getTags().add(tagRepository.getReferenceById(id));
                }
            }
            noteIds.put(d.id(), noteRepository.save(note).getId());
            noteCount++;
        }

        void card(CardData d) {
            requireVersion();
            Card card = new Card();
            card.setUserId(userId);
            card.setNoteId(require(noteIds.get(d.noteId()), "card aponta para note inexistente"));
            card.setDeckId(require(deckIds.get(d.deckId()), "card aponta para baralho inexistente"));
            card.setOrdinal(d.ordinal());
            card.setState(require(d.state(), "card sem estado"));
            card.setPosition(d.position());
            card.setDueAt(require(d.dueAt(), "card sem vencimento"));
            card.setIntervalDays(d.intervalDays());
            card.setEasePermille(d.easePermille());
            card.setReps(d.reps());
            card.setLapses(d.lapses());
            card.setLearningStep(d.learningStep());
            card.setLastReviewAt(d.lastReviewAt());
            card.setSuspended(d.suspended());
            card.setBuriedUntil(d.buriedUntil());
            card.setFlag(d.flag());
            cardIds.put(require(d.id(), "card sem id"), cardRepository.save(card).getId());
            cardCount++;
        }

        void reviewLog(ReviewLogData d) {
            requireVersion();
            ReviewLog log = new ReviewLog();
            log.setUserId(userId);
            log.setCardId(cardIds.getOrDefault(d.cardId(), 0L));
            log.setDeckId(d.deckId() == null ? null : deckIds.get(d.deckId()));
            log.setRating(require(d.rating(), "revisão sem nota"));
            log.setStateBefore(require(d.stateBefore(), "revisão sem estado anterior"));
            log.setStateAfter(require(d.stateAfter(), "revisão sem estado posterior"));
            log.setIntervalBefore(d.intervalBefore());
            log.setIntervalAfter(d.intervalAfter());
            log.setEaseBefore(d.easeBefore());
            log.setEaseAfter(d.easeAfter());
            log.setDueBefore(require(d.dueBefore(), "revisão sem vencimento anterior"));
            log.setDueAfter(require(d.dueAfter(), "revisão sem vencimento posterior"));
            log.setRepsBefore(d.repsBefore());
            log.setLapsesBefore(d.lapsesBefore());
            log.setLapsesAfter(d.lapsesAfter());
            log.setLearningStepBefore(d.learningStepBefore());
            log.setLastReviewBefore(d.lastReviewBefore());
            log.setTimeMs(d.timeMs());
            log.setReviewedAt(require(d.reviewedAt(), "revisão sem data"));
            reviewLogRepository.save(log);
            logCount++;
        }
    }
}

package com.example.flashcards_api.importexport;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.importexport.BackupModel.CardData;
import com.example.flashcards_api.importexport.BackupModel.DeckData;
import com.example.flashcards_api.importexport.BackupModel.DeckOptionsData;
import com.example.flashcards_api.importexport.BackupModel.NoteData;
import com.example.flashcards_api.importexport.BackupModel.ReviewLogData;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.review.ReviewLog;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Exportação completa dos dados do usuário em JSON versionado. Opções e baralhos (poucos) são gravados de uma vez;
 * notes, cards e revisões (podem ser muitos) são lidos e gravados em blocos de {@value #CHUNK} registros, sem nunca
 * manter a coleção inteira em memória.
 */
@Service
@RequiredArgsConstructor
public class BackupExportService {

    private static final int CHUNK = 500;

    private final DeckOptionsRepository deckOptionsRepository;
    private final DeckRepository deckRepository;
    private final ImportExportRepository importExportRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Grava o backup do usuário no fluxo de saída.
     *
     * @param userId dono
     * @param out    destino (não é fechado aqui)
     * @throws IOException se a escrita falhar
     */
    public void write(Long userId, OutputStream out) throws IOException {
        JsonGenerator g = objectMapper.getFactory().createGenerator(out);
        g.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        g.writeStartObject();
        g.writeNumberField("version", BackupModel.VERSION);
        g.writeStringField("exportedAt", clock.instant().toString());

        g.writeArrayFieldStart("deckOptions");
        List<DeckOptions> options = new ArrayList<>(deckOptionsRepository.findAllByUserId(userId));
        options.sort(Comparator.comparing(DeckOptions::getId));
        for (DeckOptions o : options) {
            objectMapper.writeValue(g, toData(o));
        }
        g.writeEndArray();

        g.writeArrayFieldStart("decks");
        List<Deck> decks = new ArrayList<>(deckRepository.findAllByUserId(userId));
        decks.sort(Comparator.comparing(Deck::getId));
        for (Deck d : decks) {
            objectMapper.writeValue(g, new DeckData(d.getId(), d.getParentId(), d.getName(), d.getOptionsId()));
        }
        g.writeEndArray();

        g.writeArrayFieldStart("notes");
        chunked(g, after -> {
            List<Note> notes = importExportRepository.findNotesAfter(userId, after, PageRequest.of(0, CHUNK));
            Map<Long, List<String>> tags = new HashMap<>();
            if (!notes.isEmpty()) {
                for (Object[] row : importExportRepository.findTagRows(notes.stream().map(Note::getId).toList())) {
                    tags.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((String) row[1]);
                }
            }
            return notes.stream().map(n -> new NoteData(n.getId(), n.getNoteType(), n.getFront(), n.getBack(),
                    n.getImageUrl(), n.getGuid(), tags.getOrDefault(n.getId(), List.of()), n.getCreatedAt(),
                    n.getUpdatedAt())).toList();
        }, NoteData::id);
        g.writeEndArray();

        g.writeArrayFieldStart("cards");
        chunked(g, after -> importExportRepository.findCardsAfter(userId, after, PageRequest.of(0, CHUNK)).stream()
                .map(BackupExportService::toData).toList(), CardData::id);
        g.writeEndArray();

        g.writeArrayFieldStart("reviewLogs");
        chunked(g, after -> importExportRepository.findReviewLogsAfter(userId, after, PageRequest.of(0, CHUNK))
                .stream().map(BackupExportService::toData).toList(), ReviewLogData::id);
        g.writeEndArray();

        g.writeEndObject();
        g.flush();
    }

    /** Lê blocos por chave crescente até esgotar, gravando cada elemento e descarregando a saída a cada bloco. */
    private <T> void chunked(JsonGenerator g, Function<Long, List<T>> loader, Function<T, Long> idOf)
            throws IOException {
        long after = 0;
        while (true) {
            List<T> chunk = loader.apply(after);
            for (T item : chunk) {
                objectMapper.writeValue(g, item);
            }
            g.flush();
            if (chunk.size() < CHUNK) {
                return;
            }
            after = idOf.apply(chunk.get(chunk.size() - 1));
        }
    }

    private static DeckOptionsData toData(DeckOptions o) {
        return new DeckOptionsData(o.getId(), o.getName(), o.isDefaultOptions(), o.getNewPerDay(),
                o.getReviewsPerDay(), o.getLearningSteps(), o.getRelearningSteps(), o.getGraduatingIntervalDays(),
                o.getEasyIntervalDays(), o.getStartingEase(), o.getEasyBonus(), o.getHardInterval(),
                o.getIntervalModifier(), o.getLapseNewInterval(), o.getMinimumInterval(), o.getMaximumInterval(),
                o.getLeechThreshold(), o.isBurySiblings());
    }

    private static CardData toData(Card c) {
        return new CardData(c.getId(), c.getNoteId(), c.getDeckId(), c.getOrdinal(), c.getState(), c.getPosition(),
                c.getDueAt(), c.getIntervalDays(), c.getEasePermille(), c.getReps(), c.getLapses(),
                c.getLearningStep(), c.getLastReviewAt(), c.isSuspended(), c.getBuriedUntil(), c.getFlag());
    }

    private static ReviewLogData toData(ReviewLog r) {
        return new ReviewLogData(r.getId(), r.getCardId(), r.getDeckId(), r.getRating(), r.getStateBefore(),
                r.getStateAfter(), r.getIntervalBefore(), r.getIntervalAfter(), r.getEaseBefore(), r.getEaseAfter(),
                r.getDueBefore(), r.getDueAfter(), r.getRepsBefore(), r.getLapsesBefore(), r.getLapsesAfter(),
                r.getLearningStepBefore(), r.getLastReviewBefore(), r.getTimeMs(), r.getReviewedAt());
    }
}

package com.example.flashcards_api.importexport;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.review.Rating;

import java.time.Instant;
import java.util.List;

/**
 * Formato do backup JSON (versão {@value #VERSION}). O arquivo é um objeto com {@code version}, {@code exportedAt} e
 * as listas {@code deckOptions}, {@code decks}, {@code notes}, {@code cards} e {@code reviewLogs}, nesta ordem.
 * Os ids do arquivo só servem para ligar os registros entre si; na restauração todos são remapeados.
 */
public final class BackupModel {

    /** Versão atual do formato. */
    public static final int VERSION = 1;

    private BackupModel() {
    }

    /** Conjunto de opções de baralho. */
    public record DeckOptionsData(Long id, String name, boolean defaultOptions, int newPerDay, int reviewsPerDay,
                                  String learningSteps, String relearningSteps, int graduatingIntervalDays,
                                  int easyIntervalDays, int startingEase, int easyBonus, int hardInterval,
                                  int intervalModifier, int lapseNewInterval, int minimumInterval,
                                  int maximumInterval, int leechThreshold, boolean burySiblings) {
    }

    /** Baralho (a lista é gravada em ordem de id; {@code parentId} pode vir depois do filho). */
    public record DeckData(Long id, Long parentId, String name, Long optionsId) {
    }

    /** Note com suas tags (nomes normalizados). */
    public record NoteData(Long id, NoteType noteType, String front, String back, String imageUrl, String guid,
                           List<String> tags, Instant createdAt, Instant updatedAt) {
    }

    /** Card com o estado de agendamento. */
    public record CardData(Long id, Long noteId, Long deckId, int ordinal, CardState state, long position,
                           Instant dueAt, int intervalDays, int easePermille, int reps, int lapses,
                           int learningStep, Instant lastReviewAt, boolean suspended, Instant buriedUntil,
                           short flag) {
    }

    /** Registro de revisão. {@code cardId} pode apontar para um card já excluído (ausente de {@code cards}). */
    public record ReviewLogData(Long id, Long cardId, Long deckId, Rating rating, CardState stateBefore,
                                CardState stateAfter, int intervalBefore, int intervalAfter, int easeBefore,
                                int easeAfter, Instant dueBefore, Instant dueAfter, int repsBefore,
                                int lapsesBefore, int lapsesAfter, int learningStepBefore,
                                Instant lastReviewBefore, int timeMs, Instant reviewedAt) {
    }
}

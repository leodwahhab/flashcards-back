package com.example.flashcards_api.study;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.ConflictException;
import com.example.flashcards_api.common.NotFoundException;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.review.ReviewLog;
import com.example.flashcards_api.review.ReviewLogRepository;
import com.example.flashcards_api.scheduler.CardSnapshot;
import com.example.flashcards_api.scheduler.DayBoundary;
import com.example.flashcards_api.scheduler.Sm2Scheduler;
import com.example.flashcards_api.scheduler.SchedulingResult;
import com.example.flashcards_api.study.StudyDtos.AnswerRequest;
import com.example.flashcards_api.study.StudyDtos.AnswerResponse;
import com.example.flashcards_api.study.StudyDtos.Intervals;
import com.example.flashcards_api.study.StudyDtos.QueueCard;
import com.example.flashcards_api.tag.Tag;
import com.example.flashcards_api.tag.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Registra respostas (agendamento, log, enterrar irmãos, leech) e desfaz a última resposta.
 */
@Service
@RequiredArgsConstructor
public class StudyService {

    /** Nome da tag aplicada a notes com cards leech. */
    public static final String LEECH_TAG = "leech";

    private final CardRepository cardRepository;
    private final DeckRepository deckRepository;
    private final DeckOptionsRepository deckOptionsRepository;
    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final Sm2Scheduler scheduler;
    private final StudyQueueService queueService;
    private final Clock clock;

    /**
     * Aplica uma resposta ao card: reagenda, grava o log, enterra irmãos e trata leech.
     *
     * @param userId  dono
     * @param cardId  card respondido
     * @param request rating, tempo e (opcional) versão vista pelo cliente
     * @return novo estado do card e previsão dos botões
     * @throws NotFoundException     se o card não existir ou for de outro usuário
     * @throws BusinessRuleException se o card estiver suspenso ou enterrado
     * @throws ConflictException     se a versão informada estiver desatualizada (409)
     */
    @Transactional
    public AnswerResponse answer(Long userId, Long cardId, AnswerRequest request) {
        Instant now = clock.instant();
        DayBoundary day = queueService.dayBoundary();
        Card card = cardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new NotFoundException("Card não encontrado"));
        if (request.version() != null && !request.version().equals(card.getVersion())) {
            throw new ConflictException("O card foi alterado por outra requisição; recarregue e tente novamente");
        }
        if (card.isSuspended()) {
            throw new BusinessRuleException("Card suspenso não pode ser respondido");
        }
        if (card.getBuriedUntil() != null && card.getBuriedUntil().isAfter(now)) {
            throw new BusinessRuleException("Card enterrado não pode ser respondido");
        }
        DeckOptions options = optionsOf(card, userId);
        SchedulingResult result = scheduler.schedule(CardSnapshot.of(card), request.rating(), options, now, day);

        ReviewLog log = buildLog(card, request, result, now);
        applyResult(card, result);
        if (result.leech()) {
            card.setSuspended(true);
            addLeechTag(userId, card.getNoteId());
        }
        cardRepository.saveAndFlush(card);
        reviewLogRepository.save(log);
        if (options.isBurySiblings()) {
            burySiblings(card, userId, day.startOfNextDay(now));
        }
        Intervals next = Intervals.of(scheduler.preview(CardSnapshot.of(card), options, now, day));
        return new AnswerResponse(card.getId(), card.getState(), card.getDueAt(), card.getIntervalDays(),
                card.getEasePermille(), card.getReps(), card.getLapses(), card.isSuspended(), result.leech(),
                card.getVersion(), next);
    }

    /**
     * Desfaz a última resposta do usuário: restaura o card a partir das colunas {@code *_before} do log, apaga o log,
     * desenterra irmãos enterrados por essa resposta e reverte suspensão/tag de leech causadas por ela.
     * Chamadas sucessivas desfazem as respostas anteriores. Se o card já não existe, o log é descartado e responde 404
     * (sem rollback, para que a exclusão do log valha).
     *
     * @param userId dono
     * @return card restaurado, pronto para ser reapresentado
     * @throws NotFoundException se não houver respostas a desfazer ou o card não existir mais
     */
    @Transactional(noRollbackFor = NotFoundException.class)
    public QueueCard undo(Long userId) {
        ReviewLog log = reviewLogRepository.findTopByUserIdOrderByIdDesc(userId)
                .orElseThrow(() -> new NotFoundException("Nenhuma resposta para desfazer"));
        Card card = cardRepository.findByIdAndUserId(log.getCardId(), userId).orElse(null);
        if (card == null) {
            reviewLogRepository.delete(log);
            throw new NotFoundException("O card da última resposta não existe mais");
        }
        DeckOptions options = optionsOf(card, userId);
        DayBoundary day = queueService.dayBoundary();

        boolean causedLeech = log.getLapsesAfter() > log.getLapsesBefore() && card.isSuspended()
                && options.getLeechThreshold() > 0 && log.getLapsesAfter() >= options.getLeechThreshold();

        card.setState(log.getStateBefore());
        card.setDueAt(log.getDueBefore());
        card.setIntervalDays(log.getIntervalBefore());
        card.setEasePermille(log.getEaseBefore());
        card.setReps(log.getRepsBefore());
        card.setLapses(log.getLapsesBefore());
        card.setLearningStep(log.getLearningStepBefore());
        card.setLastReviewAt(log.getLastReviewBefore());
        if (causedLeech) {
            card.setSuspended(false);
        }
        cardRepository.saveAndFlush(card);

        Instant buriedUntil = day.startOfNextDay(log.getReviewedAt());
        for (Card sibling : cardRepository.findAllByNoteIdAndUserId(card.getNoteId(), userId)) {
            if (!sibling.getId().equals(card.getId()) && buriedUntil.equals(sibling.getBuriedUntil())) {
                sibling.setBuriedUntil(null);
                cardRepository.save(sibling);
            }
        }
        if (causedLeech) {
            removeLeechTagIfNoOtherLeech(userId, card);
        }
        reviewLogRepository.delete(log);

        Instant now = clock.instant();
        Note note = noteRepository.findByIdAndUserId(card.getNoteId(), userId)
                .orElseThrow(() -> new NotFoundException("Note não encontrado"));
        return queueService.toQueueCard(card, note, options, now, day);
    }

    private ReviewLog buildLog(Card card, AnswerRequest request, SchedulingResult result, Instant now) {
        ReviewLog log = new ReviewLog();
        log.setUserId(card.getUserId());
        log.setCardId(card.getId());
        log.setDeckId(card.getDeckId());
        Rating rating = request.rating();
        log.setRating(rating);
        log.setStateBefore(card.getState());
        log.setStateAfter(result.state());
        log.setIntervalBefore(card.getIntervalDays());
        log.setIntervalAfter(result.intervalDays());
        log.setEaseBefore(card.getEasePermille());
        log.setEaseAfter(result.easePermille());
        log.setDueBefore(card.getDueAt());
        log.setDueAfter(result.dueAt());
        log.setRepsBefore(card.getReps());
        log.setLapsesBefore(card.getLapses());
        log.setLapsesAfter(result.lapses());
        log.setLearningStepBefore(card.getLearningStep());
        log.setLastReviewBefore(card.getLastReviewAt());
        log.setTimeMs(request.timeMs() == null ? 0 : request.timeMs());
        log.setReviewedAt(now);
        return log;
    }

    private void applyResult(Card card, SchedulingResult result) {
        card.setState(result.state());
        card.setDueAt(result.dueAt());
        card.setIntervalDays(result.intervalDays());
        card.setEasePermille(result.easePermille());
        card.setReps(result.reps());
        card.setLapses(result.lapses());
        card.setLearningStep(result.learningStep());
        card.setLastReviewAt(result.lastReviewAt());
    }

    private void burySiblings(Card answered, Long userId, Instant until) {
        for (Card sibling : cardRepository.findAllByNoteIdAndUserId(answered.getNoteId(), userId)) {
            boolean inLearning = sibling.getState() == CardState.LEARNING
                    || sibling.getState() == CardState.RELEARNING;
            if (!sibling.getId().equals(answered.getId()) && !inLearning && !sibling.isSuspended()) {
                sibling.setBuriedUntil(until);
                cardRepository.save(sibling);
            }
        }
    }

    private void addLeechTag(Long userId, Long noteId) {
        Note note = noteRepository.findWithTagsByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new NotFoundException("Note não encontrado"));
        Tag tag = tagRepository.findByUserIdAndName(userId, LEECH_TAG)
                .orElseGet(() -> tagRepository.save(new Tag(userId, LEECH_TAG)));
        note.getTags().add(tag);
        noteRepository.save(note);
    }

    /** O log não registra se a tag já existia; remove-a só se nenhum outro card do note continua suspenso. */
    private void removeLeechTagIfNoOtherLeech(Long userId, Card restored) {
        List<Card> siblings = cardRepository.findAllByNoteIdAndUserId(restored.getNoteId(), userId);
        boolean otherSuspended = siblings.stream()
                .anyMatch(c -> !c.getId().equals(restored.getId()) && c.isSuspended());
        if (otherSuspended) {
            return;
        }
        noteRepository.findWithTagsByIdAndUserId(restored.getNoteId(), userId).ifPresent(note -> {
            if (note.getTags().removeIf(t -> LEECH_TAG.equals(t.getName()))) {
                noteRepository.save(note);
            }
        });
    }

    private DeckOptions optionsOf(Card card, Long userId) {
        Deck deck = deckRepository.findByIdAndUserId(card.getDeckId(), userId)
                .orElseThrow(() -> new NotFoundException("Baralho não encontrado"));
        return deckOptionsRepository.findByIdAndUserId(deck.getOptionsId(), userId)
                .orElseThrow(() -> new NotFoundException("Opções do baralho não encontradas"));
    }
}

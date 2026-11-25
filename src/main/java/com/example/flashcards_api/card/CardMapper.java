package com.example.flashcards_api.card;

import com.example.flashcards_api.common.Texts;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.tag.Tag;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Conversão de {@link Card} (com seu {@link Note}) para os DTOs de leitura. Os notes devem chegar já carregados (com
 * tags) para evitar consultas por item.
 */
public final class CardMapper {

    private static final int SNIPPET_LENGTH = 120;

    private CardMapper() {
    }

    /**
     * Converte um card em resumo.
     *
     * @param card card
     * @param note note do card, com as tags carregadas
     * @return resumo
     */
    public static CardSummary toSummary(Card card, Note note) {
        CardRenderer.Rendered rendered = CardRenderer.renderCard(note, card.getOrdinal());
        return new CardSummary(card.getId(), card.getNoteId(), card.getDeckId(), note.getNoteType(),
                card.getOrdinal(), card.getState(), card.getDueAt(), card.getIntervalDays(),
                card.getEasePermille(), card.getReps(), card.getLapses(), card.isSuspended(), card.getFlag(),
                snippet(rendered.question()), tagNames(note));
    }

    /**
     * Converte vários cards em resumos, preservando a ordem.
     *
     * @param cards cards
     * @param notes notes indexados por id (com as tags carregadas)
     * @return resumos
     */
    public static List<CardSummary> toSummaries(List<Card> cards, Map<Long, Note> notes) {
        return cards.stream().map(c -> toSummary(c, notes.get(c.getNoteId()))).toList();
    }

    /**
     * Converte um card em detalhe completo.
     *
     * @param card card
     * @param note note do card, com as tags carregadas
     * @return detalhe
     */
    public static CardDetail toDetail(Card card, Note note) {
        CardRenderer.Rendered rendered = CardRenderer.renderCard(note, card.getOrdinal());
        return new CardDetail(card.getId(), card.getNoteId(), card.getDeckId(), note.getNoteType(),
                card.getOrdinal(), card.getState(), card.getPosition(), card.getDueAt(), card.getIntervalDays(),
                card.getEasePermille(), card.getReps(), card.getLapses(), card.getLearningStep(),
                card.getLastReviewAt(), card.isSuspended(), card.getBuriedUntil(), card.getFlag(),
                rendered.question(), rendered.answer(), note.getImageUrl(), tagNames(note), card.getCreatedAt(),
                card.getUpdatedAt());
    }

    /**
     * Nomes das tags do note em ordem alfabética.
     *
     * @param note note com as tags carregadas
     * @return nomes ordenados
     */
    public static List<String> tagNames(Note note) {
        return note.getTags().stream().map(Tag::getName).sorted(Comparator.naturalOrder()).toList();
    }

    private static String snippet(String question) {
        String text = Texts.stripHtml(question).replaceAll("\\s+", " ").trim();
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH - 1) + "…";
    }
}

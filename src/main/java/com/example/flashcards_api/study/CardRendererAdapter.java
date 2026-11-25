package com.example.flashcards_api.study;

import com.example.flashcards_api.card.CardRenderer;
import com.example.flashcards_api.note.Note;
import org.springframework.stereotype.Component;

/**
 * Implementação de {@link CardContentRenderer} que delega para {@link CardRenderer}, a fonte única das regras de
 * renderização de cada tipo de nota (inclusive cloze), compartilhada com a API de cards.
 */
@Component
public class CardRendererAdapter implements CardContentRenderer {

    @Override
    public Rendered render(Note note, int ordinal) {
        CardRenderer.Rendered rendered = CardRenderer.renderCard(note, ordinal);
        return new Rendered(rendered.question(), rendered.answer());
    }
}

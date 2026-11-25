package com.example.flashcards_api.study;

import com.example.flashcards_api.note.Note;

/**
 * Renderiza pergunta e resposta de um card a partir do note. A implementação padrão é
 * {@link SimpleCardContentRenderer}; pode ser substituída por uma baseada em {@code card.CardRenderer}.
 */
public interface CardContentRenderer {

    /**
     * Pergunta e resposta renderizadas.
     *
     * @param question texto da pergunta
     * @param answer   texto da resposta
     */
    record Rendered(String question, String answer) {
    }

    /**
     * Renderiza o card de um note.
     *
     * @param note    note de origem
     * @param ordinal ordinal do card dentro do note
     * @return pergunta e resposta
     */
    Rendered render(Note note, int ordinal);
}

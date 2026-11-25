package com.example.flashcards_api.card;

import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteType;

/**
 * Monta a pergunta e a resposta de um card a partir do seu note e do ordinal. Classe pura, usada pela busca de cards
 * e pelo módulo de estudo.
 */
public final class CardRenderer {

    private CardRenderer() {
    }

    /**
     * Pergunta e resposta renderizadas.
     *
     * @param question lado da pergunta
     * @param answer   lado da resposta
     */
    public record Rendered(String question, String answer) {
    }

    /**
     * Renderiza o card de ordinal {@code ordinal} do note.
     *
     * @param note    note dono do card
     * @param ordinal ordinal do card (0 para BASIC/TYPE_IN; 0 ou 1 para BASIC_REVERSED; n-1 para o cloze n)
     * @return pergunta e resposta
     * @throws IllegalArgumentException se o ordinal for negativo
     */
    public static Rendered renderCard(Note note, int ordinal) {
        return renderCard(note.getNoteType(), note.getFront(), note.getBack(), ordinal);
    }

    /**
     * Renderiza um card a partir dos campos do note.
     * <ul>
     *   <li>BASIC e TYPE_IN: frente / verso;</li>
     *   <li>BASIC_REVERSED: ordinal 1 inverte (verso / frente), os demais seguem BASIC;</li>
     *   <li>CLOZE: cloze {@code ordinal + 1}; o verso é o extra exibido após a resposta.</li>
     * </ul>
     *
     * @param type    tipo do note
     * @param front   frente (texto com lacunas no CLOZE)
     * @param back    verso (extra no CLOZE)
     * @param ordinal ordinal do card
     * @return pergunta e resposta
     * @throws IllegalArgumentException se o ordinal for negativo
     */
    public static Rendered renderCard(NoteType type, String front, String back, int ordinal) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal não pode ser negativo");
        }
        String f = front == null ? "" : front;
        String b = back == null ? "" : back;
        return switch (type) {
            case BASIC_REVERSED -> ordinal == 1 ? new Rendered(b, f) : new Rendered(f, b);
            case CLOZE -> {
                int n = ordinal + 1;
                String answer = Cloze.renderAnswer(f, n);
                if (!b.isBlank()) {
                    answer = answer + "\n\n" + b;
                }
                yield new Rendered(Cloze.renderQuestion(f, n), answer);
            }
            case BASIC, TYPE_IN -> new Rendered(f, b);
        };
    }
}

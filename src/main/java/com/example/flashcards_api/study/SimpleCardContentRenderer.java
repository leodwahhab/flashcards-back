package com.example.flashcards_api.study;

import com.example.flashcards_api.note.Note;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderização mínima, baseada em regex, usada até a integração com {@code card.CardRenderer}.
 * BASIC/TYPE_IN: frente/verso; BASIC_REVERSED ordinal 1 inverte; CLOZE oculta a lacuna {@code n = ordinal + 1}.
 */
@Component
public class SimpleCardContentRenderer implements CardContentRenderer {

    private static final Pattern CLOZE = Pattern.compile("\\{\\{c(\\d+)::(.*?)(?:::(.*?))?}}", Pattern.DOTALL);

    /**
     * Renderiza pergunta e resposta conforme o tipo do note.
     *
     * @param note    note de origem
     * @param ordinal ordinal do card dentro do note
     * @return pergunta e resposta
     */
    @Override
    public Rendered render(Note note, int ordinal) {
        return switch (note.getNoteType()) {
            case BASIC_REVERSED -> ordinal == 1
                    ? new Rendered(note.getBack(), note.getFront())
                    : new Rendered(note.getFront(), note.getBack());
            case CLOZE -> renderCloze(note, ordinal + 1);
            default -> new Rendered(note.getFront(), note.getBack());
        };
    }

    private Rendered renderCloze(Note note, int number) {
        String question = replace(note.getFront(), number, false);
        String answer = replace(note.getFront(), number, true);
        String extra = note.getBack();
        if (extra != null && !extra.isBlank()) {
            answer = answer + "\n" + extra;
        }
        return new Rendered(question, answer);
    }

    private String replace(String text, int number, boolean reveal) {
        Matcher m = CLOZE.matcher(text == null ? "" : text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String replacement;
            if (Integer.parseInt(m.group(1)) != number || reveal) {
                replacement = m.group(2);
            } else {
                replacement = m.group(3) != null ? "[" + m.group(3) + "]" : "[...]";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}

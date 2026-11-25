package com.example.flashcards_api.card;

import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.tag.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Monta a especificação da busca de cards. O card guarda só o id do note (sem associação JPA), então os filtros que
 * dependem do note usam subconsultas {@code exists}.
 */
final class CardSpecifications {

    private static final char ESCAPE = '\\';

    private CardSpecifications() {
    }

    /**
     * Critérios já resolvidos da busca.
     *
     * @param userId    dono
     * @param deckIds   baralhos aceitos (nulo = todos)
     * @param text      texto procurado (nulo = sem filtro)
     * @param tags      nomes de tags normalizados (todos devem estar no note)
     * @param states    estados aceitos (nulo/vazio = todos)
     * @param suspended filtro de suspensão
     * @param flag      bandeira exata
     * @param dueBefore vencimento estritamente anterior
     * @param minLapses mínimo de lapsos
     * @param noteType  tipo do note
     */
    record Criteria(Long userId, Collection<Long> deckIds, String text, Set<String> tags, List<CardState> states,
                    Boolean suspended, Integer flag, Instant dueBefore, Integer minLapses, NoteType noteType) {
    }

    static Specification<Card> of(Criteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), c.userId()));
            if (c.deckIds() != null) {
                predicates.add(root.get("deckId").in(c.deckIds()));
            }
            if (c.states() != null && !c.states().isEmpty()) {
                predicates.add(root.get("state").in(c.states()));
            }
            if (c.suspended() != null) {
                predicates.add(cb.equal(root.get("suspended"), c.suspended()));
            }
            if (c.flag() != null) {
                predicates.add(cb.equal(root.get("flag"), c.flag().shortValue()));
            }
            if (c.dueBefore() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("dueAt"), c.dueBefore()));
            }
            if (c.minLapses() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Integer>get("lapses"), c.minLapses()));
            }
            if (c.noteType() != null || c.text() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Note> note = sub.from(Note.class);
                List<Predicate> on = new ArrayList<>();
                on.add(cb.equal(note.get("id"), root.get("noteId")));
                if (c.noteType() != null) {
                    on.add(cb.equal(note.get("noteType"), c.noteType()));
                }
                if (c.text() != null) {
                    String pattern = "%" + escapeLike(c.text().toLowerCase()) + "%";
                    on.add(cb.or(
                            cb.like(cb.lower(note.<String>get("front")), pattern, ESCAPE),
                            cb.like(cb.lower(note.<String>get("back")), pattern, ESCAPE)));
                }
                sub.select(note.<Long>get("id")).where(on.toArray(Predicate[]::new));
                predicates.add(cb.exists(sub));
            }
            if (c.tags() != null) {
                for (String tag : c.tags()) {
                    Subquery<Long> sub = query.subquery(Long.class);
                    Root<Note> note = sub.from(Note.class);
                    Join<Note, Tag> join = note.join("tags");
                    sub.select(note.<Long>get("id")).where(
                            cb.equal(note.get("id"), root.get("noteId")),
                            cb.equal(join.get("name"), tag));
                    predicates.add(cb.exists(sub));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Escapa os curingas do LIKE para que o texto do usuário seja tratado literalmente. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

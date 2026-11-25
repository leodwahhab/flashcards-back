package com.example.flashcards_api.importexport;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.review.ReviewLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Consultas de leitura em lote usadas pela importação e exportação. As leituras em blocos usam paginação por chave
 * ({@code id > afterId}) para não carregar tudo de uma vez.
 */
public interface ImportExportRepository extends Repository<Note, Long> {

    /** Quantidade de notes do usuário. */
    long countByUserId(Long userId);

    /** Tipo e chave de duplicidade dos notes do usuário cujas chaves estão em {@code keys}. */
    @Query("select n.noteType, n.dupKey from Note n where n.userId = :userId and n.dupKey in :keys")
    List<Object[]> findTypeAndDupKey(@Param("userId") Long userId, @Param("keys") Collection<String> keys);

    /**
     * Bloco de notes para exportação CSV: cada note aparece uma vez, com o baralho do seu card de menor ordinal
     * dentre os baralhos informados.
     */
    @Query("""
            select new com.example.flashcards_api.importexport.NoteRow(n.id, n.noteType, n.front, n.back,
                   n.imageUrl, c.deckId)
            from Note n, Card c
            where c.noteId = n.id and n.userId = :userId and n.id > :afterId and c.deckId in :deckIds
              and c.ordinal = (select min(c2.ordinal) from Card c2 where c2.noteId = n.id and c2.deckId in :deckIds)
            order by n.id
            """)
    List<NoteRow> findNoteRows(@Param("userId") Long userId, @Param("deckIds") Collection<Long> deckIds,
                               @Param("afterId") Long afterId, Pageable pageable);

    /** Pares (id do note, nome da tag) dos notes informados. */
    @Query("select n.id, t.name from Note n join n.tags t where n.id in :noteIds order by t.name")
    List<Object[]> findTagRows(@Param("noteIds") Collection<Long> noteIds);

    /** Bloco de notes do usuário com id maior que {@code afterId}. */
    @Query("select n from Note n where n.userId = :userId and n.id > :afterId order by n.id")
    List<Note> findNotesAfter(@Param("userId") Long userId, @Param("afterId") Long afterId, Pageable pageable);

    /** Bloco de cards do usuário com id maior que {@code afterId}. */
    @Query("select c from Card c where c.userId = :userId and c.id > :afterId order by c.id")
    List<Card> findCardsAfter(@Param("userId") Long userId, @Param("afterId") Long afterId, Pageable pageable);

    /** Bloco de registros de revisão do usuário com id maior que {@code afterId}. */
    @Query("select r from ReviewLog r where r.userId = :userId and r.id > :afterId order by r.id")
    List<ReviewLog> findReviewLogsAfter(@Param("userId") Long userId, @Param("afterId") Long afterId,
                                        Pageable pageable);
}

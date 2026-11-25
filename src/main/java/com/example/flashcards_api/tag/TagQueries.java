package com.example.flashcards_api.tag;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consultas de tags sobre a tabela de associação {@code note_tag} (dona: {@code Note.tags}).
 */
public interface TagQueries extends Repository<Tag, Long> {

    /** Tags do usuário com a contagem de notes, ordenadas por nome (uma única consulta). */
    @Query("""
            select new com.example.flashcards_api.tag.TagResponse(t.id, t.name,
                   (select count(n) from Note n join n.tags nt where nt.id = t.id))
            from Tag t where t.userId = :userId order by t.name
            """)
    List<TagResponse> findAllWithNoteCount(@Param("userId") Long userId);

    /** Quantidade de notes da tag. */
    @Query(value = "select count(*) from note_tag where tag_id = :tagId", nativeQuery = true)
    long countNotes(@Param("tagId") Long tagId);

    /** Copia para {@code targetId} as associações de {@code sourceId} que o destino ainda não tem. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into note_tag (note_id, tag_id)
            select nt.note_id, :targetId from note_tag nt
            where nt.tag_id = :sourceId
              and not exists (select 1 from note_tag x where x.note_id = nt.note_id and x.tag_id = :targetId)
            """, nativeQuery = true)
    int copyAssociations(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    /** Remove todas as associações da tag. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from note_tag where tag_id = :tagId", nativeQuery = true)
    int deleteAssociations(@Param("tagId") Long tagId);
}

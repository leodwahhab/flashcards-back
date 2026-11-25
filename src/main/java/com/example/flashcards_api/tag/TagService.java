package com.example.flashcards_api.tag;

import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.NotFoundException;
import com.example.flashcards_api.common.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regras de negócio de tags. Outros módulos devem usar {@link #findOrCreateAll(Long, Collection)} para obter as
 * entidades de tag de um note.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final TagQueries tagQueries;

    /**
     * Lista as tags do usuário com a contagem de notes, ordenadas por nome.
     *
     * @param userId dono
     * @return tags
     */
    @Transactional(readOnly = true)
    public List<TagResponse> list(Long userId) {
        return tagQueries.findAllWithNoteCount(userId);
    }

    /**
     * Renomeia uma tag. Se já existir outra tag com o nome normalizado, as associações são mescladas nela
     * (sem duplicar) e a tag antiga é removida; a resposta é a tag resultante. Renomear para o próprio nome é no-op.
     *
     * @param userId dono
     * @param id     tag a renomear
     * @param newName nome desejado (será normalizado)
     * @return tag resultante, com contagem
     * @throws BusinessRuleException se o nome normalizado for vazio ou passar de 100 caracteres
     * @throws NotFoundException     se a tag não existir ou for de outro usuário
     */
    public TagResponse rename(Long userId, Long id, String newName) {
        Tag tag = tagRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Tag não encontrada"));
        String name = validName(newName);
        if (name.equals(tag.getName())) {
            return toResponse(tag);
        }
        Tag existing = tagRepository.findByUserIdAndName(userId, name).orElse(null);
        if (existing == null) {
            tag.setName(name);
            return toResponse(tagRepository.save(tag));
        }
        tagQueries.copyAssociations(tag.getId(), existing.getId());
        tagQueries.deleteAssociations(tag.getId());
        tagRepository.deleteById(tag.getId());
        return toResponse(existing);
    }

    /**
     * Remove a tag e suas associações com notes (os notes permanecem).
     *
     * @param userId dono
     * @param id     tag
     * @throws NotFoundException se a tag não existir ou for de outro usuário
     */
    public void delete(Long userId, Long id) {
        Tag tag = tagRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Tag não encontrada"));
        tagQueries.deleteAssociations(tag.getId());
        tagRepository.deleteById(tag.getId());
    }

    /**
     * Devolve as entidades de tag do usuário para os nomes informados, criando as que não existem. Os nomes são
     * normalizados ({@link Texts#normalizeTag(String)}), os vazios são ignorados e os repetidos colapsados.
     * Usa uma consulta para as existentes e um {@code saveAll} para as novas.
     *
     * @param userId dono das tags
     * @param names  nomes brutos (aceita nulo/vazio)
     * @return tags gerenciadas, na ordem da primeira ocorrência de cada nome normalizado
     * @throws BusinessRuleException se algum nome normalizado passar de 100 caracteres
     */
    public List<Tag> findOrCreateAll(Long userId, Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : names) {
            String name = Texts.normalizeTag(raw);
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > Texts.MAX_TAG_LENGTH) {
                throw new BusinessRuleException("A tag excede " + Texts.MAX_TAG_LENGTH + " caracteres");
            }
            normalized.add(name);
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        Map<String, Tag> byName = new HashMap<>();
        tagRepository.findAllByUserIdAndNameIn(userId, normalized).forEach(t -> byName.put(t.getName(), t));
        List<Tag> created = normalized.stream().filter(n -> !byName.containsKey(n))
                .map(n -> new Tag(userId, n)).toList();
        if (!created.isEmpty()) {
            tagRepository.saveAll(created).forEach(t -> byName.put(t.getName(), t));
        }
        List<Tag> result = new ArrayList<>();
        normalized.forEach(n -> result.add(byName.get(n)));
        return result;
    }

    private String validName(String raw) {
        String name = Texts.normalizeTag(raw);
        if (name.isEmpty()) {
            throw new BusinessRuleException("O nome da tag não pode ser vazio");
        }
        if (name.length() > Texts.MAX_TAG_LENGTH) {
            throw new BusinessRuleException("O nome da tag excede " + Texts.MAX_TAG_LENGTH + " caracteres");
        }
        return name;
    }

    private TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tagQueries.countNotes(tag.getId()));
    }
}

package com.example.flashcards_api.note;

import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.Texts;
import com.example.flashcards_api.tag.Tag;
import com.example.flashcards_api.tag.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolve nomes de tags informados pelo cliente em entidades {@link Tag} do usuário, criando as que não existem.
 * Normaliza, remove repetidas e valida o tamanho; é seguro contra criação concorrente da mesma tag.
 * Deve ser chamado dentro de uma transação.
 */
@Component
@RequiredArgsConstructor
public class NoteTagResolver {

    private final TagRepository tagRepository;
    private final TagCreator tagCreator;

    /**
     * Normaliza e valida os nomes (sem repetições, na ordem de entrada).
     *
     * @param names nomes crus (aceita nulo)
     * @return nomes normalizados e distintos
     * @throws BusinessRuleException se algum nome ficar vazio ou passar de 100 caracteres
     */
    public Set<String> normalize(Collection<String> names) {
        Set<String> result = new LinkedHashSet<>();
        if (names == null) {
            return result;
        }
        for (String raw : names) {
            String name = Texts.normalizeTag(raw);
            if (name.isEmpty()) {
                throw new BusinessRuleException("Tag não pode ser vazia");
            }
            if (name.length() > Texts.MAX_TAG_LENGTH) {
                throw new BusinessRuleException("Tag deve ter no máximo " + Texts.MAX_TAG_LENGTH + " caracteres");
            }
            result.add(name);
        }
        return result;
    }

    /**
     * Devolve as tags do usuário com os nomes informados, criando as inexistentes.
     *
     * @param userId dono das tags
     * @param names  nomes crus (aceita nulo)
     * @return tags gerenciadas pela transação atual
     * @throws BusinessRuleException se algum nome for inválido
     */
    public Set<Tag> resolve(Long userId, Collection<String> names) {
        Set<String> normalized = normalize(names);
        if (normalized.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Tag> existing = tagRepository.findAllByUserIdAndNameIn(userId, normalized);
        Set<String> found = new LinkedHashSet<>();
        existing.forEach(t -> found.add(t.getName()));
        boolean created = false;
        for (String name : normalized) {
            if (!found.contains(name)) {
                try {
                    tagCreator.create(userId, name);
                } catch (DataIntegrityViolationException e) {
                    // outra requisição criou a mesma tag primeiro: basta relê-la abaixo
                }
                created = true;
            }
        }
        List<Tag> all = created ? tagRepository.findAllByUserIdAndNameIn(userId, normalized) : existing;
        return new LinkedHashSet<>(all);
    }
}

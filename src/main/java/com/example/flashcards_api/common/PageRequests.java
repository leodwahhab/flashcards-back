package com.example.flashcards_api.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Construção validada de {@link Pageable} a partir dos parâmetros {@code page}, {@code size} e {@code sort}.
 */
public final class PageRequests {

    /** Tamanho de página padrão. */
    public static final int DEFAULT_SIZE = 20;
    /** Tamanho máximo de página. */
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /**
     * Valida e monta o {@link Pageable}.
     *
     * @param page          página (base 0); nulo equivale a 0
     * @param size          tamanho (1..100); nulo equivale a 20
     * @param sort          ordenação no formato {@code campo} ou {@code campo,asc|desc}; nulo usa {@code defaultSort}
     * @param allowedFields whitelist: nome público do campo para propriedade da entidade
     * @param defaultSort   ordenação usada quando {@code sort} é nulo ou vazio
     * @return pageable validado (com desempate por {@code id})
     * @throws BusinessRuleException se page/size/sort forem inválidos ou o campo não estiver na whitelist
     */
    public static Pageable of(Integer page, Integer size, String sort, Map<String, String> allowedFields,
                              Sort defaultSort) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) {
            throw new BusinessRuleException("page deve ser maior ou igual a 0");
        }
        if (s < 1 || s > MAX_SIZE) {
            throw new BusinessRuleException("size deve estar entre 1 e " + MAX_SIZE);
        }
        return PageRequest.of(p, s, parseSort(sort, allowedFields, defaultSort));
    }

    private static Sort parseSort(String sort, Map<String, String> allowedFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",", -1);
        String field = parts[0].trim();
        String property = allowedFields.get(field);
        if (property == null) {
            throw new BusinessRuleException("Campo de ordenação inválido: " + field);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            String dir = parts[1].trim();
            if (dir.equalsIgnoreCase("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!dir.equalsIgnoreCase("asc")) {
                throw new BusinessRuleException("Direção de ordenação inválida: " + dir);
            }
        }
        Sort result = Sort.by(direction, property);
        // desempate por id para paginação estável
        return property.equals("id") ? result : result.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}

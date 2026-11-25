package com.example.flashcards_api.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Resposta paginada padrão da API.
 *
 * @param items         itens da página
 * @param page          índice da página (base 0)
 * @param size          tamanho solicitado da página
 * @param totalElements total de elementos
 * @param totalPages    total de páginas
 * @param <T>           tipo do item
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    /**
     * Converte uma {@link Page} do Spring Data.
     *
     * @param page página de origem
     * @param <T>  tipo do item
     * @return resposta paginada
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Converte uma {@link Page} aplicando um mapeamento a cada item (entidade para DTO).
     *
     * @param page   página de origem
     * @param mapper função de conversão
     * @param <S>    tipo de origem
     * @param <T>    tipo de destino
     * @return resposta paginada
     */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return of(page.map(mapper));
    }
}

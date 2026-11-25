package com.example.flashcards_api.deck;

import java.util.List;

/**
 * Baralho na árvore devolvida pela API, com as contagens de hoje (incluindo subbaralhos).
 *
 * @param id            identificador
 * @param parentId      pai ou nulo
 * @param name          nome simples
 * @param fullName      caminho completo, ex.: {@code Pai::Filho}
 * @param optionsId     conjunto de opções
 * @param newCount      novos a estudar hoje
 * @param learningCount em aprendizado vencidos agora
 * @param reviewCount   revisões de hoje
 * @param children      subbaralhos ordenados por nome
 */
public record DeckNode(Long id, Long parentId, String name, String fullName, Long optionsId, long newCount,
                       long learningCount, long reviewCount, List<DeckNode> children) {
}

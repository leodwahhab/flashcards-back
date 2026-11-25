package com.example.flashcards_api.deck;

import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.ConflictException;
import com.example.flashcards_api.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Regras de negócio de baralhos: árvore com contagens, criação, movimentação e exclusão de subárvores.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DeckService {

    /** Profundidade máxima da árvore (um baralho raiz tem profundidade 1). */
    public static final int MAX_DEPTH = 8;
    private static final String SEPARATOR = "::";

    private final DeckRepository deckRepository;
    private final DeckOptionsRepository optionsRepository;
    private final DeckQueries queries;
    private final DeckCountsProvider countsProvider;
    private final UserDefaultsService userDefaultsService;

    /**
     * Árvore completa de baralhos do usuário com as contagens de hoje.
     *
     * @param userId dono
     * @return baralhos raiz (com filhos aninhados), ordenados por nome
     */
    @Transactional(readOnly = true)
    public List<DeckNode> tree(Long userId) {
        List<Deck> decks = deckRepository.findAllByUserId(userId);
        return buildTree(userId, decks).roots();
    }

    /**
     * Detalha um baralho (com subárvore e contagens).
     *
     * @param userId dono
     * @param id     baralho
     * @return nó do baralho
     * @throws NotFoundException se inexistente ou de outro usuário
     */
    @Transactional(readOnly = true)
    public DeckNode get(Long userId, Long id) {
        return nodeFor(userId, id, deckRepository.findAllByUserId(userId));
    }

    /**
     * Cria um baralho.
     *
     * @param userId  dono
     * @param request dados
     * @return baralho criado
     * @throws BusinessRuleException nome inválido ou profundidade acima de {@value #MAX_DEPTH}
     * @throws ConflictException     nome repetido entre irmãos
     * @throws NotFoundException     pai ou opções inexistentes (ou de outro usuário)
     */
    public DeckNode create(Long userId, CreateDeckRequest request) {
        String name = validName(request.name());
        Long optionsId = request.optionsId() != null
                ? findOptions(userId, request.optionsId()).getId()
                : defaultOptions(userId).getId();
        List<Deck> decks = deckRepository.findAllByUserId(userId);
        Map<Long, Deck> byId = index(decks);
        Long parentId = request.parentId();
        if (parentId != null) {
            if (!byId.containsKey(parentId)) {
                throw new NotFoundException("Baralho pai não encontrado");
            }
            if (depth(parentId, byId) + 1 > MAX_DEPTH) {
                throw new BusinessRuleException("Profundidade máxima de baralhos é " + MAX_DEPTH);
            }
        }
        assertSiblingNameFree(decks, parentId, name, null);

        Deck deck = new Deck();
        deck.setUserId(userId);
        deck.setName(name);
        deck.setParentId(parentId);
        deck.setOptionsId(optionsId);
        deck = deckRepository.saveAndFlush(deck);
        decks.add(deck);
        return nodeFor(userId, deck.getId(), decks);
    }

    /**
     * Altera nome, pai e/ou opções de um baralho. Mover para dentro de si mesmo ou de um descendente é proibido.
     *
     * @param userId dono
     * @param id     baralho
     * @param request campos a alterar
     * @return baralho atualizado
     * @throws BusinessRuleException ciclo, nome inválido ou profundidade excedida
     * @throws ConflictException     nome repetido entre irmãos
     */
    public DeckNode update(Long userId, Long id, UpdateDeckRequest request) {
        List<Deck> decks = deckRepository.findAllByUserId(userId);
        Map<Long, Deck> byId = index(decks);
        Deck deck = byId.get(id);
        if (deck == null) {
            throw new NotFoundException("Baralho não encontrado");
        }
        String name = request.name() != null ? validName(request.name()) : deck.getName();
        Long parentId = deck.getParentId();
        if (request.parentId() != null) {
            parentId = request.parentId().orElse(null);
        }
        if (!Objects.equals(parentId, deck.getParentId())) {
            if (parentId != null) {
                if (!byId.containsKey(parentId)) {
                    throw new NotFoundException("Baralho pai não encontrado");
                }
                if (parentId.equals(id) || subtreeIds(id, decks).contains(parentId)) {
                    throw new BusinessRuleException("Um baralho não pode ser movido para dentro de si mesmo "
                            + "ou de um descendente");
                }
            }
            int newDepth = (parentId == null ? 0 : depth(parentId, byId)) + height(id, decks);
            if (newDepth > MAX_DEPTH) {
                throw new BusinessRuleException("Profundidade máxima de baralhos é " + MAX_DEPTH);
            }
        }
        if (!Objects.equals(parentId, deck.getParentId()) || !name.equalsIgnoreCase(deck.getName())) {
            assertSiblingNameFree(decks, parentId, name, id);
        }
        if (request.optionsId() != null) {
            deck.setOptionsId(findOptions(userId, request.optionsId()).getId());
        }
        deck.setName(name);
        deck.setParentId(parentId);
        deckRepository.saveAndFlush(deck);
        return nodeFor(userId, id, decks);
    }

    /**
     * Remove o baralho, seus subbaralhos e todos os cards deles; notes que ficarem sem cards também são removidos.
     * O baralho {@code Padrão} não pode ser removido se for o último baralho raiz do usuário.
     *
     * @param userId dono
     * @param id     baralho
     * @throws BusinessRuleException se for o baralho padrão e último raiz
     */
    public void delete(Long userId, Long id) {
        List<Deck> decks = deckRepository.findAllByUserId(userId);
        Deck deck = index(decks).get(id);
        if (deck == null) {
            throw new NotFoundException("Baralho não encontrado");
        }
        long roots = decks.stream().filter(d -> d.getParentId() == null).count();
        if (deck.getParentId() == null && roots == 1
                && UserDefaultsService.DEFAULT_NAME.equalsIgnoreCase(deck.getName())) {
            throw new BusinessRuleException("O baralho padrão não pode ser removido enquanto for o único baralho raiz");
        }
        Set<Long> ids = subtreeIds(id, decks);
        List<Long> noteIds = queries.findNoteIdsByDeckIds(userId, ids);
        queries.deleteCardsByDeckIds(userId, ids);
        if (!noteIds.isEmpty()) {
            List<Long> orphans = queries.findOrphanNoteIds(userId, noteIds);
            if (!orphans.isEmpty()) {
                queries.deleteNoteTags(orphans);
                queries.deleteNotes(userId, orphans);
            }
        }
        deckRepository.deleteAllByIdInBatch(ids);
    }

    // ---------------------------------------------------------------------------------------------

    private record Tree(List<DeckNode> roots, Map<Long, DeckNode> nodes) {
    }

    private DeckNode nodeFor(Long userId, Long id, List<Deck> decks) {
        DeckNode node = buildTree(userId, decks).nodes().get(id);
        if (node == null) {
            throw new NotFoundException("Baralho não encontrado");
        }
        return node;
    }

    private Tree buildTree(Long userId, List<Deck> decks) {
        Map<Long, DeckCounts> counts = countsProvider.countsFor(userId, decks);
        Map<Long, List<Deck>> children = new HashMap<>();
        List<Deck> roots = new ArrayList<>();
        for (Deck d : decks) {
            if (d.getParentId() == null) {
                roots.add(d);
            } else {
                children.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d);
            }
        }
        Map<Long, DeckNode> nodes = new HashMap<>();
        List<DeckNode> rootNodes = roots.stream().sorted(BY_NAME)
                .map(d -> toNode(d, null, children, counts, nodes)).toList();
        return new Tree(rootNodes, nodes);
    }

    private static final Comparator<Deck> BY_NAME = Comparator.comparing((Deck d) -> d.getName().toLowerCase())
            .thenComparing(Deck::getId);

    private DeckNode toNode(Deck deck, String parentFullName, Map<Long, List<Deck>> children,
                            Map<Long, DeckCounts> counts, Map<Long, DeckNode> nodes) {
        String fullName = parentFullName == null ? deck.getName() : parentFullName + SEPARATOR + deck.getName();
        List<DeckNode> kids = children.getOrDefault(deck.getId(), List.of()).stream().sorted(BY_NAME)
                .map(c -> toNode(c, fullName, children, counts, nodes)).toList();
        DeckCounts c = counts.getOrDefault(deck.getId(), DeckCounts.ZERO);
        DeckNode node = new DeckNode(deck.getId(), deck.getParentId(), deck.getName(), fullName, deck.getOptionsId(),
                c.newCount(), c.learningCount(), c.reviewCount(), kids);
        nodes.put(deck.getId(), node);
        return node;
    }

    private String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BusinessRuleException("O nome do baralho não pode ser vazio");
        }
        if (name.length() > 255) {
            throw new BusinessRuleException("O nome do baralho excede 255 caracteres");
        }
        if (name.contains(SEPARATOR)) {
            throw new BusinessRuleException("O nome do baralho não pode conter '" + SEPARATOR + "'");
        }
        return name;
    }

    private void assertSiblingNameFree(List<Deck> decks, Long parentId, String name, Long ignoreId) {
        boolean taken = decks.stream().anyMatch(d -> !Objects.equals(d.getId(), ignoreId)
                && Objects.equals(d.getParentId(), parentId) && d.getName().equalsIgnoreCase(name));
        if (taken) {
            throw new ConflictException("Já existe um baralho com este nome no mesmo nível");
        }
    }

    private DeckOptions findOptions(Long userId, Long optionsId) {
        return optionsRepository.findByIdAndUserId(optionsId, userId)
                .orElseThrow(() -> new NotFoundException("Conjunto de opções não encontrado"));
    }

    private DeckOptions defaultOptions(Long userId) {
        return optionsRepository.findFirstByUserIdAndDefaultOptionsTrue(userId)
                .orElseGet(() -> userDefaultsService.ensureDefaults(userId).options());
    }

    private static Map<Long, Deck> index(List<Deck> decks) {
        Map<Long, Deck> map = new HashMap<>();
        decks.forEach(d -> map.put(d.getId(), d));
        return map;
    }

    /** Profundidade do baralho (raiz = 1). */
    private static int depth(Long id, Map<Long, Deck> byId) {
        int depth = 0;
        Long current = id;
        while (current != null && depth <= MAX_DEPTH + 64) { // limite defensivo contra dados corrompidos
            depth++;
            Deck d = byId.get(current);
            current = d == null ? null : d.getParentId();
        }
        return depth;
    }

    /** Altura da subárvore (o baralho sozinho = 1). */
    private static int height(Long id, List<Deck> decks) {
        int max = 0;
        for (Deck d : decks) {
            if (Objects.equals(d.getParentId(), id)) {
                max = Math.max(max, height(d.getId(), decks));
            }
        }
        return max + 1;
    }

    /** Ids do baralho e de todos os seus descendentes. */
    private static Set<Long> subtreeIds(Long id, List<Deck> decks) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(id);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Deck d : decks) {
                if (d.getParentId() != null && ids.contains(d.getParentId()) && ids.add(d.getId())) {
                    grew = true;
                }
            }
        }
        return ids;
    }
}

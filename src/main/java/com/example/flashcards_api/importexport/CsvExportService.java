package com.example.flashcards_api.importexport;

import com.example.flashcards_api.common.NotFoundException;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exportação de notes em CSV (UTF-8, cabeçalho {@code deck,noteType,front,back,tags,imageUrl}), em blocos de
 * {@value #CHUNK} notes por consulta, escrevendo direto no destino.
 * <p>
 * <b>Injeção de fórmulas:</b> os valores são exportados exatamente como estão (sem prefixar {@code '} em células que
 * começam com {@code = + - @}), porque a importação precisa restaurar o conteúdo original. Quem abrir o arquivo em
 * uma planilha deve importá-lo como texto; abrir um CSV com conteúdo não confiável diretamente no Excel/Sheets pode
 * executar fórmulas.
 */
@Service
@RequiredArgsConstructor
public class CsvExportService {

    private static final int CHUNK = 500;
    private static final String[] HEADER = {"deck", "noteType", "front", "back", "tags", "imageUrl"};

    private final DeckRepository deckRepository;
    private final ImportExportRepository importExportRepository;

    /**
     * Plano de exportação já validado.
     *
     * @param userId    dono
     * @param deckIds   baralhos incluídos (o escolhido e seus subbaralhos, ou todos)
     * @param deckPaths caminho completo ({@code Pai::Filho}) de cada baralho do usuário
     */
    public record Plan(Long userId, Set<Long> deckIds, Map<Long, String> deckPaths) {
    }

    /**
     * Valida o baralho e resolve os baralhos incluídos e seus caminhos. Deve ser chamado antes de começar a escrever
     * a resposta, para que um erro (404) ainda possa virar uma resposta de problema.
     *
     * @param userId dono
     * @param deckId baralho a exportar com seus subbaralhos (nulo = todos)
     * @return plano de exportação
     * @throws NotFoundException se o baralho não existir ou for de outro usuário
     */
    public Plan plan(Long userId, Long deckId) {
        List<Deck> decks = deckRepository.findAllByUserId(userId);
        Map<Long, Deck> byId = new HashMap<>();
        Map<Long, List<Long>> children = new HashMap<>();
        for (Deck deck : decks) {
            byId.put(deck.getId(), deck);
            children.computeIfAbsent(deck.getParentId() == null ? 0L : deck.getParentId(), k -> new ArrayList<>())
                    .add(deck.getId());
        }
        Map<Long, String> paths = new HashMap<>();
        for (Deck deck : decks) {
            paths.put(deck.getId(), path(deck, byId));
        }
        Set<Long> included = new HashSet<>();
        if (deckId == null) {
            included.addAll(byId.keySet());
        } else {
            if (!byId.containsKey(deckId)) {
                throw new NotFoundException("Baralho não encontrado");
            }
            List<Long> pending = new ArrayList<>(List.of(deckId));
            while (!pending.isEmpty()) {
                Long id = pending.remove(pending.size() - 1);
                if (included.add(id)) {
                    pending.addAll(children.getOrDefault(id, List.of()));
                }
            }
        }
        return new Plan(userId, included, paths);
    }

    /**
     * Escreve o CSV no destino, em blocos.
     *
     * @param plan plano devolvido por {@link #plan(Long, Long)}
     * @param out  destino (não é fechado aqui, mas é descarregado ao final)
     * @throws IOException se a escrita falhar
     */
    public void write(Plan plan, Appendable out) throws IOException {
        CSVPrinter printer = new CSVPrinter(out, CSVFormat.RFC4180.builder().setHeader(HEADER).build());
        long afterId = 0;
        while (!plan.deckIds().isEmpty()) {
            List<NoteRow> rows = importExportRepository.findNoteRows(plan.userId(), plan.deckIds(), afterId,
                    PageRequest.of(0, CHUNK));
            if (rows.isEmpty()) {
                break;
            }
            Map<Long, List<String>> tags = tagsOf(rows.stream().map(NoteRow::noteId).toList());
            for (NoteRow row : rows) {
                printer.printRecord(plan.deckPaths().get(row.deckId()), row.noteType().name(), row.front(),
                        row.back(), String.join(" ", tags.getOrDefault(row.noteId(), List.of())),
                        row.imageUrl() == null ? "" : row.imageUrl());
            }
            afterId = rows.get(rows.size() - 1).noteId();
            printer.flush();
            if (rows.size() < CHUNK) {
                break;
            }
        }
        printer.flush();
    }

    private Map<Long, List<String>> tagsOf(Collection<Long> noteIds) {
        Map<Long, List<String>> result = new HashMap<>();
        for (Object[] row : importExportRepository.findTagRows(noteIds)) {
            result.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }
        return result;
    }

    private static String path(Deck deck, Map<Long, Deck> byId) {
        StringBuilder path = new StringBuilder(deck.getName());
        Deck current = deck;
        int guard = 0;
        while (current.getParentId() != null && guard++ < 64) {
            current = byId.get(current.getParentId());
            if (current == null) {
                break;
            }
            path.insert(0, current.getName() + "::");
        }
        return path.toString();
    }
}

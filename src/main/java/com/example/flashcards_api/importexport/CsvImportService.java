package com.example.flashcards_api.importexport;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.common.AppProperties;
import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.NotFoundException;
import com.example.flashcards_api.common.Texts;
import com.example.flashcards_api.deck.Deck;
import com.example.flashcards_api.deck.DeckOptions;
import com.example.flashcards_api.deck.DeckOptionsRepository;
import com.example.flashcards_api.deck.DeckRepository;
import com.example.flashcards_api.deck.UserDefaultsService;
import com.example.flashcards_api.note.Note;
import com.example.flashcards_api.note.NoteRepository;
import com.example.flashcards_api.note.NoteType;
import com.example.flashcards_api.tag.Tag;
import com.example.flashcards_api.tag.TagRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponseException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importação de notes a partir de CSV. Tudo ou nada: as linhas são lidas e validadas por completo antes de qualquer
 * gravação e a gravação ocorre em uma única transação.
 * <p>
 * Formato: UTF-8 (BOM removido), campos entre aspas com quebras de linha e aspas duplicadas ({@code ""}), delimitador
 * vírgula, ponto e vírgula ou tabulação (detectado quando não informado). Colunas reconhecidas pelo cabeçalho
 * (sem diferenciar maiúsculas): {@code front}, {@code back}, {@code tags} (separadas por espaço), {@code deck}
 * (caminho {@code A::B}), {@code noteType} e {@code imageUrl}. Sem cabeçalho, a ordem é front, back, tags, deck.
 */
@Service
@RequiredArgsConstructor
public class CsvImportService {

    /** Máximo de linhas de dados por importação. */
    public static final int MAX_ROWS = 10_000;

    private static final int MAX_DECK_DEPTH = 8;
    private static final int DUP_QUERY_CHUNK = 500;
    private static final Pattern CLOZE = Pattern.compile("\\{\\{c([1-9]\\d{0,2})::");
    private static final Set<String> HEADER_NAMES = Set.of("front", "back", "tags", "deck", "notetype", "note_type",
            "type", "imageurl", "image_url", "image", "frente", "verso");

    private final DeckRepository deckRepository;
    private final DeckOptionsRepository deckOptionsRepository;
    private final NoteRepository noteRepository;
    private final CardRepository cardRepository;
    private final TagRepository tagRepository;
    private final ImportExportRepository importExportRepository;
    private final UserDefaultsService userDefaultsService;
    private final AppProperties properties;
    private final Clock clock;

    /**
     * Parâmetros da importação.
     *
     * @param deckId      baralho de destino (nulo = baralho padrão do usuário)
     * @param noteType    tipo padrão dos notes (nulo ou vazio = {@code BASIC})
     * @param delimiter   delimitador ({@code ,} {@code ;} tabulação, ou nulo/{@code auto} para detectar)
     * @param hasHeader   se a primeira linha é cabeçalho (nulo = detectar)
     * @param skipInvalid se verdadeiro, linhas inválidas são ignoradas e relatadas em vez de abortar tudo
     */
    public record Options(Long deckId, String noteType, String delimiter, Boolean hasHeader, boolean skipInvalid) {
    }

    /**
     * Erro de uma linha.
     *
     * @param row     número do registro no arquivo (o cabeçalho, se houver, é o registro 1)
     * @param message descrição do problema
     */
    public record RowError(int row, String message) {
    }

    /**
     * Resultado da importação.
     *
     * @param imported          notes criados
     * @param skippedDuplicates linhas ignoradas por duplicidade (mesmo tipo e mesma frente normalizada)
     * @param errors            linhas inválidas ignoradas (só com {@code skipInvalid=true})
     * @param duplicateRows     números dos registros ignorados por duplicidade
     * @param cardsCreated      cards criados
     * @param decksCreated      baralhos criados a partir da coluna {@code deck}
     */
    public record Result(int imported, int skippedDuplicates, List<RowError> errors, List<Integer> duplicateRows,
                         int cardsCreated, int decksCreated) {
    }

    private record ParsedRow(int row, NoteType type, String front, String back, String imageUrl,
                             Set<String> tags, List<String> deckPath, String dupKey) {
    }

    /**
     * Importa o conteúdo de um CSV.
     *
     * @param userId  dono
     * @param content bytes do arquivo
     * @param options parâmetros
     * @return contagens e relatório de erros
     * @throws NotFoundException       se o baralho não existir ou for de outro usuário
     * @throws BusinessRuleException   para arquivo binário/inválido, parâmetros inválidos ou mais de
     *                                 {@value #MAX_ROWS} linhas
     * @throws ErrorResponseException  422 com a lista de linhas inválidas, quando há erros e {@code skipInvalid}
     *                                 é falso (nada é importado)
     * @throws ResponseStatusException 413 se o arquivo exceder {@code app.media.max-bytes}
     */
    @Transactional
    public Result importCsv(Long userId, byte[] content, Options options) {
        long max = properties.media().maxBytes();
        if (content.length > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Arquivo excede o tamanho máximo de " + max + " bytes");
        }
        if (content.length == 0) {
            throw new BusinessRuleException("Arquivo ausente ou vazio");
        }
        NoteType defaultType = parseType(options.noteType(), NoteType.BASIC);
        UserDefaultsService.UserDefaults defaults = userDefaultsService.ensureDefaults(userId);
        Deck baseDeck = options.deckId() == null ? defaults.deck()
                : deckRepository.findByIdAndUserId(options.deckId(), userId)
                .orElseThrow(() -> new NotFoundException("Baralho não encontrado"));

        String text = decode(content);
        char delimiter = resolveDelimiter(options.delimiter(), text);
        List<CSVRecord> records = parse(text, delimiter);

        // --- cabeçalho
        Map<String, Integer> columns = new HashMap<>();
        int firstData = 0;
        if (!records.isEmpty()) {
            boolean header = options.hasHeader() != null ? options.hasHeader() : looksLikeHeader(records.get(0));
            if (header) {
                firstData = 1;
                for (int i = 0; i < records.get(0).size(); i++) {
                    columns.putIfAbsent(canonical(records.get(0).get(i)), i);
                }
                if (!columns.containsKey("front")) {
                    throw new BusinessRuleException("O cabeçalho precisa ter a coluna 'front'");
                }
            } else {
                columns.put("front", 0);
                columns.put("back", 1);
                columns.put("tags", 2);
                columns.put("deck", 3);
            }
        }
        if (records.size() - firstData > MAX_ROWS) {
            throw new BusinessRuleException("O arquivo excede o máximo de " + MAX_ROWS + " linhas");
        }

        // --- leitura e validação (nada é gravado aqui)
        List<ParsedRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (int i = firstData; i < records.size(); i++) {
            CSVRecord rec = records.get(i);
            if (isBlankRecord(rec)) {
                continue;
            }
            try {
                rows.add(parseRow(rec, columns, defaultType));
            } catch (RowException e) {
                errors.add(new RowError((int) rec.getRecordNumber(), e.getMessage()));
            }
        }
        if (!errors.isEmpty() && !options.skipInvalid()) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O arquivo tem " + errors.size() + " linha(s) inválida(s); nada foi importado");
            pd.setTitle("Importação inválida");
            pd.setProperty("errors", errors);
            throw new ErrorResponseException(HttpStatus.UNPROCESSABLE_ENTITY, pd, null);
        }

        // --- duplicidades (no banco e dentro do próprio arquivo)
        Set<String> existing = findExistingDupKeys(userId, rows);
        Set<String> seen = new HashSet<>();
        List<ParsedRow> toImport = new ArrayList<>();
        List<Integer> duplicateRows = new ArrayList<>();
        for (ParsedRow row : rows) {
            String key = row.type() + "|" + row.dupKey();
            if (existing.contains(key) || !seen.add(key)) {
                duplicateRows.add(row.row());
            } else {
                toImport.add(row);
            }
        }

        // --- gravação
        Writer writer = new Writer(userId, baseDeck, defaults.options());
        for (ParsedRow row : toImport) {
            writer.write(row);
        }
        return new Result(toImport.size(), duplicateRows.size(), errors, duplicateRows, writer.cards,
                writer.decksCreated);
    }

    /** Estado da fase de gravação: caches de baralhos, tags e opções, e a próxima posição de cards novos. */
    private final class Writer {
        private final Long userId;
        private final Deck baseDeck;
        private final DeckOptions defaultOptions;
        private final Map<String, Deck> deckByPath = new HashMap<>();
        private final Map<Long, DeckOptions> optionsById = new HashMap<>();
        private final Map<String, Tag> tagByName = new HashMap<>();
        private long position;
        int cards;
        int decksCreated;

        Writer(Long userId, Deck baseDeck, DeckOptions defaultOptions) {
            this.userId = userId;
            this.baseDeck = baseDeck;
            this.defaultOptions = defaultOptions;
            this.position = cardRepository.findMaxPositionByUserId(userId);
        }

        void write(ParsedRow row) {
            Deck deck = row.deckPath().isEmpty() ? baseDeck : resolveDeck(row.deckPath());
            Note note = new Note();
            note.setUserId(userId);
            note.setNoteType(row.type());
            note.setFront(row.front());
            note.setBack(row.back());
            note.setImageUrl(row.imageUrl());
            for (String name : row.tags()) {
                note.getTags().add(tag(name));
            }
            note = noteRepository.save(note);

            int ease = optionsById.computeIfAbsent(deck.getOptionsId(),
                    id -> deckOptionsRepository.findById(id).orElse(defaultOptions)).getStartingEase();
            List<Card> created = new ArrayList<>();
            for (int ordinal : ordinals(row)) {
                Card card = new Card();
                card.setUserId(userId);
                card.setNoteId(note.getId());
                card.setDeckId(deck.getId());
                card.setOrdinal(ordinal);
                card.setState(CardState.NEW);
                card.setPosition(++position);
                card.setDueAt(clock.instant());
                card.setEasePermille(ease);
                created.add(card);
            }
            cardRepository.saveAll(created);
            cards += created.size();
        }

        private Tag tag(String name) {
            return tagByName.computeIfAbsent(name, n -> tagRepository.findByUserIdAndName(userId, n)
                    .orElseGet(() -> tagRepository.save(new Tag(userId, n))));
        }

        /** Acha ou cria, nível a nível, o caminho de baralhos (a partir da raiz), com as opções padrão. */
        private Deck resolveDeck(List<String> path) {
            String key = String.join("::", path).toLowerCase(Locale.ROOT);
            Deck cached = deckByPath.get(key);
            if (cached != null) {
                return cached;
            }
            Long parentId = null;
            Deck current = null;
            StringBuilder prefix = new StringBuilder();
            for (String segment : path) {
                prefix.append(prefix.isEmpty() ? "" : "::").append(segment.toLowerCase(Locale.ROOT));
                current = deckByPath.get(prefix.toString());
                if (current == null) {
                    Long parent = parentId;
                    current = deckRepository.findByUserIdAndParentIdAndNameIgnoreCase(userId, parent, segment)
                            .orElseGet(() -> {
                                Deck deck = new Deck();
                                deck.setUserId(userId);
                                deck.setParentId(parent);
                                deck.setName(segment);
                                deck.setOptionsId(defaultOptions.getId());
                                decksCreated++;
                                return deckRepository.save(deck);
                            });
                    deckByPath.put(prefix.toString(), current);
                }
                parentId = current.getId();
            }
            return current;
        }
    }

    /** Ordinais dos cards de um note: BASIC/TYPE_IN 0; BASIC_REVERSED 0 e 1; CLOZE n-1 por número distinto. */
    static List<Integer> ordinals(ParsedRow row) {
        return switch (row.type()) {
            case BASIC_REVERSED -> List.of(0, 1);
            case CLOZE -> clozeNumbers(row.front()).stream().map(n -> n - 1).toList();
            default -> List.of(0);
        };
    }

    private static TreeSet<Integer> clozeNumbers(String front) {
        TreeSet<Integer> numbers = new TreeSet<>();
        Matcher m = CLOZE.matcher(front);
        while (m.find()) {
            numbers.add(Integer.valueOf(m.group(1)));
        }
        return numbers;
    }

    private Set<String> findExistingDupKeys(Long userId, List<ParsedRow> rows) {
        List<String> keys = rows.stream().map(ParsedRow::dupKey).distinct().toList();
        Set<String> existing = new HashSet<>();
        for (int i = 0; i < keys.size(); i += DUP_QUERY_CHUNK) {
            List<String> chunk = keys.subList(i, Math.min(keys.size(), i + DUP_QUERY_CHUNK));
            for (Object[] pair : importExportRepository.findTypeAndDupKey(userId, chunk)) {
                existing.add(pair[0] + "|" + pair[1]);
            }
        }
        return existing;
    }

    private ParsedRow parseRow(CSVRecord rec, Map<String, Integer> columns, NoteType defaultType) {
        NoteType type = defaultType;
        String typeCell = cell(rec, columns, "notetype", "note_type", "type");
        if (!typeCell.isBlank()) {
            try {
                type = parseType(typeCell, defaultType);
            } catch (BusinessRuleException e) {
                throw new RowException("Tipo de nota inválido: '" + typeCell.trim() + "'");
            }
        }
        String front = cell(rec, columns, "front", "frente");
        String back = cell(rec, columns, "back", "verso");
        String imageUrl = cell(rec, columns, "imageurl", "image_url", "image").trim();
        if (front.isBlank() && imageUrl.isEmpty()) {
            throw new RowException("A frente (front) está vazia");
        }
        if (imageUrl.length() > 2048) {
            throw new RowException("imageUrl excede 2048 caracteres");
        }
        if (type == NoteType.CLOZE && clozeNumbers(front).isEmpty()) {
            throw new RowException("Nota CLOZE sem lacuna no formato {{c1::resposta}}");
        }
        Set<String> tags = new LinkedHashSet<>();
        String tagCell = cell(rec, columns, "tags").trim();
        if (!tagCell.isEmpty()) {
            for (String raw : tagCell.split("\\s+")) {
                String tag = Texts.normalizeTag(raw);
                if (tag.length() > Texts.MAX_TAG_LENGTH) {
                    throw new RowException("Tag excede " + Texts.MAX_TAG_LENGTH + " caracteres");
                }
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        List<String> path = new ArrayList<>();
        String deckCell = cell(rec, columns, "deck").trim();
        if (!deckCell.isEmpty()) {
            for (String segment : deckCell.split("::", -1)) {
                String name = segment.trim();
                if (name.isEmpty() || name.length() > 255) {
                    throw new RowException("Caminho de baralho inválido: '" + deckCell + "'");
                }
                path.add(name);
            }
            if (path.size() > MAX_DECK_DEPTH) {
                throw new RowException("Caminho de baralho excede " + MAX_DECK_DEPTH + " níveis");
            }
        }
        return new ParsedRow((int) rec.getRecordNumber(), type, front, back, imageUrl.isEmpty() ? null : imageUrl,
                tags, path, Texts.dupKey(front));
    }

    private static String cell(CSVRecord rec, Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(name);
            if (index != null && index < rec.size()) {
                return rec.get(index);
            }
        }
        return "";
    }

    private static boolean isBlankRecord(CSVRecord rec) {
        for (String value : rec) {
            if (!value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String canonical(String header) {
        String name = header.trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "frente" -> "front";
            case "verso" -> "back";
            default -> name;
        };
    }

    private static boolean looksLikeHeader(CSVRecord first) {
        boolean known = false;
        for (String value : first) {
            if (value.isBlank()) {
                continue;
            }
            if (!HEADER_NAMES.contains(value.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
            known = true;
        }
        return known;
    }

    private static NoteType parseType(String value, NoteType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return NoteType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Tipo de nota inválido: '" + value.trim() + "' (aceitos: "
                    + Arrays.toString(NoteType.values()) + ")");
        }
    }

    /** Decodifica como UTF-8 estrito (recusa binário e bytes inválidos) e remove o BOM. */
    private static String decode(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                throw new BusinessRuleException("O arquivo parece ser binário, não um CSV de texto");
            }
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new BusinessRuleException("O arquivo não está em UTF-8 válido");
        }
    }

    private static char resolveDelimiter(String requested, String text) {
        if (requested != null && !requested.isEmpty() && !requested.equalsIgnoreCase("auto")) {
            return switch (requested.toLowerCase(Locale.ROOT)) {
                case ",", "comma", "virgula", "vírgula" -> ',';
                case ";", "semicolon", "ponto-e-virgula" -> ';';
                case "\t", "tab", "tabulacao", "tabulação" -> '\t';
                default -> throw new BusinessRuleException("Delimitador inválido: use ',', ';' ou tabulação");
            };
        }
        // Escolhe o candidato que produz mais colunas na primeira linha de dados (respeitando aspas).
        char best = ',';
        int bestColumns = 1;
        for (char candidate : new char[]{',', ';', '\t'}) {
            int columns = firstRecordColumns(text, candidate);
            if (columns > bestColumns) {
                best = candidate;
                bestColumns = columns;
            }
        }
        return best;
    }

    /**
     * Pontuação de um delimitador candidato nos primeiros registros: número de colunas, com bônus quando todos
     * os registros amostrados têm o mesmo número de colunas (evita confundir vírgulas do texto com o delimitador).
     */
    private static int firstRecordColumns(String text, char delimiter) {
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format(delimiter))) {
            int first = -1;
            boolean consistent = true;
            int count = 0;
            for (CSVRecord rec : parser) {
                if (first < 0) {
                    first = rec.size();
                } else if (rec.size() != first) {
                    consistent = false;
                }
                if (++count >= 20) {
                    break;
                }
            }
            return first <= 1 ? 1 : first + (consistent ? 1000 : 0);
        } catch (IOException | RuntimeException e) {
            return 1;
        }
    }

    private static CSVFormat format(char delimiter) {
        return CSVFormat.RFC4180.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true).build();
    }

    private static List<CSVRecord> parse(String text, char delimiter) {
        List<CSVRecord> records = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format(delimiter))) {
            for (CSVRecord rec : parser) {
                records.add(rec);
                if (records.size() > MAX_ROWS + 1) {
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new BusinessRuleException("CSV malformado: " + e.getMessage());
        }
        return records;
    }

    /** Erro de validação de uma linha (vira {@link RowError}). */
    private static final class RowException extends RuntimeException {
        RowException(String message) {
            super(message, null, false, false);
        }
    }
}

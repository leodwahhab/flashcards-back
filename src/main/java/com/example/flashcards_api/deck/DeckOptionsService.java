package com.example.flashcards_api.deck;

import com.example.flashcards_api.common.BusinessRuleException;
import com.example.flashcards_api.common.ConflictException;
import com.example.flashcards_api.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Regras de negócio dos conjuntos de opções de baralho ({@link DeckOptions}).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DeckOptionsService {

    private static final int MAX_STEPS = 10;
    private static final int MAX_STEP_MINUTES = 40_320; // 28 dias

    private final DeckOptionsRepository repository;
    private final DeckRepository deckRepository;
    private final UserDefaultsService userDefaultsService;

    /**
     * Lista as opções do usuário (garante que o padrão exista), ordenadas por nome.
     *
     * @param userId dono
     * @return opções do usuário
     */
    public List<DeckOptionsResponse> list(Long userId) {
        userDefaultsService.ensureDefaults(userId);
        return repository.findAllByUserId(userId).stream()
                .sorted(Comparator.comparing(o -> o.getName().toLowerCase()))
                .map(DeckOptionsResponse::from).toList();
    }

    /**
     * Busca um conjunto do usuário.
     *
     * @param userId dono
     * @param id     id das opções
     * @return opções
     * @throws NotFoundException se inexistente ou de outro usuário
     */
    public DeckOptionsResponse get(Long userId, Long id) {
        return DeckOptionsResponse.from(find(userId, id));
    }

    /**
     * Cria um conjunto; campos omitidos assumem os padrões do contrato.
     *
     * @param userId  dono
     * @param request dados
     * @return opções criadas
     * @throws ConflictException     se o nome já existir
     * @throws BusinessRuleException se algum valor violar as regras cruzadas
     */
    public DeckOptionsResponse create(Long userId, DeckOptionsRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new BusinessRuleException("O nome não pode ser vazio");
        }
        if (repository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("Já existe um conjunto de opções com este nome");
        }
        DeckOptions options = new DeckOptions();
        options.setUserId(userId);
        options.setName(name);
        apply(options, request);
        return DeckOptionsResponse.from(repository.save(options));
    }

    /**
     * Atualiza um conjunto; campos nulos mantêm o valor atual.
     *
     * @param userId  dono
     * @param id      id das opções
     * @param request dados
     * @return opções atualizadas
     * @throws ConflictException se o novo nome já existir
     */
    public DeckOptionsResponse update(Long userId, Long id, DeckOptionsRequest request) {
        DeckOptions options = find(userId, id);
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new BusinessRuleException("O nome não pode ser vazio");
        }
        if (repository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
            throw new ConflictException("Já existe um conjunto de opções com este nome");
        }
        options.setName(name);
        apply(options, request);
        return DeckOptionsResponse.from(repository.save(options));
    }

    /**
     * Remove um conjunto. O padrão não pode ser removido (422) e um conjunto em uso por baralhos também não (409).
     *
     * @param userId dono
     * @param id     id das opções
     * @throws BusinessRuleException se for o conjunto padrão
     * @throws ConflictException     se algum baralho o usar
     */
    public void delete(Long userId, Long id) {
        DeckOptions options = find(userId, id);
        if (options.isDefaultOptions()) {
            throw new BusinessRuleException("O conjunto de opções padrão não pode ser removido");
        }
        if (deckRepository.existsByOptionsId(id)) {
            throw new ConflictException("O conjunto de opções está em uso por baralhos");
        }
        repository.delete(options);
    }

    /**
     * Torna o conjunto o padrão do usuário (o anterior deixa de ser).
     *
     * @param userId dono
     * @param id     id das opções
     * @return opções agora padrão
     */
    public DeckOptionsResponse makeDefault(Long userId, Long id) {
        DeckOptions target = find(userId, id);
        if (!target.isDefaultOptions()) {
            List<DeckOptions> previous = repository.findAllByUserId(userId).stream()
                    .filter(DeckOptions::isDefaultOptions).toList();
            previous.forEach(o -> o.setDefaultOptions(false));
            repository.saveAllAndFlush(previous);
            target.setDefaultOptions(true);
            repository.save(target);
        }
        return DeckOptionsResponse.from(target);
    }

    private DeckOptions find(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Conjunto de opções não encontrado"));
    }

    private void apply(DeckOptions o, DeckOptionsRequest r) {
        if (r.newPerDay() != null) o.setNewPerDay(r.newPerDay());
        if (r.reviewsPerDay() != null) o.setReviewsPerDay(r.reviewsPerDay());
        if (r.learningSteps() != null) o.setLearningSteps(normalizeSteps("learningSteps", r.learningSteps(), true));
        if (r.relearningSteps() != null) {
            o.setRelearningSteps(normalizeSteps("relearningSteps", r.relearningSteps(), false));
        }
        if (r.graduatingIntervalDays() != null) o.setGraduatingIntervalDays(r.graduatingIntervalDays());
        if (r.easyIntervalDays() != null) o.setEasyIntervalDays(r.easyIntervalDays());
        if (r.startingEase() != null) o.setStartingEase(r.startingEase());
        if (r.easyBonus() != null) o.setEasyBonus(r.easyBonus());
        if (r.hardInterval() != null) o.setHardInterval(r.hardInterval());
        if (r.intervalModifier() != null) o.setIntervalModifier(r.intervalModifier());
        if (r.lapseNewInterval() != null) o.setLapseNewInterval(r.lapseNewInterval());
        if (r.minimumInterval() != null) o.setMinimumInterval(r.minimumInterval());
        if (r.maximumInterval() != null) o.setMaximumInterval(r.maximumInterval());
        if (r.leechThreshold() != null) o.setLeechThreshold(r.leechThreshold());
        if (r.burySiblings() != null) o.setBurySiblings(r.burySiblings());

        if (o.getEasyIntervalDays() < o.getGraduatingIntervalDays()) {
            throw new BusinessRuleException("easyIntervalDays não pode ser menor que graduatingIntervalDays");
        }
        if (o.getMaximumInterval() < o.getMinimumInterval()) {
            throw new BusinessRuleException("maximumInterval não pode ser menor que minimumInterval");
        }
    }

    /** Valida e normaliza passos ("1  10" vira "1 10"): inteiros positivos, não decrescentes, até 10. */
    private String normalizeSteps(String field, String raw, boolean required) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                throw new BusinessRuleException(field + " deve ter ao menos um passo");
            }
            return "";
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length > MAX_STEPS) {
            throw new BusinessRuleException(field + " aceita no máximo " + MAX_STEPS + " passos");
        }
        List<Integer> values = new ArrayList<>();
        for (String part : parts) {
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new BusinessRuleException(field + " deve conter apenas inteiros separados por espaço");
            }
            if (value < 1 || value > MAX_STEP_MINUTES) {
                throw new BusinessRuleException(field + ": cada passo deve estar entre 1 e " + MAX_STEP_MINUTES
                        + " minutos");
            }
            if (!values.isEmpty() && value < values.get(values.size() - 1)) {
                throw new BusinessRuleException(field + " deve estar em ordem crescente");
            }
            values.add(value);
        }
        return String.join(" ", values.stream().map(String::valueOf).toList());
    }
}

package com.example.flashcards_api.deck;

import com.example.flashcards_api.card.Card;
import com.example.flashcards_api.card.CardRepository;
import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Garante que {@code GET /decks} não faz uma consulta por baralho (sem N+1). */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DeckTreeQueryCountTest extends AbstractApiIntegrationTest {

    @Autowired
    private EntityManagerFactory emf;
    @Autowired
    private CardRepository cardRepository;

    @Test
    void getDecks_usesConstantNumberOfStatements() throws Exception {
        TestUser u = registerAndLogin();
        Long parent = null;
        for (int i = 0; i < 12; i++) {
            Long id = testData.deck(u.userId(), "D" + i, i % 3 == 0 ? null : parent).getId();
            parent = id;
            Card c = testData.basicNote(u.userId(), id, "q" + i, "a").cards().get(0);
            c.setState(i % 2 == 0 ? CardState.REVIEW : CardState.NEW);
            cardRepository.save(c);
        }
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        mvc.perform(getAs(u, "/api/v1/decks")).andExpect(status().isOk());

        long statements = stats.getPrepareStatementCount();
        // decks + opções + contagem agrupada de cards + contagem agrupada do histórico
        assertThat(statements).as("statements").isBetween(3L, 5L); // piso garante que a estatística está ativa
    }
}

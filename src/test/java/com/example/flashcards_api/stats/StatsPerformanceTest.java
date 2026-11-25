package com.example.flashcards_api.stats;

import com.example.flashcards_api.card.CardState;
import com.example.flashcards_api.review.Rating;
import com.example.flashcards_api.review.ReviewLog;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanidade de desempenho: com milhares de respostas e cards, cada endpoint executa poucas instruções SQL
 * (nada de consulta por linha).
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class StatsPerformanceTest extends AbstractStatsTest {

    private static final int LOGS = 5000;
    private static final int MAX_STATEMENTS_PER_REQUEST = 8;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void endpointsUseFewStatementsWith5000ReviewLogs() throws Exception {
        TestUser u = registerAndLogin();
        Long deck = testData.deck(u.userId(), "Perf").getId();
        Long child = testData.deck(u.userId(), "Perf filho", deck).getId();
        Rating[] ratings = Rating.values();
        CardState[] states = CardState.values();
        List<ReviewLog> logs = new ArrayList<>(LOGS);
        Instant start = clock.instant().minus(Duration.ofDays(300));
        for (int i = 0; i < LOGS; i++) {
            logs.add(newLog(u.userId(), i % 2 == 0 ? deck : child, ratings[i % 4], states[i % 4],
                    start.plusSeconds(i * 5000L), 1500));
        }
        reviewLogRepository.saveAll(logs);
        for (int i = 0; i < 200; i++) {
            card(u.userId(), i % 2 == 0 ? deck : child, CardState.REVIEW, i % 60, 2500, i % 3,
                    clock.instant().plus(Duration.ofHours(i * 7L)));
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        String[] urls = {"/api/v1/stats/today", "/api/v1/stats/overview?deckId=" + deck,
                "/api/v1/stats/forecast?days=365&deckId=" + deck, "/api/v1/stats/heatmap?deckId=" + deck,
                "/api/v1/stats/retention?days=365", "/api/v1/stats/streak"};
        for (String url : urls) {
            stats.clear();
            get(u, url);
            assertThat(stats.getPrepareStatementCount()).as(url).isLessThanOrEqualTo(MAX_STATEMENTS_PER_REQUEST);
        }

        assertThat(get(u, "/api/v1/stats/heatmap?deckId=" + child).get("totalReviews").asLong()).isEqualTo(LOGS / 2);
        assertThat(get(u, "/api/v1/stats/heatmap?deckId=" + deck).get("totalReviews").asLong()).isEqualTo(LOGS);
        assertThat(get(u, "/api/v1/stats/heatmap").get("totalReviews").asLong()).isEqualTo(LOGS);
        assertThat(get(u, "/api/v1/stats/forecast?days=365").get("totalDue").asLong()).isEqualTo(200);
    }
}

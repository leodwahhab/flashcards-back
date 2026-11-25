package com.example.flashcards_api.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestsTest {

    private static final Map<String, String> ALLOWED = Map.of("due", "dueAt", "id", "id");
    private static final Sort DEFAULT = Sort.by("id");

    @Test
    void defaults_whenNull() {
        Pageable p = PageRequests.of(null, null, null, ALLOWED, DEFAULT);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(20);
        assertThat(p.getSort()).isEqualTo(DEFAULT);
    }

    @Test
    void sizeOutOfRange_rejected() {
        assertThatThrownBy(() -> PageRequests.of(0, 0, null, ALLOWED, DEFAULT))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PageRequests.of(0, 101, null, ALLOWED, DEFAULT))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(PageRequests.of(0, 100, null, ALLOWED, DEFAULT).getPageSize()).isEqualTo(100);
    }

    @Test
    void negativePage_rejected() {
        assertThatThrownBy(() -> PageRequests.of(-1, 10, null, ALLOWED, DEFAULT))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void sort_mapsPublicNameToPropertyWithIdTiebreaker() {
        Pageable p = PageRequests.of(1, 10, "due,desc", ALLOWED, DEFAULT);
        assertThat(p.getSort().getOrderFor("dueAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(p.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void sort_outsideWhitelistOrBadDirection_rejected() {
        assertThatThrownBy(() -> PageRequests.of(0, 10, "password", ALLOWED, DEFAULT))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PageRequests.of(0, 10, "due,sideways", ALLOWED, DEFAULT))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void pageResponse_mapsPageMetadata() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2), org.springframework.data.domain.PageRequest.of(1, 2), 5);
        PageResponse<String> response = PageResponse.of(page, n -> "n" + n);
        assertThat(response.items()).containsExactly("n1", "n2");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}

package app.skymek.commons.paging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedResponseTest {

    @Test
    void aMiddlePageHasNeighboursOnBothSides() {
        PagedResponse<String> page = PagedResponse.of(List.of("b"), 1, 10, 25);

        assertEquals(3, page.totalPages);
        assertTrue(page.hasNext);
        assertTrue(page.hasPrevious);
    }

    @Test
    void theLastPageHasNoNext() {
        PagedResponse<String> page = PagedResponse.of(List.of("c"), 2, 10, 25);

        assertFalse(page.hasNext);
    }

    @Test
    void aZeroSizeHasNoPages() {
        assertEquals(0, PagedResponse.of(List.of(), 0, 0, 5).totalPages);
    }

    @Test
    void anEmptyPageCanStillReportTheTotal() {
        PagedResponse<String> page = PagedResponse.empty(4, 10, 25);

        assertEquals(List.of(), page.content);
        assertEquals(25, page.totalElements);
    }

    @Test
    void paginateAnswersTheLastPageForAnIndexPastTheEnd() {
        PagedResponse<Integer> page = PagedResponse.paginate(List.of(1, 2, 3, 4, 5), 9, 2);

        assertEquals(2, page.page);
        assertEquals(List.of(5), page.content);
    }

    @Test
    void paginateKeepsTheSizeBetweenOneAndTwoHundred() {
        assertEquals(1, PagedResponse.paginate(List.of(1, 2), 0, 0).size);
        assertEquals(200, PagedResponse.paginate(List.of(1, 2), 0, 500).size);
    }

    @Test
    void paginateOfAnEmptyListIsTheFirstEmptyPage() {
        PagedResponse<Integer> page = PagedResponse.paginate(List.of(), 3, 20);

        assertEquals(0, page.page);
        assertEquals(0, page.totalPages);
    }
}

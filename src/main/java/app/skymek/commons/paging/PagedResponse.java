package app.skymek.commons.paging;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * One page of a list, as every SkyMek service answers it. {@code page} counts from 0; the frontend reads every
 * field, so their names and types are the contract.
 */
@RegisterForReflection
public class PagedResponse<T> {

    public List<T> content;
    public int page;
    public int size;
    public long totalElements;
    public int totalPages;
    public boolean hasNext;
    public boolean hasPrevious;

    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        PagedResponse<T> response = new PagedResponse<>();
        response.content = content;
        response.page = page;
        response.size = size;
        response.totalElements = totalElements;
        response.totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        response.hasNext = page < response.totalPages - 1;
        response.hasPrevious = page > 0;
        return response;
    }

    public static <T> PagedResponse<T> empty(int page, int size) {
        return of(List.of(), page, size, 0);
    }

    /** A page with no rows that still reports how many there are, e.g. when the page asked for is past the end. */
    public static <T> PagedResponse<T> empty(int page, int size, long totalElements) {
        return of(List.of(), page, size, totalElements);
    }

    /**
     * Cuts a page out of a list already in memory. The size is kept between 1 and 200 and the index inside the
     * pages that exist, so a page past the end answers the last one.
     */
    public static <T> PagedResponse<T> paginate(List<T> list, int pageIndex, int pageSize) {
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int totalElements = list.size();
        if (totalElements == 0) {
            return empty(0, safePageSize);
        }
        int totalPages = (int) Math.ceil((double) totalElements / safePageSize);
        int safePageIndex = Math.min(Math.max(0, pageIndex), totalPages - 1);
        int fromIndex = safePageIndex * safePageSize;
        int toIndex = Math.min(fromIndex + safePageSize, totalElements);
        return of(list.subList(fromIndex, toIndex), safePageIndex, safePageSize, totalElements);
    }
}

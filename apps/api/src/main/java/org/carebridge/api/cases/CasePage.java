package org.carebridge.api.cases;

import java.util.List;

/** One page of cases the caller may read, in follow-up due order. */
public record CasePage(List<DischargeCase> items, int page, int size, long totalItems) {

    public CasePage {
        items = List.copyOf(items);
    }

    public static CasePage empty(CaseQuery query) {
        return new CasePage(List.of(), query.page(), query.size(), 0);
    }

    public int totalPages() {
        return (int) ((totalItems + size - 1) / size);
    }
}

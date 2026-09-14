package com.coderplatform.model;

import java.util.ArrayList;
import java.util.List;

public class SnippetListResponse {

    private List<SnippetSummaryResponse> snippets = new ArrayList<>();
    private long total;

    public SnippetListResponse() {
    }

    public SnippetListResponse(List<SnippetSummaryResponse> snippets, long total) {
        this.snippets = snippets;
        this.total = total;
    }

    public List<SnippetSummaryResponse> getSnippets() {
        return snippets;
    }

    public void setSnippets(List<SnippetSummaryResponse> snippets) {
        this.snippets = snippets;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}

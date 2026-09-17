package com.app.sme_health_backend.search.dto;

import java.math.BigDecimal;
import java.util.Objects;

public class SearchResultResponse {

    private String id;
    private String title;
    private String description;
    private String type;
    private String date;
    private BigDecimal amount;
    private String category;
    private String href;
    private String badge;

    public SearchResultResponse() {
    }

    public SearchResultResponse(
            String id,
            String title,
            String description,
            String type,
            String date,
            BigDecimal amount,
            String category,
            String href,
            String badge
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.type = type;
        this.date = date;
        this.amount = amount;
        this.category = category;
        this.href = href;
        this.badge = badge;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchResultResponse that = (SearchResultResponse) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SearchResultResponse{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", date='" + date + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}

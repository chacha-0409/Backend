package com.ll.demo.domain.quote.dto;

import com.ll.demo.domain.quote.entity.Quote;
import java.time.LocalDateTime;
import java.util.List;

public record QuoteResponse(
        Long id,
        String content,
        String originalContent,
        String summary,
        String authorName,
        Integer authorBirthYear,
        List<String> taggedMemberNames,
        LocalDateTime createDate
) {
    public QuoteResponse(Quote quote) {
        this(
                quote.getId(),
                quote.getContent(),
                quote.getOriginalContent(),
                quote.getSummary(),
                quote.getAuthor().getNickname(),
                quote.getAuthor().getBirthYear() != null
                        ? Integer.valueOf(quote.getAuthor().getBirthYear()) : null,
                quote.getQuoteTags().stream()
                                .map(tag -> tag.getMember().getName())
                                .toList(),
                quote.getCreateDate()
        );
    }

    public static QuoteResponse from(Quote quote) {
        return new QuoteResponse(quote);
    }
}
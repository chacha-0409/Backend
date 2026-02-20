package com.ll.demo.domain.quote.dto;

import com.ll.demo.domain.quote.entity.Quote;
import java.util.List;

public record MyQuoteResponse(
        Long id,
        String content,
        String groupName,
        String authorNickname,
        String birthYear,
        List<String> taggedNicknames
) {
    public static MyQuoteResponse from(Quote quote, String groupName) {
        String content = (quote.getSummary() != null && !quote.getSummary().isBlank())
                ? quote.getSummary()
                : quote.getContent();

        return new MyQuoteResponse(
                quote.getId(),
                quote.getSummary(), // ai 요약 데이터만 summary로 교체
                groupName,
                quote.getAuthor().getNickname(),
                quote.getAuthor().getBirthYear(),
                quote.getQuoteTags().stream()
                        .map(qt -> qt.getMember().getNickname())
                        .toList()
        );
    }
}
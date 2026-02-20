package com.ll.demo.domain.quote.repository;

import com.ll.demo.domain.member.member.entity.Member;
import com.ll.demo.domain.quote.entity.Quote;
import com.ll.demo.domain.quote.entity.QuoteTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteTagRepository extends JpaRepository<QuoteTag, Long> {
    // 특정 글에 달린 태그 모두 삭제
    void deleteAllByQuote(Quote quote);

    // 특정 글의 태그 목록 조회
    List<QuoteTag> findAllByQuote(Quote quote);

    // 태그 중복 방지
    boolean existsByQuoteAndMember(Quote quote, Member member);
}
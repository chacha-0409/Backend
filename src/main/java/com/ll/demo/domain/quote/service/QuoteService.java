package com.ll.demo.domain.quote.service;

import com.ll.demo.domain.friendship.friendship.repository.FriendshipRepository;
import com.ll.demo.domain.group.group.entity.GroupMember;
import com.ll.demo.domain.group.group.repository.GroupMemberRepository;
import com.ll.demo.domain.member.member.entity.Member;
import com.ll.demo.domain.member.member.repository.MemberRepository;
import com.ll.demo.domain.notification.service.NotificationService;
import com.ll.demo.domain.quote.dto.MyQuoteResponse;
import com.ll.demo.domain.quote.dto.QuoteDetailResponse;
import com.ll.demo.domain.quote.dto.QuoteListDto;
import com.ll.demo.domain.quote.dto.QuoteResponse;
import com.ll.demo.domain.quote.dto.QuoteTagRequestResponse;
import com.ll.demo.domain.quote.entity.Quote;
import com.ll.demo.domain.quote.entity.QuoteLike;
import com.ll.demo.domain.quote.entity.QuoteTag;
import com.ll.demo.domain.quote.entity.QuoteTagRequest;
import com.ll.demo.domain.quote.entity.TagRequestStatus;
import com.ll.demo.domain.quote.repository.QuoteLikeRepository;
import com.ll.demo.domain.quote.repository.QuoteRepository;
import com.ll.demo.domain.quote.repository.QuoteTagRepository;
import com.ll.demo.domain.quote.repository.QuoteTagRequestRepository;
import com.ll.demo.global.exceptions.GlobalException;
import com.ll.demo.global.security.SecurityUser;
import com.ll.demo.domain.friendship.friendship.repository.FriendshipRepository;
import com.ll.demo.domain.group.group.entity.GroupMember;
import com.ll.demo.domain.group.group.repository.GroupMemberRepository;
import com.ll.demo.domain.quote.dto.MyQuoteResponse;
import com.ll.demo.domain.quote.dto.QuoteDetailResponse;
import com.ll.demo.domain.quote.dto.QuoteListDto;
import com.ll.demo.domain.quote.dto.QuoteResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ll.demo.global.gemini.GeminiService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final QuoteLikeRepository quoteLikeRepository;
    private final MemberRepository memberRepository;
    private final QuoteTagRequestRepository quoteTagRequestRepository;
    private final QuoteTagRepository quoteTagRepository;
    private final NotificationService notificationService;
    private final FriendshipRepository friendshipRepository;
    private final GroupMemberRepository groupMemberRepository;
        // ai 추가
    private final GeminiService geminiService;

    // 명언 작성 (저장)
    @Transactional
    public QuoteResponse createQuote(Long authorId, String content, String originalContent, List<Long> taggedMemberIds) {
        validateOneQuotePerDay(authorId);

        Member author = memberRepository.findById(authorId)
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다."));

        // AI 요약 로직 추가
        String summary = geminiService.summarize(originalContent);

        Quote quote = Quote.builder()
                .author(author)
                .content(content)
                .originalContent(originalContent)
                .summary(summary)
                .build();

        quoteRepository.save(quote);

        if (taggedMemberIds != null && !taggedMemberIds.isEmpty()) {
            for (Long memberId : taggedMemberIds) {
                Member taggedMember = memberRepository.findById(memberId)
                        .orElseThrow(() -> new RuntimeException("태그된 회원을 찾을 수 없습니다."));

                // 태그 저장
                QuoteTag quoteTag = new QuoteTag(quote, taggedMember);
                quoteTagRepository.save(quoteTag);

                // 알림 발송
                notificationService.create(
                        taggedMember,
                        author,
                        "TAG",
                        author.getName() + "님이 글에 태그했습니다.",
                        quote.getId()
                );
            }
        }

        return new QuoteResponse(quote);
    }

    private void validateOneQuotePerDay(Long authorId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        boolean hasQuoteToday = quoteRepository.existsByAuthorIdAndCreateDateBetween(authorId, startOfDay, endOfDay);

        if (hasQuoteToday) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "오늘 이미 명언을 작성하셨습니다.");
        }
    }

    // 좋아요 등록
    @Transactional
    public void likeQuote(Member member, Long quoteId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("명언을 찾을 수 없습니다."));

        if (quoteLikeRepository.existsByQuoteAndMember(quote, member)) {
            return;
        }

        QuoteLike quoteLike = new QuoteLike(quote, member);
        quoteLikeRepository.save(quoteLike);
    }

    // 좋아요 취소
    @Transactional
    public void unlikeQuote(Member member, Long quoteId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("명언을 찾을 수 없습니다."));

        quoteLikeRepository.findByQuoteAndMember(quote, member)
                .ifPresent(quoteLikeRepository::delete);
    }
    // 명언 목록 조회 - 날짜 필터링 + 필요 정보 (수정됨)
    public QuoteListDto getQuoteList(Member currentUser, LocalDate date) {

        LocalDateTime startDate = date.atStartOfDay();
        LocalDateTime endDate = date.plusDays(1).atStartOfDay();

        List<Quote> quotes = quoteRepository.findAllByDateRange(startDate, endDate);

        // 1. 내 명언 목록 (수정: ID, 태그 정보 포함)
        List<MyQuoteResponse> myQuotes = quotes.stream()
                .filter(q -> q.getAuthor().getId().equals(currentUser.getId()))
                .map(q -> {
                    String groupName = getQuoteGroupName(q);

                    // ★ [추가] 내 글에 달린 태그 닉네임 가져오기
                    List<String> taggedNicknames = quoteTagRepository.findAllByQuote(q).stream()
                            .map(qt -> qt.getMember().getNickname()) // Entity 필드명(member/taggedMember) 확인 필요
                            .toList();

                    // ★ [수정] DTO 생성 시 ID와 태그 리스트 전달
                    // (MyQuoteResponse 생성자 순서에 맞춰서 넣어주세요)
                    return new MyQuoteResponse(
                            q.getId(),
                            q.getContent(),
                            groupName,
                            q.getAuthor().getNickname(),
                            String.valueOf(q.getAuthor().getBirthYear()),
                            taggedNicknames
                    );
                })
                .toList();

        // 2. 친구/타인 명언 목록
        List<QuoteDetailResponse> otherQuotes = quotes.stream()
                .filter(q -> !q.getAuthor().getId().equals(currentUser.getId()))
                .map(q -> {
                    boolean isLiked = quoteLikeRepository.existsByQuoteAndMember(q, currentUser);
                    boolean isFriend = friendshipRepository.existsByMemberAndFriend(currentUser, q.getAuthor());

                    List<String> taggedNicknames = quoteTagRepository.findAllByQuote(q).stream()
                            .map(qt -> qt.getMember().getNickname())
                            .toList();

                    return QuoteDetailResponse.from(q, taggedNicknames, isLiked, isFriend);
                })
                .toList();

        return new QuoteListDto(myQuotes, otherQuotes);
    }

    // 명언 작성자가 속한 그룹
    private String getQuoteGroupName(Quote quote) {
        List<GroupMember> groupMembers = groupMemberRepository.findByMember(quote.getAuthor());
        if (!groupMembers.isEmpty()) {
            return groupMembers.get(0).getGroup().getName();
        }
        return "아직 그룹이 없습니다.";
    }

    // 로그인한 사용자
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null; // 인증되지 않은 사용자
        }

        if (authentication.getPrincipal() instanceof SecurityUser securityUser) {
            return securityUser.getMember().getId();
        }

        return null;
    }

    public List<QuoteResponse> findMyQuotes(Long memberId) {
        List<Quote> myQuotes = quoteRepository.findAllByAuthorId(memberId);

        return myQuotes.stream()
                .map(QuoteResponse::new)
                .collect(Collectors.toList());
    }

    // 내가 좋아요 누른 명언 조회
    public List<QuoteResponse> findLikedQuotes(Long memberId) {
        List<Quote> likedQuotes = quoteRepository.findQuotesLikedByMember(memberId);

        return likedQuotes.stream()
                .map(QuoteResponse::from)
                .collect(Collectors.toList());
    }

    // 특정 날짜의 전체 명언
    public List<QuoteResponse> findQuotesByDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<Quote> quotes = quoteRepository.findAllByCreateDateBetweenOrderByCreateDateDesc(startOfDay, endOfDay);

        return quotes.stream()
                .map(QuoteResponse::new)
                .toList();
    }

    // 태그 수정 기능
    @Transactional
    public void updateTags(Long authorId, Long quoteId, List<Long> taggedMemberIds) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("명언을 찾을 수 없습니다."));

        // 작성자 본인 확인
        if (!quote.getAuthor().getId().equals(authorId)) {
            throw new RuntimeException("수정 권한이 없습니다.");
        }

        // 기존 태그 초기화
        quoteTagRepository.deleteAllByQuote(quote);

        // 새로운 태그 저장 및 알림 발송
        if (taggedMemberIds != null && !taggedMemberIds.isEmpty()) {
            for (Long memberId : taggedMemberIds) {
                Member taggedMember = memberRepository.findById(memberId)
                        .orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));

                // 태그 저장
                QuoteTag quoteTag = new QuoteTag(quote, taggedMember);
                quoteTagRepository.save(quoteTag);

                // 알림 발송
                notificationService.create(
                        taggedMember,       // 받는 사람 (태그된 친구)
                        quote.getAuthor(),  // 보낸 사람 (글쓴이)
                        "TAG",
                        quote.getAuthor().getName() + "님이 글에 태그했습니다.",
                        quote.getId()
                );
            }
        }
    }
    // 태그 요청
    @Transactional
    public void requestTag(Long requesterId, Long quoteId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("명언을 찾을 수 없습니다."));

        Member requester = memberRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다."));

        if (quote.getAuthor().getId().equals(requesterId)) {
            throw new RuntimeException("본인 글에는 태그 요청을 할 수 없습니다.");
        }

        if (quoteTagRequestRepository.existsByQuoteAndRequester(quote, requester)) {
            throw new RuntimeException("이미 태그 요청을 보냈습니다.");
        }

        QuoteTagRequest request = QuoteTagRequest.builder()
                .quote(quote)
                .requester(requester)
                .status(TagRequestStatus.PENDING)
                .build();

        quoteTagRequestRepository.save(request);

        // 알림 발송
        notificationService.create(
                quote.getAuthor(),
                requester,          
                "TAG_REQUEST", 
                requester.getName() + "님이 태그를 요청했습니다.",
                quote.getId()
        );
    }

    // 태그 요청 수락
    @Transactional
    public void acceptTagRequest(Long authorId, Long requestId) {
        QuoteTagRequest request = quoteTagRequestRepository.findById(requestId)
                .orElseThrow(() -> new GlobalException("404", "존재하지 않는 요청입니다."));

        if (!request.getQuote().getAuthor().getId().equals(authorId)) {
            throw new GlobalException("403", "이 요청을 처리할 권한이 없습니다.");
        }

        if (request.getStatus() != TagRequestStatus.PENDING) {
            throw new GlobalException("400", "이미 처리된(수락/거절) 요청입니다.");
        }

        request.accept();

        QuoteTag quoteTag = new QuoteTag(request.getQuote(), request.getRequester());
        quoteTagRepository.save(quoteTag);

        notificationService.create(
                request.getRequester(),     
                request.getQuote().getAuthor(),
                "TAG_ACCEPTED",                 
                request.getQuote().getAuthor().getName() + "님이 태그 요청을 수락했습니다!",
                request.getQuote().getId() 
        );
    }

    // 태그 요청 거절
    @Transactional
    public void rejectTagRequest(Long authorId, Long requestId) {
        QuoteTagRequest request = quoteTagRequestRepository.findById(requestId)
                .orElseThrow(() -> new GlobalException("404", "존재하지 않는 요청입니다."));

        if (!request.getQuote().getAuthor().getId().equals(authorId)) {
            throw new GlobalException("403", "이 요청을 처리할 권한이 없습니다.");
        }

        if (request.getStatus() != TagRequestStatus.PENDING) {
            throw new GlobalException("400", "이미 처리된 요청입니다.");
        }

        request.reject();

    }

    // 태그 요청 목록 조회
    public List<QuoteTagRequestResponse> getPendingTagRequests(Long authorId, Long quoteId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new GlobalException("404", "명언을 찾을 수 없습니다."));

        if (!quote.getAuthor().getId().equals(authorId)) {
            throw new GlobalException("403", "권한이 없습니다.");
        }

        List<QuoteTagRequest> requests = quoteTagRequestRepository.findAllByQuoteIdAndStatus(quoteId, TagRequestStatus.PENDING);

        return requests.stream()
                .map(QuoteTagRequestResponse::from)
                .collect(Collectors.toList());
    }
}


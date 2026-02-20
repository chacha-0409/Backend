package com.ll.demo.domain.quote.quote.service;

import com.ll.demo.domain.friendship.friendship.repository.FriendshipRepository;
import com.ll.demo.domain.group.group.repository.GroupMemberRepository;
import com.ll.demo.domain.member.member.entity.Member;
import com.ll.demo.domain.member.member.repository.MemberRepository;
import com.ll.demo.domain.notification.service.NotificationService;
import com.ll.demo.domain.quote.dto.QuoteDetailResponse;
import com.ll.demo.domain.quote.dto.QuoteListDto;
import com.ll.demo.domain.quote.dto.QuoteResponse;
import com.ll.demo.domain.quote.entity.Quote;
import com.ll.demo.domain.quote.entity.QuoteTagRequest;
import com.ll.demo.domain.quote.entity.TagRequestStatus;
import com.ll.demo.domain.quote.repository.QuoteLikeRepository;
import com.ll.demo.domain.quote.repository.QuoteRepository;
import com.ll.demo.domain.quote.repository.QuoteTagRepository;
import com.ll.demo.domain.quote.repository.QuoteTagRequestRepository;
import com.ll.demo.domain.quote.service.QuoteService;
import com.ll.demo.global.gemini.GeminiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock private QuoteRepository quoteRepository;
    @Mock private QuoteLikeRepository quoteLikeRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private QuoteTagRequestRepository quoteTagRequestRepository;
    @Mock private QuoteTagRepository quoteTagRepository;
    @Mock private NotificationService notificationService;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GeminiService geminiService;

    private Member sender;
    private Quote likedQuote;

    @InjectMocks
    private QuoteService quoteService;

    private Member liker;
    private Member author;
    private Quote testQuote;
    private Member requester;
    private QuoteTagRequest request;

    @BeforeEach
    void setUp() {
        // 좋아요한 사람
        liker = Member.builder()
                .nickname("좋아요요정")
                .build();
        ReflectionTestUtils.setField(liker, "id", 1L);

        // 글 작성자
        author = Member.builder()
                .nickname("작성자")
                .birthYear("1995")
                .build();
        ReflectionTestUtils.setField(author, "id", 2L);

        // 태그 요청한 사람
        requester = Member.builder()
                .nickname("요청자")
                .build();
        ReflectionTestUtils.setField(requester, "id", 3L);

        // 테스트용 명언
        testQuote = Quote.builder()
                .author(author)
                .content("테스트 명언")
                .build();
        ReflectionTestUtils.setField(testQuote, "id", 100L);
        ReflectionTestUtils.setField(testQuote, "createDate", LocalDateTime.now());

        likedQuote = testQuote;

        // 태그해줘
        request = QuoteTagRequest.builder()
                .quote(testQuote)
                .requester(requester)
                .status(TagRequestStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(request, "id", 200L);
    }

    @Test
    @DisplayName("성공: 내가 좋아요를 누른 명언 목록이 정확히 DTO로 변환되어 반환되어야 한다")
    void findLikedQuotes_Success() {
        when(quoteRepository.findQuotesLikedByMember(1L)).thenReturn(List.of(likedQuote));

        List<QuoteResponse> result = quoteService.findLikedQuotes(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(100L);
        assertThat(result.get(0).authorName()).isEqualTo("명언제조기");

        verify(quoteRepository, times(1)).findQuotesLikedByMember(1L);
    }

    @Test
    @DisplayName("성공: 좋아요를 누르면 데이터가 저장되고 작성자에게 알림이 가야 한다")
    void likeQuote_Success_WithNotification() {
        when(quoteRepository.findById(100L)).thenReturn(Optional.of(testQuote));
        when(quoteLikeRepository.existsByQuoteAndMember(testQuote, liker)).thenReturn(false);

        quoteService.likeQuote(liker, 100L);

        verify(quoteLikeRepository, times(1)).save(any());

        verify(notificationService, times(1)).create(
                eq(author),
                eq(liker),
                eq("LIKE"),
                contains("좋아합니다"),
                eq(100L)
        );
    }

    @Test
    @DisplayName("성공: 수락 시 상태가 ACCEPTED로 변하고 QuoteTag가 생성되어야 한다")
    void acceptTagRequest_Success() {
        Member author = Member.builder().nickname("작성자").build();
        ReflectionTestUtils.setField(author, "id", 1L);

        Member requester = Member.builder().nickname("요청자").build();
        ReflectionTestUtils.setField(requester, "id", 3L);

        Quote quote = Quote.builder().author(author).build();
        ReflectionTestUtils.setField(quote, "id", 10L);

        QuoteTagRequest pendingRequest = QuoteTagRequest.builder()
                .quote(quote)
                .requester(requester)
                .status(TagRequestStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(pendingRequest, "id", 100L);

        when(quoteTagRequestRepository.findById(100L)).thenReturn(Optional.of(pendingRequest));

        quoteService.acceptTagRequest(1L, 100L);

        assertThat(pendingRequest.getStatus()).isEqualTo(TagRequestStatus.ACCEPTED);
        verify(quoteTagRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("성공: 특정 날짜 조회 시 내 글과 타인의 글(좋아요/친구 포함)이 정확히 매핑되어야 한다")
    void getQuoteList_Detailed_Success() {
        LocalDate testDate = LocalDate.of(2024, 5, 20);

        Member friend = Member.builder().nickname("친구닉네임").build();
        ReflectionTestUtils.setField(friend, "id", 2L);

        Quote myQuote = Quote.builder().author(sender).content("내 명언").summary("내 요약").build();
        ReflectionTestUtils.setField(myQuote, "id", 10L);

        Quote friendQuote = Quote.builder().author(friend).content("친구 명언").summary("친구 요약").build();
        ReflectionTestUtils.setField(friendQuote, "id", 11L);

        when(quoteRepository.findAllByDateRange(any(), any())).thenReturn(List.of(myQuote, friendQuote));
        when(quoteLikeRepository.existsByQuoteAndMember(friendQuote, sender)).thenReturn(true);
        when(friendshipRepository.existsByMemberAndFriend(sender, friend)).thenReturn(true);
        when(groupMemberRepository.findByMember(sender)).thenReturn(List.of()); // 그룹 없음 처리

        QuoteListDto result = quoteService.getQuoteList(sender, testDate);

        assertThat(result.myQuotes()).hasSize(1);
        assertThat(result.myQuotes().get(0).id()).isEqualTo(10L);

        assertThat(result.otherQuotes()).hasSize(1);
        QuoteDetailResponse other = result.otherQuotes().get(0);
        assertThat(other.isLiked()).isTrue();
        assertThat(other.isFriendQuote()).isTrue();
        assertThat(other.content()).isEqualTo("친구 요약");
    }
}
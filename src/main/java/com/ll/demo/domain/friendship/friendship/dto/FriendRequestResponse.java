package com.ll.demo.domain.friendship.friendship.dto;

import com.ll.demo.domain.friendship.friendship.entity.Friendship;
import com.ll.demo.domain.member.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FriendRequestResponse {
    private Long requestId;
    private Long senderId;
    private String senderNickname;
    private String senderProfileImage;
    private String senderIntroduction;

    public static FriendRequestResponse from(Friendship friendship) {
        Member sender = friendship.getMember();
        return FriendRequestResponse.builder()
                .requestId(friendship.getId())
                .senderId(sender.getId())
                .senderNickname(sender.getNickname())
                .senderProfileImage(sender.getProfileImage())
                .senderIntroduction(sender.getIntroduction())
                .build();
    }
}

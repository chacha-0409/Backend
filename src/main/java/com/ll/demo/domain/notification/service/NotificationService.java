package com.ll.demo.domain.notification.service;

import com.ll.demo.domain.member.member.entity.Member;
import com.ll.demo.domain.notification.dto.NotificationResponse;
import com.ll.demo.domain.notification.entity.Notification;
import com.ll.demo.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional
    public void create(Member receiver, Member sender, String type, String message, Long targetId) {
        Notification notification = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .type(type)
                .message(message)
                .targetId(targetId)
                .build();

        notificationRepository.save(notification);
    }

    public List<NotificationResponse> findMyNotifications(Long memberId) {
        List<Notification> notifications = notificationRepository.findByReceiverIdOrderByCreateDateDesc(memberId);

        return notifications.stream()
                .map(NotificationResponse::new)
                .toList();
    }

    @Transactional
    public void markAsRead(Long receiverId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("알림을 찾을 수 없습니다."));

        if (!notification.getReceiver().getId().equals(receiverId)) {
            throw new RuntimeException("권한이 없습니다.");
        }

        notification.markAsRead();
    }

    public List<NotificationResponse> findMyNotifications(Long memberId, String type) {
        List<Notification> notifications;

        if (type != null && !type.isBlank()) {
            notifications = notificationRepository.findByReceiverIdAndTypeOrderByCreateDateDesc(memberId, type);
        } else {
            notifications = notificationRepository.findByReceiverIdOrderByCreateDateDesc(memberId);
        }

        return notifications.stream()
                .map(NotificationResponse::new)
                .toList();
    }

    // 읽지 않은 콕 찌르기 개수
    public long countUnreadByType(Long memberId, String type) {
        return notificationRepository.countByReceiverIdAndTypeAndReadDateIsNull(memberId, type);
    }
}
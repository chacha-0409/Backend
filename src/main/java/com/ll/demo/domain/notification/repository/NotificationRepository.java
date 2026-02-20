package com.ll.demo.domain.notification.repository;

import com.ll.demo.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 전체 알림 조회
    List<Notification> findByReceiverIdOrderByCreateDateDesc(Long receiverId);

    // 특정 타입의 알림만 조회
    List<Notification> findByReceiverIdAndTypeOrderByCreateDateDesc(Long receiverId, String type);

    // 특정 타입의 읽지 않은 알림 개수
    long countByReceiverIdAndTypeAndReadDateIsNull(Long receiverId, String type);
}
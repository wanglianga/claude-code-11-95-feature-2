package com.fishport.repo;

import com.fishport.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);

    List<Notification> findByVoyageIdOrderByCreatedAtAsc(Long voyageId);
}

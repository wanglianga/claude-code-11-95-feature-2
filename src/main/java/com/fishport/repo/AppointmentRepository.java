package com.fishport.repo;

import com.fishport.domain.Appointment;
import com.fishport.domain.Types;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByAppointmentNo(String appointmentNo);

    List<Appointment> findByPortIdAndServiceDate(Long portId, LocalDate serviceDate);

    List<Appointment> findByVoyageId(Long voyageId);

    List<Appointment> findByVoyageIdAndStatusNotIn(Long voyageId, List<Types.ApptStatus> statuses);
}

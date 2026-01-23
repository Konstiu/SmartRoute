package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Repository
public interface InjuryRepository extends JpaRepository<Injuries, Long> {
    Injuries findByIdAndApplicationUser(Long id, ApplicationUser applicationUser);

    List<Injuries> getAllByApplicationUser(ApplicationUser user);

    @Query("SELECT i from Injuries i WHERE i.applicationUser = :user AND i.lastInjuryDate > :from AND i.lastHealthyDate < :to ORDER BY i.lastHealthyDate ASC")
    List<Injuries> getAllByUserBetweenDateOrderByDateAsc(@Param("user") ApplicationUser user, @Param("from") LocalDate from, @Param("to") LocalDate to);

    void deleteAllByApplicationUser(ApplicationUser applicationUser);
}

package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InjuryRepository extends JpaRepository<Injuries, Long> {
    Injuries findByIdAndApplicationUser(Long id, ApplicationUser applicationUser);

    List<Injuries> getAllByApplicationUser(ApplicationUser user);

    void deleteAllByApplicationUser(ApplicationUser applicationUser);
}

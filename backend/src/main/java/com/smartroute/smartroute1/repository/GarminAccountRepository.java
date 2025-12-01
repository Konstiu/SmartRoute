package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.GarminAccount;
import com.smartroute.smartroute1.entity.ApplicationUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GarminAccountRepository extends JpaRepository<GarminAccount, Long> {

    GarminAccount findByUser(ApplicationUser user);
}

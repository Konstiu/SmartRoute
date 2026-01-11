package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<UserDevice, Long> {
    List<UserDevice> getAllByUser(ApplicationUser user);

    Optional<UserDevice> findByUserAndDeviceId(ApplicationUser user, String deviceId);

    List<UserDevice> findAllByUser(ApplicationUser user);
}

package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.ActivityService;
import com.smartroute.smartroute1.service.GarminImportService;
import com.smartroute.smartroute1.service.StravaService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@AllArgsConstructor
@Service
public class ActivityServiceImpl implements ActivityService {
    private StravaService stravaService;
    private GarminImportService garminService;
    private UserRepository userRepository;
    private StravaAccountRepository stravaAccountRepository;

    @Override
    public void synchronize(String email, int count) {
        ApplicationUser user = userRepository.findUserByEmail(email);

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        StravaAccount account = stravaAccountRepository.findByUser(user).orElse(null);

        if (account != null) {
            stravaService.importStravaActivities(email, count);
        }

        if (garminService.isGarminConnected(email)) {
            garminService.syncActivities(user, count, null, null);
        }
    }
}

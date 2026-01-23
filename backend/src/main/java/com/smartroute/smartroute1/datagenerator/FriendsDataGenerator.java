package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.entity.enums.FriendshipStatus;
import com.smartroute.smartroute1.repository.FriendshipRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.util.List;

@Profile("generateData")
@Component
@AllArgsConstructor
@DependsOn("userDataGenerator")
public class FriendsDataGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    @PostConstruct
    @Transactional
    public void generateFriendships() {
        if (friendshipRepository.count() > 0) {
            LOGGER.info("Friendships already generated");
            return;
        }

        LOGGER.info("Generating friendships");

        List<ApplicationUser> users = userRepository.findAll();

        for (int i = 1; i < users.size(); i++) {
            for (int j = i + 1; j < users.size(); j++) {
                Friendship friendship = new Friendship();
                friendship.setSender(users.get(i));
                friendship.setReceiver(users.get(j));
                friendship.setStatus(FriendshipStatus.ACCEPTED);
                friendshipRepository.save(friendship);
            }
        }

        LOGGER.info("Successfully generated {} friendships", friendshipRepository.count());
    }

}

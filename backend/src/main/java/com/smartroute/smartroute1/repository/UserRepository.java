package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<ApplicationUser, Long> {

    List<ApplicationUser> findAll();

    @Query("SELECT u FROM ApplicationUser u WHERE LOWER(u.email) = LOWER(:email)")
    ApplicationUser findUserByEmail(@Param("email") String email);

    ApplicationUser getByEmail(String email);
}

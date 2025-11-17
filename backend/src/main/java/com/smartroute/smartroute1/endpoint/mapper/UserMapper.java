package com.smartroute.smartroute1.endpoint.mapper;


import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.dto.UserDetailDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    default CreateUserDto applicationUserToDto(ApplicationUser user) {
        CreateUserDto createStudentDto = new CreateUserDto();

        if (user != null) {
            createStudentDto.setPassword(user.getPassword());
            createStudentDto.setFirstname(user.getFirstname());
            createStudentDto.setLastname(user.getLastname());
            createStudentDto.setEmail(user.getEmail());

        }
        return createStudentDto;
    }

    default UserDetailDto applicationUserToDetailDto(ApplicationUser user) {
        UserDetailDto userDetailDto = new UserDetailDto();

        if (user != null) {
            userDetailDto.setFirstname(user.getFirstname());
            userDetailDto.setLastname(user.getLastname());
            userDetailDto.setEmail(user.getEmail());
            userDetailDto.setSex(user.getSex());
            userDetailDto.setHeight(user.getHeight());
            userDetailDto.setWeight(user.getWeight());
            userDetailDto.setBirthdate(user.getBirthdate());
            userDetailDto.setExperienceLevel(user.getExperienceLevel());
            userDetailDto.setActiveWeekdays(user.getActiveWeekdays());
        }
        return userDetailDto;
    }
}
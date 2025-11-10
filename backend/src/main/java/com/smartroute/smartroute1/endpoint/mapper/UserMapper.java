package com.smartroute.smartroute1.endpoint.mapper;


import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
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
}
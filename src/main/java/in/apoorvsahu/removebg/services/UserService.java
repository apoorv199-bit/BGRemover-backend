package in.apoorvsahu.removebg.services;

import in.apoorvsahu.removebg.dtos.UserDto;

public interface UserService {
    UserDto saveUser(UserDto userDto);

    UserDto getUserByClerkId(String clerkId);

    void deleteUserByClerkId(String clerkId);
}

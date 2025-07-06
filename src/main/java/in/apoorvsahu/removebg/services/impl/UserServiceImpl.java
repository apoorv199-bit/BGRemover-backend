package in.apoorvsahu.removebg.services.impl;

import in.apoorvsahu.removebg.Repositories.UserRepository;
import in.apoorvsahu.removebg.dtos.UserDto;
import in.apoorvsahu.removebg.entities.User;
import in.apoorvsahu.removebg.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserDto saveUser(UserDto userDto) {
        Optional<User> optionalUser = userRepository.findByClerkId(userDto.getClerkId());
        if(optionalUser.isPresent()){
            User existingUser = optionalUser.get();
            existingUser.setEmail(userDto.getEmail());
            existingUser.setFirstName(userDto.getFirstName());
            existingUser.setLastName(userDto.getLastName());
            existingUser.setPhotoUrl(userDto.getPhotoUrl());

            if(userDto.getCredits() != null){
                existingUser.setCredits(userDto.getCredits());
            }
            return mapToDto(userRepository.save(existingUser));
        }
        User newUser = mapToEntity(userDto);
        userRepository.save(newUser);
        return mapToDto(newUser);
    }

    @Override
    public UserDto getUserByClerkId(String clerkId) {
        User user = userRepository.findByClerkId(clerkId).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return mapToDto(user);
    }

    @Override
    public void deleteUserByClerkId(String clerkId) {
        User user = userRepository.findByClerkId(clerkId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        userRepository.delete(user);
    }

    private User mapToEntity(UserDto userDto){
        return User.builder()
                .clerkId(userDto.getClerkId())
                .email(userDto.getEmail())
                .firstName(userDto.getFirstName())
                .lastName(userDto.getLastName())
                .photoUrl(userDto.getPhotoUrl())
                .build();
    }

    private UserDto mapToDto(User user){
        return UserDto.builder()
                .clerkId(user.getClerkId())
                .credits(user.getCredits())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }
}

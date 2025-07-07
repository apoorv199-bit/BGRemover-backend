package in.apoorvsahu.removebg.services.impl;

import in.apoorvsahu.removebg.Repositories.UserRepository;
import in.apoorvsahu.removebg.dtos.UserDto;
import in.apoorvsahu.removebg.entities.User;
import in.apoorvsahu.removebg.exceptions.UserNotFoundException;
import in.apoorvsahu.removebg.exceptions.UserServiceException;
import in.apoorvsahu.removebg.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDto saveUser(UserDto userDto) {
        try {
            Optional<User> optionalUser = userRepository.findByClerkId(userDto.getClerkId());

            if (optionalUser.isPresent()) {
                User existingUser = optionalUser.get();
                updateExistingUser(existingUser, userDto);
                User savedUser = userRepository.save(existingUser);
                log.info("Updated existing user with clerkId: {}", userDto.getClerkId());
                return mapToDto(savedUser);
            } else {
                User newUser = mapToEntity(userDto);
                User savedUser = userRepository.save(newUser);
                log.info("Created new user with clerkId: {}", userDto.getClerkId());
                return mapToDto(savedUser);
            }
        } catch (DataAccessException e) {
            log.error("Database error while saving user with clerkId: {}", userDto.getClerkId(), e);
            throw new UserServiceException("Unable to save user data. Please try again later");
        } catch (Exception e) {
            log.error("Unexpected error while saving user with clerkId: {}", userDto.getClerkId(), e);
            throw new UserServiceException("An unexpected error occurred while saving user");
        }
    }

    @Override
    public UserDto getUserByClerkId(String clerkId) {
        try {
            User user = userRepository.findByClerkId(clerkId)
                    .orElseThrow(() -> new UserNotFoundException("User with ID " + clerkId + " not found"));
            return mapToDto(user);
        } catch (DataAccessException e) {
            log.error("Database error while fetching user with clerkId: {}", clerkId, e);
            throw new UserServiceException("Unable to fetch user data. Please try again later");
        } catch (Exception e) {
            log.error("Unexpected error while fetching user with clerkId: {}", clerkId, e);
            throw new UserServiceException("An unexpected error occurred while fetching user");
        }
    }

    @Override
    @Transactional
    public void deleteUserByClerkId(String clerkId) {
        try {
            User user = userRepository.findByClerkId(clerkId)
                    .orElseThrow(() -> new UserNotFoundException("User with ID " + clerkId + " not found"));
            userRepository.delete(user);
            log.info("Deleted user with clerkId: {}", clerkId);
        } catch (DataAccessException e) {
            log.error("Database error while deleting user with clerkId: {}", clerkId, e);
            throw new UserServiceException("Unable to delete user. Please try again later");
        } catch (Exception e) {
            log.error("Unexpected error while deleting user with clerkId: {}", clerkId, e);
            throw new UserServiceException("An unexpected error occurred while deleting user");
        }
    }

    private void updateExistingUser(User existingUser, UserDto userDto) {
        existingUser.setEmail(userDto.getEmail());
        existingUser.setFirstName(userDto.getFirstName());
        existingUser.setLastName(userDto.getLastName());
        existingUser.setPhotoUrl(userDto.getPhotoUrl());

        if (userDto.getCredits() != null) {
            existingUser.setCredits(userDto.getCredits());
        }
    }

    private User mapToEntity(UserDto userDto) {
        return User.builder()
                .clerkId(userDto.getClerkId())
                .email(userDto.getEmail())
                .firstName(userDto.getFirstName())
                .lastName(userDto.getLastName())
                .photoUrl(userDto.getPhotoUrl())
                .credits(userDto.getCredits())
                .build();
    }

    private UserDto mapToDto(User user) {
        return UserDto.builder()
                .clerkId(user.getClerkId())
                .credits(user.getCredits())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .photoUrl(user.getPhotoUrl())
                .build();
    }
}
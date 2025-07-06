package in.apoorvsahu.removebg.controllers;

import in.apoorvsahu.removebg.dtos.UserDto;
import in.apoorvsahu.removebg.response.RemoveBgResponse;
import in.apoorvsahu.removebg.services.RemoveBgService;
import in.apoorvsahu.removebg.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final RemoveBgService removeBgService;
    private final UserService userService;

    @PostMapping("/remove-background")
    public ResponseEntity<?> removeBackground(@RequestParam("file")MultipartFile file,
                                              Authentication authentication){
        RemoveBgResponse response = null;
        Map<String, Object> responseMap = new HashMap<>();
        try {
            if (authentication.getName().isEmpty() || authentication.getName() == null){
                response = RemoveBgResponse.builder()
                        .statusCode(HttpStatus.FORBIDDEN)
                        .success(false)
                        .data("User does not have permission/access to this resource")
                        .build();
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            UserDto userDto = userService.getUserByClerkId(authentication.getName());

            if (userDto.getCredits() == 0){
                responseMap.put("message", "No credit balance");
                responseMap.put("creditBalance", userDto.getCredits());
                response = RemoveBgResponse.builder()
                        .success(false)
                        .data(responseMap)
                        .statusCode(HttpStatus.OK)
                        .build();
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }

            byte[] imageBytes = removeBgService.removeBackground(file);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            userDto.setCredits(userDto.getCredits() - 1);
            userService.saveUser(userDto);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(base64Image);
        } catch (Exception e) {
            response = RemoveBgResponse.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR)
                    .success(false)
                    .data("Something went wrong")
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

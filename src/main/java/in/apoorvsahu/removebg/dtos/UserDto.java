package in.apoorvsahu.removebg.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDto {
    private String clerkId;
    private String email;
    private String firstName;
    private String lastName;
    private Integer credits;
    private String photoUrl;
}

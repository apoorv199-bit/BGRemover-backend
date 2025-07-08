package in.apoorvsahu.removebg.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoveBgResponse {
    private boolean success;
    private Object data;
    private String message;
    private HttpStatus statusCode;
}
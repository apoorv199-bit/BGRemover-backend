package in.apoorvsahu.removebg.services;

import org.springframework.web.multipart.MultipartFile;

public interface RemoveBgService {

    byte[] removeBackground(MultipartFile file);
}

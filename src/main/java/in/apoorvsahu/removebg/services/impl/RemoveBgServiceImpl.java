package in.apoorvsahu.removebg.services.impl;

import in.apoorvsahu.removebg.clients.ClipdropClient;
import in.apoorvsahu.removebg.services.RemoveBgService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class RemoveBgServiceImpl implements RemoveBgService {

    @Value("${clipdrop.apikey}")
    private String apiKey;

    private final ClipdropClient clipdropClient;

    @Override
    public byte[] removeBackground(MultipartFile file) {
        return clipdropClient.removeBackground(file, apiKey);
    }
}

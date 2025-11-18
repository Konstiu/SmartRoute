package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.mapper.StravaActivityMapper;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.GpxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gpx")
@RequiredArgsConstructor
public class GpxEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final GpxService gpxService;
    private final StravaActivityMapper stravaActivityMapper;

    @Secured("ROLE_USER")
    @PostMapping("import-strava")
    public List<StravaActivityDto> importStravaGpx(@RequestParam("files") List<MultipartFile> files) throws ValidationException, IOException {
        LOGGER.info("POST /api/v1/gpx/import-strava");
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<StravaActivityDto> dtos = new ArrayList<>();
        for (MultipartFile file : files) {
            try (InputStream is = file.getInputStream()) {
                StravaActivityDto dto = stravaActivityMapper.entityToDto(
                    gpxService.importStravaGpxFile(is, email)
                );
                dtos.add(dto);
            }
        }
        return dtos;
    }
}

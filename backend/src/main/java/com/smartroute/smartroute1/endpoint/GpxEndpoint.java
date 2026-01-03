package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.DetailedActivityDto;
import com.smartroute.smartroute1.endpoint.dto.RunClassificationDecisionDto;
import com.smartroute.smartroute1.endpoint.mapper.RunClassificationMapper;
import com.smartroute.smartroute1.endpoint.mapper.StravaActivityMapper;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.RunClassificationDecision;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.GpxService;
import com.smartroute.smartroute1.service.RunClassificationService;
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
    private final RunClassificationService runClassificationService;
    private final RunClassificationMapper runClassificationMapper;

    @Secured("ROLE_USER")
    @PostMapping("import-strava")
    public List<DetailedActivityDto> importStravaGpx(@RequestParam("files") List<MultipartFile> files) throws ValidationException, IOException {
        LOGGER.info("POST /api/v1/gpx/import-strava");
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<DetailedActivityDto> dtos = new ArrayList<>();
        if (files.stream().map(MultipartFile::getSize).reduce(Long::sum).orElse(0L) > 10L * 1024L * 10124L) {
            throw new ValidationException("Uploaded files exceed maximum total size of 10 MB");
        }
        for (MultipartFile file : files) {
            try (InputStream is = file.getInputStream()) {
                Activity activity = gpxService.importStravaGpxFile(is, email);

                RunClassificationDecision decision = activity.getRunTypeClassification();
                RunClassificationDecisionDto runClassification;
                if (decision == null) {
                    runClassification = runClassificationService.classifyRun(activity.getId());
                } else {
                    runClassification = runClassificationMapper.entityToDto(decision);
                }

                DetailedActivityDto dto = stravaActivityMapper.toDetailedViewDto(
                    activity,
                    runClassification
                );
                dtos.add(dto);
            }
        }
        return dtos;
    }
}

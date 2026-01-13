package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDto;
import com.smartroute.smartroute1.endpoint.dto.RunClassificationResultDto;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.service.RunTrainingClassificationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;


@SpringBootTest
@ActiveProfiles({"test", "generateData"})
public class RunTrainingClassificationServiceTest {


    @Autowired
    private RunTrainingClassificationService runTrainingClassificationService;


    @Test
    void classifyRun_shouldReturnClassification() {

        RunClassificationDto dto = new RunClassificationDto(
            3600,              // duration (sec)
            0.9,               // duration_pct_pb_20
            10.0,              // distance
            0.9,               // distance_pct_pb_20
            5.0,               // pace
            0.9,               // pace_pct_pb_20
            100.0,             // elevation_gain
            400.0,             // session_load
            2,                 // num_pace_spikes
            false,             // pace spikes missing
            80,                // readiness_score
            0.8,               // consistency_score
            10.0,              // tsb
            30,                // age
            70.0,              // weight
            175,               // height
            Sex.MALE,
            ExperienceLevel.INTERMEDIATE,
            0.1,               // injury_index
            0.75,              // hr_avg
            false,             // hr_avg_missing
            190.0,             // hr_max
            false,             // hr_max_missing
            10, 20f, false,
            20, 40f, false,
            15, 30f, false,
            5, 10f, false,
            0, 0f, false,
            3,                 // num_hr_spikes
            false,             // num_hr_spikes_missing
            3.0,               // wind
            15.0,              // temperature
            3,                 // uv
            0.0,               // precipitation
            0.0                // snow
        );

        RunClassificationResultDto result =
            runTrainingClassificationService.classifyRun(dto);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getClassification());
    }

    @Test
    void classifyCsv_shouldWriteClassification() throws Exception {

        Path input = Mockito.mock(Path.class);
        Path output = Mockito.mock(Path.class);

        String header = "duration,pace_pct_pb_20,distance,distance_pct_pb_20,pace,pace_pct_pb_20,"
            + "elevation_gain,session_load,num_pace_spikes,num_pace_spikes_missing,readiness_score,consistency_score,"
            + "tsb,age,weight,height,sex,experience_level,injury_index,hr_avg,hr_avg_missing,"
            + "hr_max,hr_max_missing,zone1,zone1pct,zone1_missing,zone2,zone2pct,zone2_missing,zone3,zone3pct,zone3_missing,"
            + "zone4,zone4pct,zone4_missing,zone5,zone5pct,zone5_missing,num_hr_spikes,num_hr_spikes_missing,"
            + "windSpeed10m,temperature2m,uv_index,precipitation,snowDepth";

        String data =
            "3600,0.9,10,0.9,5,0.9,100,400,2,false,80,0.8,10,30,70,175,MALE,INTERMEDIATE,"
                + "0.1,0.75,false,190,false,10,20,false,20,40,false,15,30,false,5,10,false,0,0,false,"
                + "3,false,3,15,3,0,0";

        BufferedReader reader = Mockito.mock(BufferedReader.class);
        BufferedWriter writer = Mockito.mock(BufferedWriter.class);

        Mockito.when(reader.readLine())
            .thenReturn(header)
            .thenReturn(data)
            .thenReturn(null);

        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {

            files.when(() -> Files.newBufferedReader(input))
                .thenReturn(reader);

            files.when(() -> Files.newBufferedWriter(output))
                .thenReturn(writer);

            Path result = runTrainingClassificationService.classifyCsv(input, output);

            Assertions.assertEquals(output, result);

            Mockito.verify(writer).write(Mockito.contains("classification"));
            Mockito.verify(writer, Mockito.atLeastOnce())
                .write(Mockito.contains(","));
        }
    }
}

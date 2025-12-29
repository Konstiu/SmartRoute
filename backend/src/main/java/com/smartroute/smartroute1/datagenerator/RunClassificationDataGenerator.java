package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDto;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.RunType;
import com.smartroute.smartroute1.entity.enums.RunTypeProfile;
import com.smartroute.smartroute1.entity.enums.RunnerProfile;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.service.RunClassificationService;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class RunClassificationDataGenerator {
    private static final int NUMBER_OF_RUNS = 8000;
    private static final Path CSV_PATH = Paths.get("backend", "target", "RunDataset.csv");
    private static final Path CSV_OUT_PATH = Paths.get("backend", "target", "RunDataset_Labelled.csv");
    private static final String CSV_HEADER =
            "duration,duration_pct_pb_20,distance,distance_pct_pb_20,"
                    + "pace,pace_pct_pb_20,elevation_gain,session_load,"
                    + "num_pace_spikes,readiness_score,consistency_score,tsb,"
                    + "age,weight,height,sex,experience_level,injury_index,"
                    + "hr_avg,hr_avg_missing,hr_max,hr_max_missing,"
                    + "zone1,zone1_missing,zone2,zone2_missing,"
                    + "zone3,zone3_missing,zone4,zone4_missing,"
                    + "zone5,zone5_missing,num_hr_spikes,num_hr_spikes_missing,"
                    + "windSpeed10m,temperature2m,uv_index,precipitation,snowDepth";
    private final Random random = new Random();
    private final RunClassificationService runClassificationService;

    public RunClassificationDataGenerator(RunClassificationService runClassificationService) {
        this.runClassificationService = runClassificationService;
    }

    private static RunnerProfile runnerProfile(ExperienceLevel level) {
        return switch (level) {
            case BEGINNER -> new RunnerProfile(
                    18, 60,
                    6.5, 8.0,
                    8.0,
                    185,
                    1.4
            );
            case CASUAL -> new RunnerProfile(
                    18, 65,
                    5.8, 7.2,
                    12.0,
                    190,
                    1.2
            );
            case INTERMEDIATE -> new RunnerProfile(
                    18, 65,
                    4.8, 6.2,
                    20.0,
                    195,
                    1.0
            );
            case ADVANCED -> new RunnerProfile(
                    18, 55,
                    4.0, 5.2,
                    30.0,
                    200,
                    0.8
            );
            case COMPETITIVE_ATHLETE -> new RunnerProfile(
                    18, 45,
                    3.2, 4.5,
                    42.0,
                    205,
                    0.6
            );
        };
    }

    private static RunTypeProfile runTypeProfile(RunType type) {
        return switch (type) {
            case EASY_RUN -> new RunTypeProfile(
                    1.15,
                    0.7,
                    0.6,
                    10,
                    0
            );
            case TEMPO_RUN -> new RunTypeProfile(
                    0.95,
                    0.8,
                    0.8,
                    35,
                    1
            );
            case INTERVAL_RUN -> new RunTypeProfile(
                    0.85,
                    0.5,
                    0.7,
                    60,
                    6
            );
            case LONG_RUN -> new RunTypeProfile(
                    1.10,
                    1.3,
                    1.1,
                    25,
                    1
            );
        };
    }

    public void createCsv() throws IOException {
        Files.createDirectories(CSV_PATH.getParent());

        try (BufferedWriter w = Files.newBufferedWriter(CSV_PATH)) {

            w.write(CSV_HEADER);
            w.newLine();

            List<RunClassificationDto> runs = new ArrayList<>();
            for (int i = 0; i < NUMBER_OF_RUNS; i++) {
                runs.add(generate());
            }

            for (RunClassificationDto r : runs) {
                w.write(toCsv(r));
                w.newLine();
            }
        }

        runClassificationService.classifyCsv(CSV_PATH, CSV_OUT_PATH);
    }

    private RunClassificationDto generate() {
        ExperienceLevel exp = randomEnum(ExperienceLevel.class);
        RunType runType = randomEnum(RunType.class);

        RunnerProfile athlete = runnerProfile(exp);
        RunTypeProfile type = runTypeProfile(runType);

        double basePace = rand(athlete.minPace(), athlete.maxPace());
        double pace = basePace * type.paceMultiplier();

        double distance = rand(3.0, athlete.maxDistance()) * type.distanceMultiplier();
        int duration = (int) (distance * pace * 60);

        double elevation = rand(0, distance * 25) * type.elevationMultiplier();

        double hrAvg = rand(0.7, 0.9) * athlete.maxHr();
        double hrMax = rand(hrAvg + 5, athlete.maxHr());

        int zone5 = runType == RunType.INTERVAL_RUN ? randInt(300, 900) : randInt(0, 200);
        int zone4 = randInt(300, 1800);
        int zone3 = randInt(600, 2400);
        int zone2 = randInt(600, 3600);
        int zone1 = Math.max(0, duration - (zone2 + zone3 + zone4 + zone5));

        return new RunClassificationDto(
                duration,
                rand(0.7, 1.3),
                distance,
                rand(0.7, 1.3),
                pace,
                rand(0.7, 1.3),
                elevation,
                duration * 0.8,
                type.paceSpikes(),
                randInt(40, 90),
                rand(0.3, 1.0),
                rand(-20, 20),
                randInt(18, 60),
                rand(55, 90),
                randInt(155, 195),
                randomEnum(Sex.class),
                exp,
                athlete.injuryRiskFactor() * rand(0.1, 0.5),
                hrAvg,
                false,
                hrMax,
                false,
                zone1, false,
                zone2, false,
                zone3, false,
                zone4, false,
                zone5, false,
                randInt(0, 5),
                false,
                rand(0, 12),
                rand(-5, 30),
                randInt(0, 10),
                rand(0, 5),
                rand(0, 20)
        );
    }

    private String toCsv(RunClassificationDto dto) {
        return String.join(",",
                dto.getDuration().toString(),
                dto.getDurationPb20().toString(),
                dto.getDistance().toString(),
                dto.getDistancePb20().toString(),
                dto.getPace().toString(),
                dto.getPacePb20().toString(),
                dto.getElevationGain().toString(),
                dto.getSessionLoad().toString(),
                dto.getNumPaceSpikes().toString(),
                dto.getReadinessScore().toString(),
                dto.getConsistencyScore().toString(),
                dto.getTsb().toString(),
                dto.getAge().toString(),
                dto.getWeight().toString(),
                dto.getHeight().toString(),
                dto.getSex().name(),
                dto.getExperienceLevel().name(),
                dto.getInjuryIndex().toString(),
                dto.getHrAvg().toString(),
                dto.getHrAvgMissing().toString(),
                dto.getHrMax().toString(),
                dto.getHrMaxMissing().toString(),
                dto.getZone1().toString(),
                dto.getZone1Missing().toString(),
                dto.getZone2().toString(),
                dto.getZone2Missing().toString(),
                dto.getZone3().toString(),
                dto.getZone3Missing().toString(),
                dto.getZone4().toString(),
                dto.getZone4Missing().toString(),
                dto.getZone5().toString(),
                dto.getZone5Missing().toString(),
                dto.getNumHrSpikes().toString(),
                dto.getNumHrSpikesMissing().toString(),
                dto.getWindSpeed10m().toString(),
                dto.getTemperature2m().toString(),
                dto.getUvIndex().toString(),
                dto.getPrecipitation().toString(),
                dto.getSnowDepth().toString()
        );
    }

    private double rand(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private int randInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    private <T extends Enum<?>> T randomEnum(Class<T> e) {
        return e.getEnumConstants()[random.nextInt(e.getEnumConstants().length)];
    }

}

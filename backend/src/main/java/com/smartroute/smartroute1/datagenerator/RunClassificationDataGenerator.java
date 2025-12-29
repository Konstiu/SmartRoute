package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDto;
import com.smartroute.smartroute1.endpoint.dto.RunClassificationResultDto;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.RunType;
import com.smartroute.smartroute1.entity.enums.RunTypeProfile;
import com.smartroute.smartroute1.entity.enums.RunnerProfile;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.service.RunTrainingClassificationService;
import com.smartroute.smartroute1.util.BoundedDirichletDistributor;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
    private static final int K1 = 30;
    private static final int K2 = 60;
    private static final int K3 = 120;
    private static final int K4 = 240;
    private static final int K5 = 480;
    private static final float TIME_MODIFIER = 75;
    private final Random random = new Random();
    private final RunTrainingClassificationService runTrainingClassificationService;

    public RunClassificationDataGenerator(RunTrainingClassificationService runTrainingClassificationService) {
        this.runTrainingClassificationService = runTrainingClassificationService;
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
                    0,
                    0,
                    0,
                    0.1,
                    0.55,
                    0.45
            );
            case TEMPO_RUN -> new RunTypeProfile(
                    0.95,
                    0.8,
                    0.8,
                    35,
                    1,
                    0.05,
                    0.1,
                    0.4,
                    0.35,
                    0.1

            );
            case INTERVAL_RUN -> new RunTypeProfile(
                    0.85,
                    0.5,
                    0.7,
                    60,
                    6,
                    0.15,
                    0.2,
                    0.2,
                    0.3,
                    0.15
            );
            case LONG_RUN -> new RunTypeProfile(
                    1.10,
                    1.3,
                    1.1,
                    25,
                    1,
                    0.02,
                    0.03,
                    0.15,
                    0.6,
                    0.2
            );
        };
    }

    public void createCsv() throws IOException {
        Files.createDirectories(CSV_PATH.getParent());

        try (BufferedWriter w = Files.newBufferedWriter(CSV_PATH)) {

            w.write(CSV_HEADER);
            w.newLine();

            List<RunClassificationResultDto> runs = new ArrayList<>();
            for (int i = 0; i < NUMBER_OF_RUNS; i++) {
                runs.add(generate());
            }

            for (RunClassificationResultDto r : runs) {
                w.write(toCsv(r));
                w.newLine();
            }
        }

        //runTrainingClassificationService.classifyCsv(CSV_PATH, CSV_OUT_PATH);
    }

    private RunClassificationResultDto generate() {
        ExperienceLevel exp = randomEnum(ExperienceLevel.class);
        RunType runType = randomEnum(RunType.class);

        RunnerProfile athlete = runnerProfile(exp);
        RunTypeProfile type = runTypeProfile(runType);

        double basePace = rand(athlete.minPace(), athlete.maxPace());
        double pace = basePace * type.paceMultiplier();

        double distance = rand(3.0, athlete.maxDistance()) * type.distanceMultiplier();
        int duration = (int) (distance * pace * 60);


        double hrAvg = rand(0.7, 0.9) * athlete.maxHr();
        double[] base = new double[]{
                type.zone5p(),
                type.zone4p(),
                type.zone3p(),
                type.zone2p(),
                type.zone1p(),
        };

        double[] min = new double[5];
        double[] max = new double[5];
        for (int i = 0; i < 5; i++) {
            min[i] = Math.max(0, base[i] - 0.1);
            max[i] = Math.min(1, base[i] + 0.1);
        }


        BoundedDirichletDistributor distributor = new BoundedDirichletDistributor();
        double[] distribution = distributor.distribute(base, min, max, 0.2);

        Map<Integer, Float> zoneTimes = new HashMap<>();

        zoneTimes.put(1, (float) Math.round(distribution[4] * duration));
        zoneTimes.put(2, (float) Math.round(distribution[3] * duration));
        zoneTimes.put(3, (float) Math.round(distribution[2] * duration));
        zoneTimes.put(4, (float) Math.round(distribution[1] * duration));
        zoneTimes.put(5, (float) Math.round(distribution[0] * duration));
        double elevation = rand(0, distance * 25) * type.elevationMultiplier();
        double hrMax = rand(hrAvg + 5, athlete.maxHr());

        double temperature = rand(-5, 30);

        return new RunClassificationResultDto(new RunClassificationDto(
                duration,
                rand(0.7, 1.3),
                distance,
                rand(0.7, 1.3),
                pace,
                rand(0.7, 1.3),
                elevation,
                (double) calculateTrimp(zoneTimes),
                (int) Math.round(type.paceSpikes() * randDouble(1, 1.8) * (((double) duration / 3600))),
                randInt(40, 90),
                rand(0.3, 1.0),
                rand(-20, 20),
                randInt(18, 60),
                rand(55, 90),
                randInt(155, 195),
                randomEnum(Sex.class),
                exp,
                randInt(0, 100) < 50 ? athlete.injuryRiskFactor() * rand(0.1, 0.5) : 0,
                hrAvg,
                false,
                hrMax,
                false,
                (int) (float) zoneTimes.get(1), false,
                (int) (float) zoneTimes.get(2), false,
                (int) (float) zoneTimes.get(3), false,
                (int) (float) zoneTimes.get(4), false,
                (int) (float) zoneTimes.get(5), false,
                runType == RunType.INTERVAL_RUN ? randInt(5, 10) * (duration / 3600) : randInt(0, 2) * (duration / 3600),
                false,
                rand(0, 12),
                temperature,
                randInt(0, 10),
                randInt(0, 100) < 20 ? rand(0, 5) : 0,
                temperature < 1 ? rand(0, 20) : 0
        ),
                runType);
    }

    private String toCsv(RunClassificationResultDto dto) {
        return String.join(",",
                dto.getRun().getDuration().toString(),
                dto.getRun().getDurationPb20().toString(),
                dto.getRun().getDistance().toString(),
                dto.getRun().getDistancePb20().toString(),
                dto.getRun().getPace().toString(),
                dto.getRun().getPacePb20().toString(),
                dto.getRun().getElevationGain().toString(),
                dto.getRun().getSessionLoad().toString(),
                dto.getRun().getNumPaceSpikes().toString(),
                dto.getRun().getReadinessScore().toString(),
                dto.getRun().getConsistencyScore().toString(),
                dto.getRun().getTsb().toString(),
                dto.getRun().getAge().toString(),
                dto.getRun().getWeight().toString(),
                dto.getRun().getHeight().toString(),
                dto.getRun().getSex().name(),
                dto.getRun().getExperienceLevel().name(),
                dto.getRun().getInjuryIndex().toString(),
                dto.getRun().getHrAvg().toString(),
                dto.getRun().getHrAvgMissing().toString(),
                dto.getRun().getHrMax().toString(),
                dto.getRun().getHrMaxMissing().toString(),
                dto.getRun().getZone1().toString(),
                dto.getRun().getZone1Missing().toString(),
                dto.getRun().getZone2().toString(),
                dto.getRun().getZone2Missing().toString(),
                dto.getRun().getZone3().toString(),
                dto.getRun().getZone3Missing().toString(),
                dto.getRun().getZone4().toString(),
                dto.getRun().getZone4Missing().toString(),
                dto.getRun().getZone5().toString(),
                dto.getRun().getZone5Missing().toString(),
                dto.getRun().getNumHrSpikes().toString(),
                dto.getRun().getNumHrSpikesMissing().toString(),
                dto.getRun().getWindSpeed10m().toString(),
                dto.getRun().getTemperature2m().toString(),
                dto.getRun().getUvIndex().toString(),
                dto.getRun().getPrecipitation().toString(),
                dto.getRun().getSnowDepth().toString(),
                dto.getClassification().name()

        );
    }

    private double rand(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private int randInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    private double randDouble(double min, double max) {
        return random.nextDouble(max - min + 1) + min;
    }

    private <T extends Enum<?>> T randomEnum(Class<T> e) {
        return e.getEnumConstants()[random.nextInt(e.getEnumConstants().length)];
    }

    private int calculateTrimp(Map<Integer, Float> timeInZones) {
        float trimp = 0;
        for (Map.Entry<Integer, Float> entry : timeInZones.entrySet()) {
            float timeInZone = entry.getValue();
            int coefficient = switch (entry.getKey()) {
                case 1 -> K1;
                case 2 -> K2;
                case 3 -> K3;
                case 4 -> K4;
                case 5 -> K5;
                default -> 0;
            };

            // Reduce weight for Zone 1 and 2 for shorter activities
            float timeCoefficient = 1;
            if (coefficient == K1 || coefficient == K2) {
                timeCoefficient = Math.min(1, timeInZone / 60 / TIME_MODIFIER);
            }

            //TRIMP = Sum (K_i * TK_i * t_i), with t_i in hours
            trimp += coefficient * timeCoefficient * (timeInZone / 3600);
        }
        return Math.round(trimp);
    }

}

package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDto;
import com.smartroute.smartroute1.endpoint.dto.RunClassificationResultDto;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.service.RunClassificationService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class RunClassificationServiceImpl implements RunClassificationService {

    @Override
    public RunClassificationResultDto classifyRun(RunClassificationDto dto) {


        double loadPerMin = normalizeLoad(dto);
        double intensity = resolveIntensity(dto);

        Thresholds t = Thresholds.forExperience(dto.getExperienceLevel());

        // EASY
        double easy = 0;
        if (intensity < t.easyMaxHr) {
            easy += 3;
        }
        if (loadPerMin < t.easyMaxLoad) {
            easy += 2;
        }
        if (dto.getConsistencyScore() != null && dto.getConsistencyScore() > 0.6) {
            easy += 1;
        }

        // TEMPO
        double tempo = 0;
        if (intensity >= t.tempoMinHr && intensity <= t.tempoMaxHr) {
            tempo += 4;
        }
        if (loadPerMin >= t.tempoMinLoad && loadPerMin <= t.tempoMaxLoad) {
            tempo += 2;
        }
        if (dto.getNumPaceSpikes() != null && dto.getNumPaceSpikes() <= 3) {
            tempo += 1;
        }

        // INTERVAL
        double interval = 0;
        if (isIntervalLike(dto, t.intervalPaceSpikes)) {
            interval += 4;
        }
        if (intensity >= t.intervalMinHr) {
            interval += 3;
        }
        if (dto.getPacePb20() != null && dto.getPacePb20() > 1.05) {
            interval += 2;
        }

        // LONG
        double durationMin = dto.getDuration() / 60.0;
        double longer = 0;
        if (durationMin >= 90) {
            longer += 4;
        }
        if (intensity < t.tempoMinHr) {
            longer += 2;
        }
        if (durationMin > 120) {
            longer += 2;
        }

        return new RunClassificationResultDto(dto, maxScore(easy, tempo, interval, longer));
    }

    @Override
    public Path classifyCsv(Path csvPath, Path outputCsv) throws IOException {

        try (BufferedReader reader = Files.newBufferedReader(csvPath);
             BufferedWriter writer = Files.newBufferedWriter(outputCsv)) {

            String header = reader.readLine(); // skip header
            if (header == null) {
                return outputCsv;
            }
            writer.write(header + ",classification");
            writer.newLine();

            String line;
            while ((line = reader.readLine()) != null) {

                RunClassificationDto dto = parseRunLine(line);
                RunClassificationResultDto result = classifyRun(dto);

                writer.write(line + "," + result.getClassification().name());
                writer.newLine();
            }
        }
        return outputCsv;
    }


    private WorkoutType maxScore(double easy, double tempo, double interval, double longer) {
        if (interval >= tempo && interval >= easy && interval >= longer) {
            return WorkoutType.INTERVAL_RUN;
        }
        if (longer >= tempo && longer >= easy) {
            return WorkoutType.LONG_RUN;
        }
        if (tempo >= easy) {
            return WorkoutType.TEMPO_RUN;
        }
        return WorkoutType.EASY_RUN;
    }

    private double resolveIntensity(RunClassificationDto dto) {

        // Prefer HR if available
        if (Boolean.FALSE.equals(dto.getHrAvgMissing()) && dto.getHrAvg() != null) {
            return normalizeHr(dto.getHrAvg(), dto.getSex());
        }

        // Fallback to pace vs PB
        if (dto.getPacePb20() != null) {
            return Math.min(1.0, dto.getPacePb20());
        }

        // Last resort
        return 0.75;
    }

    private double normalizeHr(double hrPctMax, Sex sex) {
        if (sex == Sex.FEMALE) {
            return hrPctMax * 0.97;
        }
        return hrPctMax;
    }

    private double normalizeLoad(RunClassificationDto dto) {
        double weightFactor = 70.0 / Math.max(50.0, dto.getWeight());
        return (dto.getSessionLoad() / (dto.getDuration() / 60.0)) * weightFactor;
    }

    private boolean isIntervalLike(RunClassificationDto dto, int intervalPaceSpikes) {
        boolean paceSpikes = dto.getNumPaceSpikes() != null && dto.getNumPaceSpikes() >= intervalPaceSpikes;
        boolean hrSpikes = Boolean.FALSE.equals(dto.getNumHrSpikesMissing())
                && dto.getNumHrSpikes() != null
                && dto.getNumHrSpikes() >= 5;

        return paceSpikes || hrSpikes;
    }

    private RunClassificationDto parseRunLine(String line) {

        String[] c = line.split(",");

        int i = 0;

        return new RunClassificationDto(
                parseInt(c[i++]),              // duration
                parseDouble(c[i++]),           // duration_pct_pb_20
                parseDouble(c[i++]),           // distance
                parseDouble(c[i++]),           // distance_pct_pb_20
                parseDouble(c[i++]),           // pace
                parseDouble(c[i++]),           // pace_pct_pb_20
                parseDouble(c[i++]),           // elevation_gain
                parseDouble(c[i++]),           // session_load
                parseInt(c[i++]),              // num_pace_spikes
                parseInt(c[i++]),              // readiness_score
                parseInt(c[i++]),              // suffer_score
                parseDouble(c[i++]),           // consistency_score
                parseDouble(c[i++]),           // tsb
                parseInt(c[i++]),              // age
                parseDouble(c[i++]),           // weight
                parseInt(c[i++]),              // height
                Sex.valueOf(c[i++].trim()),    // sex
                ExperienceLevel.valueOf(c[i++].trim()), // experience_level
                parseDouble(c[i++]),           // injury_index
                parseDouble(c[i++]),           // hr_avg
                parseBoolean(c[i++]),          // hr_avg_missing
                parseDouble(c[i++]),           // hr_max
                parseBoolean(c[i++]),          // hr_max_missing
                parseInt(c[i++]),              // zone1
                parseBoolean(c[i++]),          // zone1_missing
                parseInt(c[i++]),              // zone2
                parseBoolean(c[i++]),          // zone2_missing
                parseInt(c[i++]),              // zone3
                parseBoolean(c[i++]),          // zone3_missing
                parseInt(c[i++]),              // zone4
                parseBoolean(c[i++]),          // zone4_missing
                parseInt(c[i++]),              // zone5
                parseBoolean(c[i++]),          // zone5_missing
                parseInt(c[i++]),              // num_hr_spikes
                parseBoolean(c[i++]),          // num_hr_spikes_missing
                parseDouble(c[i++]),           // windSpeed10m
                parseDouble(c[i++]),           // temperature2m
                parseInt(c[i++]),              // uv_index
                parseDouble(c[i++]),           // precipitation
                parseDouble(c[i++])            // snowDepth
        );
    }

    private Integer parseInt(String s) {
        return s == null || s.isBlank() ? null : Integer.parseInt(s.trim());
    }

    private Double parseDouble(String s) {
        return s == null || s.isBlank() ? null : Double.parseDouble(s.trim());
    }

    private Boolean parseBoolean(String s) {
        return s == null || s.isBlank() ? Boolean.TRUE : Boolean.parseBoolean(s.trim());
    }

    private static class Thresholds {

        double easyMaxHr;
        double tempoMinHr;
        double tempoMaxHr;
        double intervalMinHr;

        double easyMaxLoad;
        double tempoMinLoad;
        double tempoMaxLoad;

        int intervalPaceSpikes;

        static Thresholds forExperience(ExperienceLevel level) {

            Thresholds t = new Thresholds();

            switch (level) {

                case BEGINNER -> {
                    t.easyMaxHr = 0.70;
                    t.tempoMinHr = 0.78;
                    t.tempoMaxHr = 0.84;
                    t.intervalMinHr = 0.88;
                    t.easyMaxLoad = 3.5;
                    t.tempoMinLoad = 4.5;
                    t.tempoMaxLoad = 7.5;
                    t.intervalPaceSpikes = 5;
                }

                case CASUAL -> {
                    t.easyMaxHr = 0.73;
                    t.tempoMinHr = 0.80;
                    t.tempoMaxHr = 0.86;
                    t.intervalMinHr = 0.90;
                    t.easyMaxLoad = 4.0;
                    t.tempoMinLoad = 5.0;
                    t.tempoMaxLoad = 8.5;
                    t.intervalPaceSpikes = 6;
                }

                case INTERMEDIATE -> {
                    t.easyMaxHr = 0.75;
                    t.tempoMinHr = 0.82;
                    t.tempoMaxHr = 0.88;
                    t.intervalMinHr = 0.91;
                    t.easyMaxLoad = 5.0;
                    t.tempoMinLoad = 6.0;
                    t.tempoMaxLoad = 10.0;
                    t.intervalPaceSpikes = 6;
                }

                case ADVANCED -> {
                    t.easyMaxHr = 0.78;
                    t.tempoMinHr = 0.84;
                    t.tempoMaxHr = 0.90;
                    t.intervalMinHr = 0.92;
                    t.easyMaxLoad = 6.0;
                    t.tempoMinLoad = 7.0;
                    t.tempoMaxLoad = 12.0;
                    t.intervalPaceSpikes = 7;
                }

                case COMPETITIVE_ATHLETE -> {
                    t.easyMaxHr = 0.80;
                    t.tempoMinHr = 0.86;
                    t.tempoMaxHr = 0.92;
                    t.intervalMinHr = 0.94;
                    t.easyMaxLoad = 7.0;
                    t.tempoMinLoad = 8.0;
                    t.tempoMaxLoad = 14.0;
                    t.intervalPaceSpikes = 8;
                }
                // default is intermediate
                default -> {
                    t.easyMaxHr = 0.75;
                    t.tempoMinHr = 0.82;
                    t.tempoMaxHr = 0.88;
                    t.intervalMinHr = 0.91;
                    t.easyMaxLoad = 5.0;
                    t.tempoMinLoad = 6.0;
                    t.tempoMaxLoad = 10.0;
                    t.intervalPaceSpikes = 6;
                }
            }
            return t;
        }
    }
}

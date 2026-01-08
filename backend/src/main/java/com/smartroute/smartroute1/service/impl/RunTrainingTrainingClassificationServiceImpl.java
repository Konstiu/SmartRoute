package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDto;
import com.smartroute.smartroute1.endpoint.dto.RunClassificationResultDto;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.RunType;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.service.RunTrainingClassificationService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.Float.parseFloat;

@Service
public class RunTrainingTrainingClassificationServiceImpl implements RunTrainingClassificationService {

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

        // TEMPO
        boolean tempoEligible =
                intensity >= t.tempoMinHr
                        && intensity <= t.tempoMaxHr
                        && loadPerMin >= t.tempoMinLoad
                        && loadPerMin <= t.tempoMaxLoad
                        && dto.getNumPaceSpikes() != null
                        && dto.getNumPaceSpikes() <= 3;


        // INTERVAL

        boolean highInjuryRisk = dto.getInjuryIndex() != null && dto.getInjuryIndex() > 0.7;
        double durationMin = dto.getDuration() / 60.0;

        boolean intervalEligible =
                !highInjuryRisk
                        && durationMin >= 20
                        && durationMin <= 75
                        && intensity >= t.intervalMinHr
                        && dto.getPacePb20() != null && dto.getPacePb20() < 0.95
                        && isIntervalLike(dto, t.intervalPaceSpikes);


        // LONG

        boolean longEligible =
                durationMin >= 75
                        && intensity < t.tempoMinHr;

        double longer = longEligible ? 5 : -100;
        double interval = intervalEligible ? 5 : -100;
        double tempo = tempoEligible ? 5 : -100;
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


    private RunType maxScore(double easy, double tempo, double interval, double longer) {
        if (interval >= 0) {
            return RunType.INTERVAL_RUN;
        }
        if (longer >= 0) {
            return RunType.LONG_RUN;
        }
        if (tempo >= 0) {
            return RunType.TEMPO_RUN;
        }
        if (easy > 0) {
            return RunType.EASY_RUN;
        }
        return RunType.TEMPO_RUN;
    }

    private double resolveIntensity(RunClassificationDto dto) {

        double baseIntensity;

        // Prefer HR if available
        if (Boolean.FALSE.equals(dto.getHrAvgMissing()) && dto.getHrAvg() != null) {
            baseIntensity = normalizeHr(dto.getHrAvg(), dto.getSex());
        } else if (dto.getPacePb20() != null) {
            baseIntensity = Math.min(1.0, dto.getPacePb20());
        } else {
            baseIntensity = 0.75;
        }

        // ---- Environment penalties ----
        double envPenalty =
                windPenalty(dto.getWindSpeed10m()) * temperaturePenalty(dto.getTemperature2m()) * uvPenalty(dto.getUvIndex());

        // ---- Readiness & injury gating ----
        double readinessFactor = dto.getReadinessScore() != null
                ? dto.getReadinessScore() / 100.0
                : 0.75;

        double injuryFactor = dto.getInjuryIndex() != null && dto.getInjuryIndex() > 0.6
                ? 0.85
                : 1.0;

        return baseIntensity * envPenalty * readinessFactor * injuryFactor;
    }


    private double uvPenalty(Integer uv) {
        if (uv == null) {
            return 1.0;
        }
        if (uv < 5) {
            return 1.0;
        }
        if (uv < 8) {
            return 1.03;
        }
        return 1.06;
    }

    private double normalizeHr(double hrPctMax, Sex sex) {
        if (sex == Sex.FEMALE) {
            return hrPctMax * 0.97;
        }
        return hrPctMax;
    }

    private double normalizeLoad(RunClassificationDto dto) {

        double weightFactor = 70.0 / Math.max(50.0, dto.getWeight());
        double baseLoad = dto.getSessionLoad() / (dto.getDuration() / 60.0);

        double surfacePenalty =
                surfacePenalty(dto.getSnowDepth(), dto.getPrecipitation());

        double windPenalty =
                windPenalty(dto.getWindSpeed10m());

        return baseLoad * weightFactor * surfacePenalty * windPenalty;
    }

    private boolean isIntervalLike(RunClassificationDto dto, int intervalPaceSpikes) {
        boolean paceSpikes = dto.getNumPaceSpikes() != null && dto.getNumPaceSpikes() >= intervalPaceSpikes;
        boolean hrSpikes = Boolean.FALSE.equals(dto.getNumHrSpikesMissing())
                && dto.getNumHrSpikes() != null
                && dto.getNumHrSpikes() >= 5;

        return paceSpikes && hrSpikes;
    }

    private double windPenalty(double windSpeed) {
        if (windSpeed < 5) {
            return 1.0;
        }
        if (windSpeed < 15) {
            return 1.05;
        }
        return 1.10;
    }

    private double temperaturePenalty(double temp) {
        if (temp < 5) {
            return 0.97;
        }
        if (temp <= 20) {
            return 1.0;
        }
        if (temp <= 30) {
            return 1.05;
        }
        return 1.10;
    }

    private double surfacePenalty(Double snow, Double rain) {
        if ((snow != null && snow > 1) || (rain != null && rain > 5)) {
            return 1.10;
        }
        return 1.0;
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
                parseFloat(c[i++]),              // zone1pct
                parseBoolean(c[i++]),          // zone1_missing
                parseInt(c[i++]),              // zone2
                parseFloat(c[i++]),              // zone2pct
                parseBoolean(c[i++]),          // zone2_missing
                parseInt(c[i++]),              // zone3
                parseFloat(c[i++]),              // zone3pct
                parseBoolean(c[i++]),          // zone3_missing
                parseInt(c[i++]),              // zone4
                parseFloat(c[i++]),              // zone4pct
                parseBoolean(c[i++]),          // zone4_missing
                parseInt(c[i++]),              // zone5
                parseFloat(c[i++]),              // zone5pct
                parseBoolean(c[i++]),          // zone5_missing
                parseInt(c[i++]),              // num_hr_spikes
                parseBoolean(c[i++]),          // num_hr_spikes_missing
                parseDouble(c[i++]),           // windSpeed10m
                parseDouble(c[i++]),           // temperature2m
                parseInt(c[i++]),              // uv_index
                parseDouble(c[i++]),           // precipitation
                parseDouble(c[i])            // snowDepth
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

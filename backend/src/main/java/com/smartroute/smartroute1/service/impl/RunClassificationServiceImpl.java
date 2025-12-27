package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDto;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.service.RunClassificationService;
import org.springframework.stereotype.Service;

@Service
public class RunClassificationServiceImpl implements RunClassificationService {

    @SuppressWarnings("checkstyle:NeedBraces")
    @Override
    public WorkoutType classifyRun(RunClassificationDto dto) {

        double durationMin = dto.getDuration() / 60.0;
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

        return maxScore(easy, tempo, interval, longer);
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
            }
            return t;
        }
    }
}

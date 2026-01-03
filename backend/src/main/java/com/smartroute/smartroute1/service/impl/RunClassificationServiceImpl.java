package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.endpoint.dto.RunClassificationDecisionDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.RunType;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import com.smartroute.smartroute1.service.ConsistencyAnalyzerService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.RunClassificationService;
import com.smartroute.smartroute1.service.WeatherService;
import jakarta.transaction.Transactional;
import jakarta.xml.bind.JAXBException;
import org.dmg.pmml.ResultFeature;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.EvaluatorUtil;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.OutputField;
import org.jpmml.evaluator.TargetField;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.jpmml.evaluator.InputField;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RunClassificationServiceImpl implements RunClassificationService {
    private final ActivityRepository activityRepository;
    private final ActivityProcessingService activityProcessingService;
    private final ReadinessScoreService readinessScoreService;
    private final ConsistencyAnalyzerService consistencyAnalyzerService;
    private final FatigueAndOverloadService fatigueAndOverloadService;
    private final InjuryAwareTrainingService injuryAwareTrainingService;
    private final Evaluator evaluator;
    private final List<TargetField> targetFields;
    private final List<OutputField> outputFields;
    private final WeatherService weatherService;

    public RunClassificationServiceImpl(ActivityRepository activityRepository, ActivityProcessingService activityProcessingService, ReadinessScoreService readinessScoreService, ConsistencyAnalyzerService consistencyAnalyzerService,
                                        InjuryAwareTrainingService injuryAwareTrainingService,
                                        FatigueAndOverloadService fatigueAndOverloadService, WeatherService weatherService)
        throws IOException, JAXBException, SAXException, ParserConfigurationException {
        this.activityRepository = activityRepository;
        this.activityProcessingService = activityProcessingService;
        this.readinessScoreService = readinessScoreService;
        this.consistencyAnalyzerService = consistencyAnalyzerService;

        this.evaluator = new LoadingModelEvaluatorBuilder()
            .load(new ClassPathResource("models/run_classifier.pmml").getInputStream())
            .build();
        this.evaluator.verify();

        this.targetFields = evaluator.getTargetFields();
        this.outputFields = evaluator.getOutputFields();
        this.fatigueAndOverloadService = fatigueAndOverloadService;
        this.injuryAwareTrainingService = injuryAwareTrainingService;
        this.weatherService = weatherService;
    }

    @Override
    @Transactional
    public RunClassificationDecisionDto classifyRun(Long activityId) {
        Activity activity = activityRepository.findById(activityId).orElseThrow();

        if (!activity.getSportType().equals("Run")) {
            return null;
        }

        return evaluate(activity);
    }

    private Map<String, FieldValue> buildFeatureMap(Activity activity) {
        Map<String, FieldValue> featureMap = new HashMap<>();

        for (InputField inputField : evaluator.getInputFields()) {

            String fieldName = inputField.getName();
            Object rawValue = extractValue(activity, fieldName);

            FieldValue fieldValue = inputField.prepare(rawValue);

            featureMap.put(fieldName, fieldValue);
        }
        return featureMap;
    }

    private Object extractValue(Activity activity, String fieldName) {
        ApplicationUser user = activity.getUser();

        return switch (fieldName) {
            // Activity data
            case "duration" -> activity.getMovingTime();
            case "duration_pct_pb_20" -> getDurationPercentageRelativeToPbLast20Runs(activity);

            case "distance" -> activity.getDistance();
            case "distance_pct_pb_20" -> getDistancePercentageRelativeToPbLast20Runs(activity);

            case "pace" -> activity.getAverageSpeed();
            case "pace_pct_pb_20" -> getPacePercentageRelativeToPbLast20Runs(activity);

            case "elevation_gain" -> activity.getTotalElevationGain();

            case "session_load" -> activity.getSessionLoad();

            case "num_pace_spikes" -> activityProcessingService.detectPaceSpikes(activity);

            case "readiness_score" -> getReadinessScoreBeforeActivity(activity);

            case "consistency_score" -> getConsistencyScoreBeforeActivity(activity);

            case "tsb" -> fatigueAndOverloadService.tsbOn(user, activity.getStartDate().atZone(ZoneOffset.UTC).toLocalDate());

            // User data
            case "age" -> Period.between(user.getBirthdate(), LocalDate.now()).getYears();

            case "weight" -> user.getWeight().floatValue();

            case "height" -> user.getHeight();

            case "sex" -> user.getSex().ordinal();

            case "experience_level" -> user.getExperienceLevel().ordinal();

            case "injury_index" -> injuryAwareTrainingService.getInjuryIndex(user.getEmail());

            //Heart rate data
            case "hr_avg" -> activity.getMaxHeartrate() != null ? getMaxHrPercentageRelativeToAllRuns(activity) : -1;
            case "hr_avg_missing" -> activity.getMaxHeartrate() != null ? 0 : 1;

            case "hr_max" -> activity.getAverageHeartrate() != null ? getMaxAverageHrPercentageRelativeToAllRuns(activity) : -1;
            case "hr_max_missing" -> activity.getAverageHeartrate() != null ? 0 : 1;

            case "zone1" -> activity.getTimeZ1() != null ? activity.getTimeZ1() : 0;
            case "zone1_missing" -> activity.getTimeZ1() != null ? 0 : 1;

            case "zone2" -> activity.getTimeZ2() != null ? activity.getTimeZ2() : -1;
            case "zone2_missing" -> activity.getTimeZ2() != null ? 0 : 1;

            case "zone3" -> activity.getTimeZ3() != null ? activity.getTimeZ3() : -1;
            case "zone3_missing" -> activity.getTimeZ3() != null ? 0 : 1;

            case "zone4" -> activity.getTimeZ4() != null ? activity.getTimeZ4() : -1;
            case "zone4_missing" -> activity.getTimeZ4() != null ? 0 : 1;

            case "zone5" -> activity.getTimeZ5() != null ? activity.getTimeZ5() : -1;
            case "zone5_missing" -> activity.getTimeZ5() != null ? 0 : 1;

            case "num_hr_spikes" -> activityProcessingService.detectHeartRateSpikes(activity);
            case "num_hr_spikes_missing" -> activityProcessingService.detectHeartRateSpikes(activity) != -1 ? 0 : 1;

            // Weather data
            // TODO cannot fetch past weather -> store in activity or ignore weather?
            /*
            case "windSpeed10m" -> weatherService.getWeatherAtTime(activity.getSummaryPolyline())
            windSpeed10m, temperature2m, uv_index, precipitation, snowDepth,
             */

            case "windSpeed10m" -> 0;
            case "temperature2m" -> 0;
            case "uv_index" -> 0;
            case "precipitation" -> 0;
            case "snowDepth" -> 0;

            default -> null;
        };
    }

    private RunClassificationDecisionDto evaluate(Activity activity) {
        Map<String, FieldValue> input = buildFeatureMap(activity);
        Map<String, ?> results = evaluator.evaluate(input);
        results = EvaluatorUtil.decodeAll(results);

        TargetField targetField = targetFields.getFirst();
        String targetName = targetField.getName();

        Object targetValue = results.get(targetName);

        Map<RunType, Double> probabilities = new EnumMap<>(RunType.class);

        for (OutputField outputField : outputFields) {
            if (!outputField.getField().getResultFeature().equals(ResultFeature.PROBABILITY)) {
                continue;
            }

            String fieldName = outputField.getName();
            Object value = results.get(fieldName);

            if (value == null) {
                continue;
            }

            double probability = ((Number) value).doubleValue();
            RunType workoutType = mapProbabilityField(fieldName);

            probabilities.put(workoutType, probability);
        }

        return getRunClassificationDecisionDto(targetValue, probabilities);
    }

    private static RunClassificationDecisionDto getRunClassificationDecisionDto(Object targetValue, Map<RunType, Double> probabilities) {
        if (targetValue == null) {
            throw new IllegalStateException("PMML did not return a prediction");
        }

        if (!(targetValue instanceof Integer label)) {
            throw new IllegalStateException("PMML did not return an integer");
        }

        RunClassificationDecisionDto dto = new RunClassificationDecisionDto();
        dto.setRunType(switch (label) {
            case 0 -> RunType.EASY_RUN;
            case 1 -> RunType.TEMPO_RUN;
            case 2 -> RunType.INTERVAL_RUN;
            case 3 -> RunType.LONG_RUN;
            default -> throw new IllegalStateException("PMML did return an invalid value");
        });
        dto.setProbabilities(probabilities);
        return dto;
    }

    private RunType mapProbabilityField(String fieldName) {
        if (fieldName.contains("(0)")) {
            return RunType.EASY_RUN;
        }
        if (fieldName.contains("(1)")) {
            return RunType.TEMPO_RUN;
        }
        if (fieldName.contains("(2)")) {
            return RunType.INTERVAL_RUN;
        }
        if (fieldName.contains("(3)")) {
            return RunType.LONG_RUN;
        }

        throw new IllegalArgumentException("Unknown probability field: " + fieldName);
    }

    private double getConsistencyScoreBeforeActivity(Activity activity) {
        ApplicationUser user = activity.getUser();
        ConsistencyScoreResultDto result = consistencyAnalyzerService.computeScore(user, activity.getStartDate().minus(14, ChronoUnit.DAYS), activity.getStartDate(), user.getActiveWeekdays().size());

        return result.getFinalScore();
    }

    private int getReadinessScoreBeforeActivity(Activity activity) {
        ApplicationUser user = activity.getUser();
        return readinessScoreService.calculateReadinessScore(user, activity.getStartDate().atZone(ZoneOffset.UTC).toLocalDate());
    }

    private double getDurationPercentageRelativeToPbLast20Runs(Activity activity) {
        ApplicationUser user = activity.getUser();

        // TODO exclude activity from query? (to allow > 100%) - compare with model data generator; last 20 vs last 20 before activity?
        int maxDuration = activityRepository.findTop3AvgDurationInLast20ActivitiesByUserAndType(user, "Run");

        if (maxDuration == -1) {
            return 1.00;
        }
        return (double) activity.getMovingTime() / maxDuration;
    }

    private double getDistancePercentageRelativeToPbLast20Runs(Activity activity) {
        ApplicationUser user = activity.getUser();

        // TODO exclude activity from query? (to allow > 100%) - compare with model data generator; last 20 vs last 20 before activity?
        int maxDistance = activityRepository.findTop3AvgDistanceInLast20ActivitiesByUserAndType(user, "Run");

        if (maxDistance == -1) {
            return 1.00;
        }
        return activity.getDistance() / maxDistance;
    }

    private double getPacePercentageRelativeToPbLast20Runs(Activity activity) {
        ApplicationUser user = activity.getUser();

        // TODO exclude activity from query? (to allow > 100%) - compare with model data generator; last 20 vs last 20 before activity?
        double maxPace = activityRepository.findTop3AvgPaceInLast20ActivitiesByUserAndType(user, "Run");

        if (maxPace == -1) {
            return 1.00;
        }
        return activity.getAverageSpeed() / maxPace;
    }

    private double getMaxHrPercentageRelativeToAllRuns(Activity activity) {
        ApplicationUser user = activity.getUser();

        double maxHr = activityRepository.getMaxMaxHrInAllActivitiesByUserAndType(user, "Run");

        if (maxHr == -1) {
            return 1.00;
        }
        return activity.getMaxHeartrate() / maxHr;
    }

    private double getMaxAverageHrPercentageRelativeToAllRuns(Activity activity) {
        ApplicationUser user = activity.getUser();

        double maxAvgHr = activityRepository.getMaxAverageHrInAllActivitiesByUserAndType(user, "Run");

        if (maxAvgHr == -1) {
            return 1.00;
        }
        return activity.getAverageHeartrate() / maxAvgHr;
    }
}

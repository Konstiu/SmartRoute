package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.*;
import com.smartroute.smartroute1.endpoint.mapper.PolyLineMapper;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import com.smartroute.smartroute1.service.RouteEvaluationService;
import com.smartroute.smartroute1.service.RouteGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RouteGenerationServiceImpl implements RouteGenerationService {

    private final ActivityRepository activityRepository;
    private final OpenRouteServiceService openRouteServiceService;
    private final RouteEvaluationService routeEvaluationService;
    private static final int historySize = 10;

    private static final double easyDistanceFactor = 0.8f; // 0.7 - 1.0
    private static final double tempoDistanceFactor = 0.9f; // 0.8 - 1.1
    private static final double longDistanceFactor = 1.7f; // 1.5 - 2.0
    private static final double maxDistanceProgress = 1000; // 1.0 - 1.3

    private static final double readinessPacePenalty = 0.1;
    private static final double easyPaceFactor = 0.1; // 0.05 - 0.15
    private static final double tempoPaceFactor = 0.2; // 0.10 - 0.25
    private static final double longPaceFactor = 0.05; // 0.0 - 0.1

    // https://www.dailymail.co.uk/health/article-14071885/How-long-able-run-without-stopping-age.html
    private static final double[][] firstDistanceMale = {
            {2800, 2400, 2200, 1600, 1400}, // 20 - 29
            {2700, 2300, 1900, 1500, 1300}, // 30 - 39
            {2500, 2100, 1700, 1400, 1200}, // 40 - 49
            {2400, 2000, 1600, 1300, 1100}, // 50+
    };
    private static final double[][] firstDistanceFemale = {
            {2700, 2200, 1800, 1500, 1300}, // 20 - 29
            {2500, 2000, 1700, 1400, 1200}, // 30 - 39
            {2300, 1900, 1500, 1200, 1000}, // 40 - 49
            {2200, 1700, 1400, 1100, 900}, // 50+
    };

    @Override
    public RouteDto generateRouteDetails(ApplicationUser user, WorkoutType workoutType, double readinessScore) {
        List<Activity> activities = activityRepository.findTop10ByUserAndTypeIsAndWorkoutTypeIsOrderByStartDateDesc(
                user, "Run", workoutType, PageRequest.of(0, historySize));

        if (activities.isEmpty()) {
            double distanceEstimate = estimateFirstDistance(user);
            return new RouteDto(distanceEstimate * 1.5, distanceEstimate / 30.0, getElevationCeiling(workoutType));
        }

        double targetDistance = calculateTargetDistance(activities, workoutType, readinessScore);
        double targetPace = calculateTargetPace(activities, workoutType, readinessScore);

        return new RouteDto(targetDistance, targetPace, getElevationCeiling(workoutType));
    }

    private double estimateFirstDistance(ApplicationUser user) {
        int levelIndex = switch (user.getExperienceLevel()) {
            case BEGINNER -> 0;
            case CASUAL -> 1;
            case INTERMEDIATE -> 2;
            case ADVANCED -> 3;
            case COMPETITIVE_ATHLETE -> 4;
        };

        long age = ChronoUnit.YEARS.between(user.getBirthdate(), LocalDate.now());
        int ageIndex = 0;
        if (age >= 50) {
            ageIndex = 3;
        } else if (age >= 40) {
            ageIndex = 2;
        } else if (age >= 30) {
            ageIndex = 1;
        }

        if (user.getSex() == Sex.MALE) {
            return firstDistanceMale[ageIndex][levelIndex];
        }

        return firstDistanceFemale[ageIndex][levelIndex];
    }

    private double calculateTargetDistance(List<Activity> activities, WorkoutType workoutType, double readinessScore) {
        double[] distances = activities.stream().map(Activity::getDistance).mapToDouble(Float::doubleValue).sorted().toArray();

        double medianDistance = distances[distances.length / 2];
        if (distances.length % 2 == 0) {
            medianDistance = (medianDistance + distances[distances.length / 2 + 1]) / 2;
        }

        double readinessModifier = 0.8 + 0.4 * (readinessScore / 100);

        if (workoutType == WorkoutType.EASY_RUN) {
            return medianDistance * easyDistanceFactor * readinessModifier;
        } else if (workoutType == WorkoutType.TEMPO_RUN || workoutType == WorkoutType.INTERVAL_RUN) {
            return medianDistance * tempoDistanceFactor * readinessModifier;
        } else {
            return Math.min(medianDistance * longDistanceFactor * readinessModifier, medianDistance + maxDistanceProgress);
        }
    }

    private double calculateTargetPace(List<Activity> activities, WorkoutType workoutType, double readinessScore) {
        double[] averagePaces = activities.stream()
                .map(a -> (a.getMovingTime() / 60) / (a.getDistance() / 1000))
                .sorted()
                .mapToDouble(Float::doubleValue).toArray();

        double medianPace = averagePaces[averagePaces.length / 2];
        if (averagePaces.length % 2 == 0) {
            medianPace = (medianPace + averagePaces[averagePaces.length / 2 - 1]) / 2;
        }

        double readinessModifier = (1 + readinessPacePenalty * (1 - readinessScore / 100));

        if (workoutType == WorkoutType.EASY_RUN) {
            return 1000 / (medianPace * (1 + easyPaceFactor) * readinessModifier) / 60;
        } else if (workoutType == WorkoutType.TEMPO_RUN || workoutType == WorkoutType.INTERVAL_RUN) {
            return 1000 / (medianPace * (1 - tempoPaceFactor) * readinessModifier) / 60;
        } else {
            assert workoutType == WorkoutType.LONG_RUN;
            return 1000 / (medianPace * (1 + longPaceFactor) * readinessModifier) / 60;
        }
    }

    private double getElevationCeiling(WorkoutType workoutType) {
        if (workoutType == WorkoutType.EASY_RUN) {
            return 80;
        } else if (workoutType == WorkoutType.TEMPO_RUN || workoutType == WorkoutType.INTERVAL_RUN) {
            return 150;
        } else {
            assert workoutType == WorkoutType.LONG_RUN;
            return 300;
        }
    }

    public GeoJsonDto generateRoundTrip(GeoJsonPosition coordinate, int length) {
        double lng = coordinate.getLongitude();
        double lat = coordinate.getLatitude();
        double factor = 500; // TODO
        int num = 10;

        List<GeoJsonPosition> pos = new ArrayList<>();
        for (int i = -num / 2; i <= num / 2; i++) {
            for (int j = -num / 2; j <= num / 2; j++) {
                double dx = i * factor;
                double dy = j * factor;
                double angle = Math.atan2(dy, dx);
                double d = Math.sqrt(dx * dx + dy * dy);
                var p = offsetPoint(lat, lng, d, angle);
                pos.add(p);
            }
        }

        GeoJsonPosition realCenter = new GeoJsonPosition(lat, lng, null);
        List<GeoJsonPosition> ele = openRouteServiceService.requestElevation(pos);
        GeoJsonPosition center = ele.getFirst();
        for (GeoJsonPosition p : ele) {
            if (haversineDistance(center, realCenter) > haversineDistance(p, realCenter))
                center = p;
        }


        Random r = new Random();
        GeoJsonPosition point = ele.get(r.nextInt(ele.size()));

        for (int i = 0; i < 30; i++) {
            GeoJsonPosition randPos = ele.get(r.nextInt(ele.size()));
            double distance = haversineDistance(center, randPos);
            double steepness1 = (randPos.getAltitude() - center.getAltitude()) / distance;
            double steepness2 = (point.getAltitude() - center.getAltitude()) / distance;
            if (distance > length / 2.0 && steepness1 < steepness2) {
                point = randPos;
            }
        }

        GeoJsonDto route = openRouteServiceService.requestRoute(List.of(new GeoJsonPosition(center.getLatitude(), center.getLongitude(), null),
                new GeoJsonPosition(point.getLatitude(), point.getLongitude(), null),
                new GeoJsonPosition(center.getLatitude(), center.getLongitude(), null)
        ), false); // TODO: vienna bool
        List<GeoJsonPosition> coords = route.getFeatures().getFirst().getGeometry().getCoordinates();
        double realDistance = routeEvaluationService.evaluateRoute(coords);
        double error = (realDistance - length) / length;
        while (error > 0.05) {
            System.out.println("length: " + length + ", realDistance: " + realDistance + ", error: " + error + ", count: " + coords.size());
            error = Math.min(error, 0.8);
            int removal = (int) Math.round(coords.size() * (1 - (1 - error / 2)));
            int removalLocation = (coords.size() - removal) / 2;
            for (int i = 0; i < removal; i++) {
                coords.remove(removalLocation);
            }
            realDistance = routeEvaluationService.evaluateRoute(coords);
            error = (realDistance - length) / length;
        }

        double minLat = coords.getFirst().getLatitude();
        double minLng = coords.getFirst().getLongitude();
        double minAlt = coords.getFirst().getAltitude();
        double maxLat = coords.getFirst().getLatitude();
        double maxLng = coords.getFirst().getLongitude();
        double maxAlt = coords.getFirst().getAltitude();
        double ascent = 0;
        double descent = 0;
        double distance = 0;
        for (int i = 1; i < coords.size(); i++) {
            GeoJsonPosition p = coords.get(i);
            if (p.getLatitude() < minLat) {
                minLat = p.getLatitude();
            } else if (p.getLatitude() > maxLat) {
                maxLat = p.getLatitude();
            }

            if (p.getLongitude() < minLng) {
                minLng = p.getLongitude();
            } else if (p.getLongitude() > maxLng) {
                maxLng = p.getLongitude();
            }

            if (p.getAltitude() < minAlt) {
                minAlt = p.getAltitude();
            } else if (p.getAltitude() > maxAlt) {
                maxAlt = p.getAltitude();
            }

            if (coords.get(i).getAltitude() > coords.get(i - 1).getAltitude()) {
                ascent += coords.get(i).getAltitude() - coords.get(i - 1).getAltitude();
            } else if (coords.get(i).getAltitude() < coords.get(i - 1).getAltitude()) {
                descent += coords.get(i - 1).getAltitude() - coords.get(i).getAltitude();
            }

            distance += haversineDistance(coords.get(i), coords.get(i - 1));
        }

        GeoJsonDto dto = new GeoJsonDto();
        GeoJsonFeature feature = new GeoJsonFeature();
        dto.setFeatures(List.of(feature));
        GeoJsonProperties props = new GeoJsonProperties();
        feature.setProperties(props);
        GeoJsonGeometryLineString ls = new GeoJsonGeometryLineString();
        feature.setGeometry(ls);

        dto.setType("FeatureCollection");
        dto.setBbox(List.of(minLat, minLng, minAlt, maxLat, maxLng, maxAlt));

        feature.setBbox(List.of(minLat, minLng, minAlt, maxLat, maxLng, maxAlt));
        feature.setType("Feature");

        props.setAscent(ascent);
        props.setDescent(descent);
        props.setDistance(distance);

        ls.setType("LineString");
        ls.setCoordinates(coords);

        return dto;
    }

    private static GeoJsonPosition offsetPoint(double lat, double lng, double d, double angle) {
        double R = 6_371_000; // earth radius approx.
        lat = Math.toRadians(lat);
        lng = Math.toRadians(lng);

        double lat2 = Math.asin(Math.sin(lat) * Math.cos(d / R) + Math.cos(lat) * Math.sin(d / R) * Math.cos(angle));
        double lng2 = lng + Math.atan((Math.sin(angle) * Math.sin(d / R) * Math.cos(lat)) / (Math.cos(d / R) - Math.sin(lat) * Math.sin(lat2)));

        return new GeoJsonPosition(Math.toDegrees(lat2), Math.toDegrees(lng2), null);
    }

    public static double haversineDistance(GeoJsonPosition p1, GeoJsonPosition p2) {
        final int R = 6_371_000; // Radius of the Earth in km

        // Convert latitude and longitude from degrees to radians
        double lat1Rad = Math.toRadians(p1.getLatitude());
        double lat2Rad = Math.toRadians(p2.getLatitude());
        double deltaLat = Math.toRadians(p2.getLatitude() - p1.getLatitude());
        double deltaLon = Math.toRadians(p2.getLongitude() - p1.getLongitude());

        // Haversine formula
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

}

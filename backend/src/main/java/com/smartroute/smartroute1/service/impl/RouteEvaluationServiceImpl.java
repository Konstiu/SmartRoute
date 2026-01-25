package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.service.RouteEvaluationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteEvaluationServiceImpl implements RouteEvaluationService {

    // https://medium.com/strava-engineering/an-improved-gap-model-8b07ae8886c3

    double[][] gapConversion = {
            {-32.1830985915493, 1.5958904109589},
            {-27.7464788732394, 1.38356164383562},
            {-23.7323943661972, 1.22602739726027},
            {-20.2816901408451, 1.08904109589041},
            {-17.2535211267606, 1.0},
            {-14.5774647887324, 0.931506849315068},
            {-12.2535211267606, 0.897260273972603},
            {-10.2816901408451, 0.876712328767123},
            {-8.52112676056338, 0.876712328767123},
            {-6.97183098591549, 0.883561643835616},
            {-5.70422535211268, 0.897260273972603},
            {-4.5774647887324, 0.910958904109589},
            {-3.59154929577465, 0.924657534246575},
            {-2.74647887323944, 0.945205479452055},
            {-2.04225352112676, 0.958904109589041},
            {-1.47887323943662, 0.979452054794521},
            {-0.985915492957747, 0.986301369863014},
            {0.492957746478873, 1},
            {0, 1},
            {0.563380281690141, 1.00684931506849},
            {0.985915492957747, 1.02054794520548},
            {1.47887323943662, 1.03424657534247},
            {2.04225352112676, 1.06164383561644},
            {2.8169014084507, 1.08904109589041},
            {3.66197183098592, 1.12328767123288},
            {4.5774647887324, 1.16438356164384},
            {5.70422535211268, 1.21232876712329},
            {7.04225352112676, 1.28082191780822},
            {8.52112676056338, 1.36301369863014},
            {10.2816901408451, 1.47945205479452},
            {12.3239436619718, 1.62328767123288},
            {14.6478873239437, 1.81506849315068},
            {17.2535211267606, 2.04109589041096},
            {20.2816901408451, 2.32191780821918},
            {23.8028169014085, 2.61643835616438},
            {27.7464788732394, 2.95205479452055},
            {32.1830985915493, 3.32876712328767},
    };

    @Override
    public double evaluateRoute(List<GeoJsonPosition> route) {
        double sum = 0;
        double convertedSum = 0;
        for (int i = 1; i < route.size(); i++) {
            double distance = haversineDistance(route.get(i - 1), route.get(i)) * 1000;
            double elevation = route.get(i).getAltitude() - route.get(i - 1).getAltitude();
            System.out.println(elevation / distance);
            if (distance > 0.0001) {
                convertedSum += distance * calculateInterpolatedConversion(elevation / distance);
            } else {
                convertedSum += distance;
            }
            sum += distance;
        }
        return convertedSum;
    }

    private double calculateInterpolatedConversion(double incline) {
        int l = 0;
        int r = gapConversion.length - 1;
        while (l <= r) {
            int m = (l + r) / 2;
            double[] entry = gapConversion[m];
            if (entry[0] <= incline) {
                l = m + 1;
            } else if (entry[0] > incline) {
                r = m - 1;
            } else {
                return gapConversion[m][1];
            }
        }
        int i = r;
        if (i == gapConversion.length - 1) {
            i -= 1; // use previous two values as approximation
        }
        return lerp(gapConversion[i][0],
                gapConversion[i + 1][0],
                gapConversion[i][1],
                gapConversion[i + 1][1], incline);
    }

    private double lerp(double x1, double x2, double y1, double y2, double value) {
        double slope = (y2 - y1) / (x2 - x1);
        double intercept = y1 - slope * x1;
        return slope * value + intercept;
    }

    public static double haversineDistance(GeoJsonPosition p1, GeoJsonPosition p2) {
        final int R = 6371; // Radius of the Earth in km

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

//    private double elevationGain(GeoJsonDto route) {
//        List<GeoJsonPosition> coordinates = route.getFeatures().getFirst().getGeometry().getCoordinates();
//        double elevationGain = 0;
//        GeoJsonPosition lastCoordinate = coordinates.getFirst();
//        for (GeoJsonPosition coordinate : coordinates) {
//            elevationGain += Math.max(0, coordinate.getAltitude() - lastCoordinate.getAltitude());
//        }
//        return elevationGain;
//    }
}

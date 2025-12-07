package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.AddStopsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class AddStopsTest {
    @Autowired
    AddStopsService service;

    private final static GeoJsonPosition CORD1 = new GeoJsonPosition(48.245319, 16.300269, 0.0);
    private final static GeoJsonPosition CORD2 = new GeoJsonPosition(48.241755, 16.297835, 0.0);
    private final static GeoJsonPosition CORD3 = new GeoJsonPosition(48.245142, 16.321690, 0.0);
    private final static GeoJsonPosition CORD4 = new GeoJsonPosition(48.248767, 16.301225, 0.0);

    private final static GeoJsonPosition START_CORD = new GeoJsonPosition(48.243195, 16.299518, 0.0);
    private final static GeoJsonPosition FAR_AWAY_CORD = new GeoJsonPosition(48.296071, 16.555631, 0.0);

//    @Test
//    void addPoint() throws IOException, ValidationException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        //coords.add(CORD1);
//        coords.add(CORD2);
//        //coords.add(CORD3);
//        //coords.add(CORD4);
//        List<GeoJsonPosition> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx1.gpx");
//        List<GeoJsonPosition> newRoute = service.addWaypoints(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create1.gpx");
//    }
//
//    @Test
//    void pointIsStartPoint() throws IOException, ValidationException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        coords.add(START_CORD);
//        List<GeoJsonPosition> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx1.gpx");
//        List<GeoJsonPosition> newRoute = service.addWaypoints(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create2.gpx");
//    }
//
//    @Test
//    void pointIsFarAway() throws IOException, ValidationException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        coords.add(FAR_AWAY_CORD);
//        List<GeoJsonPosition> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx1.gpx");
//        List<GeoJsonPosition> newRoute = service.addWaypoints(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create3.gpx");
//    }
}

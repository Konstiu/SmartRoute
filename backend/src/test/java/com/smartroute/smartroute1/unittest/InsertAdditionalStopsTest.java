package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.InsertAdditionalStops;
import com.smartroute.smartroute1.util.Coordinate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class InsertAdditionalStopsTest {
    @Autowired
    InsertAdditionalStops service;

    private final static Coordinate CORD1 = new Coordinate(48.245319, 16.300269);
    private final static Coordinate CORD2 = new Coordinate(48.241755, 16.297835);
    private final static Coordinate CORD3 = new Coordinate(48.245142, 16.321690);
    private final static Coordinate CORD4 = new Coordinate(48.248767, 16.301225);

    private final static Coordinate START_CORD = new Coordinate(48.243195, 16.299518);
    private final static Coordinate FAR_AWAY_CORD = new Coordinate(48.296071, 16.555631);

//    @Test
//    void addPoint() throws IOException, ValidationException {
//        List<Coordinate> coords = new ArrayList<>();
//        coords.add(CORD1);
//        coords.add(CORD2);
//        coords.add(CORD3);
//        coords.add(CORD4);
//        List<Coordinate> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx1.gpx");
//        List<Coordinate> newRoute = service.addWaypoints(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create1.gpx");
//    }
//
//    @Test
//    void pointIsStartPoint() throws IOException, ValidationException {
//        List<Coordinate> coords = new ArrayList<>();
//        coords.add(START_CORD);
//        List<Coordinate> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx1.gpx");
//        List<Coordinate> newRoute = service.addWaypoints(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create2.gpx");
//    }
//
//    @Test
//    void pointIsFarAway() throws IOException, ValidationException {
//        List<Coordinate> coords = new ArrayList<>();
//        coords.add(FAR_AWAY_CORD);
//        List<Coordinate> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx1.gpx");
//        List<Coordinate> newRoute = service.addWaypoints(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create3.gpx");
//    }
}

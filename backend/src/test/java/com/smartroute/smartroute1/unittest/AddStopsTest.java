package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.AddStopsService;
import com.smartroute.smartroute1.service.impl.AddStopsServiceImpl;
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
    @Autowired
    AddStopsServiceImpl impl;

    private final static GeoJsonPosition CORD1 = new GeoJsonPosition(48.245319, 16.300269, 0.0);
    private final static GeoJsonPosition CORD2 = new GeoJsonPosition(48.241755, 16.297835, 0.0);
    private final static GeoJsonPosition CORD3 = new GeoJsonPosition(48.245142, 16.321690, 0.0);
    private final static GeoJsonPosition CORD4 = new GeoJsonPosition(48.248767, 16.301225, 0.0);


    private final static GeoJsonPosition CORD5 = new GeoJsonPosition(48.237686, 16.299071, 0.0);
    private final static GeoJsonPosition START_CORD = new GeoJsonPosition(48.243195, 16.299518, 0.0);
    private final static GeoJsonPosition FAR_AWAY_CORD = new GeoJsonPosition(48.296071, 16.555631, 0.0);


    private final static GeoJsonPosition C1 = new GeoJsonPosition(48.226683, 16.333192, 0.0);
    private final static GeoJsonPosition C2 = new GeoJsonPosition( 48.225932, 16.332497, 0.0);
    private final static GeoJsonPosition C3 = new GeoJsonPosition( 48.216481, 16.330088, 0.0);
    private final static GeoJsonPosition C4 = new GeoJsonPosition( 48.229492, 16.361286, 0.0);

    private final static GeoJsonPosition C5 = new GeoJsonPosition( 48.223973, 16.343729, 0.0);

//    @Test
//    void addPoint() throws IOException, ValidationException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        //coords.add(CORD1);
//        //coords.add(CORD2);
//        //coords.add(CORD3);
//        //coords.add(CORD4);
//        coords.add(CORD5);
//        List<GeoJsonPosition> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx1.gpx");
//        List<GeoJsonPosition> newRoute = service.addWaypoints(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create1.gpx");
//    }
//
//    @Test
//    void addPoint2() throws IOException, ValidationException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        coords.add(C1);
//        coords.add(C2);
//        coords.add(C3);
//        coords.add(C4);
//        coords.add(C5);
//        List<GeoJsonPosition> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx2.gpx");
//        List<GeoJsonPosition> newRoute = service.addWaypoints(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create2.gpx");
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
//
//    @Test
//    void testKeepLength() throws ValidationException, IOException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        coords.add(C1);
//        coords.add(C2);
//        coords.add(C3);
//        //coords.add(C4);
//        //coords.add(C5);
//        List<GeoJsonPosition> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx2.gpx");
//        List<GeoJsonPosition> newRoute = impl.reshape(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create4.gpx");
//    }
}

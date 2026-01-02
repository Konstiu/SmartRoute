package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.AddStopsEndpoint;
import com.smartroute.smartroute1.endpoint.dto.AddStopsDto;
import com.smartroute.smartroute1.endpoint.dto.StopPointDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.AddStopsService;
import com.smartroute.smartroute1.service.impl.AddStopsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class AddStopsTest {
    @Autowired
    AddStopsService service;
    @Autowired
    AddStopsEndpoint endpoint;
    @Autowired
    ObjectMapper objectMapper;

    private final static GeoJsonPosition CORD1 = new GeoJsonPosition(48.245319, 16.300269, 0.0);
    private final static GeoJsonPosition CORD2 = new GeoJsonPosition(48.241755, 16.297835, 0.0);
    private final static GeoJsonPosition CORD3 = new GeoJsonPosition(48.245142, 16.321690, 0.0);
    private final static GeoJsonPosition CORD4 = new GeoJsonPosition(48.248767, 16.301225, 0.0);


    private final static GeoJsonPosition CORD5 = new GeoJsonPosition(48.237686, 16.299071, 0.0);
    private final static GeoJsonPosition START_CORD = new GeoJsonPosition(48.243195, 16.299518, 0.0);
    private final static GeoJsonPosition FAR_AWAY_CORD = new GeoJsonPosition(48.296071, 16.555631, 0.0);


    private final static GeoJsonPosition C1 = new GeoJsonPosition(48.226683, 16.333192, 0.0);
    private final static GeoJsonPosition C2 = new GeoJsonPosition(48.225932, 16.332497, 0.0);
    private final static GeoJsonPosition C3 = new GeoJsonPosition(48.216481, 16.330088, 0.0);
    private final static GeoJsonPosition C4 = new GeoJsonPosition(48.229492, 16.361286, 0.0);
    private final static GeoJsonPosition C5 = new GeoJsonPosition(48.223973, 16.343729, 0.0);

    private final static GeoJsonPosition C1G3 = new GeoJsonPosition(48.208992, 16.377387, 0.0);
    private final static GeoJsonPosition C2G3 = new GeoJsonPosition(48.214326, 16.358193, 0.0);

    private final static GeoJsonPosition C3G3 = new GeoJsonPosition(48.201805, 16.345101, 0.0); // schottenfeld

    private final static GeoJsonPosition C4G3 = new GeoJsonPosition(48.213613, 16.370182, 180.0);

    @Test
    void testEndpointReshape_withRealPayload() throws Exception {
        String json = """
                {"originalRoute":[{"latitude":48.706356,"longitude":9.663278,"altitude":null},{"latitude":48.705975,"longitude":9.663085,"altitude":null},{"latitude":48.705251,"longitude":9.662559,"altitude":null},{"latitude":48.705208,"longitude":9.66243,"altitude":null},{"latitude":48.705013,"longitude":9.662562,"altitude":null},{"latitude":48.704825,"longitude":9.66174,"altitude":null},{"latitude":48.704775,"longitude":9.661459,"altitude":null},{"latitude":48.704734,"longitude":9.661229,"altitude":null},{"latitude":48.70468,"longitude":9.660928,"altitude":null},{"latitude":48.704637,"longitude":9.660586,"altitude":null},{"latitude":48.70463,"longitude":9.660226,"altitude":null},{"latitude":48.704687,"longitude":9.659416,"altitude":null},{"latitude":48.704701,"longitude":9.659265,"altitude":null},{"latitude":48.704785,"longitude":9.658543,"altitude":null},{"latitude":48.704837,"longitude":9.658112,"altitude":null},{"latitude":48.704295,"longitude":9.657968,"altitude":null},{"latitude":48.704837,"longitude":9.658112,"altitude":null},{"latitude":48.704938,"longitude":9.657395,"altitude":null},{"latitude":48.704972,"longitude":9.657054,"altitude":null},{"latitude":48.705006,"longitude":9.656708,"altitude":null},{"latitude":48.705028,"longitude":9.656483,"altitude":null},{"latitude":48.705034,"longitude":9.656447,"altitude":null},{"latitude":48.705197,"longitude":9.655446,"altitude":null},{"latitude":48.705325,"longitude":9.654973,"altitude":null},{"latitude":48.705437,"longitude":9.654585,"altitude":null},{"latitude":48.70559,"longitude":9.654073,"altitude":null},{"latitude":48.705673,"longitude":9.653794,"altitude":null},{"latitude":48.70594,"longitude":9.652898,"altitude":null},{"latitude":48.705751,"longitude":9.652788,"altitude":null},{"latitude":48.705613,"longitude":9.652672,"altitude":null},{"latitude":48.705522,"longitude":9.652566,"altitude":null},{"latitude":48.70542,"longitude":9.652275,"altitude":null},{"latitude":48.70539,"longitude":9.652228,"altitude":null},{"latitude":48.705346,"longitude":9.652158,"altitude":null},{"latitude":48.705487,"longitude":9.651851,"altitude":null},{"latitude":48.705638,"longitude":9.651565,"altitude":null},{"latitude":48.705902,"longitude":9.651703,"altitude":null},{"latitude":48.706084,"longitude":9.651799,"altitude":null},{"latitude":48.706227,"longitude":9.651916,"altitude":null},{"latitude":48.706333,"longitude":9.651543,"altitude":null},{"latitude":48.706513,"longitude":9.650909,"altitude":null},{"latitude":48.706673,"longitude":9.649867,"altitude":null},{"latitude":48.706731,"longitude":9.649543,"altitude":null},{"latitude":48.706828,"longitude":9.64959,"altitude":null},{"latitude":48.706941,"longitude":9.649613,"altitude":null},{"latitude":48.707075,"longitude":9.649594,"altitude":null},{"latitude":48.70726,"longitude":9.649626,"altitude":null},{"latitude":48.707569,"longitude":9.649655,"altitude":null},{"latitude":48.708128,"longitude":9.649564,"altitude":null},{"latitude":48.708383,"longitude":9.649506,"altitude":null},{"latitude":48.708654,"longitude":9.649444,"altitude":null},{"latitude":48.708925,"longitude":9.649383,"altitude":null},{"latitude":48.708991,"longitude":9.649366,"altitude":null},{"latitude":48.709028,"longitude":9.649356,"altitude":null},{"latitude":48.709043,"longitude":9.649231,"altitude":null},{"latitude":48.709084,"longitude":9.649176,"altitude":null},{"latitude":48.709407,"longitude":9.649185,"altitude":null},{"latitude":48.709568,"longitude":9.649238,"altitude":null},{"latitude":48.709699,"longitude":9.649157,"altitude":null},{"latitude":48.709834,"longitude":9.649072,"altitude":null},{"latitude":48.709911,"longitude":9.649107,"altitude":null},{"latitude":48.710011,"longitude":9.64912,"altitude":null},{"latitude":48.710113,"longitude":9.64914,"altitude":null},{"latitude":48.71016,"longitude":9.649281,"altitude":null},{"latitude":48.710201,"longitude":9.64932,"altitude":null},{"latitude":48.710211,"longitude":9.649504,"altitude":null},{"latitude":48.710217,"longitude":9.649614,"altitude":null},{"latitude":48.710219,"longitude":9.64965,"altitude":null},{"latitude":48.710255,"longitude":9.649792,"altitude":null},{"latitude":48.710427,"longitude":9.650199,"altitude":null},{"latitude":48.710522,"longitude":9.650425,"altitude":null},{"latitude":48.710664,"longitude":9.650761,"altitude":null},{"latitude":48.711172,"longitude":9.651879,"altitude":null},{"latitude":48.711192,"longitude":9.651921,"altitude":null},{"latitude":48.711418,"longitude":9.652299,"altitude":null},{"latitude":48.711638,"longitude":9.652419,"altitude":null},{"latitude":48.711885,"longitude":9.652516,"altitude":null},{"latitude":48.71235,"longitude":9.652581,"altitude":null},{"latitude":48.712515,"longitude":9.652545,"altitude":null},{"latitude":48.712816,"longitude":9.652278,"altitude":null},{"latitude":48.713012,"longitude":9.652258,"altitude":null},{"latitude":48.71355,"longitude":9.652179,"altitude":null},{"latitude":48.713965,"longitude":9.652143,"altitude":null},{"latitude":48.714015,"longitude":9.652141,"altitude":null},{"latitude":48.713975,"longitude":9.652308,"altitude":null},{"latitude":48.713881,"longitude":9.652697,"altitude":null},{"latitude":48.713621,"longitude":9.653805,"altitude":null},{"latitude":48.713583,"longitude":9.653969,"altitude":null},{"latitude":48.713512,"longitude":9.65402,"altitude":null},{"latitude":48.71326,"longitude":9.653994,"altitude":null},{"latitude":48.713175,"longitude":9.654719,"altitude":null},{"latitude":48.713116,"longitude":9.655216,"altitude":null},{"latitude":48.713069,"longitude":9.655621,"altitude":null},{"latitude":48.713047,"longitude":9.656176,"altitude":null},{"latitude":48.713018,"longitude":9.656978,"altitude":null},{"latitude":48.713011,"longitude":9.658469,"altitude":null},{"latitude":48.713273,"longitude":9.658484,"altitude":null},{"latitude":48.713459,"longitude":9.658494,"altitude":null},{"latitude":48.713517,"longitude":9.658497,"altitude":null},{"latitude":48.713567,"longitude":9.658502,"altitude":null},{"latitude":48.713603,"longitude":9.658505,"altitude":null},{"latitude":48.713971,"longitude":9.658902,"altitude":null},{"latitude":48.713987,"longitude":9.659041,"altitude":null},{"latitude":48.713988,"longitude":9.659267,"altitude":null},{"latitude":48.714059,"longitude":9.659623,"altitude":null},{"latitude":48.714058,"longitude":9.660341,"altitude":null},{"latitude":48.714055,"longitude":9.66167,"altitude":null},{"latitude":48.713705,"longitude":9.661712,"altitude":null},{"latitude":48.713781,"longitude":9.66281,"altitude":null},{"latitude":48.713789,"longitude":9.663221,"altitude":null},{"latitude":48.71368,"longitude":9.664452,"altitude":null},{"latitude":48.713669,"longitude":9.664967,"altitude":null},{"latitude":48.71372,"longitude":9.665368,"altitude":null},{"latitude":48.713962,"longitude":9.666242,"altitude":null},{"latitude":48.714008,"longitude":9.666535,"altitude":null},{"latitude":48.713988,"longitude":9.666696,"altitude":null},{"latitude":48.713877,"longitude":9.666899,"altitude":null},{"latitude":48.713712,"longitude":9.667537,"altitude":null},{"latitude":48.713583,"longitude":9.667837,"altitude":null},{"latitude":48.71334,"longitude":9.66892,"altitude":null},{"latitude":48.712858,"longitude":9.668534,"altitude":null},{"latitude":48.712251,"longitude":9.667969,"altitude":null},{"latitude":48.711053,"longitude":9.666938,"altitude":null},{"latitude":48.711061,"longitude":9.666689,"altitude":null},{"latitude":48.711096,"longitude":9.666339,"altitude":null},{"latitude":48.711095,"longitude":9.6661,"altitude":null},{"latitude":48.71107,"longitude":9.666063,"altitude":null},{"latitude":48.710857,"longitude":9.665921,"altitude":null},{"latitude":48.710604,"longitude":9.665798,"altitude":null},{"latitude":48.710772,"longitude":9.664943,"altitude":null},{"latitude":48.710824,"longitude":9.66456,"altitude":null},{"latitude":48.710772,"longitude":9.664943,"altitude":null},{"latitude":48.710604,"longitude":9.665798,"altitude":null},{"latitude":48.710363,"longitude":9.665674,"altitude":null},{"latitude":48.710115,"longitude":9.665587,"altitude":null},{"latitude":48.709747,"longitude":9.665458,"altitude":null},{"latitude":48.709109,"longitude":9.665201,"altitude":null},{"latitude":48.708483,"longitude":9.664771,"altitude":null},{"latitude":48.70828,"longitude":9.664622,"altitude":null},{"latitude":48.707864,"longitude":9.664315,"altitude":null},{"latitude":48.707824,"longitude":9.664285,"altitude":null},{"latitude":48.70762,"longitude":9.664129,"altitude":null},{"latitude":48.707243,"longitude":9.663848,"altitude":null},{"latitude":48.706447,"longitude":9.663324,"altitude":null},{"latitude":48.706356,"longitude":9.663278,"altitude":null}],"newPoints":[{"latitude":48.711013530088096,"longitude":9.64179039001465,"altitude":null}]}
                """;

        AddStopsDto dto = objectMapper.readValue(json, AddStopsDto.class);
        endpoint.reshape(dto);
    }

    @Test
    void testEndpointInsert_withRealPayload() throws Exception {
        String json = """
                {"originalRoute":[{"latitude":48.706356,"longitude":9.663278,"altitude":null},{"latitude":48.705975,"longitude":9.663085,"altitude":null},{"latitude":48.705251,"longitude":9.662559,"altitude":null},{"latitude":48.705208,"longitude":9.66243,"altitude":null},{"latitude":48.705013,"longitude":9.662562,"altitude":null},{"latitude":48.704825,"longitude":9.66174,"altitude":null},{"latitude":48.704775,"longitude":9.661459,"altitude":null},{"latitude":48.704734,"longitude":9.661229,"altitude":null},{"latitude":48.70468,"longitude":9.660928,"altitude":null},{"latitude":48.704637,"longitude":9.660586,"altitude":null},{"latitude":48.70463,"longitude":9.660226,"altitude":null},{"latitude":48.704687,"longitude":9.659416,"altitude":null},{"latitude":48.704701,"longitude":9.659265,"altitude":null},{"latitude":48.704785,"longitude":9.658543,"altitude":null},{"latitude":48.704837,"longitude":9.658112,"altitude":null},{"latitude":48.704295,"longitude":9.657968,"altitude":null},{"latitude":48.704837,"longitude":9.658112,"altitude":null},{"latitude":48.704938,"longitude":9.657395,"altitude":null},{"latitude":48.704972,"longitude":9.657054,"altitude":null},{"latitude":48.705006,"longitude":9.656708,"altitude":null},{"latitude":48.705028,"longitude":9.656483,"altitude":null},{"latitude":48.705034,"longitude":9.656447,"altitude":null},{"latitude":48.705197,"longitude":9.655446,"altitude":null},{"latitude":48.705325,"longitude":9.654973,"altitude":null},{"latitude":48.705437,"longitude":9.654585,"altitude":null},{"latitude":48.70559,"longitude":9.654073,"altitude":null},{"latitude":48.705673,"longitude":9.653794,"altitude":null},{"latitude":48.70594,"longitude":9.652898,"altitude":null},{"latitude":48.705751,"longitude":9.652788,"altitude":null},{"latitude":48.705613,"longitude":9.652672,"altitude":null},{"latitude":48.705522,"longitude":9.652566,"altitude":null},{"latitude":48.70542,"longitude":9.652275,"altitude":null},{"latitude":48.70539,"longitude":9.652228,"altitude":null},{"latitude":48.705346,"longitude":9.652158,"altitude":null},{"latitude":48.705487,"longitude":9.651851,"altitude":null},{"latitude":48.705638,"longitude":9.651565,"altitude":null},{"latitude":48.705902,"longitude":9.651703,"altitude":null},{"latitude":48.706084,"longitude":9.651799,"altitude":null},{"latitude":48.706227,"longitude":9.651916,"altitude":null},{"latitude":48.706333,"longitude":9.651543,"altitude":null},{"latitude":48.706513,"longitude":9.650909,"altitude":null},{"latitude":48.706673,"longitude":9.649867,"altitude":null},{"latitude":48.706731,"longitude":9.649543,"altitude":null},{"latitude":48.706828,"longitude":9.64959,"altitude":null},{"latitude":48.706941,"longitude":9.649613,"altitude":null},{"latitude":48.707075,"longitude":9.649594,"altitude":null},{"latitude":48.70726,"longitude":9.649626,"altitude":null},{"latitude":48.707569,"longitude":9.649655,"altitude":null},{"latitude":48.708128,"longitude":9.649564,"altitude":null},{"latitude":48.708383,"longitude":9.649506,"altitude":null},{"latitude":48.708654,"longitude":9.649444,"altitude":null},{"latitude":48.708925,"longitude":9.649383,"altitude":null},{"latitude":48.708991,"longitude":9.649366,"altitude":null},{"latitude":48.709028,"longitude":9.649356,"altitude":null},{"latitude":48.709043,"longitude":9.649231,"altitude":null},{"latitude":48.709084,"longitude":9.649176,"altitude":null},{"latitude":48.709407,"longitude":9.649185,"altitude":null},{"latitude":48.709568,"longitude":9.649238,"altitude":null},{"latitude":48.709699,"longitude":9.649157,"altitude":null},{"latitude":48.709834,"longitude":9.649072,"altitude":null},{"latitude":48.709911,"longitude":9.649107,"altitude":null},{"latitude":48.710011,"longitude":9.64912,"altitude":null},{"latitude":48.710113,"longitude":9.64914,"altitude":null},{"latitude":48.71016,"longitude":9.649281,"altitude":null},{"latitude":48.710201,"longitude":9.64932,"altitude":null},{"latitude":48.710211,"longitude":9.649504,"altitude":null},{"latitude":48.710217,"longitude":9.649614,"altitude":null},{"latitude":48.710219,"longitude":9.64965,"altitude":null},{"latitude":48.710255,"longitude":9.649792,"altitude":null},{"latitude":48.710427,"longitude":9.650199,"altitude":null},{"latitude":48.710522,"longitude":9.650425,"altitude":null},{"latitude":48.710664,"longitude":9.650761,"altitude":null},{"latitude":48.711172,"longitude":9.651879,"altitude":null},{"latitude":48.711192,"longitude":9.651921,"altitude":null},{"latitude":48.711418,"longitude":9.652299,"altitude":null},{"latitude":48.711638,"longitude":9.652419,"altitude":null},{"latitude":48.711885,"longitude":9.652516,"altitude":null},{"latitude":48.71235,"longitude":9.652581,"altitude":null},{"latitude":48.712515,"longitude":9.652545,"altitude":null},{"latitude":48.712816,"longitude":9.652278,"altitude":null},{"latitude":48.713012,"longitude":9.652258,"altitude":null},{"latitude":48.71355,"longitude":9.652179,"altitude":null},{"latitude":48.713965,"longitude":9.652143,"altitude":null},{"latitude":48.714015,"longitude":9.652141,"altitude":null},{"latitude":48.713975,"longitude":9.652308,"altitude":null},{"latitude":48.713881,"longitude":9.652697,"altitude":null},{"latitude":48.713621,"longitude":9.653805,"altitude":null},{"latitude":48.713583,"longitude":9.653969,"altitude":null},{"latitude":48.713512,"longitude":9.65402,"altitude":null},{"latitude":48.71326,"longitude":9.653994,"altitude":null},{"latitude":48.713175,"longitude":9.654719,"altitude":null},{"latitude":48.713116,"longitude":9.655216,"altitude":null},{"latitude":48.713069,"longitude":9.655621,"altitude":null},{"latitude":48.713047,"longitude":9.656176,"altitude":null},{"latitude":48.713018,"longitude":9.656978,"altitude":null},{"latitude":48.713011,"longitude":9.658469,"altitude":null},{"latitude":48.713273,"longitude":9.658484,"altitude":null},{"latitude":48.713459,"longitude":9.658494,"altitude":null},{"latitude":48.713517,"longitude":9.658497,"altitude":null},{"latitude":48.713567,"longitude":9.658502,"altitude":null},{"latitude":48.713603,"longitude":9.658505,"altitude":null},{"latitude":48.713971,"longitude":9.658902,"altitude":null},{"latitude":48.713987,"longitude":9.659041,"altitude":null},{"latitude":48.713988,"longitude":9.659267,"altitude":null},{"latitude":48.714059,"longitude":9.659623,"altitude":null},{"latitude":48.714058,"longitude":9.660341,"altitude":null},{"latitude":48.714055,"longitude":9.66167,"altitude":null},{"latitude":48.713705,"longitude":9.661712,"altitude":null},{"latitude":48.713781,"longitude":9.66281,"altitude":null},{"latitude":48.713789,"longitude":9.663221,"altitude":null},{"latitude":48.71368,"longitude":9.664452,"altitude":null},{"latitude":48.713669,"longitude":9.664967,"altitude":null},{"latitude":48.71372,"longitude":9.665368,"altitude":null},{"latitude":48.713962,"longitude":9.666242,"altitude":null},{"latitude":48.714008,"longitude":9.666535,"altitude":null},{"latitude":48.713988,"longitude":9.666696,"altitude":null},{"latitude":48.713877,"longitude":9.666899,"altitude":null},{"latitude":48.713712,"longitude":9.667537,"altitude":null},{"latitude":48.713583,"longitude":9.667837,"altitude":null},{"latitude":48.71334,"longitude":9.66892,"altitude":null},{"latitude":48.712858,"longitude":9.668534,"altitude":null},{"latitude":48.712251,"longitude":9.667969,"altitude":null},{"latitude":48.711053,"longitude":9.666938,"altitude":null},{"latitude":48.711061,"longitude":9.666689,"altitude":null},{"latitude":48.711096,"longitude":9.666339,"altitude":null},{"latitude":48.711095,"longitude":9.6661,"altitude":null},{"latitude":48.71107,"longitude":9.666063,"altitude":null},{"latitude":48.710857,"longitude":9.665921,"altitude":null},{"latitude":48.710604,"longitude":9.665798,"altitude":null},{"latitude":48.710772,"longitude":9.664943,"altitude":null},{"latitude":48.710824,"longitude":9.66456,"altitude":null},{"latitude":48.710772,"longitude":9.664943,"altitude":null},{"latitude":48.710604,"longitude":9.665798,"altitude":null},{"latitude":48.710363,"longitude":9.665674,"altitude":null},{"latitude":48.710115,"longitude":9.665587,"altitude":null},{"latitude":48.709747,"longitude":9.665458,"altitude":null},{"latitude":48.709109,"longitude":9.665201,"altitude":null},{"latitude":48.708483,"longitude":9.664771,"altitude":null},{"latitude":48.70828,"longitude":9.664622,"altitude":null},{"latitude":48.707864,"longitude":9.664315,"altitude":null},{"latitude":48.707824,"longitude":9.664285,"altitude":null},{"latitude":48.70762,"longitude":9.664129,"altitude":null},{"latitude":48.707243,"longitude":9.663848,"altitude":null},{"latitude":48.706447,"longitude":9.663324,"altitude":null},{"latitude":48.706356,"longitude":9.663278,"altitude":null}],"newPoints":[{"latitude":48.711013530088096,"longitude":9.64179039001465,"altitude":null}]}
                """;

        AddStopsDto dto = objectMapper.readValue(json, AddStopsDto.class);
        endpoint.addWaypoints(dto);
    }


//    @Test
//    void testKeepLengthGpx3() throws ValidationException, IOException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        //coords.add(C1G3);
//        coords.add(C2G3);
//        //coords.add(C3G3);
//        //coords.add(C4);
//        //coords.add(C5);
//        List<GeoJsonPosition> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx3.gpx");
//        List<GeoJsonPosition> newRoute = service.reshape(originalRoute, coords, 0.1);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/outGpx3.gpx");
//    }
//
//
//    @Test
//    void addPoint() throws IOException, ValidationException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        coords.add(C1G3);
//        coords.add(C2G3);
//        coords.add(C3G3);
//        coords.add(C4G3);
//        //coords.add(CORD2);
//        //coords.add(CORD3);
//        //coords.add(CORD4);
//        //coords.add(CORD5);
//        List<GeoJsonPosition> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx3.gpx");
//        List<GeoJsonPosition> newRoute = service.addWaypoints(originalRoute, coords);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create1.gpx");
//    }
//
//    @Test
//    void testKeepLength() throws ValidationException, IOException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        //coords.add(C1);
//        //coords.add(C2);
//        //coords.add(C3);
//        coords.add(C4);
//        coords.add(C5);
//        List<GeoJsonPosition> originalRoute = service.gpxToPolyline("/home/sprotte/Documents/gpxFiles/in/testGpx2.gpx");
//        List<GeoJsonPosition> newRoute = service.reshape(originalRoute, coords, 0.1);
//        service.createGpx(newRoute, "/home/sprotte/Documents/gpxFiles/out/create4.gpx");
//    }
//
//    @Test
//    void addPoint2() throws IOException, ValidationException {
//        List<GeoJsonPosition> coords = new ArrayList<>();
//        //coords.add(C1);
//        //coords.add(C2);
//        //coords.add(C3);
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
}

package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonGeometryLineString;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.springframework.data.geo.Point;

import java.util.ArrayList;
import java.util.List;


import static reactor.netty.http.HttpConnectionLiveness.log;

@Mapper(componentModel = "spring")
public interface PolyLineMapper {

    default String geoJsonGeometryLineStringToPolyline(GeoJsonGeometryLineString lineString) {
        StringBuilder sb = new StringBuilder();
        double lastLat = 0;
        double lastLong = 0;
        for (GeoJsonPosition pos : lineString.getCoordinates()) {
            convertCoordinate(pos.getLatitude() - lastLat, sb);
            convertCoordinate(pos.getLongitude() - lastLong, sb);
            lastLat = pos.getLatitude();
            lastLong = pos.getLongitude();
        }
        return sb.toString();
    }

    private void convertCoordinate(double coordinate, StringBuilder sb) {
        int coord = (int) Math.round(coordinate * 1e6);
        coord <<= 1;
        if (coordinate < 0) {
            coord = ~coord;
        }
        while (coord >= 0x20) {
            sb.append((char) (((coord & 0b11111) | 0x20) + 63));
            coord >>= 5;
        }
        sb.append((char) (coord + 63));
    }


    /**
     * Port to Java of Mark McClures Javascript PolylineEncoder :
     * http://facstaff.unca.edu/mcmcclur/GoogleMaps/EncodePolyline/decode.js
     */

    default List<GeoJsonPosition> decodePolylineToPoints(String encoded) {
        double precision = 1E5;
        List<GeoJsonPosition> track = new ArrayList<GeoJsonPosition>();
        int index = 0;
        int lat = 0;
        int lng = 0;

        while (index < encoded.length()) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                if (index >= encoded.length()) {
                    return track;
                }
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            GeoJsonPosition p = new GeoJsonPosition((double) lat / precision, (double) lng / precision, 0.0);
            track.add(p);
        }
        return track;
    }
}
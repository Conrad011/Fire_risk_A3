package com.firerisk.util;

import java.util.Locale;

/** Calculos geograficos sobre coordenadas em graus. */
public final class GeoMath {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double KM_PER_DEGREE = 111.32;

    private GeoMath() {
    }

    /** Distancia em linha reta (haversine), em km, com uma casa decimal. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(dLambda / 2), 2);
        return Math.round(2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a)) * 10) / 10.0;
    }

    /** Retangulo {@code oeste,sul,leste,norte} que contem o circulo informado. */
    public static String boundingBox(double lat, double lon, double radiusKm) {
        double dLat = radiusKm / KM_PER_DEGREE;
        double dLon = radiusKm / (KM_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        return String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f",
                Math.max(lon - dLon, -180), Math.max(lat - dLat, -90),
                Math.min(lon + dLon, 180), Math.min(lat + dLat, 90));
    }
}

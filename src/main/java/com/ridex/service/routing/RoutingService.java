package com.ridex.service.routing;

/**
 * Road route between two points. Kept behind an interface so the provider can be replaced
 * (public OSRM today; self-hosted OSRM, GraphHopper or OpenRouteService later) without touching callers.
 */
public interface RoutingService {

    Route route(double fromLat, double fromLng, double toLat, double toLng);

    /**
     * @param path      JSON array of [lat, lng] points, ready for Leaflet's L.polyline
     * @param estimated true when the provider was unavailable and a straight-line estimate was used
     */
    record Route(double distanceKm, int durationMin, String path, boolean estimated) {
    }

    /** Great-circle distance in km (same formula as the distance_km() SQL function). */
    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 6371 * 2 * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}

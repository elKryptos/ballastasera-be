package com.kryptosystems.ballastasera.services.manager;

import java.util.Optional;

public interface GeocodingService {

    /** Resuelve una direccion (calle + ciudad) en coordenadas.
     * Optional.empty() si no se encontro ningun resultado o el
     * proveedor no esta disponible. */
    Optional<GeoPoint> geoCode(String address, String cityName);

    record GeoPoint(double latitude, double longitude) { }
}

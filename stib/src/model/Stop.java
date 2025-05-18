package model;

/**
 * Représente une route dans le réseau de transport.
 * Cette classe immuable contient les informations de base sur une route,
 * telles que son identifiant, son nom court, son nom long et son type.
 *
 * @param routeId        L'identifiant unique de la route.
 * @param shortName      Le nom court de la route (par exemple, un numéro ou un code).
 * @param routeLongName  Le nom complet ou descriptif de la route.
 * @param routeType      Le type de la route (par exemple, "BUS", "TRAM", etc.).
 */
public record Stop(String stopId,String stopName,double stopLat,double stopLon) {
}

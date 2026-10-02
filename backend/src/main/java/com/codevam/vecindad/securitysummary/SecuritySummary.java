package com.codevam.vecindad.securitysummary;

public record SecuritySummary(long vehiclesInside, long visitorsInside, long pendingVisitRequests, long openAlerts,
                              long packagesPending, long openIncidents, long vehicleEntriesToday) {}

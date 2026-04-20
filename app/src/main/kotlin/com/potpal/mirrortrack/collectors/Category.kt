package com.potpal.mirrortrack.collectors

/**
 * Top-level grouping for collectors. Used for UI organization and
 * per-category retention policies. Adding a new category requires
 * UI changes in CategoryBrowser, so prefer fitting new collectors
 * into existing categories when reasonable.
 */
enum class Category(val displayName: String, val description: String) {
    DEVICE_IDENTITY(
        "Device Identity",
        "Build info, identifiers, hardware fingerprint"
    ),
    NETWORK(
        "Network",
        "Connectivity, wifi, carrier, IP addresses"
    ),
    LOCATION(
        "Location",
        "GPS, activity recognition, geocoded regions"
    ),
    SENSORS(
        "Sensors",
        "Motion, environment, biometric sensors"
    ),
    BEHAVIORAL(
        "Behavioral",
        "App usage, screen state, battery, lifecycle"
    ),
    PERSONAL(
        "Personal Data",
        "Contacts, calendar, media metadata"
    ),
    APPS(
        "Installed Apps",
        "Package list, install metadata, permissions"
    )
}

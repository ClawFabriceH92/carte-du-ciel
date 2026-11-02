package fr.cartedueciel.app.data

/**
 * Villes proposées pour la saisie manuelle du lieu (coordonnées approximatives du centre-ville,
 * au centième de degré près : largement suffisant, cf. cahier des charges §5). L'utilisateur peut
 * aussi saisir des coordonnées quelconques.
 */
data class City(val name: String, val latitude: Double, val longitude: Double)

object Cities {
    val all: List<City> = listOf(
        City("Paris", 48.8566, 2.3522),
        City("Marseille", 43.2965, 5.3698),
        City("Lyon", 45.7640, 4.8357),
        City("Toulouse", 43.6047, 1.4442),
        City("Nice", 43.7102, 7.2620),
        City("Nantes", 47.2184, -1.5536),
        City("Montpellier", 43.6108, 3.8767),
        City("Strasbourg", 48.5734, 7.7521),
        City("Bordeaux", 44.8378, -0.5792),
        City("Lille", 50.6292, 3.0573),
        City("Rennes", 48.1173, -1.6778),
        City("Reims", 49.2583, 4.0317),
        City("Le Havre", 49.4944, 0.1079),
        City("Toulon", 43.1242, 5.9280),
        City("Grenoble", 45.1885, 5.7245),
        City("Dijon", 47.3220, 5.0415),
        City("Brest", 48.3904, -4.4861),
        City("Clermont-Ferrand", 45.7772, 3.0870),
        City("Limoges", 45.8336, 1.2611),
        City("Ajaccio", 41.9192, 8.7386),
        City("Bruxelles", 50.8503, 4.3517),
        City("Genève", 46.2044, 6.1432),
        City("Luxembourg", 49.6116, 6.1319),
        City("Londres", 51.5074, -0.1278),
        City("Montréal", 45.5017, -73.5673),
        City("Québec", 46.8139, -71.2080),
        City("Alger", 36.7538, 3.0588),
        City("Tunis", 36.8065, 10.1815),
        City("Casablanca", 33.5731, -7.5898),
        City("Dakar", 14.7167, -17.4677),
        City("Abidjan", 5.3600, -4.0083),
        City("Fort-de-France", 14.6161, -61.0588),
        City("Pointe-à-Pitre", 16.2412, -61.5331),
        City("Cayenne", 4.9224, -52.3135),
        City("Saint-Denis (La Réunion)", -20.8789, 55.4481),
        City("Nouméa", -22.2758, 166.4580),
        City("Papeete", -17.5516, -149.5585),
    )
}

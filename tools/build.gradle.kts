plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":catalog"))
    implementation(project(":astro"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("fr.cartedueciel.tools.CatalogBuilderKt")
}

tasks.test {
    useJUnit()
}

/**
 * Génère le catalogue binaire embarqué dans l'application à partir de l'extrait HYG et des
 * fichiers d3-celestial (voir tools/data). Le fichier produit est versionné dans le dépôt afin que
 * le build Android ne dépende pas de cette étape ; la tâche est néanmoins branchée sur
 * `:app:preBuild` et ne s'exécute que si les entrées ont changé.
 *
 *   ./gradlew :tools:generateCatalog
 */
val generateCatalog by tasks.registering(JavaExec::class) {
    group = "catalogue"
    description = "Construit app/src/main/assets/catalog/sky_catalog.bin"
    val dataDir = layout.projectDirectory.dir("data")
    val output = rootProject.layout.projectDirectory.file("app/src/main/assets/catalog/sky_catalog.bin")
    val report = rootProject.layout.projectDirectory.file("app/src/main/assets/catalog/sky_catalog.report.txt")
    inputs.dir(dataDir)
    inputs.property("magnitudeLimit", "6.5")
    outputs.file(output)
    outputs.file(report)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("fr.cartedueciel.tools.CatalogBuilderKt")
    args(
        dataDir.file("hyg/hyg_v41_subset.csv").asFile.absolutePath,
        dataDir.file("d3-celestial/constellations.lines.json").asFile.absolutePath,
        dataDir.file("d3-celestial/constellations.json").asFile.absolutePath,
        output.asFile.absolutePath,
        "6.5",
    )
}

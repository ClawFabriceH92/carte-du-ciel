package fr.cartedueciel.astro.math

/**
 * Passage de l'attitude du téléphone (capteurs Android) à la matrice de vue de la caméra
 * virtuelle qui regarde « à travers » le dos du téléphone.
 *
 * Conventions Android (`SensorManager.getRotationMatrixFromVector`) :
 * - repère appareil : x vers la droite de l'écran, y vers le haut de l'écran, z sortant de
 *   l'écran (vers le visage de l'utilisateur) ; la caméra arrière regarde donc vers −z ;
 * - repère monde : x = est, y = nord **magnétique**, z = zénith ;
 * - la matrice R fournie transforme un vecteur du repère appareil vers le repère monde.
 *
 * Conventions de cette application :
 * - repère ciel [Enu] : x = est, y = nord **géographique**, z = zénith ;
 * - repère caméra OpenGL : x vers la droite de l'affichage, y vers le haut de l'affichage,
 *   la caméra regardant vers −z. La « matrice de vue » transforme un vecteur monde en vecteur
 *   caméra.
 *
 * La rotation de l'affichage (`Display.getRotation()`) est prise en compte : en paysage, la
 * droite et le haut de l'affichage ne coïncident plus avec les axes x et y de l'appareil.
 */
object DeviceOrientation {

    /** Valeurs de `android.view.Surface.ROTATION_*` (recopiées pour rester indépendant d'Android). */
    const val ROTATION_0 = 0
    const val ROTATION_90 = 1
    const val ROTATION_180 = 2
    const val ROTATION_270 = 3

    /**
     * Matrice (repère affichage → repère appareil) dont les colonnes sont les axes « droite »,
     * « haut » et « sortant » de l'affichage exprimés dans le repère de l'appareil.
     *
     * `ROTATION_90` correspond à un appareil tourné de 90° dans le sens antihoraire : le haut de
     * l'affichage est alors l'axe +x de l'appareil et sa droite l'axe −y.
     */
    fun displayToDevice(displayRotation: Int): Mat3 = when (displayRotation) {
        ROTATION_0 -> Mat3.IDENTITY
        ROTATION_90 -> Mat3.fromColumns(Vec3(0.0, -1.0, 0.0), Vec3(1.0, 0.0, 0.0), Vec3.Z)
        ROTATION_180 -> Mat3.fromColumns(Vec3(-1.0, 0.0, 0.0), Vec3(0.0, -1.0, 0.0), Vec3.Z)
        ROTATION_270 -> Mat3.fromColumns(Vec3(0.0, 1.0, 0.0), Vec3(-1.0, 0.0, 0.0), Vec3.Z)
        else -> throw IllegalArgumentException("Rotation d'affichage inconnue : $displayRotation")
    }

    /**
     * Correction du nord magnétique vers le nord géographique.
     *
     * Le repère monde d'Android a son axe y vers le nord magnétique. Si la déclinaison vaut δ
     * (positive quand le nord magnétique est à l'est du nord géographique), la direction
     * « nord magnétique » a pour azimut géographique δ. La matrice renvoyée transforme un vecteur
     * du repère magnétique vers le repère géographique : c'est une rotation de −δ autour du zénith.
     *
     * @param declinationDeg déclinaison magnétique (degrés, est positif)
     * @param manualOffsetDeg décalage manuel supplémentaire réglé par l'utilisateur (degrés),
     *   ajouté à la déclinaison : positif si le ciel affiché doit tourner vers l'est.
     */
    fun magneticToTrue(declinationDeg: Double, manualOffsetDeg: Double = 0.0): Mat3 =
        Mat3.rotationZ(-Math.toRadians(declinationDeg + manualOffsetDeg))

    /**
     * Matrice de vue (repère ciel ENU → repère caméra OpenGL) pour une attitude donnée.
     *
     * @param deviceToMagneticWorld matrice R d'Android (repère appareil → repère monde magnétique)
     * @param displayRotation valeur de `Display.getRotation()`
     * @param declinationDeg déclinaison magnétique au lieu et à la date courants (degrés)
     * @param manualOffsetDeg décalage manuel de boussole (degrés)
     */
    fun viewMatrix(
        deviceToMagneticWorld: Mat3,
        displayRotation: Int,
        declinationDeg: Double,
        manualOffsetDeg: Double = 0.0,
    ): Mat3 {
        // repère affichage → appareil → monde magnétique → monde géographique
        val displayToTrueWorld = magneticToTrue(declinationDeg, manualOffsetDeg) * deviceToMagneticWorld * displayToDevice(displayRotation)
        // La matrice de vue est l'inverse (transposée, car rotation) : monde → affichage/caméra.
        return displayToTrueWorld.transposed()
    }

    /** Direction visée par la caméra arrière, dans le repère ciel, pour une matrice de vue donnée. */
    fun lookDirection(viewMatrix: Mat3): Vec3 = viewMatrix.transposed() * Vec3(0.0, 0.0, -1.0)

    /** Direction du « haut » de l'affichage, dans le repère ciel. */
    fun upDirection(viewMatrix: Mat3): Vec3 = viewMatrix.transposed() * Vec3.Y

    /**
     * Angle de roulis de l'affichage (radians) : rotation apparente de l'image autour de la
     * direction visée, positive dans le sens antihoraire, nulle quand le haut de l'affichage
     * pointe « vers le haut » du ciel. Utile pour orienter les libellés et la flèche de guidage.
     */
    fun rollAngle(viewMatrix: Mat3): Double {
        val look = lookDirection(viewMatrix)
        val up = upDirection(viewMatrix)
        // Projection du zénith dans le plan de l'image.
        val zenithInImage = Vec3.Z - look * (Vec3.Z dot look)
        if (zenithInImage.length < 1e-9) return 0.0
        val right = look cross up
        val zx = zenithInImage dot right
        val zy = zenithInImage dot up
        return kotlin.math.atan2(zx, zy)
    }
}

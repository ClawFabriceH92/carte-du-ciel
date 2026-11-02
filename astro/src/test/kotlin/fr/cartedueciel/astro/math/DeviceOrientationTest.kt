package fr.cartedueciel.astro.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Positions types du cahier des charges (section 9) : téléphone vertical face au nord,
 * pointant au zénith, en paysage.
 */
class DeviceOrientationTest {

    /** Matrice Android (appareil -> monde) dont les colonnes sont les axes de l'appareil en coordonnées monde. */
    private fun androidMatrix(deviceX: Vec3, deviceY: Vec3, deviceZ: Vec3) = Mat3.fromColumns(deviceX, deviceY, deviceZ)

    private fun assertAzAlt(expectedAz: Double, expectedAlt: Double, v: Vec3, eps: Double = 1e-6) {
        val h = Enu.toHorizontal(v)
        if (expectedAlt < 89.999 && expectedAlt > -89.999) {
            val dAz = ((h.azimuthDeg - expectedAz + 540.0) % 360.0) - 180.0
            assertEquals("azimut", 0.0, dAz, eps)
        }
        assertEquals("hauteur", expectedAlt, h.altitudeDeg, eps)
    }

    @Test
    fun `telephone vertical, ecran face au sud, la camera arriere vise le nord`() {
        // x appareil = est, y appareil (haut de l'écran) = zénith, z appareil (vers l'utilisateur) = sud.
        val r = androidMatrix(Vec3.X, Vec3.Z, Vec3(0.0, -1.0, 0.0))
        assertTrue(r.isRotation())
        val view = DeviceOrientation.viewMatrix(r, DeviceOrientation.ROTATION_0, declinationDeg = 0.0)
        assertAzAlt(0.0, 0.0, DeviceOrientation.lookDirection(view))
        // Le haut de l'écran est vers le zénith : pas de roulis.
        assertEquals(0.0, DeviceOrientation.rollAngle(view), 1e-9)
        // Le zénith apparaît en haut de l'image, l'est à droite.
        val cam = SkyCamera(view, 60.0, 0.6)
        assertTrue(cam.project(Enu.fromHorizontal(0.0, 10.0))!!.y > 0.0)
        assertTrue(cam.project(Enu.fromHorizontal(10.0, 0.0))!!.x > 0.0)
    }

    @Test
    fun `telephone a plat ecran vers le ciel, la camera arriere vise le nadir`() {
        // Matrice identité d'Android : x = est, y = nord, z = zénith (écran vers le ciel).
        val view = DeviceOrientation.viewMatrix(Mat3.IDENTITY, DeviceOrientation.ROTATION_0, 0.0)
        assertAzAlt(0.0, -90.0, DeviceOrientation.lookDirection(view))
        // Haut de l'écran vers le nord.
        assertAzAlt(0.0, 0.0, DeviceOrientation.upDirection(view))
    }

    @Test
    fun `telephone a plat ecran vers le sol, la camera arriere vise le zenith`() {
        // Retourné autour de l'axe nord : x -> ouest, y -> nord, z -> nadir.
        val r = androidMatrix(Vec3(-1.0, 0.0, 0.0), Vec3.Y, Vec3(0.0, 0.0, -1.0))
        val view = DeviceOrientation.viewMatrix(r, DeviceOrientation.ROTATION_0, 0.0)
        assertAzAlt(0.0, 90.0, DeviceOrientation.lookDirection(view))
    }

    @Test
    fun `en paysage la direction visee est inchangee mais le haut de l'affichage tourne`() {
        val r = androidMatrix(Vec3.X, Vec3.Z, Vec3(0.0, -1.0, 0.0)) // vertical face au nord
        // Appareil tourné de 90° antihoraire : le haut de l'affichage est l'axe +x de l'appareil (est ici).
        val view90 = DeviceOrientation.viewMatrix(r, DeviceOrientation.ROTATION_90, 0.0)
        assertAzAlt(0.0, 0.0, DeviceOrientation.lookDirection(view90))
        assertAzAlt(90.0, 0.0, DeviceOrientation.upDirection(view90))
        val view270 = DeviceOrientation.viewMatrix(r, DeviceOrientation.ROTATION_270, 0.0)
        assertAzAlt(270.0, 0.0, DeviceOrientation.upDirection(view270))
        val view180 = DeviceOrientation.viewMatrix(r, DeviceOrientation.ROTATION_180, 0.0)
        assertAzAlt(0.0, -90.0, DeviceOrientation.upDirection(view180))
    }

    @Test
    fun `la declinaison magnetique decale l'azimut vers l'est`() {
        // Capteur : la caméra vise le nord magnétique. Déclinaison +2° (nord magnétique à l'est du
        // nord géographique) : l'azimut géographique visé vaut donc 2°.
        val r = androidMatrix(Vec3.X, Vec3.Z, Vec3(0.0, -1.0, 0.0))
        val view = DeviceOrientation.viewMatrix(r, DeviceOrientation.ROTATION_0, declinationDeg = 2.0)
        assertAzAlt(2.0, 0.0, DeviceOrientation.lookDirection(view))
        // Décalage manuel de −5° en plus : azimut 2 − 5 = −3 = 357°.
        val view2 = DeviceOrientation.viewMatrix(r, DeviceOrientation.ROTATION_0, 2.0, manualOffsetDeg = -5.0)
        assertAzAlt(357.0, 0.0, DeviceOrientation.lookDirection(view2))
    }

    @Test
    fun `telephone incline a 45 degres vers le ciel face a l'est`() {
        // Écran face à l'ouest, incliné : y appareil = (vers l'est et le haut)...
        // Construction : rotation de la position « vertical face à l'est » de 45° vers le haut.
        val up45 = Enu.fromHorizontal(90.0, 45.0)        // direction visée souhaitée
        val screenUp = Enu.fromHorizontal(270.0, 45.0)   // haut de l'écran : vers l'ouest et le ciel ? non : vers le zénith penché
        // Pour viser (90°, +45°) avec le haut de l'écran dans le plan vertical : haut = (az 270°, alt 45°) est faux ;
        // le haut de l'écran doit être perpendiculaire à la visée, dans le plan vertical contenant la visée : (az 270°, alt 45°)·(az 90°, alt 45°) = -cos²45+sin²45 = 0. OK.
        assertEquals(0.0, up45 dot screenUp, 1e-12)
        val deviceZ = -up45          // l'axe z de l'appareil est opposé à la visée
        val deviceY = screenUp
        val deviceX = deviceY cross deviceZ // x = y × z pour un trièdre direct
        val r = Mat3.fromColumns(deviceX, deviceY, deviceZ)
        assertTrue(r.isRotation())
        val view = DeviceOrientation.viewMatrix(r, DeviceOrientation.ROTATION_0, 0.0)
        assertAzAlt(90.0, 45.0, DeviceOrientation.lookDirection(view))
        assertEquals(0.0, DeviceOrientation.rollAngle(view), 1e-9)
    }
}

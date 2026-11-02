package fr.cartedueciel.astro.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class GeometryTest {

    private fun assertVec(expected: Vec3, actual: Vec3, eps: Double = 1e-9) {
        assertEquals("x", expected.x, actual.x, eps)
        assertEquals("y", expected.y, actual.y, eps)
        assertEquals("z", expected.z, actual.z, eps)
    }

    @Test
    fun `rotations elementaires et conventions ENU`() {
        // Rotation de +90° autour de z : x -> y.
        assertVec(Vec3.Y, Mat3.rotationZ(PI / 2) * Vec3.X)
        assertVec(Vec3.Z, Mat3.rotationX(PI / 2) * Vec3.Y)
        assertVec(Vec3.X, Mat3.rotationY(PI / 2) * Vec3.Z)
        // Azimut 90° (est), hauteur 0 -> +x ; azimut 0 (nord) -> +y ; zénith -> +z.
        assertVec(Vec3.X, Enu.fromHorizontal(90.0, 0.0))
        assertVec(Vec3.Y, Enu.fromHorizontal(0.0, 0.0))
        assertVec(Vec3.Z, Enu.fromHorizontal(123.0, 90.0))
        val h = Enu.toHorizontal(Enu.fromHorizontal(237.5, -12.25))
        assertEquals(237.5, h.azimuthDeg, 1e-9)
        assertEquals(-12.25, h.altitudeDeg, 1e-9)
        // Équatorial : RA 6 h (90°), Dec 0 -> +y.
        assertVec(Vec3.Y, Equatorial.fromRaDec(PI / 2, 0.0))
        val (ra, dec) = Equatorial.toRaDec(Equatorial.fromRaDec(5.1, -0.4))
        assertEquals(5.1, ra, 1e-12)
        assertEquals(-0.4, dec, 1e-12)
    }

    @Test
    fun `produit matriciel, transposee et test de rotation`() {
        val a = Mat3.rotationX(0.3) * Mat3.rotationZ(-1.1) * Mat3.rotationY(2.0)
        assertTrue(a.isRotation())
        val v = Vec3(0.2, -0.7, 1.3)
        assertVec(v, a.transposed() * (a * v))
        assertEquals(1.0, a.determinant(), 1e-12)
        val gl = a.toGl4x4()
        assertEquals(a[1, 0], gl[1].toDouble(), 1e-6) // column-major : colonne 0, ligne 1
        assertEquals(a[0, 1], gl[4].toDouble(), 1e-6)
        assertEquals(1f, gl[15])
    }

    @Test
    fun `quaternion vers matrice et retour`() {
        val q = Quaternion.fromAxisAngle(Vec3(1.0, 2.0, -0.5), 1.3)
        val m = q.toMat3()
        assertTrue(m.isRotation())
        val back = Quaternion.fromMat3(m)
        assertEquals(0.0, q.angleTo(back), 1e-9)
        // Rotation de 90° autour de z appliquée via la matrice : x -> y.
        assertVec(Vec3.Y, Quaternion.fromAxisAngle(Vec3.Z, PI / 2).toMat3() * Vec3.X)
    }

    @Test
    fun `slerp interpole la rotation la plus courte`() {
        val a = Quaternion.IDENTITY
        val b = Quaternion.fromAxisAngle(Vec3.Z, PI / 2)
        assertEquals(0.0, a.slerp(b, 0.0).angleTo(a), 1e-9)
        assertEquals(0.0, a.slerp(b, 1.0).angleTo(b), 1e-9)
        val mid = a.slerp(b, 0.5)
        assertEquals(PI / 4, mid.angleTo(a), 1e-9)
        // -b représente la même rotation : le chemin reste le plus court.
        val mid2 = a.slerp(-b, 0.5)
        assertEquals(0.0, mid.angleTo(mid2), 1e-9)
    }

    @Test
    fun `vecteur de rotation Android reproduit la formule de SensorManager`() {
        // Formule de SensorManager.getRotationMatrixFromVector (AOSP), avec q0 = w.
        val q = Quaternion.fromAxisAngle(Vec3(0.3, -0.2, 0.9), 0.8)
        val values = floatArrayOf(q.x.toFloat(), q.y.toFloat(), q.z.toFloat(), q.w.toFloat())
        val m = Quaternion.fromAndroidRotationVector(values).toMat3()
        val q1 = q.x; val q2 = q.y; val q3 = q.z; val q0 = q.w
        val expected = Mat3(
            1 - 2 * q2 * q2 - 2 * q3 * q3, 2 * q1 * q2 - 2 * q3 * q0, 2 * q1 * q3 + 2 * q2 * q0,
            2 * q1 * q2 + 2 * q3 * q0, 1 - 2 * q1 * q1 - 2 * q3 * q3, 2 * q2 * q3 - 2 * q1 * q0,
            2 * q1 * q3 - 2 * q2 * q0, 2 * q2 * q3 + 2 * q1 * q0, 1 - 2 * q1 * q1 - 2 * q2 * q2,
        )
        for (i in 0 until 9) assertEquals(expected.m[i], m.m[i], 1e-6)
        // Sans quatrième composante, w est reconstruit.
        val m3 = Quaternion.fromAndroidRotationVector(floatArrayOf(q.x.toFloat(), q.y.toFloat(), q.z.toFloat())).toMat3()
        for (i in 0 until 9) assertEquals(expected.m[i], m3.m[i], 1e-5)
    }

    @Test
    fun `projection perspective et deprojection`() {
        val cam = SkyCamera(Mat3.IDENTITY, fovYDeg = 60.0, aspect = 0.5)
        // La caméra regarde vers -z : un point droit devant est au centre.
        val center = cam.project(Vec3(0.0, 0.0, -1.0))
        assertNotNull(center)
        assertEquals(0.0, center!!.x, 1e-12)
        assertEquals(0.0, center.y, 1e-12)
        // Un point derrière n'est pas projeté.
        assertNull(cam.project(Vec3(0.0, 0.0, 1.0)))
        // Bord supérieur : hauteur = demi-champ vertical.
        val top = cam.project(Enu.fromHorizontal(0.0, 0.0).let { Vec3(0.0, kotlin.math.tan(Math.toRadians(30.0)), -1.0) })
        assertEquals(1.0, top!!.y, 1e-9)
        val dir = Vec3(0.3, -0.2, -1.0).normalized()
        val back = cam.unproject(cam.project(dir)!!)
        assertEquals(0.0, dir.angleTo(back), 1e-9)
        assertEquals(2.0 * Math.toDegrees(kotlin.math.atan(kotlin.math.tan(Math.toRadians(30.0)) * 0.5)), cam.fovXDeg, 1e-9)
    }

    @Test
    fun `couleur d'etoile selon l'indice B-V`() {
        val white = StarColor.rgb(0.3)
        assertEquals(1f, white[0], 1e-6f); assertEquals(1f, white[1], 1e-6f); assertEquals(1f, white[2], 1e-6f)
        val blue = StarColor.rgb(-0.3)
        assertTrue(blue[2] > blue[0])
        val red = StarColor.rgb(1.8)
        assertTrue(red[0] > red[2])
        val unknown = StarColor.rgb(Double.NaN)
        assertEquals(sqrt(3f), sqrt(unknown[0] * unknown[0] + unknown[1] * unknown[1] + unknown[2] * unknown[2]), 1e-6f)
    }
}

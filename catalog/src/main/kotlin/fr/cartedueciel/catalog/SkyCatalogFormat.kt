package fr.cartedueciel.catalog

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Format binaire compact du catalogue (grand-boutiste, via DataInput/DataOutput).
 *
 * ```
 * en-tête : "CDCS" (4 octets), version u16, drapeaux u16, nbÉtoiles i32, nbChaînes i32,
 *           nbConstellations u16, nbTracés i32, nbSommets i32, magnitudeLimite f32, 8 octets réservés
 * chaînes  : nbChaînes × (longueur u16 + octets UTF-8) ; la chaîne 0 est toujours vide
 * étoiles  : nbÉtoiles × { ra f32, dec f32, mag×1000 i16, ci×1000 i16, hip i32, hd i32,
 *            constellation i8, bayer u8, exposant u8, flamsteed u16, nomPropre u16, distance_pc f32 } (31 octets)
 * constell.: nbConstellations × { abbr u16, latin u16, génitif u16, fr u16, en u16,
 *            raLibellé f32, decLibellé f32, rang u8, premierTracé i32, nbTracés u16 }
 * tracés   : nbTracés × { constellation u16, nbSommets u16, nbSommets × index i32 }
 * ```
 */
object SkyCatalogFormat {
    const val MAGIC = "CDCS"
    const val VERSION = 1

    fun read(input: InputStream): SkyCatalog {
        val din = DataInputStream(input.buffered())
        val magic = ByteArray(4).also { din.readFully(it) }.toString(Charsets.US_ASCII)
        if (magic != MAGIC) throw IOException("Catalogue invalide (magic '$magic')")
        val version = din.readUnsignedShort()
        if (version != VERSION) throw IOException("Version de catalogue non prise en charge : $version")
        din.readUnsignedShort() // drapeaux
        val starCount = din.readInt()
        val stringCount = din.readInt()
        val constellationCount = din.readUnsignedShort()
        val polylineCount = din.readInt()
        din.readInt() // nombre total de sommets (information)
        val magLimit = din.readFloat()
        din.skipBytes(8)

        val strings = ArrayList<String>(stringCount)
        repeat(stringCount) {
            val len = din.readUnsignedShort()
            val bytes = ByteArray(len).also { b -> din.readFully(b) }
            strings += String(bytes, Charsets.UTF_8)
        }

        val ra = FloatArray(starCount); val dec = FloatArray(starCount)
        val mag = ShortArray(starCount); val ci = ShortArray(starCount)
        val hip = IntArray(starCount); val hd = IntArray(starCount)
        val con = ByteArray(starCount); val bayer = ByteArray(starCount); val sup = ByteArray(starCount)
        val flam = ShortArray(starCount); val name = ShortArray(starCount)
        val dist = FloatArray(starCount)
        for (i in 0 until starCount) {
            ra[i] = din.readFloat(); dec[i] = din.readFloat()
            mag[i] = din.readShort(); ci[i] = din.readShort()
            hip[i] = din.readInt(); hd[i] = din.readInt()
            con[i] = din.readByte(); bayer[i] = din.readByte(); sup[i] = din.readByte()
            flam[i] = din.readShort(); name[i] = din.readShort()
            dist[i] = din.readFloat()
        }

        val constellations = ArrayList<Constellation>(constellationCount)
        for (i in 0 until constellationCount) {
            val abbr = strings[din.readUnsignedShort()]
            val latin = strings[din.readUnsignedShort()]
            val gen = strings[din.readUnsignedShort()]
            val fr = strings[din.readUnsignedShort()]
            val en = strings[din.readUnsignedShort()]
            val lra = din.readFloat(); val ldec = din.readFloat()
            val rank = din.readUnsignedByte()
            val first = din.readInt(); val count = din.readUnsignedShort()
            constellations += Constellation(i, abbr, latin, gen, fr, en, lra, ldec, rank, first, count)
        }

        val polylines = ArrayList<Polyline>(polylineCount)
        repeat(polylineCount) {
            val c = din.readUnsignedShort()
            val n = din.readUnsignedShort()
            val idx = IntArray(n) { din.readInt() }
            polylines += Polyline(c, idx)
        }

        return SkyCatalog(
            buildMagnitudeLimit = magLimit,
            strings = strings,
            stars = StarTable(ra, dec, mag, ci, hip, hd, con, bayer, sup, flam, name, dist),
            constellations = constellations,
            polylines = polylines,
        )
    }

    fun write(catalog: SkyCatalog, output: OutputStream) {
        val dout = DataOutputStream(output.buffered())
        val s = catalog.stars
        require(catalog.strings.isNotEmpty() && catalog.strings[0].isEmpty()) { "La chaîne 0 doit être vide" }
        require(catalog.constellations.size <= 0xFFFF && catalog.strings.size <= 0xFFFF)
        val stringIndex = HashMap<String, Int>()
        catalog.strings.forEachIndexed { i, str -> stringIndex.putIfAbsent(str, i) }
        fun sid(str: String): Int = stringIndex[str] ?: throw IllegalArgumentException("Chaîne absente de la table : '$str'")

        dout.writeBytes(MAGIC)
        dout.writeShort(VERSION)
        dout.writeShort(0)
        dout.writeInt(s.size)
        dout.writeInt(catalog.strings.size)
        dout.writeShort(catalog.constellations.size)
        dout.writeInt(catalog.polylines.size)
        dout.writeInt(catalog.polylines.sumOf { it.starIndices.size })
        dout.writeFloat(catalog.buildMagnitudeLimit)
        dout.write(ByteArray(8))

        for (str in catalog.strings) {
            val bytes = str.toByteArray(Charsets.UTF_8)
            require(bytes.size <= 0xFFFF)
            dout.writeShort(bytes.size)
            dout.write(bytes)
        }
        for (i in 0 until s.size) {
            dout.writeFloat(s.raRad[i]); dout.writeFloat(s.decRad[i])
            dout.writeShort(s.magMilli[i].toInt()); dout.writeShort(s.colorMilli[i].toInt())
            dout.writeInt(s.hip[i]); dout.writeInt(s.hd[i])
            dout.writeByte(s.constellation[i].toInt()); dout.writeByte(s.bayer[i].toInt()); dout.writeByte(s.bayerSuperscript[i].toInt())
            dout.writeShort(s.flamsteed[i].toInt()); dout.writeShort(s.properNameId[i].toInt())
            dout.writeFloat(s.distancePc[i])
        }
        for (c in catalog.constellations) {
            dout.writeShort(sid(c.abbreviation)); dout.writeShort(sid(c.latinName)); dout.writeShort(sid(c.genitive))
            dout.writeShort(sid(c.nameFr)); dout.writeShort(sid(c.nameEn))
            dout.writeFloat(c.labelRaRad); dout.writeFloat(c.labelDecRad)
            dout.writeByte(c.rank)
            dout.writeInt(c.firstPolyline); dout.writeShort(c.polylineCount)
        }
        for (p in catalog.polylines) {
            dout.writeShort(p.constellationIndex)
            dout.writeShort(p.starIndices.size)
            p.starIndices.forEach { dout.writeInt(it) }
        }
        dout.flush()
    }
}

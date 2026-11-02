package fr.cartedueciel.sensors

import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.WindowManager

/** Rotation courante de l'affichage (`Surface.ROTATION_*`), avec repli pour les API < 30. */
object DisplayRotation {
    fun current(context: Context): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }
    } catch (e: Exception) {
        Surface.ROTATION_0
    }
}

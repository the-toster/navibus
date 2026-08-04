package com.github.thetoster.navibus

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Иконки плагина. [IconLoader.getIcon] кэширует инстанс, поэтому холдер зовётся один
 * раз, а не на каждый маркер. Ресурс лежит в корне classpath (`/navibus-gutter.svg`);
 * SVG задаёт размер 12×12 (штатный размер gutter-иконки) через `width/height`.
 */
object NavibusIcons {
    @JvmField
    val Handler: Icon = IconLoader.getIcon("/navibus-gutter.svg", NavibusIcons::class.java)
}

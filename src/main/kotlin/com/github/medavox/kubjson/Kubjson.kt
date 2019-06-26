package com.github.medavox.kubjson

import kotlin.reflect.KClass

/**
 * @author Adam Howard
@since 2019-06-26
 */
object Kubjson {
    fun toUbjson(anything:Any?):ByteArray {
        return Writer.writeAnything(anything)
    }

    fun <T:Any> fromUbjson(byteArray:ByteArray, toType: KClass<T>):T {
        TODO()
    }
}
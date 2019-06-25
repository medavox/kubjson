package com.github.medavox.kubjson

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

import kotlin.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberProperties

import com.github.medavox.kubjson.Markers.*

/**Basic low-level converter from JVM types to their UBJSON equivalents.
 * NOTE: both UBJSON and Java (and by extension Kotlin) are Big-Endian, so no endianness conversion is necessary*/
object Writer {

    //archetypes for use with isInstance
    private val boolean = false
    private val byte:Byte = 127
    private val short:Short = Short.MAX_VALUE
    private val int:Int = Int.MAX_VALUE
    private val long:Long = Long.MAX_VALUE
    private val float:Float = Float.MAX_VALUE
    private val double:Double = Double.MAX_VALUE
    private val char:Char = 'c'
    private val string:String = ""
    private val bigDecimal = BigDecimal.ONE
    private val bigInteger = BigInteger.ONE

    //todo: take arrays and lists as arrays
    //todo: take all Big Number formats as High Precision Numbers
    //todo: handle Map types as UBJSON objects
    fun writeObject(obj:Any):ByteArray {
        //write object tag-marker
        var out = writeChar('{')

        val cls:KClass<Any> = obj.javaClass.kotlin
        //val cls:KClass<Any> = obj::class as KClass<Any>//alternative way of getting a KClass<Any>, not KCLASS<out Any>
        var props/*:Collection<KProperty1<Any, *>>*/ = cls.declaredMemberProperties
        for(clas in obj.javaClass.kotlin.allSuperclasses) {
            //FIXME: find another way to get all inherited properties from all superclasses,
            // without doing an unchecked cast
            props += (clas.declaredMemberProperties as Collection<KProperty1<Any, *>>)
        }

        //exclude properties annotated with @KubjsonIgnore
        props.filter { it.annotations.any { ann -> ann.annotationClass == KubjsonIgnore::class} }
        //calculate & write number of properties
        out += writeChar('#') + writeLength(props.size)
        for(prop:KProperty1<Any, *> in props) {
            val typeSurrogate = (prop.returnType.classifier as KClass<*>)//.objectInstance
            println("${prop.name}:"+typeSurrogate.simpleName+" = "+prop.get(obj))

            //write variable name
            out += writeString(prop.name)

            //just write a null tag if the value is null; type info will just have to be omitted
            if(prop.get(obj) == null) {
                //println("NULL")
                out += writeNull()
                continue
            }

            out += when {
                typeSurrogate.isInstance(boolean) -> writeBoolean(prop.get(obj) as Boolean)
                typeSurrogate.isInstance(byte) -> writeInt8(prop.get(obj) as Byte)
                typeSurrogate.isInstance(short) -> writeInt16(prop.get(obj) as Short)
                typeSurrogate.isInstance(int) -> writeInt32(prop.get(obj) as Int)
                typeSurrogate.isInstance(long) -> writeInt64(prop.get(obj) as Long)
                typeSurrogate.isInstance(float) -> writeFloat32(prop.get(obj) as Float)
                typeSurrogate.isInstance(double) -> writeFloat64(prop.get(obj) as Double)
                typeSurrogate.isInstance(char) -> writeChar(prop.get(obj) as Char)
                typeSurrogate.isInstance(string) -> writeString(prop.get(obj) as String)
                typeSurrogate.isInstance(bigDecimal) -> writeHighPrecisionNumber(prop.get(obj) as BigDecimal)
                typeSurrogate.isInstance(bigInteger)  -> writeHighPrecisionNumber((prop.get(obj) as BigInteger).toBigDecimal())
                typeSurrogate.isInstance(booleanArrayOf()) -> writeArray(prop.get(obj) as BooleanArray)
                typeSurrogate.isInstance(byteArrayOf()) -> writeArray(prop.get(obj) as ByteArray)
                typeSurrogate.isInstance(shortArrayOf()) -> writeArray(prop.get(obj) as ShortArray)
                typeSurrogate.isInstance(intArrayOf()) -> writeArray(prop.get(obj) as IntArray)
                typeSurrogate.isInstance(longArrayOf()) -> writeArray(prop.get(obj) as LongArray)
                typeSurrogate.isInstance(floatArrayOf()) -> writeArray(prop.get(obj) as FloatArray)
                typeSurrogate.isInstance(doubleArrayOf()) -> writeArray(prop.get(obj) as DoubleArray)
                typeSurrogate.isInstance(charArrayOf()) -> writeArray(prop.get(obj) as CharArray)
                typeSurrogate.isInstance(emptyArray<Any?>()) -> writeArray(prop.get(obj) as Array<Any?>)
                else -> {
                    println("UNHANDLED TYPE:"+typeSurrogate)
                    //todo: try to serialise unknown types as a nested object
                    byteArrayOf()
                }
            }
        }
        return out
    }

    internal fun <T> writeArray(array:Array<T>):ByteArray {
        //"WARNING: Kotlin (and the JVM) do not directly support heterogeneous arrays. Attempting to use an object instead..."
        TODO()
    }

    internal fun writeByteArray(byteArray:ByteArray):ByteArray {
        return writeChar(ValueTypes.ARRAY.marker)++byteArray
    }

    private fun writeMarker(valueType:Markers):ByteArray {
        return writeChar(valueType.marker)
    }

    private fun writeMarkers(vararg markers:Markers):ByteArray {
        var byteArray:ByteArray = byteArrayOf()
        for(marker in markers) {
            byteArray += writeChar(marker.marker)
        }
        return byteArray
    }

    /**Write a variable-length integral numeric type which is large enough to hold the specified value*/
    internal fun writeLength(length:Long):ByteArray {
        if(length < Byte.MAX_VALUE) {
            return writeMarker(Markers.INT8_TYPE)  + writeInt8(length.toByte())
        }else if(length < Short.MAX_VALUE) {
            return writeMarker(Markers.INT16_TYPE) + writeInt16(length.toShort())
        }else if(length < Int.MAX_VALUE) {
            return writeMarker(Markers.INT32_TYPE) + writeInt32(length.toInt())
        }else {
            return writeMarker(Markers.INT64_TYPE) + writeInt64((length))
        }
    }
    /**Write a variable-length integral numeric type which is large enough to hold the specified value*/
    internal fun writeLength(length:Int):ByteArray {
        return writeLength(length.toLong())
    }
    /**Write a variable-length integral numeric type which is large enough to hold the specified value*/
    internal fun writeLength(length:Short):ByteArray {
        return writeLength(length.toLong())
    }
    /**Write a variable-length integral numeric type which is large enough to hold the specified value*/
    internal fun writeLength(length:Byte):ByteArray {
        return writeLength(length.toLong())
    }

    internal fun writeNull(): ByteArray {
        return writeMarker(Markers.NULL_TYPE)
    }

    internal fun writeNoOp(): ByteArray {
        return writeMarker(Markers.NO_OP_TYPE)
    }

    internal fun writeBoolean(boolean:Boolean):ByteArray {
        if(boolean == true) {
            return writeChar('T')
        }else {
            return writeChar('F')
        }
    }

    internal fun writeInt8(int8:Byte):ByteArray {
        return byteArrayOf(int8)
    }

    @UseExperimental(ExperimentalUnsignedTypes::class)
    internal fun writeUint8(uint8:UByte):ByteArray {
        return byteArrayOf(uint8.toByte())
    }

    /**Write a Short value < 256 as a uint8.
     * @throws IllegalArgumentException if the passed value is >= 256*/
    @Throws(IllegalArgumentException::class)
    fun writeUint8(uint8:Short):ByteArray {
        if(uint8 > 255) {
            throw IllegalArgumentException("short argument cannot be > 255. Was $uint8.")
        }
        return byteArrayOf(ByteBuffer.allocate(Short.SIZE_BYTES).putShort(uint8).array()[1])
    }
    internal fun writeInt16(int16:Short):ByteArray {
        return ByteBuffer.allocate(Short.SIZE_BYTES).putShort(int16).array()
    }
    internal fun writeInt32(int32:Int):ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(int32).array()
    }
    internal fun writeInt64(int64:Long):ByteArray {
        return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(int64).array()
    }
    internal fun writeFloat32(float32:Float):ByteArray {
        return ByteBuffer.allocate(4).putFloat(float32).array()
    }
    internal fun writeFloat64(float64:Double):ByteArray {
        return ByteBuffer.allocate(8).putDouble(float64).array()
    }
    /**Write an 8-bit ASCII character.
     * Java/kotlin Chars are always 16-bit, whereas UBJSON chars are only 8-bit.
    This method throws an error that the upper byte will be lost,
    if the char's upper byte is nonzero
     * @throws IndexOutOfBoundsException if the JVM 16-bit Char's upper 8 bits are nonzero*/
    @Throws(IndexOutOfBoundsException::class)
    fun writeChar(char:Char):ByteArray {
        //java/kotlin chars are always 16-bit, whereas UBJSON chars are only 8-bit.
        //Throw an error to the user that the upper byte will be lost,
        // if the char's upper byte is nonzero
        val bb = ByteBuffer.allocate(Char.SIZE_BYTES).putChar(char)
        //println("0th byte of the char:"+bb[0])
        //println("byte buffer as array:"+bb.array().toHexString())
        if(bb[0] != 0.toByte()) {
            throw IndexOutOfBoundsException("the supplied 16-bit JVM  char '$char''s upper 8 bits are non-zero, " +
                    "which a UBJSON 8-bit char cannot store. "+
                    "Use an UBSJON String instead for 16-bit-wide characters.")
        } else {
            return bb.array()
        }
    }
    internal fun writeString(string:String):ByteArray {
        //UTF-8 is the default CharSet argument anyway, but it's better to be explicit
        return writeLength(string.length.toLong())+string.toByteArray(Charsets.UTF_8)
    }
    internal fun writeHighPrecisionNumber(highPrecisionNumber:BigDecimal):ByteArray {
        return writeString(highPrecisionNumber.toPlainString())
    }
}
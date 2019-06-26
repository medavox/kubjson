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
 * Both UBJSON and Java (and by extension Kotlin) are Big-Endian, so no endianness conversion is necessary.
 * the byte arrays returned by these methods only contain the data payload*/
object Writer {



    //todo: take arrays and lists as arrays
    //todo: take all Big Number formats as High Precision Numbers
    //todo: handle Map types as UBJSON objects
    fun writeObject(obj:Any):ByteArray {
        //write object tag-marker
        var out = byteArrayOf()

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

        //write the name, type and value of every property
        for(prop:KProperty1<Any, *> in props) {
            val typeSurrogate = (prop.returnType.classifier as KClass<*>)//.objectInstance
            println("${prop.name}:"+typeSurrogate.simpleName+" = "+prop.get(obj))

            //write variable name & value
            out += writeString(prop.name) + writeAnything(prop.get(obj))
        }
        return out
    }



    /**Unlike most other write-methods here, this method precedes the written content with its type marker*/
    private fun writeAnything(any:Any?, writeTypeMarker:Boolean=true):ByteArray {
        data class TypeAndContent(val typeMarker:ByteArray, val content:ByteArray)
        //just write a null tag if the value is null; type info will just have to be omitted
        if(any == null) {
            //println("NULL")
            return writeNull()
        }

        //archetypes for use with isInstance
        val boolean = false
        val byte:Byte = 127
        val short:Short = Short.MAX_VALUE
        val int:Int = Int.MAX_VALUE
        val long:Long = Long.MAX_VALUE
        val float:Float = Float.MAX_VALUE
        val double:Double = Double.MAX_VALUE
        val char:Char = 'c'
        val string:String = ""
        val bigDecimal = BigDecimal.ONE
        val bigInteger = BigInteger.ONE

        val typeSurrogate = any.javaClass.kotlin
        val (type, content) = with(typeSurrogate) {
            when {
                isInstance(boolean) -> TypeAndContent(writeBoolean(any as Boolean), byteArrayOf())//the boolean's type marker IS the content
                isInstance(byte) -> TypeAndContent(writeMarker(INT8_TYPE), writeInt8(any as Byte))
                isInstance(short) -> TypeAndContent(writeMarker(INT16_TYPE), writeInt16(any as Short))
                isInstance(int) -> TypeAndContent(writeMarker(INT32_TYPE), writeInt32(any as Int))
                isInstance(long) -> TypeAndContent(writeMarker(INT64_TYPE), writeInt64(any as Long))
                isInstance(float) -> TypeAndContent(writeMarker(FLOAT32_TYPE), writeFloat32(any as Float))
                isInstance(double) -> TypeAndContent(writeMarker(FLOAT64_TYPE), writeFloat64(any as Double))
                isInstance(char) -> TypeAndContent(writeMarker(CHAR_TYPE), writeChar(any as Char))
                isInstance(string) -> TypeAndContent(writeMarker(STRING_TYPE), writeString(any as String))
                isInstance(bigDecimal) -> TypeAndContent(writeMarker(HIGH_PRECISION_NUMBER_TYPE), writeHighPrecisionNumber(any as BigDecimal))
                isInstance(bigInteger) -> TypeAndContent(writeMarker(HIGH_PRECISION_NUMBER_TYPE), writeHighPrecisionNumber((any as BigInteger).toBigDecimal()))
                isInstance(booleanArrayOf()) -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as BooleanArray))
                isInstance(byteArrayOf()) -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as ByteArray))
                isInstance(shortArrayOf()) -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as ShortArray))
                isInstance(intArrayOf()) -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as IntArray))
                isInstance(longArrayOf()) -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as LongArray))
                isInstance(floatArrayOf()) -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as FloatArray))
                isInstance(doubleArrayOf()) -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as DoubleArray))
                isInstance(charArrayOf()) -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as CharArray))
                isInstance(emptyArray<Any?>()) -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as Array<Any?>))
                else -> {
                    println("UNHANDLED TYPE:" + typeSurrogate)
                    //todo: try to serialise unknown types as an object
                    TypeAndContent(byteArrayOf(), byteArrayOf())
                }
            }
        }
        return if(writeTypeMarker) type + content else content
    }

    fun writeArray(array:Array<Any?>):ByteArray {
        //don't include the type marker, by convention with the other methods
        var outputBytes:ByteArray = byteArrayOf()
        //first: check that every elementin the array is of the same type,
        //by adding the type of every element to a set
        val typesInArray:Set<KClass<*>?> = array.map { it?.javaClass?.kotlin}.toSet()
        println("types in array: "+typesInArray)
        val homogeneous = if(typesInArray.size > 1) {
            //if the set.size > 1, use heterogeneous array syntax

            false
        }else {
            // otherwise, make a homogeneous array of that one type
            outputBytes += writeMarkers(HOMOGENEOUS_CONTAINER_TYPE)
            true
        }
        //also: check for nullability in elements, then nullness

        //either way, write array length (we always write array length)
        outputBytes += writeMarker(CONTAINER_LENGTH) + writeLength(array.size)

        println("array type:"+array::class.typeParameters)
        for(element in array) {
            println("element: "+element)
            println("type: "+element?.javaClass?.simpleName)
            outputBytes += writeAnything(element, homogeneous)
        }
    }

    fun writeArray(array:BooleanArray):ByteArray {
        TODO()
        //check if all values are the same. if they are, use the homgeneous array syntax and don't bother with payloads
        //if they're not all the same, set each one individually
    }

    fun writeArray(array:ByteArray):ByteArray {
        return writeMarkers(ARRAY_START, HOMOGENEOUS_CONTAINER_TYPE, UINT8_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size) + array
    }

    fun writeArray(array:ShortArray):ByteArray {
        var byteOutput = writeMarkers(ARRAY_START, HOMOGENEOUS_CONTAINER_TYPE, INT16_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)

        for(i in array.indices) {
            byteOutput += writeInt16(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:IntArray):ByteArray {
        var byteOutput = writeMarkers(ARRAY_START, HOMOGENEOUS_CONTAINER_TYPE, INT32_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)
        for(i in array.indices) {
            byteOutput += writeInt32(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:LongArray):ByteArray {
        var byteOutput = writeMarkers(ARRAY_START, HOMOGENEOUS_CONTAINER_TYPE, INT64_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)

        for(i in array.indices) {
            byteOutput += writeInt64(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:FloatArray):ByteArray {
        var byteOutput = writeMarkers(ARRAY_START, HOMOGENEOUS_CONTAINER_TYPE, FLOAT32_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)

        for(i in array.indices) {
            byteOutput += writeFloat32(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:DoubleArray):ByteArray {
        var byteOutput = writeMarkers(ARRAY_START, HOMOGENEOUS_CONTAINER_TYPE, FLOAT64_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)

        for(i in array.indices) {
            byteOutput += writeFloat64(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:CharArray):ByteArray {
        var byteOutput = writeMarkers(ARRAY_START, HOMOGENEOUS_CONTAINER_TYPE, CHAR_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)

        for(i in array.indices) {
            byteOutput += writeChar(array[i])
        }
        return byteOutput
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
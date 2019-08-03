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

    //todo: handle JVM List types as arrays
    //todo: handle Map types as UBJSON objects
    //todo: always write a numeric value to the smallest type that can hold it, as per the spec:
    // http://ubjson.org/developer-resources/#best_smallest_num
    fun writeObject(obj:Any):ByteArray {
        //don't write object tag-marker, by convention with other methods
        var out = byteArrayOf()
        val p = Printa("writeObject")

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
            p.rintln("${prop.name}:"+typeSurrogate.simpleName+" = "+prop.get(obj))

            //write variable name & value
            out += writeString(prop.name) + writeAnything(prop.get(obj), true)
        }
        return out
    }

    /**Unlike most other write-methods here, this method precedes the written content with its type marker*/
    internal fun writeAnything(any:Any?, writeTypeMarker:Boolean=true):ByteArray {
        data class TypeAndContent(val typeMarker:ByteArray, val content:ByteArray)
        val p = Printa("writeAnything")
        val (type, content) = when(any) {
            //just write a null tag if the value is null; type info will just have to be omitted
            null -> TypeAndContent(writeNull(), byteArrayOf())
            is Boolean -> TypeAndContent(writeBoolean(any), byteArrayOf())//the boolean's type marker IS the content
            is Byte -> TypeAndContent(writeMarker(INT8_TYPE), writeInt8(any))
            is Short -> TypeAndContent(writeMarker(INT16_TYPE), writeInt16(any))
            is Int -> TypeAndContent(writeMarker(INT32_TYPE), writeInt32(any))
            is Long -> TypeAndContent(writeMarker(INT64_TYPE), writeInt64(any))
            is Float -> TypeAndContent(writeMarker(FLOAT32_TYPE), writeFloat32(any))
            is Double -> TypeAndContent(writeMarker(FLOAT64_TYPE), writeFloat64(any))
            is Char -> TypeAndContent(writeMarker(CHAR_TYPE), writeChar(any))
            is String -> TypeAndContent(writeMarker(STRING_TYPE), writeString(any))
            is BigDecimal -> TypeAndContent(writeMarker(HIGH_PRECISION_NUMBER_TYPE), writeHighPrecisionNumber(any))
            is BigInteger -> TypeAndContent(writeMarker(HIGH_PRECISION_NUMBER_TYPE), writeHighPrecisionNumber((any).toBigDecimal()))
            //fixme: vanilla array types aren't detected properly, and neither are subtypes of Any
            is BooleanArray -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any))
            is ByteArray -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any))
            is ShortArray -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any))
            is IntArray -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any))
            is LongArray -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any))
            is FloatArray -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any))
            is DoubleArray -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any))
            is CharArray -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any))
            is Array<*> -> TypeAndContent(writeMarker(ARRAY_START), writeArray(any as Array<Any?>))
            is Any -> TypeAndContent(writeMarker(OBJECT_START), writeObject(any))
            else -> {
                p.rintln("UNHANDLED TYPE:" + any.javaClass.kotlin)
                //todo: try to serialise unknown types as an object
                TypeAndContent(byteArrayOf(), byteArrayOf())
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
        //println("types in array: "+typesInArray)
        val homogeneous = if(typesInArray.size > 1) {
            //if there is > 1 type in the whole array, use heterogeneous array syntax
            false
        }else {
            // otherwise, make a homogeneous array of that one type
            outputBytes += writeMarkers(HOMOGENEOUS_CONTAINER_TYPE)
            true
        }
        //also: check for nullability in elements, then nullness

        //either way, write array length (we always write array length)
        outputBytes += writeMarker(CONTAINER_LENGTH) + writeLength(array.size)
        val p = Printa("writeArray")
        p.rintln("array type:"+array::class.typeParameters)
        for(element in array) {
            p.rintln("element: "+element)
            p.rintln("type: "+element?.javaClass?.simpleName)
            outputBytes += writeAnything(element, homogeneous)
        }
        return outputBytes
    }

    fun writeArray(array:BooleanArray):ByteArray {
        //check if all values are the same. if they are, use the homgeneous array syntax and don't bother with payloads
        //if they're not all the same, write each one individually

        return if(array.all { it == true }) {
            writeMarkers(HOMOGENEOUS_CONTAINER_TYPE, TRUE_TYPE, CONTAINER_LENGTH) +
                    writeLength(array.size)
        }else if(array.all{ it == false}) {
            writeMarkers(HOMOGENEOUS_CONTAINER_TYPE, FALSE_TYPE, CONTAINER_LENGTH) +
                    writeLength(array.size)
        }else {
            var out = byteArrayOf()
            for(bool in array) {
                out += writeBoolean(bool)
            }
            writeMarker(CONTAINER_LENGTH) + writeLength(array.size) + out
        }
    }

    fun writeArray(array:ByteArray):ByteArray {
        //don't write the type marker: the caller does that
        return writeMarkers(HOMOGENEOUS_CONTAINER_TYPE, UINT8_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size) + array
    }

    fun writeArray(array:ShortArray):ByteArray {
        //don't write the type marker: the caller does that
        var byteOutput = writeMarkers(HOMOGENEOUS_CONTAINER_TYPE, INT16_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)

        for(i in array.indices) {
            byteOutput += writeInt16(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:IntArray):ByteArray {
        //don't write the type marker: the caller does that
        var byteOutput = writeMarkers(HOMOGENEOUS_CONTAINER_TYPE, INT32_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)
        for(i in array.indices) {
            byteOutput += writeInt32(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:LongArray):ByteArray {
        //don't write the type marker: the caller does that
        var byteOutput = writeMarkers(HOMOGENEOUS_CONTAINER_TYPE, INT64_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)

        for(i in array.indices) {
            byteOutput += writeInt64(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:FloatArray):ByteArray {
        //don't write the type marker: the caller does that
        var byteOutput = writeMarkers(HOMOGENEOUS_CONTAINER_TYPE, FLOAT32_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)

        for(i in array.indices) {
            byteOutput += writeFloat32(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:DoubleArray):ByteArray {
        //don't write the type marker: the caller does that
        var byteOutput = writeMarkers(HOMOGENEOUS_CONTAINER_TYPE, FLOAT64_TYPE, CONTAINER_LENGTH) +
                writeLength(array.size)

        for(i in array.indices) {
            byteOutput += writeFloat64(array[i])
        }
        return byteOutput
    }

    fun writeArray(array:CharArray):ByteArray {
        //don't write the type marker: the caller does that
        var byteOutput = writeMarkers(HOMOGENEOUS_CONTAINER_TYPE, CHAR_TYPE, CONTAINER_LENGTH) +
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
    private fun writeLength(length:Long):ByteArray {
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
    private fun writeLength(length:Int):ByteArray {
        return writeLength(length.toLong())
    }
    /**Write a variable-length integral numeric type which is large enough to hold the specified value*/
    private fun writeLength(length:Short):ByteArray {
        return writeLength(length.toLong())
    }
    /**Write a variable-length integral numeric type which is large enough to hold the specified value*/
    private fun writeLength(length:Byte):ByteArray {
        return writeLength(length.toLong())
    }

    private fun writeNull(): ByteArray {
        return writeMarker(NULL_TYPE)
    }

    internal fun writeNoOp(): ByteArray {
        return writeMarker(NO_OP_TYPE)
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
                    "Use an UBSJON String instead for UTF-8 support, which includes 16-bit-wide characters.")
        } else {
            return byteArrayOf(bb[1])
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
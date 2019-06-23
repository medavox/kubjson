package com.github.medavox.kubjson

import java.io.OutputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import kotlin.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties

internal fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun main() {
    //tust('h')
    //tust('д')
    val writer = Writer(ByteArrayOutputStream(2))
    writer.writeUint8(127)
    writer.writeUint8(128)
    writer.writeUint8(255)
    writer.writeUint8(256)
}

internal fun tust(c:Char) {
    with(System.out) {
        //println("char size in bytes:" + Char.SIZE_BYTES)
        print("bytes of '$c': ")
        println(ByteBuffer.allocate(Char.SIZE_BYTES).putChar(c).array().toHexString())
        print("'$c'.toByte: ")
        println(byteArrayOf(c.toByte()).toHexString())
    }
    val writer = Writer(ByteArrayOutputStream(2))
    writer.writeChar(c)
}
fun hexOf(owt:Short){
    with(System.out) {
        //println("char size in bytes:" + Char.SIZE_BYTES)
        print("bytes of '$owt': ")
        println(ByteBuffer.allocate(Short.SIZE_BYTES).putShort(owt).array().toHexString())
    }
}

/**Basic low-level converter from JVM types to their UBJSON equivalents.
 * NOTE: both UBJSON and Java (and by extension Kotlin) are Big-Endian, so no endianness conversion is necessary*/
class Writer(/*outputStream:OutputStream*/) {

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

    fun /*<T : Any>*/ writeObject(obj:Any):ByteArray {
        //write object tag
        var out = writeChar('{')

        //calculate & write number of properties
        val cls:KClass<Any> = obj.javaClass.kotlin
        //val cls:KClass<Any> = obj::class as KClass<Any>
        var props/*:Collection<KProperty1<Any, *>>*/ = cls.declaredMemberProperties
        for(clas/*:KClass<Any>*/ in obj.javaClass.kotlin.allSuperclasses) {
            props += (clas.declaredMemberProperties as Collection<KProperty1<Any, *>>)
        }
        //println("all properties:"+props)
        //exclude properties annotated with @KubjsonIgnore
        props.filter { it.annotations.any { ann -> ann.annotationClass == KubjsonIgnore::class} }
        out += writeChar('#') + writeLength(props.size.toLong())
        for(prop:KProperty1<Any, *> in props) {
            println("property: "+prop::class)
            print("${prop.name}:")
            val typeSurrogate = (prop.returnType.classifier as KClass<*>)//.objectInstance
            //println("property type: $typeSurrogate\n")

            //write variable name
            out += writeString(prop.name)

            //just write a null tag if the value is null; type info will just have to be omitted
            if(prop.get(obj) == null) {
                println("NULL")
                out += writeNull()
                continue
            }

            out += when {
                typeSurrogate.isInstance(boolean) -> {
                    println("Boolean = "+prop.get(obj))
                    writeBoolean(prop.get(obj) as Boolean)
                }
                typeSurrogate.isInstance(byte) -> {
                    println("Byte = "+prop.get(obj))
                    writeInt8(prop.get(obj) as Byte)
                }
                typeSurrogate.isInstance(short) -> {
                    println("Short = "+prop.get(obj))
                    writeInt16(prop.get(obj) as Short)
                }
                typeSurrogate.isInstance(int) -> {
                    println("Integer = "+prop.get(obj))
                    writeInt32(prop.get(obj) as Int)
                }
                typeSurrogate.isInstance(long) -> {
                    println("Long = "+prop.get(obj))
                    writeInt64(prop.get(obj) as Long)
                }
                typeSurrogate.isInstance(float) -> {
                    println("Float = "+prop.get(obj))
                    writeFloat32(prop.get(obj) as Float)
                }
                typeSurrogate.isInstance(double) -> {
                    println("Double = "+prop.get(obj))
                    writeFloat64(prop.get(obj) as Double)
                }
                typeSurrogate.isInstance(char) -> {
                    println("Char = "+prop.get(obj))
                    writeChar(prop.get(obj) as Char)
                }
                typeSurrogate.isInstance(string) -> {
                    println("String = "+prop.get(obj))
                    writeString(prop.get(obj) as String)
                }
                else -> {
                    println("unknown="+typeSurrogate)
                    byteArrayOf()
                }
            }
        }
        return out
    }

    internal fun writeTypeMarker(valueType:ValueTypes):ByteArray {
        return writeChar(valueType.marker)
    }

    /**Write a variable-length integral numeric type which is large enough to hold the specified value*/
    internal fun writeLength(length:Long):ByteArray {
        if(length < Byte.MAX_VALUE) {
            return writeTypeMarker(ValueTypes.INT8)  + writeInt8(length.toByte())
        }else if(length < Short.MAX_VALUE) {
            return writeTypeMarker(ValueTypes.INT16) + writeInt16(length.toShort())
        }else if(length < Int.MAX_VALUE) {
            return writeTypeMarker(ValueTypes.INT32) + writeInt32(length.toInt())
        }else {
            return writeTypeMarker(ValueTypes.INT64) + writeInt64((length))
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
        return writeTypeMarker(ValueTypes.NULL)
    }

    internal fun writeNoOp(): ByteArray {
        return writeTypeMarker(ValueTypes.NO_OP)
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
    /**java/kotlin chars are always 16-bit, whereas UBJSON chars are only 8-bit.
    This method throws an error to the user that the upper byte will be lost,
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
            throw IndexOutOfBoundsException("JVM 16-bit char '$char' uses the upper 8 bits, " +
                    "which a UBJSON 8-bit char cannot store. "+
                    "Use a UBSJON String instead for 16-bit-wide characters.")
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
package com.github.medavox.kubjson

import java.io.OutputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import kotlin.IllegalArgumentException

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

    internal fun writeLength(length:Long):ByteArray {
        if(length < Byte.MAX_VALUE) {
            return writeInt8(length.toByte())
        }else if(length < Short.MAX_VALUE) {
            return writeInt16(length.toShort())
        }else if(length < Int.MAX_VALUE) {
            return writeInt32(length.toInt())
        }else {
            return writeInt64((length))
        }
    }

    internal fun writeNull(): ByteArray {
        return byteArrayOf('Z'.toByte())
    }

    internal fun writeNoOp(): ByteArray {
        return byteArrayOf('N'.toByte())
    }

    internal fun writeBoolean(boolean:Boolean):ByteArray {
        if(boolean == true) {
            return byteArrayOf('T'.toByte())
        }else {
            return byteArrayOf('F'.toByte())
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
        hexOf(uint8)
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
        return string.toByteArray(Charsets.UTF_8)//this is the default anyway, but it's better ot be explicit
    }
    internal fun writeHighPrecisionNumber(highPrecisionNumber:BigDecimal):ByteArray {
        return writeString(highPrecisionNumber.toPlainString())
    }
}
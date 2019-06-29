package com.github.medavox.kubjson

import com.github.medavox.kubjson.Markers.*
import java.io.InputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.KClass

class Reader(private val inputStream: InputStream, private val listener: ReaderListener) {

    //All integer types (int8, uint8, int16, int32 and int64) are written in most-significant-bit order
    // (high byte written first, aka “big endian“).

    //The char type is synonymous with 1-byte, UTF8 encoded value (decimal values 0-127).
    // A char value must not have a decimal value larger than 127.

    //The Universal Binary JSON specification dictates UTF-8 as the required string encoding
    // (this includes the high-precision number type as it is a string-encoded value).

    //using a listener is no good, because it can't handle container types:
    // how would you tell if onBoolean() was being called for a value in the current type,
    // or a type inside that type?
    fun InputStream.readNext():Any? {
        val oneByte:ByteArray = byteArrayOf(0)
        read(oneByte)
        return when(readChar(oneByte[0])) {
            NULL_TYPE.marker -> null
            NO_OP_TYPE.marker -> Unit
            TRUE_TYPE.marker -> true
            FALSE_TYPE.marker -> false

            INT8_TYPE.marker -> {
                val ba = ByteArray(1)
                read(ba)
                readInt8(ba[0])
            }
            UINT8_TYPE.marker -> {
                val ba = ByteArray(1)
                read(ba)
                readUint8(ba[0])
            }
            INT16_TYPE.marker -> {
                val ba = ByteArray(2)
                read(ba)
                readInt16(ba)
            }
            INT32_TYPE.marker -> {
                val ba = ByteArray(4)
                read(ba)
                readInt32(ba)
            }
            INT64_TYPE.marker -> {
                val ba = ByteArray(8)
                read(ba)
                readInt64(ba)
            }
            FLOAT32_TYPE.marker -> {
                val ba = ByteArray(4)
                read(ba)
                readFloat32(ba)
            }
            FLOAT64_TYPE.marker -> {
                val ba = ByteArray(8)
                read(ba)
                readFloat64(ba)
            }
            CHAR_TYPE.marker -> {
                val ba = ByteArray(1)
                read(ba)
                readChar(ba[0])
            }
            STRING_TYPE.marker -> {
                val strLength = readLength(inputStream)
                if(strLength > Int.MAX_VALUE) {
                    throw IllegalStateException("string is longer than Kotlin's max supported length: $strLength")
                }
                val ba = ByteArray(strLength.toInt())
                read(ba)
                readString(ba)
            }
            HIGH_PRECISION_NUMBER_TYPE.marker -> {
                val strLength = readLength(inputStream)
                if(strLength > Int.MAX_VALUE) {
                    throw IllegalStateException("string is longer than Kotlin's max supported length: $strLength")
                }
                val ba = ByteArray(strLength.toInt())
                read(ba)
                readHighPrecisionNumber(ba)
            }
            OBJECT_START.marker -> {

            }
            else -> throw IllegalArgumentException("was expecting a UBJSON type marker, but got ${oneByte[0]} = '${oneByte[0].toChar()}'")
        }
    }

    /**it's a bad  idea to pass all the bytes at the start, because we don't know how many bytes we'll need,
     and we might try and pass more bytes than are left in the array, even if those extras bytes are not needed*/
    internal fun readLength(input:InputStream):Long {
        val oneByte = ByteArray(1)
        input.read(oneByte)
        return when(readChar(oneByte[0])) {
            INT8_TYPE.marker -> {
                val ba = ByteArray(1)
                input.read(ba)
                readInt8(ba[0]).toLong()
            }
            INT16_TYPE.marker -> {
                val ba = ByteArray(2)
                input.read(ba)
                readInt16(ba).toLong()
            }
            INT32_TYPE.marker -> {
                val ba = ByteArray(4)
                input.read(ba)
                readInt32(ba).toLong()
            }
            INT64_TYPE.marker -> {
                val ba = ByteArray(8)
                input.read(ba)
                readInt64(ba)
            }
            else -> throw IllegalArgumentException("was expecting a numeric type marker, " +
                    "but got ${oneByte[0]} = '${oneByte[0].toChar()}'")
        }
    }

    internal fun readInt8(b:Byte):Byte{
        return ByteBuffer.wrap(byteArrayOf(b)).order(ByteOrder.BIG_ENDIAN).get()
    }

    /**Read the contents of the UInt8 in the start of the passed [ByteArray], without a preceding type marker or length.*/
    @UseExperimental(ExperimentalUnsignedTypes::class)
    internal fun readUint8(b:Byte):UByte {
        return readInt8(b).toUByte()
    }

    /**Read a UBJSON Int16 value into a JVM Short.
     * @param b a [ByteArray] of the contents (no type marker)*/
    internal fun readInt16(b:ByteArray):Short {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getShort()
    }

    /**Read the contents of the Int32 contained at the start of the passed [ByteArray] into a JVM Int(eger),
     * without a preceding type marker or length.*/
    internal fun readInt32(b:ByteArray):Int {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt()
    }

    /**Read the contents of the Int64 contained at the start of the passed [ByteArray] into a JVM Long,
     * without a preceding type marker or length.*/
    internal fun readInt64(b:ByteArray):Long {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getLong()
    }

    internal fun readFloat32(b:ByteArray):Float {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getFloat()
    }

    internal fun readFloat64(b:ByteArray):Double {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getDouble()
    }

    internal fun readChar(b:Byte):Char {
        return readInt8(b).toChar()
    }

    /**Read the value of a UBJSON UTF-8 encoded string into a JVM UTF-16 String
     * @param b a [ByteArray] containing just the content of the string -- no type marker or length field*/
    internal fun readString(b:ByteArray):String {
        return b.toString(Charsets.UTF_8)
    }

    internal fun readHighPrecisionNumber(b:ByteArray):BigDecimal {
        return BigDecimal(readString(b))
    }
}
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

    //fixme: using a listener is no good, because it can't handle container types:
    // how would you tell if onBoolean() was being called for a value in the current type,
    // or a type inside that type?
    fun processNextValue() {
        val oneByte:ByteArray = byteArrayOf(0)
        inputStream.read(oneByte)
        when(readChar(oneByte[0])) {
            NULL_TYPE.marker -> listener.onNull()
            NO_OP_TYPE.marker -> listener.onNoOp()
            TRUE_TYPE.marker -> listener.onBoolean(true)
            FALSE_TYPE.marker -> listener.onBoolean(false)

            INT8_TYPE.marker -> {
                val ba = ByteArray(1)
                inputStream.read(ba)
                listener.onInt8(readInt8(ba[0]))
            }
            UINT8_TYPE.marker -> {
                val ba = ByteArray(1)
                inputStream.read(ba)
                listener.onUint8(readUint8(ba[0]))
            }
            INT16_TYPE.marker -> {
                val ba = ByteArray(2)
                inputStream.read(ba)
                listener.onInt16(readInt16(ba))
            }
            INT32_TYPE.marker -> {
                val ba = ByteArray(4)
                inputStream.read(ba)
                listener.onInt32(readInt32(ba))
            }
            INT64_TYPE.marker -> {
                val ba = ByteArray(8)
                inputStream.read(ba)
                listener.onInt64(readInt64(ba))
            }
            FLOAT32_TYPE.marker -> {
                val ba = ByteArray(4)
                inputStream.read(ba)
                listener.onFloat32(readFloat32(ba))
            }
            FLOAT64_TYPE.marker -> {
                val ba = ByteArray(8)
                inputStream.read(ba)
                listener.onFloat64(readFloat64(ba))
            }
            CHAR_TYPE.marker -> {
                val ba = ByteArray(1)
                inputStream.read(ba)
                listener.onChar(readChar(ba[0]))
            }
            STRING_TYPE.marker -> {
                val strLength = readLength()
                if(strLength > Int.MAX_VALUE) {
                    throw IllegalStateException("string is longer than Kotlin's max supported length: $strLength")
                }
                val ba = ByteArray(strLength.toInt())
                inputStream.read(ba)
                listener.onString(readString(ba))
            }
            HIGH_PRECISION_NUMBER_TYPE.marker -> {
                val strLength = readLength()
                if(strLength > Int.MAX_VALUE) {
                    throw IllegalStateException("string is longer than Kotlin's max supported length: $strLength")
                }
                val ba = ByteArray(strLength.toInt())
                inputStream.read(ba)
                listener.onHighPrecisionNumber(readHighPrecisionNumber(ba))
            }
            else -> throw IllegalArgumentException("was expecting a UBJSON type marker, but got ${oneByte[0]}")
        }
    }

    internal fun readLength():Long {
        val oneByte = ByteArray(1)
        inputStream.read(oneByte)
        return when(readChar(oneByte[0])) {
            INT8_TYPE.marker -> {
                val ba = ByteArray(1)
                readInt8(ba[0]).toLong()
            }
            INT16_TYPE.marker -> {
                val ba = ByteArray(2)
                readInt16(ba).toLong()
            }
            INT32_TYPE.marker -> {
                val ba = ByteArray(4)
                readInt32(ba).toLong()
            }
            INT64_TYPE.marker -> {
                val ba = ByteArray(8)
                readInt64(ba)
            }
            else -> throw IllegalArgumentException("was expecting a numeric type marker, but got ${oneByte[0]}")
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
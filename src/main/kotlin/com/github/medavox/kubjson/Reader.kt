package com.github.medavox.kubjson

import com.github.medavox.kubjson.ValueTypes.*
import java.io.InputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Reader(private val inputStream:InputStream, private val listener: ReaderListener) {

    //All integer types (int8, uint8, int16, int32 and int64) are written in most-significant-bit order
    // (high byte written first, aka “big endian“).

    //The char type is synonymous with 1-byte, UTF8 encoded value (decimal values 0-127).
    // A char value must not have a decimal value larger than 127.

    //The Universal Binary JSON specification dictates UTF-8 as the required string encoding
    // (this includes the high-precision number type as it is a string-encoded value).

    fun processNextValue() {
        val oneByte:ByteArray = byteArrayOf(0)
        inputStream.read(oneByte)
        when(readChar(oneByte[0])) {
            NULL.marker -> listener.onNull()
            NO_OP.marker -> listener.onNoOp()
            TRUE.marker -> listener.onBoolean(true)
            FALSE.marker -> listener.onBoolean(false)

            INT8.marker -> {
                val ba = ByteArray(1)
                inputStream.read(ba)
                listener.onInt8(readInt8(ba[0]))
            }
            UINT8.marker -> {
                val ba = ByteArray(1)
                inputStream.read(ba)
                listener.onUint8(readUint8(ba[0]))
            }
            INT16.marker -> {
                val ba = ByteArray(2)
                inputStream.read(ba)
                listener.onInt16(readInt16(ba))
            }
            INT32.marker -> {
                val ba = ByteArray(4)
                inputStream.read(ba)
                listener.onInt32(readInt32(ba))
            }
            INT64.marker -> {
                val ba = ByteArray(8)
                inputStream.read(ba)
                listener.onInt64(readInt64(ba))
            }
            FLOAT32.marker -> {
                val ba = ByteArray(4)
                inputStream.read(ba)
                listener.onFloat32(readFloat32(ba))
            }
            FLOAT64.marker -> {
                val ba = ByteArray(8)
                inputStream.read(ba)
                listener.onFloat64(readFloat64(ba))
            }
            CHAR.marker -> {
                val ba = ByteArray(1)
                inputStream.read(ba)
                listener.onChar(readChar(ba[0]))
            }
            STRING.marker -> {
                val strLength = readLength()
                if(strLength > Int.MAX_VALUE) {
                    throw IllegalStateException("string is longer than Kotlin's max supported length: $strLength")
                }
                val ba = ByteArray(strLength.toInt())
                inputStream.read(ba)
                listener.onString(readString(ba))
            }
            HIGH_PRECISION_NUMBER.marker -> {
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

    private fun readLength():Long {
        val oneByte = ByteArray(1)
        inputStream.read(oneByte)
        return when(readChar(oneByte[0])) {
            INT8.marker -> {
                val ba = ByteArray(1)
                readInt8(ba[0]).toLong()
            }
            INT16.marker -> {
                val ba = ByteArray(2)
                readInt16(ba).toLong()
            }
            INT32.marker -> {
                val ba = ByteArray(4)
                readInt32(ba).toLong()
            }
            INT64.marker -> {
                val ba = ByteArray(8)
                readInt64(ba)
            }
            else -> throw IllegalArgumentException("was expecting a numeric type marker, but got ${oneByte[0]}")
        }
    }

    private fun readInt8(b:Byte):Byte{
        return ByteBuffer.wrap(byteArrayOf(b)).order(ByteOrder.BIG_ENDIAN).get()
    }

    @UseExperimental(ExperimentalUnsignedTypes::class)
    private fun readUint8(b:Byte):UByte {
        return readInt8(b).toUByte()
    }

    private fun readInt16(b:ByteArray):Short {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getShort()
    }

    private fun readInt32(b:ByteArray):Int {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt()
    }

    private fun readInt64(b:ByteArray):Long {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getLong()
    }

    private fun readFloat32(b:ByteArray):Float {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getFloat()
    }

    private fun readFloat64(b:ByteArray):Double {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getDouble()
    }

    private fun readChar(b:Byte):Char {
        return readInt8(b).toChar()
    }

    private fun readString(b:ByteArray):String {
        return b.toString(Charsets.UTF_8)
    }

    private fun readHighPrecisionNumber(b:ByteArray):BigDecimal {
        return BigDecimal(readString(b))
    }
}
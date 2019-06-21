package com.github.medavox.kubjson

import java.io.OutputStream
import java.math.BigDecimal

class Writer(outputStream:OutputStream) {

    private fun writeLength(length:Long):ByteArray {
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

    private fun writeNull(): ByteArray {
        return byteArrayOf('Z'.toByte())
    }

    private fun writeNoOp(): ByteArray {
        return byteArrayOf('N'.toByte())
    }

    private fun writeBoolean(boolean:Boolean):ByteArray {
        if(boolean == true) {
            return byteArrayOf('T'.toByte())
        }else {
            return byteArrayOf('F'.toByte())
        }
    }

    private fun writeInt8(int8:Byte):ByteArray {

    }

    @UseExperimental(ExperimentalUnsignedTypes::class)
    private fun writeUint8(uint8:UByte):ByteArray {

    }
    private fun writeInt16(int16:Short):ByteArray {

    }
    private fun writeInt32(int32:Int):ByteArray {

    }
    private fun writeInt64(int64:Long):ByteArray {

    }
    private fun writeFloat32(float32:Float):ByteArray {

    }
    private fun writeFloat64(float64:Double):ByteArray {

    }
    private fun writeChar(char:Char):ByteArray {

    }
    private fun writeString(string:String):ByteArray {

    }
    private fun writeHighPrecisionNumber(highPrecisionNumber:BigDecimal):ByteArray {

    }

}
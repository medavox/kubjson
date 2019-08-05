package com.github.medavox.kubjson

import java.io.InputStream

/**Provides 1. a more convenient API for reading [ByteArray]s from an [InputStream], and
 * 2. the ability to peek at the next byte in the input, without removing it from the next read() call.
 * @constructor Construct a new instance
 * @param inputStream */
class InputStreamShim(private val inputStream: InputStream) {
    private var peekedByte:Byte? = null

    fun readBytes(numBytesToRead:Int):ByteArray {
        val peeked = peekedByte
        val outputByteArray = ByteArray(if(peeked != null) numBytesToRead -1 else numBytesToRead)
        val bytesActuallyRead = inputStream.read(outputByteArray)
        if(bytesActuallyRead + (if(peeked != null) 1 else 0) != numBytesToRead) {
            System.err.println("unable to read requested number of bytes $numBytesToRead from input stream: " +
                    "not enough bytes left in input stream, only read $bytesActuallyRead bytes")
        }
        //return the read araray, plus the peeked byte stuck on the front (if it's not null)
        return if(peeked != null) {
            peekedByte = null
            byteArrayOf(peeked, *outputByteArray)
        }else {
            outputByteArray
        }
    }

    fun peekAtNextByte():Byte {
        val peekyByter = readOneByte()
        peekedByte = peekyByter
        return peekyByter
    }

    fun readOneByte():Byte {
        return readBytes(1)[0]
    }
}
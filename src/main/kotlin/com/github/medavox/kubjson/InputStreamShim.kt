package com.github.medavox.kubjson

import java.io.InputStream

/**Provides 1. a more convenient API for reading [ByteArray]s from an [InputStream], and
 * 2. the ability to peek at the next byte in the input, without removing it from the next read() call.
 * @constructor Construct a new instance
 * @param inputStream */
class InputStreamShim(private val inputStream: InputStream) {
    private var peekedByte:Byte? = null
    //todo: keep track of bytes read In This Class, instead of in the Reader
    var bytesReadSoFar:Long = 0L
        private set
    @Throws(InsufficientBytesReadException::class)
    fun readBytes(numBytesToRead:Int):ByteArray {
        val peeked = peekedByte
        val outputByteArray = ByteArray(if(peeked != null) numBytesToRead -1 else numBytesToRead)
        val bytesActuallyRead = inputStream.read(outputByteArray)
        val bytesReadIncludingPeeked = bytesActuallyRead + (if(peeked != null) 1 else 0)
        bytesReadSoFar += bytesReadIncludingPeeked
        if(bytesReadIncludingPeeked != numBytesToRead) {
            System.err.println("unable to read requested number of bytes $numBytesToRead from input stream: " +
                    "not enough bytes left in input stream, only read $bytesActuallyRead bytes")
            throw InsufficientBytesReadException(numBytesToRead, bytesActuallyRead, bytesReadSoFar)
        }
        //return the read array, plus the peeked byte stuck on the front (if it's not null)
        return if(peeked != null) {
            peekedByte = null//reset the instance variable, because we've now used its value
            byteArrayOf(peeked, *outputByteArray)
        }else {
            outputByteArray
        }
    }

    /**Get the next byte from the underlying inputstream, without removing it from the output of the next read call.
     *
     * Since peeking doesn't count as reading the byte,
     *if multiple consecutive calls to peek() are made without any calls to a read function in between,
     * they all return the same byte.
     *
     * @return the next byte in the input stream*/
    fun peekNextByte():Byte {
        val peekyBefore = peekedByte
        if(peekyBefore == null) {
            val peekyByter = readOneByte()
            peekedByte = peekyByter
            return peekyByter
        }else {
            return peekyBefore
        }
    }

    fun readOneByte():Byte {
        return readBytes(1)[0]
    }
}
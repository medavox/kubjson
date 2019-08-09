package com.github.medavox.kubjson

import java.io.InputStream

/**Provides 1. a more convenient API for reading [ByteArray]s from an [InputStream], and
 * 2. the ability to peek at the next byte in the input,
 * without excluding it from the returned ByteArray of the next read() call.
 * @constructor Construct a new instance
 * @param inputStream */
class InputStreamShim(private val inputStream: InputStream) {
    private var peekedByte:Byte? = null
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
    // peeking by calling readOneByte still actually adds that byte to the bytesReadSoFar;
    //so just do the read manually
    fun peekNextByte():Byte {
        val peekyBefore = peekedByte
        return if(peekyBefore == null) {
            val oneByte = ByteArray(1)
            inputStream.read(oneByte)
            val peekyByter = oneByte[0]
            peekedByte = peekyByter
            peekyByter
        }else {
            peekyBefore
        }
    }

    /**Convenience method which returns a single byte.*/
    fun readOneByte():Byte {
        return readBytes(1)[0]
    }
}
package com.github.medavox.kubjson

/**Thrown when fewer bytes than were requested could be read from the [InputStream]*/
class InsufficientBytesReadException(bytesRequested:Int, bytesActuallyRead:Int)
    :Exception("unable to read requested number of bytes $bytesRequested from input stream: " +
        "not enough bytes left in input stream, only read $bytesActuallyRead bytes")
package com.github.medavox.kubjson

class InsufficientBytesReadException(bytesRequested:Int, bytesActuallyRead:Int)
    :Exception("unable to read requested number of bytes $bytesRequested from input stream: " +
        "not enough bytes left in input stream, only read $bytesActuallyRead bytes")
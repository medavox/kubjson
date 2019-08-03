package com.github.medavox.kubjson

/**Thrown when there is an error in the UBJSON being parsed.*/
class UbjsonParseException(msg:String, val errorIndex:Long) : Exception("$msg ; at input byte index $errorIndex")
package com.github.medavox.kubjson

//Type 	Size 	Marker 	Length 	Data Payload
enum class ValueTypes(val size:Short, val marker:Char, hasLength:Boolean, hasPayload:Boolean) {
    NULL(1, 'Z', false, false),
    NO_OP(1, 'N', false, false),
    TRUE(1, 'T', false, false),
    FALSE(1, 'F', false, false),
    INT8(2, 'i', false, true),
    UINT8(2, 'U', false, true),
    INT16(3, 'I', false, true),
    INT32(5, 'l', false, true),
    INT64(9, 'L', false, true),
    FLOAT32(5, 'd', false, true),
    FLOAT64(9, 'D', false, true),
    CHAR(2,'C', 	 false,true),
    HIGH_PRECISION_NUMBER(-1, /*1 byte + int num val + string byte len*/ 	 'H',true, true/*if non-empty*/),
    STRING(-1, /*1-byte + int num val + string byte len*/ 'S', true, true /*if non-empty*/),

}
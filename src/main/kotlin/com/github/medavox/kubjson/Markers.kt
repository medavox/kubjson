package com.github.medavox.kubjson

enum class Markers(val marker:Char) {
    NULL_TYPE('Z'),
    NO_OP_TYPE('N'),
    TRUE_TYPE('T'),
    FALSE_TYPE('F'),
    INT8_TYPE('i'),
    UINT8_TYPE('U'),
    INT16_TYPE('I'),
    INT32_TYPE('l'),
    INT64_TYPE('L'),
    FLOAT32_TYPE('d'),
    FLOAT64_TYPE('D'),
    CHAR_TYPE('C'),
    HIGH_PRECISION_NUMBER_TYPE('H'),
    STRING_TYPE('S'),
    ARRAY_START('['),
    ARRAY_END(']'),
    OBJECT_START('{'),
    OBJECT_END('}'),
    HOMOGENEOUS_CONTAINER_TYPE('$'),
    CONTAINER_LENGTH('#'),
}

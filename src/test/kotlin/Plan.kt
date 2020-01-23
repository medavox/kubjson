/*Each type conversion should have a pair: the Java version,
and the expected byte array it converts to/from

read function tests will be fed the byte array,
    and the output will be expected to equal the java version.
write function tests will be fed the java version,
    and the output will be expected to equal the byte array.

failing writing these by hand, codec tests would be a start

the following should be tested:
each value type
one homogeneous array for each value type
a single heterogeneous array containing every value type
one homogeneous object for each value type
one heterogeneous object containing every value type
an array of objects
an object containing arrays

maybe:
a heterogeneous array containing every value type, plus arrays and objects
a heterogeneous object containing every value type, plus arrays and objects

deeply nested object/array combos (fuzzy testing?)
can't do fuzzy testing on procedurally-generated data,
would have to be codec tests
*/
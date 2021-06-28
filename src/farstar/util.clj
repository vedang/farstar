(ns farstar.util
  (:require [byte-streams :as bs])
  (:import java.nio.ByteBuffer))

(def byte-array-class (class (byte-array 0)))

(bs/def-conversion [java.lang.Short byte-array-class]
  [short-int]
  (.. (ByteBuffer/allocate 2)
      (putShort short-int)
      array))

(bs/def-conversion [byte-array-class java.lang.Short]
  [short-int-ba]
  (.. (ByteBuffer/wrap short-int-ba)
      (getShort)))

(bs/def-conversion [java.lang.Integer byte-array-class]
  [integer]
  (.. (ByteBuffer/allocate 4)
      (putInt integer)
      array))

(bs/def-conversion [byte-array-class java.lang.Integer]
  [integer-ba]
  (.. (ByteBuffer/wrap integer-ba)
      (getInt)))

(bs/def-conversion [java.lang.Long byte-array-class]
  [long-int]
  (.. (ByteBuffer/allocate 8)
      (putLong long-int)
      array))

(bs/def-conversion [byte-array-class java.lang.Long]
  [long-int-ba]
  (.. (ByteBuffer/wrap long-int-ba)
      (getLong)))

(bs/def-conversion [nil java.lang.Long]
  [_]
  nil)

(bs/def-conversion [nil java.lang.Integer]
  [_]
  nil)

(bs/def-conversion [nil java.lang.Short]
  [_]
  nil)

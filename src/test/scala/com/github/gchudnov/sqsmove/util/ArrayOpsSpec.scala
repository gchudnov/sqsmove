package com.github.gchudnov.sqsmove.util

import zio.test.Assertion.*
import zio.test.*
import zio.*
import com.github.gchudnov.sqsmove.util.ArrayOps.*

object ArrayOpsSpec extends DefaultRunnableSpec:
  def spec: ZSpec[Environment, Failure] = suite("ArrayOps")(
    test("bytes can be encoded to base64") {
      val input    = "ABC"
      val expected = "QUJD"

      val actual = bytesToBase64(input.getBytes)
      assert(actual)(equalTo(expected))
    },
    test("base64 string can be decoded") {
      val input    = "QUJD"
      val expected = "ABC"

      val actual = base64ToBytes(input).map(new String(_))
      assert(actual)(equalTo(Right(expected)))
    },
    test("when an invalid base64 string is decoded, return an error") {
      val input    = "1"
      val expected = "ABC"

      val actual = base64ToBytes(input)
      assert(actual.isLeft)(equalTo(true))
    }
  )

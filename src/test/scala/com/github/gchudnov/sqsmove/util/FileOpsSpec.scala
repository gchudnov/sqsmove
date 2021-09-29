package com.github.gchudnov.sqsmove.util

import zio.test.Assertion.*
import zio.test.*
import zio.*
import com.github.gchudnov.sqsmove.util.FileOps.*
import java.io.File

object FileOpsSpec extends DefaultRunnableSpec:
  def spec: ZSpec[Environment, Failure] = suite("FileOps")(
    test("file extension can be replaced if extension exists") {
      val newExt   = "meta"
      val input    = new File("/path/to/file.txt")
      val expected = new File(s"/path/to/file.${newExt}")

      val actual = replaceExtension(input, newExt)
      assert(actual)(equalTo(expected))
    },
    test("file extension can be replaced if no extension is present") {
      val newExt   = "meta"
      val input    = new File("/path/to/file")
      val expected = new File(s"/path/to/file.${newExt}")

      val actual = replaceExtension(input, newExt)
      assert(actual)(equalTo(expected))
    },
    test("file can be saved to a directory") {
      // TODO: implement it!
      ???
      // val input    = "QUJD"
      // val expected = "ABC"

      // val actual = base64ToBytes(input).map(new String(_))
      // assert(actual)(equalTo(Right(expected)))
    },
    // test("when an invalid base64 string is decoded, return an error") {
    //   val input    = "1"
    //   val expected = "ABC"

    //   val actual = base64ToBytes(input)
    //   assert(actual.isLeft)(equalTo(true))
    // }
  )

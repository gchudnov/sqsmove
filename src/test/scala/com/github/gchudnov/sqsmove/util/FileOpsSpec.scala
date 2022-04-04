package com.github.gchudnov.sqsmove.util

import zio.test.Assertion.*
import zio.test.*
import zio.*
import com.github.gchudnov.sqsmove.util.FileOps.*
import com.github.gchudnov.sqsmove.util.DirOps
import java.io.File

object FileOpsSpec extends ZIOSpecDefault:
  def spec: ZSpec[TestEnvironment with Scope, Any] =
    suite("FileOps")(
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
      test("file can be saved to a directory and read after that") {
        val fn   = "save-file-test"
        val data = "  123  \n  456\t"

        val actual = for
          d    <- DirOps.newTmpDir("file-ops-test")
          f     = new File(d, fn)
          _    <- saveString(f, data)
          text <- readAll(f)
        yield text

        val expected = data

        assert(actual)(equalTo(Right(expected)))
      }
    )

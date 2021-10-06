package com.github.gchudnov.sqsmove.util

import zio.test.Assertion.*
import zio.test.*
import zio.*
import com.github.gchudnov.sqsmove.util.DirOps.*
import com.github.gchudnov.sqsmove.util.FileOps.*
import java.io.File

object DirOpsSpec extends DefaultRunnableSpec:
  def spec: ZSpec[Environment, Failure] = suite("DirOps")(
    test("files can be listed using a predicate") {
      val nf1 = "f1.txt"
      val nf2 = "f2.dat"
      val nf3 = "f3.txt"

      val actual = for
        d1 <- newTmpDir("list-files")
        d2 <- newDir(new File(d1, "subdir").toPath)
        _  <- saveString(new File(d2, nf1), "")
        _  <- saveString(new File(d2, nf2), "")
        _  <- saveString(new File(d2, nf3), "")
        fs <- listFilesBy(d2, it => it.getName.endsWith("txt")).map(_.map(_.getName))
      yield fs.sorted

      val expected = List(nf1, nf3).sorted

      assert(actual)(equalTo(Right(expected)))
    }
  )

package com.github.gchudnov.sqsmove.util

import java.io.File
import java.nio.file.{ Files, Path }
import scala.util.Using
import scala.util.control.Exception.*

object DirOps:

  def newDir(dir: Path): Either[Throwable, File] =
    allCatch.either {
      Files.createDirectory(dir).toFile
    }

  def newTmpDir(prefix: String): Either[Throwable, File] =
    allCatch.either {
      Files.createTempDirectory(prefix).toFile
    }

  def listFilesBy(dir: File, predicate: (File) => Boolean): Either[Throwable, List[File]] =
    for
      _       <- Either.cond(dir.isDirectory, dir, new RuntimeException(s"${dir} is not a directory"))
      files   <- allCatch.either(dir.listFiles).flatMap(Option(_).toRight(new RuntimeException(s"${dir} does not denote a directory")))
      filtered = files.filter(it => it.isFile && predicate(it)).toList
    yield filtered

  def listFilesByExtension(dir: File, extensions: List[String]): Either[Throwable, List[File]] =
    listFilesBy(dir, file => extensions.exists(file.getName.endsWith(_)))

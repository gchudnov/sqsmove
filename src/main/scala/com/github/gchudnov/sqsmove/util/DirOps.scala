package com.github.gchudnov.sqsmove.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

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
    allCatch.either {
      dir.listFiles
        .filter(it => it.isFile && predicate(it))
        .toList
    }

  def listFilesByExtension(dir: File, extensions: List[String]): Either[Throwable, List[File]] =
    listFilesBy(dir, file => extensions.exists(file.getName.endsWith(_)))

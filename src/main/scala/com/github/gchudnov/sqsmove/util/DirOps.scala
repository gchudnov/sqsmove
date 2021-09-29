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

  def listFilesBy(dir: File, predicate: (File) => Boolean): Either[Throwable, List[File]] =
    allCatch.either {
      dir.listFiles
        .filter(_.isFile)
        .filter(predicate)
        .toList
    }

  def listFilesByExtension(dir: File, extensions: List[String]): Either[Throwable, List[File]] =
    listFilesBy(dir, file => extensions.exists(file.getName.endsWith(_)))
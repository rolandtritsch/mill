package mill.init

import coursier.LocalRepositories
import coursier.core.Repository
import coursier.maven.MavenRepository
import mill.api.{Loose, PathRef, Result}
import mill.main.buildgen.BuildGenUtil
import mill.scalalib.scalafmt.ScalafmtWorkerModule
import mill.util.{Jvm, Util}
import mill.{Command, T, Task, TaskModule}

import scala.util.control.NoStackTrace

@mill.api.experimental
trait BuildGenModule extends TaskModule {

  def defaultCommandName(): String = "init"

  def buildGenClasspath: T[Loose.Agg[PathRef]]

  def buildGenMainClass: T[String]

  def buildGenScalafmtConfig: T[PathRef] = PathRef(BuildGenUtil.scalafmtConfigFile)

  def init(args: String*): Command[Unit] = Task.Command {
    val root = millSourcePath

    val mainClass = buildGenMainClass()
    val classPath = buildGenClasspath().map(_.path)
    val exit = Jvm.callSubprocess(
      mainClass = mainClass,
      classPath = classPath,
      mainArgs = args,
      workingDir = root
    ).exitCode

    if (exit == 0) {
      val files = BuildGenUtil.buildFiles(root).map(PathRef(_)).toSeq
      val config = buildGenScalafmtConfig()
      T.log.info("formatting Mill build files")
      ScalafmtWorkerModule.worker().reformat(files, config)

      T.log.info("init completed, run \"mill resolve _\" to list available tasks")
    } else {
      throw BuildGenException(s"$mainClass exit($exit)")
    }
  }
}
@mill.api.experimental
object BuildGenModule {

  def millModule(artifact: String): Result[Loose.Agg[PathRef]] =
    Util.millProjectModule(artifact, millRepositories)

  def millRepositories: Seq[Repository] = Seq(
    LocalRepositories.ivy2Local,
    MavenRepository("https://repo1.maven.org/maven2"),
    MavenRepository("https://oss.sonatype.org/content/repositories/releases")
  )
}

@mill.api.experimental
case class BuildGenException(message: String) extends Exception(message) with NoStackTrace

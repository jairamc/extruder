package extruder.examples

import cats.Eval
import cats.data.{NonEmptyList, Reader}
import org.zalando.grafter.GenericReader._
import org.zalando.grafter._
import extruder.core.{SystemPropertiesResolvers, ValidationFailure}
import extruder.resolution._

case class ApplicationConfig(http: HttpConfig, db: DbConfig)

case class DbConfig(url: String)

object DbConfig {
  implicit def reader: Reader[ApplicationConfig, DbConfig] = Reader(_.db)
}

case class Application(httpServer: HttpServer, db: Database)

object Application {
  implicit def reader: Reader[ApplicationConfig, Application] = genericReader
}

case class HttpServer(config: HttpConfig)

object HttpServer {
  implicit def reader: Reader[ApplicationConfig, HttpServer] = HttpConfig.reader.map(HttpServer.apply)
}

case class HttpConfig(host: String, port: Int)

object HttpConfig {
  implicit def reader: Reader[ApplicationConfig, HttpConfig] = Reader(_.http)
}

case class Database(dbConfig: DbConfig) extends Start {
  def start: Eval[StartResult] = StartResult.eval("postgres")(dbConfig.url)
}

object Database {
  implicit def reader: Reader[ApplicationConfig, Database] = genericReader
}


object Grafter extends App {
  System.setProperty("applicationconfig.http.httpconfig.host", "localhost")
  System.setProperty("applicationconfig.http.httpconfig.port", "8080")
  System.setProperty("applicationconfig.db.dbconfig.url", "jdbc:localhost/database")

  def convertToStartResult(failures: NonEmptyList[ValidationFailure]): Eval[List[StartResult]] =
    Eval.later(failures.toList.map(failure =>
      failure.exception.fold[StartResult](StartFailure(failure.message))(ex => StartError(failure.message, ex))
    ))

  val start: Eval[List[StartResult]] = resolve[ApplicationConfig, SystemPropertiesResolvers](SystemPropertiesResolvers)
    .fold(convertToStartResult, app => Rewriter.start(GenericReader[ApplicationConfig, Application].run(app)))

  println(start.value)
}


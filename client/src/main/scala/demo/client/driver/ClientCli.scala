package demo.client.driver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.{fromAnyRef => configValue}
import demo.client.HttpClient
import demo.core.Util

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by hollinwilkins on 3/29/16.
  */
object ClientCli extends App {
  val parser = new scopt.OptionParser[Config]("client") {
    head("Demo Client", "1.0")
    help("help") text "prints this usage text"
    opt[String]('h', "host") text "Demo server host (default, http://localhost:8080)" action {
      (host, config) => config.withValue("client.host", configValue(host))
    }
    cmd("create-artist") action {
      (_, config) => config.withValue("command.className", configValue("demo.client.driver.CreateArtist"))
    } text "Create an artist on the server" children(
      opt[String]("name") required() text "<artist name>" action {
        (name, config) => config.withValue("artist.name", configValue(name))
      }, opt[String]("genre") required() text "<artist genre>" action {
      (genre, config) => config.withValue("artist.genre", configValue(genre))
    })
    cmd("create-song") action {
      (_, config) => config.withValue("command.className", configValue("demo.client.driver.CreateSong"))
    } text "Create a song for an artist" children(
      opt[String]("artist") required() text "<artist name or slug>" action {
        (artist, config) => config.withValue("song.artistSlug", configValue(Util.slug(artist)))
      }, opt[String]("name") required() text "<song name>" action {
      (name, config) => config.withValue("song.name", configValue(name))
    }, opt[Int]("duration") required() text "<duration in seconds>" action {
      (duration, config) => config.withValue("song.duration", configValue(duration))
    })
    cmd("list-songs") action {
      (_, config) => config.withValue("command.className", configValue("demo.client.driver.ListSongs"))
    } text "List songs for an artist" children(
      opt[String]("artist") required() text "<artist name of slug>" action {
        (artist, config) => config.withValue("artistSlug", configValue(Util.slug(artist)))
      })
  }

  parser.parse(args, ConfigFactory.defaultApplication()) match {
    case Some(config) =>
      implicit val system = ActorSystem("create-artist")
      implicit val materializer = ActorMaterializer()
      implicit val ec = system.dispatcher

      val client = HttpClient(config.getString("client.host"))

      Class.forName(config.getString("command.className"))
        .newInstance()
        .asInstanceOf[Command]
        .execute(client, config)

      val terminator = Http().shutdownAllConnectionPools().flatMap(_ => system.terminate())
      Await.result(terminator, 10.seconds)
    case None => parser.showUsageAsError()
  }
}

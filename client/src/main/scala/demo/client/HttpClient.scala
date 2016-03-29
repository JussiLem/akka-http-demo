package demo.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.{FromResponseUnmarshaller, Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.{BidiFlow, Flow}
import demo.client.HttpClient.FlowType
import demo.core.api.{ReadArtistResponse, _}
import spray.json._
import demo.core.serialization.CoreJsonSupport._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Created by hollinwilkins on 3/28/16.
  */
object HttpClient {
  type FlowType = Flow[(HttpRequest, Any), (Try[HttpResponse], Any), Any]

  def apply(host: String, port: Int)
           (implicit ec: ExecutionContext,
            materializer: Materializer,
            system: ActorSystem): HttpClient = {
    val flow = Http().cachedHostConnectionPool[Any](host, port)
    HttpClient(flow)(ec, materializer, system)
  }
}

case class HttpClient(flow: FlowType)
                     (override implicit val ec: ExecutionContext,
                      override implicit val materializer: Materializer,
                      implicit val system: ActorSystem) extends Client {
  override val readArtistFlow: Flow[ReadArtistRequest, Future[ReadArtistResponse], Any] = {
    val outbound = Flow[ReadArtistRequest].map {
      request =>
        val httpRequest = HttpRequest(uri = s"/artists/${request.uid}",
          method = HttpMethods.GET)
        (httpRequest, Unit)
    }

    BidiFlow.fromFlows(outbound, inbound[ReadArtistResponse]).join(flow)
  }

  override val createArtistFlow: Flow[CreateArtistRequest, Future[CreateArtistResponse], Any] = {
    val outbound = Flow[CreateArtistRequest].map {
      request =>
        val httpRequest = HttpRequest(uri = "/artists",
          method = HttpMethods.POST,
          entity = request.artist.toJson.prettyPrint)
        (httpRequest, Unit)
    }

    BidiFlow.fromFlows(outbound, inbound[CreateArtistResponse]).join(flow)
  }

  override val createSongFlow: Flow[CreateSongRequest, Future[CreateSongResponse], Any] = {
    val outbound = Flow[CreateSongRequest].map {
      request =>
        val httpRequest = HttpRequest(uri = s"/artists/${request.song.artistUid}/songs",
          method = HttpMethods.POST,
          entity = request.song.toJson.prettyPrint)
        (httpRequest, Unit)
    }

    BidiFlow.fromFlows(outbound, inbound[CreateSongResponse]).join(flow)
  }

  private def inbound[Response]
  (implicit um: FromResponseUnmarshaller[Response]): Flow[(Try[HttpResponse], Any), Future[Response], Any] = {
    Flow[(Try[HttpResponse], Any)].map {
      case (tryResponse, _) =>
        Future.fromTry(tryResponse).flatMap {
          response => Unmarshal(response).to[Response]
        }
    }
  }
}

/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.http

import play.api.libs.json.{Json, Writes}
import play.api.http.HttpVerbs.{POST => POST_VERB}
import uk.gov.hmrc.play.audit.http.{HeaderCarrier, HttpAuditing}
import uk.gov.hmrc.play.http.logging.{MdcLoggingExecutionContext, ConnectionTracing}
import MdcLoggingExecutionContext._

import scala.concurrent.Future

trait HttpPost extends HttpVerb with ConnectionTracing with HttpAuditing {

  protected def doPost[A](url: String, body: A, headers: Seq[(String,String)])(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse]

  protected def doPostString(url: String, body: String, headers: Seq[(String,String)])(implicit hc: HeaderCarrier): Future[HttpResponse]
  
  protected def doEmptyPost[A](url: String)(implicit hc: HeaderCarrier): Future[HttpResponse]

  protected def doFormPost(url: String, body: Map[String, Seq[String]])(implicit hc: HeaderCarrier): Future[HttpResponse]

  private def defaultHandler(implicit hc: HeaderCarrier): ProcessingFunction =
    (responseF: Future[HttpResponse], url: String) => responseF.map { response => handleResponse(POST_VERB, url)(response)}

  def POST[A](url: String, body: A)(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = {
    POST(url, body, defaultHandler)
  }

  def POST[A](url: String, body: A, responseHandler: ProcessingFunction, headers: Seq[(String,String)] = Seq.empty)(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = {
    withTracing(POST_VERB, url) {
      val httpResponse = doPost(url, body, headers)
      auditRequestWithResponseF(url, POST_VERB, Option(Json.stringify(rds.writes(body))), httpResponse)
      responseHandler(mapErrors(POST_VERB, url, httpResponse), url)
    }
  }

  def POSTString(url: String, body: String, headers: Seq[(String,String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    POSTString(url = url, body = body, responseHandler = defaultHandler, headers = headers)
  }

  def POSTString(url: String, body: String, responseHandler: ProcessingFunction, headers: Seq[(String,String)] = Seq.empty, auditRequestBody: Boolean = true, auditResponseBody: Boolean = true)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    withTracing(POST_VERB, url) {
      val httpResponse = doPostString(url, body, headers)
      auditRequestWithResponseF(url, POST_VERB, Option(body), httpResponse)
      responseHandler(mapErrors(POST_VERB, url, httpResponse), url)
    }
  }

  def POSTForm(url: String, body: Map[String, Seq[String]])(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    POSTForm(url, body, defaultHandler)
  }

  def POSTForm(url: String, body: Map[String, Seq[String]], responseHandler: ProcessingFunction)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    withTracing(POST_VERB, url) {
      val httpResponse = doFormPost(url, body)
      auditRequestWithResponseF(url, POST_VERB, Option(body), httpResponse)
      responseHandler(mapErrors(POST_VERB, url, httpResponse), url)
    }
  }

  def POSTEmpty(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    withTracing(POST_VERB, url) {
      val httpResponse = doEmptyPost(url)
      auditRequestWithResponseF(url, POST_VERB, None, httpResponse)
      defaultHandler(hc)(mapErrors(POST_VERB, url, httpResponse), url)
    }
  }

}


/*
 * Copyright 2024 HM Revenue & Customs
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

package auth

import play.api.libs.json.{JsString, Reads, Writes}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Crypted, Decrypter, Encrypter, PlainText}

import javax.inject.Inject

class CryptoSCRSImpl @Inject()(config: Configuration) extends CryptoSCRS {
  override lazy val crypto: Encrypter with Decrypter = new ApplicationCrypto(config.underlying).JsonCrypto
}

trait CryptoSCRS extends Logging {
  def crypto: Encrypter with Decrypter

  val rds: Reads[String] = Reads[String](js =>
    js.validate[String].map { encryptedUtr =>
      val str = crypto.decrypt(Crypted.fromBase64(encryptedUtr)).value
      logger.info(s"[CryptoSCRS] decrypted string to length - ${str.length}")
      str
    }
  )
  val wts: Writes[String] = Writes[String] { utr =>
    val encryptedUtr = new String(crypto.encrypt(PlainText(utr)).toBase64)
    JsString(encryptedUtr)
  }
}
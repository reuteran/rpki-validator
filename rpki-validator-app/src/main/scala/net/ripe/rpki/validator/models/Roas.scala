/**
 * The BSD License
 *
 * Copyright (c) 2010, 2011 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator
package models

import scala.collection._
import scala.collection.JavaConverters._
import java.io.File
import java.net.URI
import grizzled.slf4j.Logger
import net.ripe.certification.validator.fetchers._
import net.ripe.certification.validator.util._
import net.ripe.certification.validator.commands.TopDownWalker
import net.ripe.commons.certification.CertificateRepositoryObject
import net.ripe.commons.certification.cms.roa.RoaCms
import net.ripe.commons.certification.validation.ValidationResult
import net.ripe.commons.certification.validation.objectvalidators.CertificateRepositoryObjectValidationContext
import scalaz.concurrent.Promise
import net.ripe.ipresource.UniqueIpResource
import net.ripe.ipresource.Asn
import org.apache.commons.lang.builder.HashCodeBuilder
import scala.collection.mutable.HashSet

case class ValidatedRoa(val roa: RoaCms, val uri: URI, val trustAnchor: TrustAnchorLocator)

class Roas(val all: Map[String, Option[Seq[ValidatedRoa]]]) {

  val logger = Logger[this.type]

  var msg: String = "Creating new Roas object with tals: "
  all.keys.foreach {
    key =>
      msg = msg + key + " with number of validated Roas: "
      all.get(key).get match {
        case None => msg = msg + "none"
        case Some(roas) => msg = msg + roas.size
      }
      msg = msg + "\n"
  }

  logger.trace(msg)

  def update(tal: TrustAnchorLocator, validatedRoas: Seq[ValidatedRoa]) = {
    new Roas(all.updated(tal.getCaName(), Some(validatedRoas)))
  }

  def getUniqueValidatedPrefixes(): Array[ValidatedPrefix] = {

    var uniquePrefixSet = new HashSet[ValidatedPrefix]

    all.values.foreach {
      _ match {
        case None =>
        case Some(sequence) => {
          sequence.foreach {
            validatedRoa =>
              getValidatedPrefixes(validatedRoa).foreach {
                validatedPrefix => uniquePrefixSet.add(validatedPrefix)
              }
          }
        }
      }
    }
    uniquePrefixSet.toArray
  }
  
  
  private def getValidatedPrefixes(validatedRoa: ValidatedRoa): List[ValidatedPrefix] = {

    var prefixes = List[ValidatedPrefix]()

    var asn = validatedRoa.roa.getAsn()
    validatedRoa.roa.getPrefixes().asScala.foreach {
      prefix =>
        {
          var maxLength = prefix.getEffectiveMaximumLength()
          var ip = prefix.getPrefix().getStart()
          var length = prefix.getPrefix().getPrefixLength()

          var validatedPrefix = ValidatedPrefix(asn, ip, length, maxLength)

          prefixes = prefixes ++ List(validatedPrefix)

        }
    }
    prefixes
  }

}

case class ValidatedPrefix(asn: Asn, ipaddress: UniqueIpResource, length: Int, maxLength: Int) {

  override def hashCode(): Int = {
    HashCodeBuilder.reflectionHashCode(this)
  }

}

object Roas {
  private val logger = Logger[this.type]

  def apply(trustAnchors: TrustAnchors): Roas = {
    new Roas(trustAnchors.all.map(ta => ta.locator.getCaName() -> None)(breakOut))
  }

  def fetchObjects(trustAnchor: TrustAnchorLocator, certificate: CertificateRepositoryObjectValidationContext): Seq[ValidatedRoa] = {
    import net.ripe.commons.certification.rsync.Rsync

    val rsyncFetcher = new RsyncCertificateRepositoryObjectFetcher(new Rsync(), new UriToFileMapper(new File("tmp/cache/" + trustAnchor.getFile().getName())));
    val validatingFetcher = new ValidatingCertificateRepositoryObjectFetcher(rsyncFetcher);
    val notifyingFetcher = new NotifyingCertificateRepositoryObjectFetcher(validatingFetcher);
    val cachingFetcher = new CachingCertificateRepositoryObjectFetcher(notifyingFetcher);
    validatingFetcher.setOuterMostDecorator(cachingFetcher);

    val roas = collection.mutable.Buffer.empty[ValidatedRoa]
    notifyingFetcher.addCallback(new RoaCollector(trustAnchor, roas))

    trustAnchor.getPrefetchUris().asScala.foreach { prefetchUri =>
      logger.info("Prefetching '" + prefetchUri + "'")
      val validationResult = new ValidationResult();
      validationResult.setLocation(prefetchUri);
      cachingFetcher.prefetch(prefetchUri, validationResult);
    }

    val walker = new TopDownWalker(cachingFetcher)
    walker.addTrustAnchor(certificate)
    logger.info("Started validating " + trustAnchor.getCaName())
    walker.execute()
    logger.info("Finished validating " + trustAnchor.getCaName() + ", fetched " + roas.size + " valid ROAs")

    roas.toIndexedSeq
  }

  private class RoaCollector(trustAnchor: TrustAnchorLocator, roas: collection.mutable.Buffer[ValidatedRoa]) extends NotifyingCertificateRepositoryObjectFetcher.FetchNotificationCallback {
    override def afterPrefetchFailure(uri: URI, result: ValidationResult) {
      logger.warn("Failed to prefetch '" + uri + "'")
    }

    override def afterPrefetchSuccess(uri: URI, result: ValidationResult) {
      logger.debug("Prefetched '" + uri + "'")
    }

    override def afterFetchFailure(uri: URI, result: ValidationResult) {
      logger.warn("Failed to validate '" + uri + "': " + result.getFailuresForCurrentLocation().asScala.map(_.toString()).mkString(", "))
    }

    override def afterFetchSuccess(uri: URI, obj: CertificateRepositoryObject, result: ValidationResult) {
      obj match {
        case roa: RoaCms =>
          logger.debug("Fetched ROA '" + uri + "'")
          roas += new ValidatedRoa(roa, uri, trustAnchor)
        case _ =>
          logger.debug("Fetched '" + uri + "'")
      }
    }
  }
}
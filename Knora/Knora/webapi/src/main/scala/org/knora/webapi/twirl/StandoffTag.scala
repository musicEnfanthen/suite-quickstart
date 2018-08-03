/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.twirl

import java.util.UUID

import org.knora.webapi.IRI
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses

/**
  * The following case classes represent standoff classes and attributes
  * that are about to be added to the triplestore.
  *
  */


/*
    trait for standoff tag attribute
 */
trait StandoffTagAttributeV2 {

    def standoffPropertyIri: IRI

    def stringValue: String

    def rdfValue: String

}

/**
  * Represents a standoff tag attribute of type IRI.
  *
  * @param standoffPropertyIri the IRI of the standoff property
  * @param value               the value of the standoff property.
  */
case class StandoffTagIriAttributeV2(standoffPropertyIri: IRI, value: IRI) extends StandoffTagAttributeV2 {

    def stringValue = value

    def rdfValue = s"<$value>"

}

/**
  * Represents a standoff tag attribute of type URI.
  *
  * @param standoffPropertyIri the IRI of the standoff property
  * @param value               the value of the standoff property.
  */
case class StandoffTagUriAttributeV2(standoffPropertyIri: IRI, value: String) extends StandoffTagAttributeV2 {

    def stringValue = value

    def rdfValue = s""""${stringValue.toString}"^^xsd:anyURI"""

}

/**
  * Represents a standoff tag attribute that refers to another standoff node.
  *
  * @param standoffPropertyIri the IRI of the standoff property
  * @param value               the value of the standoff property.
  */
case class StandoffTagInternalReferenceAttributeV2(standoffPropertyIri: IRI, value: String) extends StandoffTagAttributeV2 {

    def stringValue = value.toString

    def rdfValue = s"<$value>"

}

/**
  * Represents a standoff tag attribute of type string.
  *
  * @param standoffPropertyIri the IRI of the standoff property
  * @param value               the value of the standoff property.
  */
case class StandoffTagStringAttributeV2(standoffPropertyIri: IRI, value: String) extends StandoffTagAttributeV2 {

    def stringValue = value

    def rdfValue = s"""\"\"\"$value\"\"\""""

}

/**
  * Represents a standoff tag attribute of type integer.
  *
  * @param standoffPropertyIri the IRI of the standoff property
  * @param value               the value of the standoff property.
  */
case class StandoffTagIntegerAttributeV2(standoffPropertyIri: IRI, value: Int) extends StandoffTagAttributeV2 {

    def stringValue = value.toString

    def rdfValue = value.toString

}

/**
  * Represents a standoff tag attribute of type decimal.
  *
  * @param standoffPropertyIri the IRI of the standoff property
  * @param value               the value of the standoff property.
  */
case class StandoffTagDecimalAttributeV2(standoffPropertyIri: IRI, value: BigDecimal) extends StandoffTagAttributeV2 {

    def stringValue = value.toString

    def rdfValue = s""""${value.toString}"^^xsd:decimal"""

}

/**
  * Represents a standoff tag attribute of type boolean.
  *
  * @param standoffPropertyIri the IRI of the standoff property
  * @param value               the value of the standoff property.
  */
case class StandoffTagBooleanAttributeV2(standoffPropertyIri: IRI, value: Boolean) extends StandoffTagAttributeV2 {

    def stringValue = value.toString

    def rdfValue = value.toString

}

/**
  * Represents any subclass of a `knora-base:StandoffTag`.
  *
  * @param standoffTagClassIri the IRI of the standoff class to be created.
  * @param dataType            the data type of the standoff class, if any.
  * @param uuid                a [[UUID]] representing this tag and any other tags that
  *                            point to semantically equivalent ranges in other versions of the same text.
  * @param startPosition       the start position of the range of characters marked up with this tag.
  * @param endPosition         the end position of the range of characters marked up with this tag.
  * @param startIndex          the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                            and make it possible to order tags that share the same position.
  * @param endIndex            the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex    the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex      the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes          the attributes attached to this tag.
  */
case class StandoffTagV2(standoffTagClassIri: IRI,
                         dataType: Option[StandoffDataTypeClasses.Value] = None,
                         uuid: String,
                         originalXMLID: Option[String],
                         startPosition: Int,
                         endPosition: Int,
                         startIndex: Int,
                         endIndex: Option[Int] = None,
                         startParentIndex: Option[Int] = None,
                         endParentIndex: Option[Int] = None,
                         attributes: Seq[StandoffTagAttributeV2] = Seq.empty[StandoffTagAttributeV2])




package org.biobank.domain.containers

import java.time.OffsetDateTime
import org.biobank.ValidationKey
import org.biobank.domain._
import org.biobank.domain.centres.CentreId
import play.api.libs.json._
import scalaz.Scalaz._

trait ContainerTypeValidations {
  val NameMinLength: Long = 2L

  case object ContainerSchemaIdInvalid extends ValidationKey

}

/**
 * Describes a container configuration which may hold child containers or specimens. Container types are used
 * to create a representation of a physical container
 */
sealed trait ContainerType
    extends ConcurrencySafeEntity[ContainerTypeId]
    with HasUniqueName
    with HasSlug
    with HasOptionalDescription
    with ContainerValidations {
  import org.biobank.CommonValidations._
  import org.biobank.domain.DomainValidations._

  /**
   * The [[domain.centres.Centre Centre]] that owns and is allowed to modify this
   * [[domain.containers.ContainerType ContainerType]].
   *
   * When equal to [[https://www.scala-lang.org/api/current/scala/None$.html None]] then it is a globally
   * accessible [[domain.containers.ContainerType ContainerType]].
   */
  val centreId: Option[CentreId]

  /**
   * How [[domain.containers.Container Containers]] of this [[domain.containers.ContainerType ContainerType]]
   * are designed and laid out, with labelled positions for children.
   */
  val schemaId: ContainerSchemaId

  /**
   * True if this [[domain.containers.ContainerType ContainerType]] can be used by (but not modified) by other
   * [[domain.centres.Centre Centres]], otherwise false.
   */
  val shared: Boolean

  /**
   * True if this [[domain.containers.ContainerType ContainerType]] can be used to create new [[Container]]s,
   * or false if this [[domain.containers.ContainerType ContainerType]] is to be used only for existing
   * [[domain.containers.Container Containers]].
   */
  val enabled: Boolean

  def withName(name: String): DomainValidation[String] = {
    validateString(name, NameMinLength, InvalidName)
  }

  def withDescription(description: Option[String]): DomainValidation[Option[String]] = {
    validateNonEmptyStringOption(description, InvalidDescription)
  }

  // def withShared(shared: Boolean): DomainValidation[StorageContainerType]

  // def withEnabled(enabled: Boolean): DomainValidation[StorageContainerType]

  override def toString: String =
    s"""|ContainerType:{
        |  id:          $id,
        |  slug:        $slug,
        |  name:        $name,
        |  description: $description,
        |  centreId:    $centreId,
        |  schemaId:    $schemaId,
        |  shared:      $shared
        |}""".stripMargin
}

object ContainerType {

  implicit val containerTypeWrites: Writes[ContainerType] = new Writes[ContainerType] {
    def writes(containerType: ContainerType): JsValue = Json.obj(
      "id"           -> containerType.id,
      "centreId"     -> containerType.centreId,
      "schemaId"     -> containerType.schemaId,
      "version"      -> containerType.version,
      "timeAdded"    -> containerType.timeAdded,
      "timeModified" -> containerType.timeModified,
      "name"         -> containerType.name,
      "description"  -> containerType.description,
      "shared"       -> containerType.shared,
      "status"       -> containerType.getClass.getSimpleName
    )
  }

}

/**
 * When a container type is enabled, it ''can'' be used to create new containers.
 */
final case class StorageContainerType(id:           ContainerTypeId,
                                      centreId:     Option[CentreId],
                                      schemaId:     ContainerSchemaId,
                                      version:      Long,
                                      timeAdded:    OffsetDateTime,
                                      timeModified: Option[OffsetDateTime],
                                      slug: Slug,
                                      name:         String,
                                      description:  Option[String],
                                      shared:       Boolean,
                                      enabled:      Boolean)
    extends ContainerType {

  // override def withName(name: String): DomainValidation[StorageContainerType] = {
  //   super.withName(name) map { _ => copy(version = version + 1, name = name) }
  // }

  // override def withDescription(description:  Option[String]): DomainValidation[StorageContainerType] = {
  //   super.withDescription(description) map { _ =>
  //     copy(version = version + 1, description = description)
  //   }
  // }

  // override def withShared(shared: Boolean): DomainValidation[StorageContainerType] = {
  //   copy(version = version + 1, shared = shared).success
  // }

  // def withEnabled(enabled: Boolean): DomainValidation[StorageContainerType] = {
  //   copy(version = version + 1, enabled = enabled).success
  // }

}

object StorageContainerType extends ContainerValidations {
  import org.biobank.CommonValidations._
  import org.biobank.domain.DomainValidations._

  def create(id:          ContainerTypeId,
             centreId:    Option[CentreId],
             schemaId:    ContainerSchemaId,
             version:     Long,
             name:        String,
             description: Option[String],
             shared:      Boolean,
             enabled:     Boolean): DomainValidation[StorageContainerType] = {
    (validateId(id) |@|
       validateIdOption(centreId, CentreIdRequired) |@|
       validateId(schemaId, ContainerSchemaIdInvalid) |@|
       validateVersion(version) |@|
       validateString(name, NameMinLength, InvalidName) |@|
       validateNonEmptyStringOption(description, InvalidDescription)) { case _ =>
        StorageContainerType(id           = id,
                             centreId     = centreId,
                             schemaId     = schemaId,
                             version      = version,
                             timeAdded    = OffsetDateTime.now,
                             timeModified = None,
                             slug         = Slug(name),
                             name         = name,
                             description  = description,
                             shared       = shared,
                             enabled      = enabled)
    }
  }

}

/**
 * When a container type is disabled, it ''can not'' be used to create new containers.
 */
final case class SpecimenContainerType(id:           ContainerTypeId,
                                       centreId:     Option[CentreId],
                                       schemaId:     ContainerSchemaId,
                                       version:      Long,
                                       timeAdded:    OffsetDateTime,
                                       timeModified: Option[OffsetDateTime],
                                       slug: Slug,
                                       name:         String,
                                       description:  Option[String],
                                       shared:       Boolean,
                                       enabled:      Boolean)
    extends ContainerType

object SpecimenContainerType extends ContainerValidations {
  import org.biobank.CommonValidations._
  import org.biobank.domain.DomainValidations._

  def create(id:           ContainerTypeId,
             centreId:     Option[CentreId],
             schemaId:     ContainerSchemaId,
             version:      Long,
             timeAdded:    OffsetDateTime,
             timeModified: Option[OffsetDateTime],
             name:         String,
             description:  Option[String],
             shared:       Boolean,
             enabled:      Boolean): DomainValidation[SpecimenContainerType] = {
    (validateId(id) |@|
       validateIdOption(centreId, CentreIdRequired) |@|
       validateId(schemaId, ContainerSchemaIdInvalid) |@|
       validateVersion(version) |@|
       validateString(name, NameMinLength, InvalidName) |@|
       validateNonEmptyStringOption(description, InvalidDescription)) { case _ =>
        SpecimenContainerType(id           = id,
                              centreId     = centreId,
                              schemaId     = schemaId,
                              version      = version,
                              timeAdded    = timeAdded,
                              timeModified = timeModified,
                              slug         = Slug(name),
                              name         = name,
                              description  = description,
                              shared       = shared,
                              enabled      = enabled)
    }
  }
}

package org.biobank.controllers

import org.biobank.domain._
import org.biobank.domain.annotations._
import org.biobank.domain.studies.{ EnabledStudy, Study }
import org.biobank.fixtures._
import scala.collection.mutable.ListBuffer
import play.api.libs.json._
import play.api.http.HttpVerbs._
import play.api.http.Status._

trait AnnotationsControllerSharedSpec[T <: ConcurrencySafeEntity[_] with HasAnnotations[_]]
    extends ControllerFixture
    with JsonHelper {
  import org.biobank.AnnotationTestUtils._

  protected val nameGenerator: NameGenerator

  protected def entityName(): String

  protected def createEntity(annotationTypes: Set[AnnotationType],
                             annotations:     Set[Annotation]): T

  protected def entityFromRepository(id: String): DomainValidation[T]

  protected def updateUri(entity: T): String

  /**
   * create pairs of annotation types and annotation of each value type plus a second of type select that
   * allows multiple selections
   *
   * the result is a map where the keys are the annotation types and the values are the corresponding
   * annotations
   */
  protected def createAnnotationsAndTypes() = {
    val options = Seq(nameGenerator.next[String],
                      nameGenerator.next[String],
                      nameGenerator.next[String])
    val multipleSelectType = factory.createAnnotationType(AnnotationValueType.Select, Some(2), options)
    val multipleSelectAnnotation = factory.createAnnotationWithValues(multipleSelectType)

    (AnnotationValueType.values.map { vt =>
       vt match {
         case AnnotationValueType.Select =>
           val annotationType = factory.createAnnotationType(vt, Some(1), options)
           (annotationType, factory.createAnnotationWithValues(annotationType))
         case _ =>
           val annotationType = factory.createAnnotationType(vt, None, Seq.empty)
           (annotationType, factory.createAnnotationWithValues(annotationType))
       }
     }.toList ++
       List((multipleSelectType, multipleSelectAnnotation))).toMap
  }

  def annotationTypeUpdateSharedBehaviour() = {

    it(s"update a ${entityName} with annotations") {
      val annotTypeData = createAnnotationsAndTypes
      val addedAnnotations = ListBuffer.empty[Annotation]

      var entity = createEntity(annotTypeData.keys.toSet, Set.empty)
      entity.annotations must have size 0

      annotTypeData.values.foreach { annotation =>
        val json = makeRequest(POST,
                               updateUri(entity),
                               Json.obj("expectedVersion" -> entity.version) ++
                                 annotationToJson(annotation))

        (json \ "status").as[String] must include ("success")

        addedAnnotations += annotation

        val jsonAnnotations = (json \ "data" \ "annotations").as[List[JsObject]]
        jsonAnnotations must have size addedAnnotations.size.toLong

        jsonAnnotations.foreach { jsonAnnotation =>
          val jsonAnnotationTypeId = (jsonAnnotation \ "annotationTypeId").as[String]
          val foundAnnotation = addedAnnotations.find(x => x.annotationTypeId.id == jsonAnnotationTypeId).value
          compareAnnotation(jsonAnnotation, foundAnnotation)
        }

        entityFromRepository(entity.id.toString).map(entity = _)
      }
    }

    it(s"fail when adding annotation and ${entityName} parent does not have annotation types") {
      val annotation = factory.createAnnotation
      val entity = createEntity(Set.empty, Set(annotation))
      val json = makeRequest(POST,
                             updateUri(entity),
                             NOT_FOUND,
                             Json.obj("expectedVersion" -> entity.version) ++ annotationToJson(annotation))

      (json \ "status").as[String] must include ("error")

      (json \ "message").as[String] must include regex "does not have annotation type"
    }

    it("fail when adding annotation and annotation has invalid annotation type id") {
      val annotationType = factory.createAnnotationType
      val annotation = factory.createAnnotation
        .copy(annotationTypeId = AnnotationTypeId(nameGenerator.next[Annotation]))

      val entity = createEntity(Set(annotationType), Set(annotation))

      val json = makeRequest(POST,
                             updateUri(entity),
                             NOT_FOUND,
                             Json.obj("expectedVersion" -> entity.version) ++
                               annotationToJson(annotation))

      (json \ "status").as[String] must include ("error")

      (json \ "message").as[String] must include regex "does not have annotation type"
    }

    it("fail when adding annotation with an invalid version") {
      val annotation = factory.createAnnotation

      val entity = createEntity(Set.empty, Set(annotation))

      val json = makeRequest(POST,
                             updateUri(entity),
                             BAD_REQUEST,
                             Json.obj("expectedVersion" -> (entity.version + 1)) ++
                               annotationToJson(annotation))

      (json \ "status").as[String] must include ("error")

      (json \ "message").as[String] must include ("expected version doesn't match current version")
    }

  }

  def annotationTypeRemoveSharedBehaviour() = {

    it("remove an annotation") {
      val annotationType = factory.createAnnotationType
      val annotation = factory.createAnnotation
      val entity = createEntity(Set(annotationType), Set(annotation))

      val json = makeRequest(
          DELETE,
          updateUri(entity) + s"/${annotation.annotationTypeId}/${entity.version}")

      (json \ "status").as[String] must include ("success")

      (json \ "data" \ "annotations").as[List[JsObject]] must have size 0
    }

    it("fail when attempting to remove a required annotation") {
      val annotationType = factory.createAnnotationType.copy(required = true)
      val annotation = factory.createAnnotation
      val entity = createEntity(Set(annotationType), Set(annotation))

      val json = makeRequest(
          DELETE,
          updateUri(entity) + s"/${annotation.annotationTypeId}/${entity.version}",
          BAD_REQUEST)

      (json \ "status").as[String] must include ("error")

      (json \ "message").as[String] must include ("annotation is required")
    }
  }

}

trait StudyAnnotationsControllerSharedSpec[T <: ConcurrencySafeEntity[_] with HasAnnotations[_]]
    extends AnnotationsControllerSharedSpec[T] {
  import org.biobank.TestUtils._
  import org.biobank.AnnotationTestUtils._

  protected def getStudy(entity: T): DomainValidation[EnabledStudy]

  protected def createAnnotationType() = {
    val name = nameGenerator.next[AnnotationType]
    AnnotationType(
      id            = AnnotationTypeId(nameGenerator.next[AnnotationType]),
      slug          = Slug(name),
      name          = name,
      description   = None,
      valueType     = AnnotationValueType.Text,
      maxValueCount = None,
      options       = Seq.empty,
      required      = true)
  }

  override def annotationTypeUpdateSharedBehaviour() = {

    super.annotationTypeUpdateSharedBehaviour

    it("fail when adding an annotation on a non enabled study") {
      val annotationType = factory.createAnnotationType
      val annotation = factory.createAnnotation
      val entity = createEntity(Set(annotationType), Set(annotation))

      getStudy(entity) mustSucceed { study =>
        study.disable mustSucceed { disabledStudy =>
          studyRepository.put(disabledStudy)

          updateOnNonEnabledStudy(disabledStudy, entity, annotation)

          disabledStudy.retire mustSucceed { retiredStudy =>
            studyRepository.put(retiredStudy)
            updateOnNonEnabledStudy(retiredStudy, entity, annotation)
          }
        }
      }
    }

    def updateOnNonEnabledStudy(study: Study, entity: T, annotation: Annotation) {
      study must not be an [EnabledStudy]

      val json = makeRequest(POST,
                             updateUri(entity),
                             BAD_REQUEST,
                             Json.obj("expectedVersion" -> entity.version) ++
                               annotationToJson(annotation))

      (json \ "status").as[String] must include ("error")

      (json \ "message").as[String] must include regex("InvalidStatus: study not enabled")

      ()
    }
  }

}

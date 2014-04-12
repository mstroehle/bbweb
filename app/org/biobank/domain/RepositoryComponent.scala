package org.biobank.domain

import org.biobank.domain.study.{
  CollectionEventAnnotationTypeRepositoryComponent,
  CollectionEventAnnotationTypeRepositoryComponentImpl,
  CollectionEventTypeRepositoryComponent,
  CollectionEventTypeRepositoryComponentImpl,
  ParticipantAnnotationTypeRepositoryComponent,
  ParticipantAnnotationTypeRepositoryComponentImpl,
  StudyRepositoryComponent,
  StudyRepositoryComponentImpl,
  SpecimenGroupRepositoryComponent,
  SpecimenGroupRepositoryComponentImpl,
  SpecimenLinkAnnotationTypeRepositoryComponent,
  SpecimenLinkAnnotationTypeRepositoryComponentImpl
}

trait RepositoryComponent
  extends StudyRepositoryComponent
  with SpecimenGroupRepositoryComponent
  with CollectionEventAnnotationTypeRepositoryComponent
  with CollectionEventTypeRepositoryComponent
  with ParticipantAnnotationTypeRepositoryComponent
  with SpecimenLinkAnnotationTypeRepositoryComponent
  with UserRepositoryComponent

trait RepositoryComponentImpl
  extends RepositoryComponent
  with StudyRepositoryComponentImpl
  with SpecimenGroupRepositoryComponentImpl
  with CollectionEventAnnotationTypeRepositoryComponentImpl
  with CollectionEventTypeRepositoryComponentImpl
  with ParticipantAnnotationTypeRepositoryComponentImpl
  with SpecimenLinkAnnotationTypeRepositoryComponentImpl
  with UserRepositoryComponentImpl
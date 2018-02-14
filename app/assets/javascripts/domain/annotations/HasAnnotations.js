/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */

import _ from 'lodash';

/**
 * Mixin for entities that have annotations.
 *
 * Maintains an array of annotations.
 */
/* @ngInject */
function HasAnnotationsFactory($q,
                               biobankApi,
                               ConcurrencySafeEntity,
                               DomainError,
                               Annotation,
                               AnnotationType,
                               annotationFactory) {

  function HasAnnotations() {}

  HasAnnotations.prototype.addAnnotation = function (annotation, url) {
    return ConcurrencySafeEntity.prototype.update.call(this,
                                                       url,
                                                       annotation.getServerAnnotation());
  };

  /**
   * The entity that includes this mixin needs to implement 'asyncCreate'.
   */
  HasAnnotations.prototype.removeAnnotation = function (annotation, url) {
    var found = _.find(this.annotations,  { annotationTypeId: annotation.annotationTypeId });

    if (!found) {
      return $q.reject('annotation with annotation type ID not present: ' + annotation.annotationTypeId);
    }
    return biobankApi.del(url);
  };

  HasAnnotations.prototype.setAnnotationTypes = function (annotationTypes) {
    var differentIds;

    annotationTypes = annotationTypes || [];
    this.annotations = this.annotations || [];

    // make sure the annotations ids match up with the corresponding annotation types
    differentIds = _.difference(_.map(this.annotations, 'annotationTypeId'),
                                _.map(annotationTypes, 'id'));

    if (differentIds.length > 0) {
      throw new DomainError('annotation types not found: ' + differentIds);
    }

    this.annotations = annotationTypes.map((annotationType) => {
      var jsonAnnotationMaybe = _.find(this.annotations, { annotationTypeId: annotationType.id });

      if ((jsonAnnotationMaybe instanceof Annotation) &&
          (jsonAnnotationMaybe.annotationType) &&
          (jsonAnnotationMaybe.annotationType instanceof AnnotationType)) {
        // annotation was already converted to Annotation or sub class
        return jsonAnnotationMaybe;
      }

      // undefined is valid input
      return annotationFactory.create(jsonAnnotationMaybe, annotationType);
    });
  };

  HasAnnotations.prototype.validAnnotations = function (annotations) {
    var result;

    if (_.isUndefined(annotations) || (annotations.length <= 0)) {
      // there are no annotation types, nothing to validate
      return true;
    }
    result = _.find(annotations, function (annot) {
      return !Annotation.isValid(annot);
    });

    return _.isUndefined(result);
  };

  return HasAnnotations;
}

export default ngModule => ngModule.factory('HasAnnotations', HasAnnotationsFactory)

/*
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2018 Canadian BioSample Repository (CBSR)
 */

import _ from 'lodash'

/**
 * Angular factory for collectionEventTypes.
 */
/* @ngInject */
function CollectionEventTypeFactory($q,
                                    $log,
                                    biobankApi,
                                    DomainEntity,
                                    ConcurrencySafeEntity,
                                    CollectionSpecimenDescription,
                                    DomainError,
                                    AnnotationType,
                                    HasCollectionSpecimenDescriptions,
                                    HasAnnotationTypes) {

  /**
   * @classdesc A CollectionEventType defines a classification name, unique to the {@link
   * domain.studies.Study|Study}, to a {@link domain.studies.Participant|Participant} visit. A participant
   * visit is a record of when specimens were collected from a participant at a collection centre.
   *
   * Use this contructor to create a new CollectionEventType to be persited on the server. Use {@link
   * domain.studies.CollectionEventType.create|create()} or {@link
   * domain.studies.CollectionEventType.asyncCreate|asyncCreate()} to create objects returned by the server.
   *
   * @class
   * @memberOf domain.studies
   *
   * @param {Object} collectionEventType the collection event type JSON returned by the server.
   *
   * @param {Study} options.study the study this collection even type belongs to.
   */
  function CollectionEventType(obj = {}, options = {}) {
    /**
     * The ID of the {@link domain.studies.Study|Study} this collection event type belongs to.
     *
     * @name domain.studies.CollectionEventType#studyId
     * @type {string}
     */

    /**
     * A short identifying name that is unique.
     *
     * @name domain.studies.CollectionEventType#name
     * @type {string}
     */

    /**
     * An optional description that can provide additional details on the name.
     *
     * @name domain.studies.CollectionEventType#description
     * @type {string}
     * @default null
     */

    /**
     * True if this collection events of this type occur more than once for the duration of the study.
     *
     * @name domain.studies.CollectionEventType#recurring
     * @type {boolean}
     */

    /**
     * The specifications for the specimens that are collected for this collection event type.
     *
     * @name domain.studies.CollectionEventType#specimenDescriptions
     * @type {Array<domain.studies.CollectionSpecimenDescription>}
     */

    /**
     * The annotation types that are collected for this collection event type.
     *
     * @name domain.studies.CollectionEventType#annotationTypes
     * @type {Array<domain.AnnotationType>}
     */

    obj = Object.assign(
      {
        id:        null,
        recurring: false
      },
      obj,
    );

    ConcurrencySafeEntity.call(this, CollectionEventType.SCHEMA, obj);

    options.specimenDescriptions = _.get(options, 'specimenDescriptions', []);
    options.annotationTypes      = _.get(options, 'annotationTypes', []);

    Object.assign(this, _.pick(options, 'study', 'specimenDescriptions', 'annotationTypes'));

    if (options.study) {
      this.studyId = options.study.id;
    }
  }

  CollectionEventType.prototype = Object.create(ConcurrencySafeEntity.prototype);
  Object.assign(CollectionEventType.prototype,
           HasAnnotationTypes.prototype,
           HasCollectionSpecimenDescriptions.prototype);
  CollectionEventType.prototype.constructor = CollectionEventType;

  CollectionEventType.url = function (...paths) {
    return DomainEntity.url.apply(null, [ 'studies/cetypes' ].concat(paths));
  };

  CollectionEventType.SCHEMA = ConcurrencySafeEntity.createDerivedSchema({
    'id': 'CollectionEventType',
    'properties': {
      'slug':                 { 'type': 'string' },
      'name':                 { 'type': 'string' },
      'description':          { 'type': [ 'string', 'null' ] },
      'recurring':            { 'type': 'boolean' },
      'specimenDescriptions': { 'type': 'array', 'items': { '$ref': 'CollectionSpecimenDescription' } },
      'annotationTypes':      { 'type': 'array', 'items': { '$ref': 'AnnotationType' } }
    },
    'required': [ 'slug', 'name', 'recurring' ]
  });

  /**
   * Checks if <tt>obj</tt> has valid properties to construct a
   * {@link domain.studies.CollectionEventType|CollectionEventType}.
   *
   * @param {object} [obj={}] - An initialization object whose properties are the same as the members from
   * this class. Objects of this type are usually returned by the server's REST API.
   *
   * @returns {domain.Validation} The validation passes if <tt>obj</tt> has a valid schema.
   */
  CollectionEventType.isValid = function(obj) {
    return ConcurrencySafeEntity.isValid(CollectionEventType.SCHEMA,
                                         [
                                           CollectionSpecimenDescription.SCHEMA,
                                           AnnotationType.SCHEMA
                                         ],
                                         obj);
  };

  /**
   * Creates a CollectionEventType, but first it validates <tt>obj</tt> to ensure that it has a valid
   * schema.
   *
   * @param {object} [obj={}] - An initialization object whose properties are the same as the members from
   * this class. Objects of this type are usually returned by the server's REST API.
   *
   * @returns {domain.studies.CollectionEventType} A collection event type created from the given object.
   *
   * @see {@link domain.studies.CollectionEventType.asyncCreate|asyncCreate()} when you need to create a
   * collection event type within asynchronous code.
   */
  CollectionEventType.create = function (obj) {
    var options = {},
        validation = CollectionEventType.isValid(obj);
    if (!validation.valid) {
      $log.error('invalid collection event type from server: ' + validation.message);
      throw new DomainError('invalid collection event type from server: ' + validation.message);
    }

    if (obj.annotationTypes) {
      try {
        options.annotationTypes = obj.annotationTypes
          .map(annotationType => AnnotationType.create(annotationType));
      } catch (e) {
        throw new DomainError('invalid annotation types from server: ' + validation.message);
      }
    }

    if (obj.specimenDescriptions) {
      try {
        options.specimenDescriptions = obj.specimenDescriptions
          .map((specimenDescription) => CollectionSpecimenDescription.create(specimenDescription));
      } catch (e) {
        throw new DomainError('invalid specimen specs from server: ' + validation.message);
      }
    }

    return new CollectionEventType(obj, options);
  };

  /**
   * Creates a CollectionEventType from a server reply but first validates that <tt>obj</tt> has a valid
   * schema. <i>Meant to be called from within promise code.</i>
   *
   * @param {object} [obj={}] - An initialization object whose properties are the same as the members from
   * this class. Objects of this type are usually returned by the server's REST API.
   *
   * @returns {Promise<domain.studies.CollectionEventType>} A collection event type wrapped in a promise.
   *
   * @see {@link domain.studies.CollectionEventType.create|create()} when not creating a collection event
   * type within asynchronous code.
   */
  CollectionEventType.asyncCreate = function (obj) {
    var result;

    try {
      result = CollectionEventType.create(obj);
      return $q.when(result);
    } catch (e) {
      return $q.reject(e);
    }
  };

  /**
   * Retrieves a CollectionEventType from the server.
   *
   * @param {string} studySlug the slug of the study this collection event type belongs to.
   *
   * @param {string} slug the sllug of the collection event type to retrieve.
   *
   * @returns {Promise<domain.studies.CollectionEventType>} The collection event type within a promise.
   */
  CollectionEventType.get = function(studySlug, slug) {
    return biobankApi.get(CollectionEventType.url(studySlug, slug))
      .then(reply => CollectionEventType.asyncCreate(reply));
  };

  /**
   * Fetches all collection event types for a {@link domain.studies.Study|Study}.
   *
   * @param {string} studySlug the slug of the study the collection event types belongs to.
   *
   * @returns {Promise<Array<domain.studies.CollectionEventType>>} An array of collection event types within a
   * promise.
   */
  CollectionEventType.list = function(studySlug, options) {
    var url = CollectionEventType.url(studySlug),
        params,
        validKeys = [
          'filter',
          'sort',
          'page',
          'limit'
        ];

    options = options || {};
    params = _.omitBy(_.pick(options, validKeys), function (value) {
      return value === '';
    });

    return biobankApi.get(url, params).then(function(reply) {
      var deferred = $q.defer();

      try {
        reply.items = reply.items.map((obj) => CollectionEventType.create(obj));
        deferred.resolve(reply);
      } catch (e) {
        deferred.reject(e);
      }
      return deferred.promise;
    });
  };

  /**
   * Sorts an array of Collection Event Types by name.
   *
   * @param {Array<CollectionEventType>} collectionEventTypes The array to sort.
   *
   * @return {Array<CollectionEventType>} A new array sorted by name.
   */
  CollectionEventType.sortByName = function(collectionEventTypes) {
    return _.sortBy(collectionEventTypes, function (collectionEventType) {
      return collectionEventType.name;
    });
  };

  CollectionEventType.prototype.add = function() {
    var json = _.pick(this, 'studyId','name', 'recurring', 'description');
    return biobankApi.post(CollectionEventType.url(this.studyId), json)
      .then(function(reply) {
        return CollectionEventType.asyncCreate(reply);
      });
  };

  CollectionEventType.prototype.remove = function () {
    var url = CollectionEventType.url(this.studyId, this.id, this.version);
    return biobankApi.del(url);
  };

  CollectionEventType.prototype.update = function (path, reqJson) {
    return ConcurrencySafeEntity.prototype.update.call(this, path, reqJson)
      .then(CollectionEventType.asyncCreate);
  };

  CollectionEventType.prototype.updateName = function (name) {
    return this.update(CollectionEventType.url('name', this.id),
                       { studyId: this.studyId, name: name });
  };

  CollectionEventType.prototype.updateDescription = function (description) {
    var json = { studyId: this.studyId };
    if (description) {
      json.description = description;
    }
    return this.update(CollectionEventType.url('description', this.id), json);
  };

  CollectionEventType.prototype.updateRecurring = function (recurring) {
    return this.update(CollectionEventType.url('recurring', this.id),
                       { studyId: this.studyId, recurring: recurring });
  };

  CollectionEventType.prototype.addSpecimenDescription = function (specimenDescription) {
    return this.update(CollectionEventType.url('spcdesc', this.id),
                       Object.assign({ studyId: this.studyId }, _.omit(specimenDescription, 'id')));
  };

  CollectionEventType.prototype.updateSpecimenDescription = function (specimenDescription) {
    return this.update(CollectionEventType.url('spcdesc', this.id, specimenDescription.id),
                       Object.assign({ studyId: this.studyId }, specimenDescription));
  };

  CollectionEventType.prototype.removeSpecimenDescription = function (specimenDescription) {
    const found = _.find(this.specimenDescriptions,  { id: specimenDescription.id });

    if (!found) {
      throw new DomainError('specimen description with ID not present: ' + specimenDescription.id);
    }

    const url = CollectionEventType.url('spcdesc',
                                        this.studyId,
                                        this.id,
                                        this.version,
                                        specimenDescription.id);
    return biobankApi.del(url).then(CollectionEventType.asyncCreate);
  };

  CollectionEventType.prototype.addAnnotationType = function (annotationType) {
    return this.update(CollectionEventType.url('annottype', this.id),
                       Object.assign({ studyId: this.studyId }, _.omit(annotationType, 'uniqueId')));
  };

  CollectionEventType.prototype.updateAnnotationType = function (annotationType) {
    return this.update(CollectionEventType.url('annottype', this.id, annotationType.id),
                       Object.assign({ studyId: this.studyId }, annotationType));
  };

  CollectionEventType.prototype.removeAnnotationType = function (annotationType) {
    var url = CollectionEventType.url('annottype', this.studyId, this.id, this.version, annotationType.id);
    return HasAnnotationTypes.prototype.removeAnnotationType.call(this, annotationType, url)
      .then(CollectionEventType.asyncCreate);
  };

  CollectionEventType.prototype.inUse = function () {
    return biobankApi.get(CollectionEventType.url('inuse', this.id));
  };

  /** return constructor function */
  return CollectionEventType;
}

export default ngModule => ngModule.factory('CollectionEventType', CollectionEventTypeFactory)

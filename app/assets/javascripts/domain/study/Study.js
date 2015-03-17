/* global define */
define(['../module', 'angular', 'underscore'], function(module, angular, _) {
  'use strict';

  module.factory('Study', StudyFactory);

  StudyFactory.$inject = [
    'funutils',
    'validationService',
    'ConcurrencySafeEntity',
    'StudyStatus',
    'Location',
    'studiesService',
    'studyLocationsService'
  ];

  /**
   *
   */
  function StudyFactory(funutils,
                         validationService,
                         ConcurrencySafeEntity,
                         StudyStatus,
                         Location,
                         studiesService,
                         studyLocationsService) {

    var requiredKeys = ['id', 'version', 'timeAdded', 'name', 'status'];

    var validateObj = funutils.partial(
      validationService.condition1(
        validationService.validator('must be a map', _.isObject),
        validationService.validator('has the correct keys',
                                    validationService.hasKeys.apply(null, requiredKeys))),
      _.identity);

    /**
     *
     */
    function Study(obj) {
      obj =  obj || {};

      ConcurrencySafeEntity.call(this, obj);

      _.extend(this, _.defaults(obj, {
        name:        '',
        description: null,
        status:      StudyStatus.DISABLED(),
        locations :  [],
        studyIds:    []
      }));
    }

    Study.prototype = Object.create(ConcurrencySafeEntity.prototype);

    /**
     * Used by promise code, so it must return an error rather than throw one.
     */
    Study.create = function (obj) {
      var validation = validateObj(obj);
      if (!_.isObject(validation)) {
        return new Error('invalid object from server: ' + validation);
      }
      return new Study(obj);
    };

    Study.list = function (options) {
      options = options || {};
      return studiesService.list(options).then(function(reply) {
        // reply is a paged result
        reply.items = _.map(reply.items, function(obj){
          return Study.create(obj);
        });
        return reply;
      });
    };

    Study.get = function (id) {
      return studiesService.get(id).then(function(reply) {
        return Study.create(reply);
      });
    };

    Study.prototype.addOrUpdate = function () {
      var self = this;
      return studiesService.addOrUpdate(self).then(function(reply) {
        return new Study.create(reply);
      });
    };

    Study.prototype.disable = function () {
      return changeState(this, 'disable');
    };

    Study.prototype.enable = function () {
      return changeState(this, 'enable');
    };

    Study.prototype.retire = function () {
      return changeState(this, 'retire');
    };

    function changeState(obj, method) {
      return studiesService[method](obj).then(function(reply) {
        return new Study.create(reply);
      });
    }

    return Study;
  }

});

/* Local Variables:  */
/* mode: js          */
/* End:              */

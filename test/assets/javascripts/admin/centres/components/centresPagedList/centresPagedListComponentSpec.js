/**
 * Jasmine test suite
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2017 Canadian BioSample Repository (CBSR)
 */
define(function (require) {
  'use strict';

  var mocks           = require('angularMocks'),
      _               = require('lodash'),
      sharedBehaviour = require('../../../../test/EntityPagedListSharedBehaviourSpec');

  function SuiteMixinFactory(ComponentTestSuiteMixin) {

    function SuiteMixin() {
    }

    SuiteMixin.prototype = Object.create(ComponentTestSuiteMixin.prototype);
    SuiteMixin.prototype.constructor = SuiteMixin;

    SuiteMixin.prototype.createScope = function () {
      ComponentTestSuiteMixin.prototype.createScope.call(
        this,
        '<centres-paged-list></centres-paged-list',
        undefined,
        'centresPagedList');
    };

    SuiteMixin.prototype.createCountsSpy = function (disabled, enabled) {
      var counts = {
        total:    disabled + enabled,
        disabled: disabled,
        enabled:  enabled
      };

      spyOn(this.CentreCounts, 'get').and.returnValue(this.$q.when(counts));
    };

   SuiteMixin.prototype.createEntity = function () {
     var entity = new this.Centre(this.factory.centre());
     return entity;
   };

   SuiteMixin.prototype.createPagedResultsSpy = function (centres) {
      var reply = this.factory.pagedResult(centres);
      spyOn(this.Centre, 'list').and.returnValue(this.$q.when(reply));
    };

    return SuiteMixin;
  }

  describe('centresPagedListComponent', function() {

    beforeEach(mocks.module('biobankApp', 'biobank.test'));

    beforeEach(inject(function(ComponentTestSuiteMixin) {
      _.extend(this, new SuiteMixinFactory(ComponentTestSuiteMixin).prototype);

      this.putHtmlTemplates(
        '/assets/javascripts/admin/centres/components/centresPagedList/centresPagedList.html',
        '/assets/javascripts/common/components/nameAndStateFilters/nameAndStateFilters.html');

      this.injectDependencies('$q',
                              '$rootScope',
                              '$compile',
                              'Centre',
                              'CentreCounts',
                              'CentreState',
                              'factory');

    }));

    it('scope is valid on startup', function() {
      this.createCountsSpy(2, 5);
      this.createPagedResultsSpy([]);
      this.createScope();

      expect(this.controller.limit).toBeDefined();
      expect(this.controller.stateData).toBeArrayOfObjects();
      expect(this.controller.stateData).toBeNonEmptyArray();
      expect(this.controller.getItems).toBeFunction();
      expect(this.controller.getItemIcon).toBeFunction();
    });

    describe('centres', function () {

      var context = {};

      beforeEach(inject(function () {
        var self = this;

        context.createController = function (centresCount) {
          var centres;
          centresCount = centresCount || 0;
          centres = _.map(_.range(centresCount), self.createEntity.bind(self));
          self.createCountsSpy(2, 5);
          self.createPagedResultsSpy(centres);
          self.createScope();
        };

        context.getEntitiesLastCallArgs = function () {
          return self.Centre.list.calls.mostRecent().args;
        };

      }));

      sharedBehaviour(context);

    });

    describe('getItemIcon', function() {

      beforeEach(function() {
        this.createCountsSpy(2, 5);
        this.createPagedResultsSpy([]);
        this.createScope();
      });

      it('getItemIcon returns a valid icon', function() {
        var self = this,
            statesInfo = [
              { state: this.CentreState.DISABLED, icon: 'glyphicon-cog' },
              { state: this.CentreState.ENABLED,  icon: 'glyphicon-ok-circle' }
            ];

        statesInfo.forEach(function (info) {
          var centre = new self.Centre(self.factory.centre({ state: info.state }));
          expect(self.controller.getItemIcon(centre)).toEqual(info.icon);
        });
      });

      it('getItemIcon throws an error for and invalid state', function() {
        var self = this,
            centre = new this.Centre(this.factory.centre({ state: this.factory.stringNext() }));

        expect(function () {
          self.controller.getItemIcon(centre);
        }).toThrowError(/invalid centre state/);
      });

    });

  });

});
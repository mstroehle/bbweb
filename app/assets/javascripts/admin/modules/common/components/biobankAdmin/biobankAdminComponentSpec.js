/**
 * Jasmine test suite
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2018 Canadian BioSample Repository (CBSR)
 */
/* global angular */

import { ComponentTestSuiteMixin } from 'test/mixins/ComponentTestSuiteMixin';
import ngModule from '../../index'

describe('Component: biobankAdmin', function() {

  beforeEach(() => {
    angular.mock.module(ngModule, 'biobank.test');
    angular.mock.inject(function() {
      Object.assign(this, ComponentTestSuiteMixin);
      this.injectDependencies('$rootScope',
                              '$compile',
                              '$q',
                              'User',
                              'adminService',
                              'userService',
                              'Factory');
      this.createController = () => {
        this.createControllerInternal(
          '<biobank-admin></biobank-admin>',
          undefined,
          'biobankAdmin');
      };

    });
  });

  it('has valid scope', function() {
    var user = this.User.create(this.Factory.user()),
        counts = {
          studies: 1,
          centres: 2,
          users: 3
        };

    this.userService.requestCurrentUser =
      jasmine.createSpy().and.returnValue(this.$q.when(user));
    spyOn(this.adminService, 'aggregateCounts').and.returnValue(this.$q.when(counts));

    this.createController();
    expect(this.controller.user).toEqual(jasmine.any(this.User));
    expect(this.controller.counts).toEqual(counts);
  });

});

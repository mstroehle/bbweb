/**
 * Jasmine test suite
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2018 Canadian BioSample Repository (CBSR)
 */
/* global angular */

import { ComponentTestSuiteMixin } from 'test/mixins/ComponentTestSuiteMixin';
import ngModule from '../../index'

describe('Component: biobankFooter', function() {

  beforeEach(() => {
    angular.mock.module(ngModule, 'biobank.test');
    angular.mock.inject(function () {
      Object.assign(this, ComponentTestSuiteMixin);
      this.injectDependencies('$rootScope', '$compile');
      this.createController = () =>
        this.createControllerInternal(
          '<biobank-footer></biobank-footer>',
          undefined,
          'biobankFooter');
    });
  });

  it('has valid scope', function() {
    this.createController();
    expect(this.controller).toBeDefined();
  });
});

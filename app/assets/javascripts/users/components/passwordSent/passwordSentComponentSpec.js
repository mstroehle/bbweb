/**
 * Jasmine test suite
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2018 Canadian BioSample Repository (CBSR)
 */
/* global angular */

import { ComponentTestSuiteMixin } from 'test/mixins/ComponentTestSuiteMixin';
import ngModule from '../../index'

describe('Component: passwordSent', function() {

  beforeEach(() => {
    angular.mock.module(ngModule, 'biobank.test');
    angular.mock.inject(function() {
      Object.assign(this, ComponentTestSuiteMixin);
      this.injectDependencies('$rootScope', '$compile', 'Factory');
      this.email = this.Factory.emailNext();

      this.createController = (email) => {
        email = email || this.email;
        this.createControllerInternal(
          '<password-sent email="vm.email"></password-sent>',
          { email: email },
          'passwordSent');
      };
    });
  });

  it('has valid scope', function() {
    this.createController();
    expect(this.controller.email).toEqual(this.email);
  });

});

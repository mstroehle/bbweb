/**
 * Jasmine test suite
 */
/* global angular */

import { ComponentTestSuiteMixin } from 'test/mixins/ComponentTestSuiteMixin';
import ngModule from '../../index'
import sharedBehaviour from 'test/behaviours/annotationTypeAddComponentSharedBehaviour';

describe('Component: participantAnnotationTypeAdd', function() {

  beforeEach(() => {
    angular.mock.module(ngModule, 'biobank.test');
    angular.mock.inject(function() {
      Object.assign(this, ComponentTestSuiteMixin);
      this.injectDependencies('$rootScope',
                              '$compile',
                              'Study',
                              'Factory');

      this.study = new this.Study(this.Factory.study());

      this.createController = () => {
        this.createControllerInternal(
          [
            '<participant-annotation-type-add',
            '  study="vm.study"',
            '</participant-annotation-type-add>'
          ].join(''),
          { study: this.study },
          'participantAnnotationTypeAdd');
      };
    });
  });

  it('should have  valid scope', function() {
    this.createController();
    expect(this.controller.study).toBe(this.study);
  });

  describe('for onSubmit and onCancel', function () {
    var context = {};

    beforeEach(function () {
      context.createController = () => {
        this.createController();
      }

      context.scope                     = this.scope;
      context.controller                = this.controller;
      context.entity                    = this.Study;
      context.addAnnotationTypeFuncName = 'addAnnotationType';
      context.returnState               = 'home.admin.studies.study.participants';
    });

    sharedBehaviour(context);
  });

});

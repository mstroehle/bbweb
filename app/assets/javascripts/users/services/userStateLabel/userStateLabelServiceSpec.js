/**
 * Jasmine test suite
 *
 */
/* global angular */

import _ from 'lodash';
import ngModule from '../../index'
import sharedBehaviour from '../../../test/behaviours/labelServiceSharedBehaviour';

describe('userStateLabelService', function() {

  beforeEach(() => {
    angular.mock.module(ngModule, 'biobank.test');
    angular.mock.inject(function(TestSuiteMixin) {
      _.extend(this, TestSuiteMixin);
      this.injectDependencies('userStateLabelService',
                              'UserState');
    });
  });

  describe('shared behaviour', function() {
    var context = {};
    beforeEach(function() {
      var self = this;

      context.labels = _.values(this.UserState);
      context.toLabelFunc =
        this.userStateLabelService.stateToLabelFunc.bind(this.userStateLabelService);
      context.expectedLabels = [];
      _.values(this.UserState).forEach(function (state) {
        context.expectedLabels[state] = self.capitalizeFirstLetter(state);
      });
    });
    sharedBehaviour(context);
  });

});

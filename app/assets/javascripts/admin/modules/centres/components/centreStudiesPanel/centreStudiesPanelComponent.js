/**
 * AngularJS Component for {@link domain.centres.Centre Centre} administration.
 *
 * @namespace admin.centres.components.centreStudiesPanel
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2018 Canadian BioSample Repository (CBSR)
 */

import _ from 'lodash'

/*
 * Controller for this component.
 */
/* @ngInject */
function CentreStudiesPanelController($scope,
                                      $log,
                                      gettextCatalog,
                                      Panel,
                                      Study,
                                      StudyName,
                                      studyStateLabelService,
                                      modalService) {

  var vm = this;
  vm.$onInit = onInit;

  //--

  function onInit() {
    vm.tableStudies    = [];
    vm.selected        = undefined;
    vm.remove          = remove;
    vm.information     = information;
    vm.onSelect        = onSelect;
    vm.studyStateLabel = studyStateLabel;
    vm.getStudyNames   = getStudyNames;

    // updates the selected tab in 'centreViewDirective' which is the parent directive
    $scope.$emit('tabbed-page-update', 'tab-selected');
  }

  function onSelect(study) {
    var foundIndex;

    if (!vm.centre.isDisabled()) {
      $log.error('Should not be allowed to add studies to centre if centre is not disabled');
      throw new Error('An application error occurred, please contact your administrator.');
    }

    foundIndex = _.findIndex(vm.centre.studyNames, function (studyName) {
      return studyName.name === study.name;
    });

    // add the study only if it's not there
    if(foundIndex < 0) {
      vm.centre.addStudy(study).then(addSuccessful);
    }
    vm.selected = undefined;

    function addSuccessful(centre) {
      vm.centre = centre;
    }
  }

  function information(studyId) { // eslint-disable-line no-unused-vars
    // FIXME: needs implementation
  }

  function remove(study) {
    // FIXME should not allow study to be removed if centre holds specimens for study
    modalService.modalOkCancel(
      gettextCatalog.getString('Remove study'),
      gettextCatalog.getString('Are you sure you want to remove study {{name}}?',
                               { name: study.name }))
      .then(function () {
        return vm.centre.removeStudy({id: study.id})
          .then(function (centre) {
            vm.centre = centre;
          })
          .catch(function (error) {
            modalService.modalOkCancel(gettextCatalog.getString('Remove failed'),
                                       gettextCatalog.getString('Could not remove study: ') + error);
          });
      });
  }

  function studyStateLabel(state) {
    return studyStateLabelService.stateToLabelFunc(state)();
  }

  function getStudyNames(viewValue) {
    return StudyName.list({ filter: 'name:like:' + viewValue });
  }


}

/**
 * An AngularJS component that allows the user to link a {@link domain.centres.Centre Centre} to one or more
 * {@link domain.studies.Study Study}.
 *
 * @memberOf admin.centres.components.centreStudiesPanel
 *
 * @param {domain.centres.Centre} centre - the centre to link studies to.
 */
const centreStudiesPanelComponent = {
  template: require('./centreStudiesPanel.html'),
  controller: CentreStudiesPanelController,
  controllerAs: 'vm',
  bindings: {
    centre: '<'
  }
};

export default ngModule => ngModule.component('centreStudiesPanel', centreStudiesPanelComponent)

/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define(['underscore'], function(_) {
  'use strict';

  /**
   *
   */
  function studiesListDirective() {
    var directive = {
      restrict: 'E',
      scope: {},
      bindToController: {},
      templateUrl : '/assets/javascripts/admin/directives/studies/studiesList/studiesList.html',
      controller: StudiesListCtrl,
      controllerAs: 'vm'
    };

    return directive;
  }

  StudiesListCtrl.$inject = [
    'Study',
    'StudyStatus',
    'studyStatusLabel',
    'StudyCounts'
  ];

  /**
   * Displays a list of studies with each in its own mini-panel.
   *
   */
  function StudiesListCtrl(Study, StudyStatus, studyStatusLabel, StudyCounts) {
    var vm = this;

    vm.studyCounts      = {};
    vm.pageSize         = 5;
    vm.updateStudies    = Study.list;
    vm.possibleStatuses = [ { id: 'all', label: 'All' } ];

    _.each(_.values(StudyStatus), function (status) {
      vm.possibleStatuses.push({id: status, label: studyStatusLabel.statusToLabel(status)});
    });

    init();

    //--

    function init() {
      StudyCounts.get().then(function (counts) {
        vm.studyCounts = counts;
      });
    }
  }

  return studiesListDirective;
});

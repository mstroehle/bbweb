define(['../../module'], function(module) {
  'use strict';

  module.controller('SpcLinkAnnotTypesPanelCtrl', SpcLinkAnnotTypesPanelCtrl);

  SpcLinkAnnotTypesPanelCtrl.$inject = [
    '$scope',
    '$state',
    '$stateParams',
    'SpcLinkAnnotTypeService',
    'spcLinkAnnotTypeRemoveService',
    'annotTypeModalService',
    'panelService'
  ];

  /**
   * A panel to display a study's specimen link annotation types.
   */
  function SpcLinkAnnotTypesPanelCtrl($scope,
                                      $state,
                                      $stateParams,
                                      SpcLinkAnnotTypeService,
                                      spcLinkAnnotTypeRemoveService,
                                      annotTypeModalService,
                                      panelService) {
    var vm = this;

    var helper = panelService.panel(
      'study.panel.specimenLinkAnnotationTypes',
      'admin.studies.study.processing.spcLinkAnnotTypeAdd',
      annotTypeModalService,
      'Specimen Link Annotation Type');

    vm.annotTypes  = $scope.annotTypes;
    vm.update      = update;
    vm.remove      = spcLinkAnnotTypeRemoveService.remove;
    vm.information = helper.information;
    vm.add         = helper.add;
    vm.panelOpen   = helper.panelOpen;
    vm.panelToggle = helper.panelToggle;

    vm.columns = [
      { title: 'Name', field: 'name', filter: { 'name': 'text' } },
      { title: 'Type', field: 'valueType', filter: { 'valueType': 'text' } },
      { title: 'Description', field: 'description', filter: { 'description': 'text' } }
    ];

    vm.tableParams = helper.getTableParams(vm.annotTypes);
    vm.tableParams.settings().$scope = $scope;  // kludge: see https://github.com/esvit/ng-table/issues/297#issuecomment-55756473

    //--

    /**
     * Switches state to update a specimen link annotation type.
     */
    function update(annotType) {
      $state.go(
        'admin.studies.study.processing.spcLinkAnnotTypeUpdate',
        { annotTypeId: annotType.id });
    }

  }

});
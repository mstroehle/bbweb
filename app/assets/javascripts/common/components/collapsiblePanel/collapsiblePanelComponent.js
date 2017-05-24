/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(function () {
  'use strict';

  /**
   * A panel that can be collapsed by the user.
   */
  var component = {
    templateUrl: '/assets/javascripts/common/components/collapsiblePanel/collapsiblePanel.html',
    transclude: true,
    controller: CollapsiblePanelController,
    controllerAs: 'vm',
    bindings: {
      heading:     '@',
      collapsible: '<'
    }
  };

  //collapsiblePanelController.$inject = [];

  function CollapsiblePanelController() {
    var vm = this;

    vm.panelOpen = true;
    vm.panelButtonClicked = panelButtonClicked;

    //---

    function panelButtonClicked() {
      vm.panelOpen = ! vm.panelOpen;
    }
  }

  return component;
});
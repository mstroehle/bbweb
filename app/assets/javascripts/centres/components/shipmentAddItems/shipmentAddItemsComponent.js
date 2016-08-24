/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(function (require) {
  'use strict';

  var _ = require('lodash');

  var component = {
    templateUrl : '/assets/javascripts/centres/components/shipmentAddItems/shipmentAddItems.html',
    controller: ShipmentAddItemsController,
    controllerAs: 'vm',
    bindings: {
      shipmentId: '<'
    }
  };

  ShipmentAddItemsController.$inject = [
    '$state',
    'gettext' ,
    'shipmentProgressItems',
    'Shipment',
    'modalInput',
    'modalService',
    'timeService',
    'notificationsService'
  ];

  /**
   * Allows the user to add items to a shipment.
   *
   * A task progress bar is used to give feedback to the user that this is one step in a multi-step process.
   */
  function ShipmentAddItemsController($state,
                                      gettext,
                                      shipmentProgressItems,
                                      Shipment,
                                      modalInput,
                                      modalService,
                                      timeService,
                                      notificationsService) {
    var vm = this;

    vm.$onInit       = onInit;
    vm.shipment      = null;
    vm.allItemsAdded = allItemsAdded;

     vm.progressInfo = {
        items: shipmentProgressItems,
        current: 2
     };

    //--

    function onInit() {
      Shipment.get(vm.shipmentId).then(function (shipment) {
        vm.shipment = shipment;
      });
    }

    /**
     * Invoked by user when all items have been added to the shipment and it is now packed.
     */
    function allItemsAdded() {
      Shipment.get(vm.shipment.id).then(function (shipment) {
        if (shipment.specimenCount > 0) {
          if (_.isUndefined(vm.timePacked)) {
            vm.timePacked = new Date();
          }
          return modalInput.dateTime(gettext('Date and time shipment was packed'),
                                     gettext('Time packed'),
                                     vm.timePacked,
                                     { required: true }).result
            .then(function (timePacked) {
              return vm.shipment.packed(timeService.dateToUtcString(timePacked))
                .then(function (shipment) {
                  return $state.go('home.shipping.shipment', { shipmentId: shipment.id});
                })
                .catch(notificationsService.updateError);
            });
        }

        return modalService.modalOk(gettext('Shipment has no specimens'),
                                    gettext('Please add specimens to this shipment fist.'));
      });
    }
  }

  return component;
});

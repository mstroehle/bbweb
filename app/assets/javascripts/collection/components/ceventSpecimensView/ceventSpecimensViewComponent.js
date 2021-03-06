/**
 * AngularJS Component for {@link domain.participants.CollectionEvent CollectionEvents}.
 *
 * @namespace collection.components.ceventSpecimensView
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2018 Canadian BioSample Repository (CBSR)
 */

import angular from 'angular';

/* @ngInject */
function CeventSpecimensViewController($q,
                                       $state,
                                       gettextCatalog,
                                       Specimen,
                                       Centre,
                                       specimenAddModal,
                                       domainNotificationService,
                                       notificationsService,
                                       resourceErrorService) {
  var vm = this;
  vm.$onInit = onInit;

  //--

  function onInit() {
    vm.specimens       = [];
    vm.centreLocations = [];
    vm.tableController = undefined;

    vm.addSpecimens    = addSpecimens;
    vm.getTableData    = getTableData;
    vm.removeSpecimen  = removeSpecimen;
    vm.viewSpecimen    = viewSpecimen;
  }

  function addSpecimens() {
    var defer = $q.defer();

    if (vm.centreLocations.length <= 0) {
      vm.study.allLocations().then(function (centreLocations) {
        defer.resolve(Centre.centreLocationToNames(centreLocations));
      });
    } else {
      defer.resolve(vm.centreLocations);
    }

    defer.promise
      .then(centreLocations => {
        vm.centreLocations = centreLocations;
        return specimenAddModal.open(vm.centreLocations,
                                     vm.collectionEvent.collectionEventType.specimenDefinitions,
                                     new Date(vm.collectionEvent.timeCompleted)).result;
      })
      .then(specimens => Specimen.add(vm.collectionEvent.id, specimens))
      .catch(function (err) {
        notificationsService.error(JSON.stringify(err));
      })
      .then(() => {
        notificationsService.success(gettextCatalog.getString('Specimen added'));
        reloadTableData();
      });
  }

  function getTableData(tableState, controller) {
    var pagination    = tableState.pagination,
        sortPredicate = tableState.sort.predicate || 'inventoryId',
        sortOrder     = tableState.sort.reverse || false,
        options = {
          sort:     sortPredicate,
          page:     1 + (pagination.start / vm.limit),
          limit: vm.limit,
          order:    sortOrder ? 'desc' : 'asc'
        };

    if (!vm.tableController && controller) {
      vm.tableController = controller;
    }

    vm.tableDataLoading = true;

    Specimen.list(vm.collectionEvent.slug, options)
      .then(function (paginatedSpecimens) {
        vm.specimens = paginatedSpecimens.items;
        tableState.pagination.numberOfPages = paginatedSpecimens.maxPages;
        vm.tableDataLoading = false;
      })
      .catch(resourceErrorService.checkUnauthorized());
  }

  function reloadTableData() {
    getTableData(vm.tableController.tableState());
  }

  function viewSpecimen(specimen) {
    $state.go('home.collection.study.participant.cevents.details.specimen',
              { specimenSlug: specimen.slug });
  }

  function removeSpecimen(specimen) {
    const promiseFn = () =>
          specimen.remove(vm.collectionEvent.id)
          .then(() => {
            notificationsService.success(gettextCatalog.getString('Specimen removed'));
            reloadTableData();
          });

    domainNotificationService.removeEntity(
      promiseFn,
      gettextCatalog.getString('Remove specimen'),
      gettextCatalog.getString(
        'Are you sure you want to remove specimen with inventory ID <strong>{{id}}</strong>?',
        { id: specimen.inventoryId }),
      gettextCatalog.getString('Remove failed'),
      gettextCatalog.getString(
        'Specimen with ID {{id}} cannot be removed',
        { id: specimen.inventoryId }))
      .catch(angular.noop);
  }

}

/**
 * An AngularJS component that displays the {@link domain.participants.Specimen Specimens} that were collected
 * in a {@link domain.participants.CollectionEvent CollectionEvent}.
 *
 * @memberOf collection.components.ceventSpecimensView
 *
 * @param {domain.studies.Study} study - The study the *Participant* belongs to.
 *
 * @param {domain.participants.Participant} participant - The participant the *Collection Event* belongs to.
 *
 * @param {domain.studies.CollectionEventType} collectionEventType - the *Collection Event Type* for this
 * *Collection Event*. Must for the study given in `study`.
 *
 * @param {domain.participants.CollectionEvent} collectionEvenType - the *Collection Event* to display the
 * specimens for. Must be for the participant given in `participant`.
 */
const ceventSpecimensViewComponent = {
  template: require('./ceventSpecimensView.html'),
  controller: CeventSpecimensViewController,
  controllerAs: 'vm',
  bindings: {
    study:               '<',
    participant:         '<',
    collectionEventType: '<',
    collectionEvent:     '<'
  }
};

export default ngModule => ngModule.component('ceventSpecimensView', ceventSpecimensViewComponent)

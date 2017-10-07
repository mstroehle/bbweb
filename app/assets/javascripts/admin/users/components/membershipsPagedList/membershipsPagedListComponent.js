/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
import PagedListController from '../../../../common/controllers/PagedListController';


/*
 * Controller for this component.
 */
class Controller extends PagedListController {

  constructor($log,
              $scope,
              $state,
              Membership,
              gettextCatalog,
              NameFilter) {
    'ngInject';
    super($log,
          $state,
          gettextCatalog,
          { nameFilter: new NameFilter() },
          5);

    this.$scope = $scope;
    this.Membership = Membership;
    this.NameFilter = NameFilter;
  }

  $onInit() {
    this.counts = { total: 1 };
    super.$onInit();
  }

  getItems(options) {
    return this.Membership.list(options);
  }

  getItemIcon() {
    return 'glyphicon-cog';
  }

}

/**
 * Displays studies in a panel list.
 *
 * @return {object} An AngularJS component.
 */
const component = {
  template: require('./membershipsPagedList.html'),
  controller: Controller,
  controllerAs: 'vm',
  bindings: {
  }
};

export default component;

/*
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2018 Canadian BioSample Repository (CBSR)
 *
 * Configures routes for the administration module.
 */

/**
 * UI Router states used for Biobank Administration.
 *
 * @name admin.adminUiRouterconfig
 * @function
 *
 * @param {AngularJS_Service} $stateProvider.
 */
/* @ngInject */
function adminUiRouterconfig($stateProvider) {

  $stateProvider.state('home.admin', {
    // this state is checked for an authorized user, see uiRouterIsAuthorized() in app.js
    url: 'admin',
    views: {
      'main@': 'biobankAdmin'
    }
  });

}

export default ngModule => ngModule.config(adminUiRouterconfig)

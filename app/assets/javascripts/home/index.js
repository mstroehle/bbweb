/**
 * The Home module.
 *
 * Shows the start page and provides controllers for the header and the footer.
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2017 Canadian BioSample Repository (CBSR)
 */

import angular from 'angular';
import homeComponent from './components/home/homeComponent';

const MODULE_NAME = 'biobank.home';

angular.module(MODULE_NAME, [])
  .config(require('./states'))

  .component('about',         require('./components/about/aboutComponent'))
  .component('biobankFooter', require('./components/biobankFooter/biobankFooterComponent'))
  .component('biobankHeader', require('./components/biobankHeader/biobankHeaderComponent'))
  .component('home',          homeComponent);

export default MODULE_NAME;
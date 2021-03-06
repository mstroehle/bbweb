/**
 * AngularJS Constants used for defining specimen types.
 *
 * @namespace domain.PreservationTemperature
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2018 Canadian BioSample Repository (CBSR)
 */

/**
 * Describes how a {@link domain.participants.Specimen Specimen} should be preserved/stored by describing
 * temperature requirements (degrees Celsius), as well as a preservation method (see {@link
 * domain.PreservationType PreservationType}).
 *
 * @enum {string}
 * @memberOf domain.PreservationTemperature
 * @see domain.PreservationType.PreservationType
 */
const PreservationTemperature = {
    PLUS_4_CELCIUS:    '4 C',
    MINUS_20_CELCIUS:  '-20 C',
    MINUS_80_CELCIUS:  '-80 C',
    MINUS_180_CELCIUS: '-180 C',
    ROOM_TEMPERATURE:  'Room Temperature'
  };

export default ngModule => ngModule.constant('PreservationTemperature', PreservationTemperature)

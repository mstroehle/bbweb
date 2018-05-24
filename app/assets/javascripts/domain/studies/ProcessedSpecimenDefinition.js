/*
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2018 Canadian BioSample Repository (CBSR)
 */

/*
 * AngularJS Factory
 */
/* @ngInject */
function ProcessedSpecimenDefinitionFactory($log, DomainEntity, DomainError) {

  /*
   * Used for validation.
   */
  const SCHEMA = {
    'id': 'ProcessedSpecimenDefinition',
    'type': 'object',
    'properties': {
      'id':                      { 'type': 'string' },
      'slug':                    { 'type': 'string' },
      'name':                    { 'type': 'string' },
      'description':             { 'type': [ 'string', 'null' ] },
      'units':                   { 'type': 'string' },
      'anatomicalSourceType':    { 'type': 'string' },
      'preservationType':        { 'type': 'string' },
      'preservationTemperature': { 'type': 'string' },
      'specimenType':            { 'type': 'string' }

    },
    'required': [
      'id',
      'slug',
      'name',
      'units',
      'anatomicalSourceType',
      'preservationType',
      'preservationTemperature',
      'specimenType'
    ]
  }

  /**
   * Used to configure a *Specimen Type* used by a {@link domain.studies.Study Study}.
   *
   * It records ownership, summary, storage, and classification information that applies to an entire group or
   * collection of {@link domain.participants.Specimen Specimens}. A *Collection Specimen Description* is
   * defined for specimen types collected from participants.
   *
   * @memberOf domain.studies
   */
  class ProcessedSpecimenDefinition extends DomainEntity {

    /**
     * @param {object} obj - An initialization object whose properties are the same as the members from
     * this class. Objects of this type are usually returned by the server's REST API.
     */
    constructor(obj) {
      /**
       * A short identifying name.
       * @name domain.studies.ProcessedSpecimenDefinition#name
       * @type string
       */

      /**
       * Specifies how the specimen amount is measured (e.g. volume, weight, length, etc.).
       * @name domain.studies.ProcessedSpecimenDefinition#units
       * @type {string}
       */

      /**
       * @name domain.studies.ProcessedSpecimenDefinition#anatomicalSourceType
       * @type {domain.AnatomicalSourceType.AnatomicalSourceType}
       */

      /**
       * @name domain.studies.ProcessedSpecimenDefinition#preservationType
       * @type {domain.PreservationType.PreservationType}
       */

      /**
       * @name domain.studies.ProcessedSpecimenDefinition#preservationTemperature
       * @type {domain.PreservationTemperature.PreservationTemperature}
       */

      /**
       * @name domain.studies.ProcessedSpecimenDefinition#specimenType
       * @type {domain.studies.SpecimenType.SpecimenType}
       */

      super(Object.assign(
        {
          name: '',
          description: null,
          anatomicalSourceType: '',
          preservationType: '',
          preservationTemperature: '',
          specimenType: '',
          units: null,
          maxCount: null
        },
        obj));
    }

    /** @private */
    static schema() {
      return SCHEMA;
    }

    /** @private */
    static additionalSchemas() {
      return [];
    }

    static create(obj) {
      var validation = ProcessedSpecimenDefinition.isValid(obj);

      if (!validation.valid) {
        $log.error('invalid object from server: ' + validation.message);
        throw new DomainError('invalid object from server: ' + validation.message);
      }
      return new ProcessedSpecimenDefinition(obj);
    }
  }

  return ProcessedSpecimenDefinition;
}

export default ngModule => ngModule.factory('ProcessedSpecimenDefinition',
                                           ProcessedSpecimenDefinitionFactory)
/**
 * Administration package module.

 * Manages all sub-modules so other RequireJS modules only have to import the package.
 */
define([
  './AdminCtrl',
  './adminService',
  './adminStates',
  './LocationEditCtrl',
  './centres/CentreEditCtrl',
  './centres/CentreSummaryTabCtrl',
  './centres/CentreCtrl',
  './centres/CentresCtrl',
  './centres/CentresTableCtrl',
  './centres/locationsPanelDirective',
  './centres/LocationsPanelCtrl',
  './centres/CentreStudiesPanelCtrl',
  './centres/centreStudiesPanelDirective',
  './centres/states',
  './studies/StudyCtrl',
  './studies/StudyEditCtrl',
  './studies/annotationTypes/CeventAnnotTypesPanelCtrl',
  './studies/annotationTypes/ParticipantsAnnotTypesPanelCtrl',
  './studies/annotationTypes/SpcLinkAnnotTypesPanelCtrl',
  './studies/annotationTypes/annotTypeModalService',
  './studies/annotationTypes/AnnotationTypeEditCtrl',
  './studies/annotationTypes/annotationTypeRemoveService',
  './studies/annotationTypes/ceventAnnotTypeRemoveService',
  './studies/annotationTypes/ceventAnnotTypesPanelDirective',
  './studies/annotationTypes/participantAnnotTypeRemoveService',
  './studies/annotationTypes/participantsAnnotTypesPanelDirective',
  './studies/annotationTypes/spcLinkAnnotTypeRemoveService',
  './studies/annotationTypes/spcLinkAnnotTypesPanelDirective',
  './studies/ceventTypes/CeventTypeEditCtrl',
  './studies/ceventTypes/ceventTypeModalService',
  './studies/ceventTypes/ceventTypeRemoveService',
  './studies/ceventTypes/CeventTypesPanelCtrl',
  './studies/ceventTypes/ceventTypesPanelDirective',
  './studies/ceventTypes/states',
  './studies/participants/states',
  './studies/processing/ProcessingTypeEditCtrl',
  './studies/processing/SpcLinkTypeEditCtrl',
  './studies/processing/processingTypeModalService',
  './studies/processing/processingTypeRemoveService',
  './studies/processing/processingTypesPanelDirective',
  './studies/processing/spcLinkTypeModalService',
  './studies/processing/spcLinkTypeRemoveService',
  './studies/processing/ProcessingTypesPanelCtrl',
  './studies/processing/SpcLinkTypesPanelCtrl',
  './studies/processing/spcLinkTypesPanelDirective',
  './studies/processing/states',
  './studies/specimenGroups/SpecimenGroupEditCtrl',
  './studies/specimenGroups/specimenGroupModalService',
  './studies/specimenGroups/specimenGroupRemoveService',
  './studies/specimenGroups/SpecimenGroupsPanelCtrl',
  './studies/specimenGroups/specimenGroupsPanelDirective',
  './studies/specimenGroups/states',
  './studies/states',
  './studies/StudiesCtrl',
  './studies/StudiesTableCtrl',
  './studies/StudySummaryTabCtrl',
  './studies/studyViewSettingsService',
  './studies/validAmountDirective',
  './studies/validCountDirective',
  './users/UsersTableCtrl',
  './users/UserModalService',
  './users/states'
], function () {});

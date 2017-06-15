/**
 * Configure routes of studies module.
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define(function () {
  'use strict';

  config.$inject = [
    '$urlRouterProvider',
    '$stateProvider'
  ];

  function config($urlRouterProvider, $stateProvider) {

    $urlRouterProvider.otherwise('/');

    $stateProvider
      .state('home.admin.studies', {
        url: '/studies',
        views: {
          'main@': {
            template: '<studies-admin></studies-admin>'
          }
        }
      })
      .state('home.admin.studies.add', {
        url: '/add',
        resolve: {
          study: ['Study', function(Study) {
            return new Study();
          }]
        },
        views: {
          'main@': {
            template: '<study-add study="vm.study"></study-add>',
            controller: StudyController,
            controllerAs: 'vm'
          }
        },
      })
      .state('home.admin.studies.study', {
        abstract: true,
        url: '/{studyId}',
        resolve: {
          study: resolveStudy
        },
        views: {
          'main@': {
            template: '<study-view study="vm.study"></study-view>',
            controller: StudyController,
            controllerAs: 'vm'
          }
        }
      })
      .state('home.admin.studies.study.summary', {
        url: '/summary',
        views: {
          'studyDetails': {
            template: '<study-summary study="vm.study"></study-summary>',
            controller: StudyController,
            controllerAs: 'vm'
          }
        }
      })
      .state('home.admin.studies.study.participants', {
        url: '/participants',
        views: {
          'studyDetails': {
            template: '<study-participants-tab study="vm.study"></study-participants-tab>',
            controller: StudyController,
            controllerAs: 'vm'
          }
        }
      })
      .state('home.admin.studies.study.collection', {
        url: '/collection',
        views: {
          'studyDetails': {
            template: '<study-collection study="vm.study"></study-collection>',
            controller: StudyController,
            controllerAs: 'vm'
          }
        }
      })
      .state('home.admin.studies.study.processing', {
        url: '/processing',
        views: {
          'studyDetails': {
            templateUrl: '/assets/javascripts/admin/studies/studyProcessingTab.html',
            controller: [
              '$scope', 'study',
              function($scope, study) {
                $scope.study = study;
                $scope.annotationTypeDescription =
                  'Specimen link annotations allow a study to collect custom named and '+
                  'defined pieces of data when processing specimens. Annotations are optional and ' +
                  'are not required to be defined.';
              }
            ]
          }
        }
      });

    resolveStudy.$inject = ['$transition$', 'Study'];
    function resolveStudy($transition$, Study) {
      if ($transition$.params().studyId) {
        return Study.get($transition$.params().studyId);
      }
      throw new Error('state parameter studyId is invalid');
    }

    StudyController.$inject = ['study'];

    function StudyController(study) {
      this.study = study;
    }
  }

  return config;
});

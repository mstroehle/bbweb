/**
 * Jasmine test suite
 *
 */
/* global angular */

import { ComponentTestSuiteMixin } from 'test/mixins/ComponentTestSuiteMixin';
import { MembershipTestSuiteMixin } from 'test/mixins/MembershipTestSuiteMixin';
import ngModule from '../../index'

describe('membershipViewComponent', function() {

  beforeEach(() => {
    angular.mock.module(ngModule, 'biobank.test')
    angular.mock.inject(function () {
      Object.assign(this, ComponentTestSuiteMixin, MembershipTestSuiteMixin)

      this.injectDependencies = ComponentTestSuiteMixin.injectDependencies;

      this.injectDependencies('$q',
                              '$httpBackend',
                              '$state',
                              'Membership',
                              'User',
                              'UserName',
                              'StudyName',
                              'CentreName',
                              'UserState',
                              'userService',
                              'domainNotificationService',
                              'modalService',
                              'asyncInputModal',
                              'notificationsService',
                              'modalInput',
                              'matchingUserNames',
                              'MembershipChangeStudiesModal',
                              'MembershipChangeCentresModal',
                              'Factory');

      this.url = (...paths) => {
        const allPaths = [ 'access/memberships' ].concat(paths);
        return ComponentTestSuiteMixin.url(...allPaths);
      };

      this.createController = (membership) => {
        this.createControllerInternal(
          '<membership-view membership="vm.membership"></membership-view>',
          { membership },
          'membershipView')
      }

      this.createCurrentUserSpy = () => {
        const user = this.User.create(this.Factory.user())
        this.userService.getCurrentUser = jasmine.createSpy().and.returnValue(user)
      }
    })
  })

  beforeEach(function() {
    this.createCurrentUserSpy()
  })

  it('has valid scope', function() {
    this.createController(this.Membership.create(this.Factory.membership()))
    expect(this.controller.userCanUpdate).toBeDefined()
    expect(this.controller.breadcrumbs).toBeDefined()
    expect(this.controller.noStudiesMembership).toBeDefined()
    expect(this.controller.noCentresMembership).toBeDefined()
    expect(this.controller.userNameLabels).toBeArray()
    expect(this.controller.studyNameLabels).toBeArray()
    expect(this.controller.centreNameLabels).toBeArray()
  })

  it('has valid user labels', function() {
    const userName = this.UserName.create(this.Factory.userNameDto())
    const membership = this.Membership.create(this.Factory.membership({ userData: [ userName ] }))
    this.createController(membership)
    expect(this.controller.userNameLabels).toBeArrayOfSize(1)
    compareLabelInfoToEntityName(this.controller.userNameLabels[0], userName)
  })

  it('has valid study labels', function() {
    const studyName = this.StudyName.create(this.Factory.studyNameDto())
    const membership = this.Membership.create(this.Factory.membership({
      studyData: {
        allEntities: false,
        entityData: [ studyName ]
      }
    }))
    this.createController(membership)
    expect(this.controller.studyNameLabels).toBeArrayOfSize(1)
    compareLabelInfoToEntityName(this.controller.studyNameLabels[0], studyName)
  })

  it('has valid centre labels', function() {
    const centreName = this.CentreName.create(this.Factory.centreNameDto())
    const membership = this.Membership.create(this.Factory.membership({
      centreData: {
        allEntities: false,
        entityData: [ centreName ]
      }
    }))
    this.createController(membership)
    expect(this.controller.centreNameLabels).toBeArrayOfSize(1)
    compareLabelInfoToEntityName(this.controller.centreNameLabels[0], centreName)
  })

  describe('when removing a membership', function() {

    beforeEach(function() {
      this.modalService.modalOkCancel   = jasmine.createSpy().and.returnValue(this.$q.when('OK'))
      this.notificationsService.success = jasmine.createSpy().and.returnValue(this.$q.when(null))
      this.createController(this.Membership.create(this.Factory.membership()))
    })

    it('can remove a membership', function() {
      this.Membership.prototype.remove  = jasmine.createSpy().and.returnValue(this.$q.when(true))
      this.controller.remove()
      this.scope.$digest()
      expect(this.Membership.prototype.remove).toHaveBeenCalled()
      expect(this.notificationsService.success).toHaveBeenCalled()
      expect(this.modalService.modalOkCancel.calls.count()).toBe(1)
    })

    it('user is informed if a membership removal attempt fails', function() {
      this.Membership.prototype.remove =
        jasmine.createSpy().and.returnValue(this.$q.reject(this.errorReply('simulated error')))
       this.controller.remove()
      this.scope.$digest()
      expect(this.Membership.prototype.remove).toHaveBeenCalled()
      expect(this.notificationsService.success).not.toHaveBeenCalled()
      expect(this.modalService.modalOkCancel.calls.count()).toBe(2)
    })

  })

  describe('updating name', function () {

    const context = {};

    beforeEach(function () {
      context.controllerUpdateFuncName = 'editName'
      context.modalInputFuncName       = 'text'
      context.membershipUpdateFuncName = 'updateName'
      context.newValue                 = this.Factory.stringNext()
    });

    sharedUpdateBehaviour(context);

  });

  describe('updating description', function () {

    const context = {};

    beforeEach(function () {
      context.controllerUpdateFuncName = 'editDescription'
      context.modalInputFuncName       = 'textArea'
      context.membershipUpdateFuncName = 'updateDescription'
      context.newValue                 = this.Factory.stringNext()
    });

    sharedUpdateBehaviour(context);

  });

  describe('adding users', function() {
    const context = {};

    beforeEach(function () {
      const userName = this.UserName.create(this.Factory.userNameDto()),
            rawMembership = this.Factory.membership()

      context.controllerAddEntityFuncName     = 'addUser'
      context.addEntityFuncName               = 'addUser'
      context.entityName                      = userName
      context.membership                      = this.Membership.create(rawMembership)
      context.controllerEntityLabelsFieldName = 'userNameLabels'
      context.entityNameClass                 = this.UserName
      context.controllerGetMatchingEntityNamesFuncName = undefined

      context.replyMembership =
        this.Membership.create(Object.assign({}, rawMembership, { userData: [ userName ]}))
    });

    sharedAsyncModalBehaviour(context);

    it('correctly adds a user', function() {
      const rawMembership = this.Factory.membership(),
            membership    = this.Membership.create(rawMembership),
            user = this.Factory.user();

      this.matchingUserNames.open = jasmine.createSpy().and.returnValue(this.$q.when({
        obj: {
          id: user.id
        }
      }));

      this.Membership.prototype.addUser = jasmine.createSpy().and.returnValue(this.$q.when(membership))

      this.createController(membership);
      this.controller.addUser();
      this.scope.$digest();

      expect(this.matchingUserNames.open).toHaveBeenCalled();
      expect(this.Membership.prototype.addUser).toHaveBeenCalled();
    })

  })

  describe('changing studies', function() {

    it('sends a request to the server', function() {
      const plainMembership = this.Factory.membership({
        studyData: {
          allEntities: true,
          entityData: []
        }
      });
      const membership = this.Membership.create(plainMembership);

      spyOn(this.MembershipChangeStudiesModal, 'open')
        .and.returnValue({ result:  this.$q.when(membership.studyData) });
      spyOn(this.notificationsService, 'success').and.returnValue(this.$q.when('OK'))
      this.createController(membership);

      this.$httpBackend
        .expectPOST(this.url('studyData', membership.id)).respond(this.reply(plainMembership));

      this.controller.changeStudies();
      this.$httpBackend.flush();
      expect(this.notificationsService.success).toHaveBeenCalled()
    });

    it('handles an error from the server', function() {
      const plainMembership = this.Factory.membership({
        studyData: {
          allEntities: true,
          entityData: []
        }
      });
      const membership = this.Membership.create(plainMembership);

      spyOn(this.MembershipChangeStudiesModal, 'open')
        .and.returnValue({ result:  this.$q.when(membership.studyData) });
      spyOn(this.domainNotificationService, 'updateErrorModal').and.returnValue(this.$q.when('OK'));

      this.createController(membership);

      this.$httpBackend
        .expectPOST(this.url('studyData', membership.id))
        .respond(400, this.errorReply('simulated bad request'));

      this.controller.changeStudies();
      this.$httpBackend.flush();
      expect(this.domainNotificationService.updateErrorModal).toHaveBeenCalled()
    });

  });

  describe('adding centres', function() {

    it('sends a request to the server', function() {
      const plainMembership = this.Factory.membership({
        centreData: {
          allEntities: true,
          entityData: []
        }
      });
      const membership = this.Membership.create(plainMembership);

      spyOn(this.MembershipChangeCentresModal, 'open')
        .and.returnValue({ result:  this.$q.when(membership.centreData) });
      spyOn(this.notificationsService, 'success').and.returnValue(this.$q.when('OK'))
      this.createController(membership);

      this.$httpBackend
        .expectPOST(this.url('centreData', membership.id)).respond(this.reply(plainMembership));

      this.controller.changeCentres();
      this.$httpBackend.flush();
      expect(this.notificationsService.success).toHaveBeenCalled()
    });

    it('handles an error from the server', function() {
      const plainMembership = this.Factory.membership({
        centreData: {
          allEntities: true,
          entityData: []
        }
      });
      const membership = this.Membership.create(plainMembership);

      spyOn(this.MembershipChangeCentresModal, 'open')
        .and.returnValue({ result:  this.$q.when(membership.centreData) });
      spyOn(this.domainNotificationService, 'updateErrorModal').and.returnValue(this.$q.when('OK'));

      this.createController(membership);

      this.$httpBackend
        .expectPOST(this.url('centreData', membership.id))
        .respond(400, this.errorReply('simulated bad request'));

      this.controller.changeCentres();
      this.$httpBackend.flush();
      expect(this.domainNotificationService.updateErrorModal).toHaveBeenCalled()
    });

  });

  describe('selecting a user label tag', function() {
    const context = {};

    beforeEach(function () {
      context.entityName = this.UserName.create(this.Factory.userNameDto())
      context.labelSelectedFuncName = 'userLabelSelected'
      context.membershipRemoveFuncName = 'removeUser'
    });

    sharedTagSelectedBehaviour(context);

  })

  describe('selecting a study label tag', function() {
    const context = {};

    beforeEach(function () {
      context.entityName = this.StudyName.create(this.Factory.studyNameDto())
      context.labelSelectedFuncName = 'studyLabelSelected'
      context.membershipRemoveFuncName = 'removeStudy'
    });

    sharedTagSelectedBehaviour(context);

  })

  describe('selecting a centre label tag', function() {
    const context = {};

    beforeEach(function () {
      context.entityName = this.CentreName.create(this.Factory.centreNameDto())
      context.labelSelectedFuncName = 'centreLabelSelected'
      context.membershipRemoveFuncName = 'removeCentre'
    });

    sharedTagSelectedBehaviour(context);

  })

  it('pressing back button returns to correct state', function() {
    this.$state.go = jasmine.createSpy().and.returnValue(null);
    this.createController(this.Membership.create(this.Factory.membership()))
    this.controller.back();
    this.scope.$digest();
    expect(this.$state.go).toHaveBeenCalledWith('home.admin.access.memberships');
  });

  function sharedUpdateBehaviour(context) {

    describe('(shared)', function() {

      beforeEach(function() {
        this.membership = this.Membership.create(this.Factory.membership())
        this.modalInput[context.modalInputFuncName] =
          jasmine.createSpy().and.returnValue({ result: this.$q.when(context.newValue)})
        this.notificationsService.updateError = jasmine.createSpy().and.returnValue(this.$q.when('OK'))
        this.notificationsService.success = jasmine.createSpy().and.returnValue(this.$q.when('OK'))
      });

      it('on update should invoke the update method on entity', function() {
        this.Membership.prototype[context.membershipUpdateFuncName] =
          jasmine.createSpy().and.returnValue(this.$q.when(this.membership))

        this.createController(this.membership)
        this.controller[context.controllerUpdateFuncName]()
        this.scope.$digest()
        expect(this.Membership.prototype[context.membershipUpdateFuncName]).toHaveBeenCalled()
        expect(this.notificationsService.success).toHaveBeenCalled()
      })

      it('error message should be displayed when update fails', function() {
        this.createController(this.membership)
        this.Membership.prototype[context.membershipUpdateFuncName] =
          jasmine.createSpy().and.returnValue(this.$q.reject('simulated error'))
        this.controller[context.controllerUpdateFuncName]()
        this.scope.$digest()
        expect(this.notificationsService.updateError).toHaveBeenCalled()
      })

    })
  }

  function sharedAsyncModalBehaviour(context) {

    describe('(shared)', function() {

      it('can add an entity', function() {
        this.Membership.prototype[context.addEntityFuncName] =
          jasmine.createSpy().and.returnValue(this.$q.when(context.replyMembership))
        this.asyncInputModal.open =
          jasmine.createSpy().and.returnValue({
            result: this.$q.when(entityNameToAsyncModalResult(context.entityName))
          })

        this.createController(context.membership)
        expect(this.controller[context.controllerEntityLabelsFieldName]).toBeArrayOfSize(0)
        this.controller[context.controllerAddEntityFuncName]()
        this.scope.$digest()
        expect(this.controller[context.controllerEntityLabelsFieldName]).toBeArrayOfSize(1)
        compareLabelInfoToEntityName(this.controller[context.controllerEntityLabelsFieldName][0],
                                     context.entityName)
      })

      it('retrieves matching entity names', function() {
        this.createController(context.membership)
        context.entityNameClass.list =
          jasmine.createSpy().and.returnValue(this.$q.when([ context.entityName ]))

        if (context.controllerGetMatchingEntityNamesFuncName) {
          this.controller[context.controllerGetMatchingEntityNamesFuncName]()()
            .then(nameObjs => {
              expect(nameObjs).toBeArrayOfSize(1)
              expect(nameObjs[0].label).toBe(context.entityName.name)
              expect(nameObjs[0].obj).toBe(context.entityName)
            });
          this.scope.$digest()
          expect(context.entityNameClass.list).toHaveBeenCalled()
        } else {
          expect().nothing();
        }
      })

      it('entity labels not modified is user presses the modal cancel button ', function() {
        this.createController(context.membership)
        this.asyncInputModal.open =
          jasmine.createSpy().and.returnValue({ result: this.$q.reject('cancel pressed') })
        expect(this.controller[context.controllerEntityLabelsFieldName]).toBeArrayOfSize(0)
        this.controller[context.controllerAddEntityFuncName]()
        expect(this.controller[context.controllerEntityLabelsFieldName]).toBeArrayOfSize(0)
      })

    })

  }

  function sharedTagSelectedBehaviour(context) {

    describe('(shared)', function() {

      beforeEach(function() {
        this.membership = this.Membership.create(this.Factory.membership())
        this.modalService.modalOkCancel   = jasmine.createSpy().and.returnValue(this.$q.when('OK'))
        this.notificationsService.success = jasmine.createSpy().and.returnValue(this.$q.when(null))
        this.createController(this.membership)
      })

      it('can remove the entity associated with the tag', function() {
        this.Membership.prototype[context.membershipRemoveFuncName] =
          jasmine.createSpy().and.returnValue(this.$q.when(this.membership))
        this.controller[context.labelSelectedFuncName](context.entityName)
        this.scope.$digest()
        expect(this.Membership.prototype[context.membershipRemoveFuncName]).toHaveBeenCalled()
        expect(this.notificationsService.success).toHaveBeenCalled()
        expect(this.modalService.modalOkCancel.calls.count()).toBe(1)
      })

      it('user is informed if removal of the entity attempt fails', function() {
        this.Membership.prototype[context.membershipRemoveFuncName] =
          jasmine.createSpy().and.returnValue(this.$q.reject(this.errorReply('simulated error')))
        this.controller[context.labelSelectedFuncName](context.entityName)
        this.scope.$digest()
        expect(this.Membership.prototype[context.membershipRemoveFuncName]).toHaveBeenCalled()
        expect(this.notificationsService.success).not.toHaveBeenCalled()
        expect(this.modalService.modalOkCancel.calls.count()).toBe(2)
      })

      it('user can press cancel on the verification modal', function() {
        this.Membership.prototype[context.membershipRemoveFuncName] =
          jasmine.createSpy().and.returnValue(this.$q.when(this.membership))
        this.modalService.modalOkCancel = jasmine.createSpy().and.returnValue(this.$q.reject('Cancel'))
        this.controller[context.labelSelectedFuncName](context.entityName)
        this.scope.$digest()
        expect(this.Membership.prototype[context.membershipRemoveFuncName]).not.toHaveBeenCalled()
        expect(this.notificationsService.success).not.toHaveBeenCalled()
        expect(this.modalService.modalOkCancel.calls.count()).toBe(1)
      })

    })

  }

  function compareLabelInfoToEntityName(labelInfo, entityName) {
    expect(labelInfo.label).toBe(entityName.name)
    expect(labelInfo.tooltip).toContain('Remove')
    expect(labelInfo.tooltip).toContain(entityName.name)
    expect(labelInfo.obj.id).toBe(entityName.id)
    expect(labelInfo.obj.name).toBe(entityName.name)
  }

  function entityNameToAsyncModalResult(entityName) {
    return {
      label: entityName.name,
      obj: entityName
    }
  }

})

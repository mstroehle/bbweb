/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2017 Canadian BioSample Repository (CBSR)
 */
/* global angular */

import _ from 'lodash';
import membershipCommonBehaviour from '../../../test/membershipCommonBehaviourSpec';

describe('Membership', function() {

  function SuiteMixinFactory(MebershipSpecCommon) {

    function SuiteMixin() {
      MebershipSpecCommon.call(this);
    }

    SuiteMixin.prototype = Object.create(MebershipSpecCommon.prototype);
    SuiteMixin.prototype.constructor = SuiteMixin;

    SuiteMixin.prototype.jsonObj = function () {
      return this.Factory.membership();
    };

    SuiteMixin.prototype.jsonObjWithEntities = function (studyEntityData, centreEntityData) {
      return _.extend(
        MebershipSpecCommon.prototype.jsonObjWithEntities.call(this, studyEntityData, centreEntityData),
        { userData: [] });
    };

    SuiteMixin.prototype.membershipFromConstructor = function () {
      return new this.Membership();
    };

    SuiteMixin.prototype.membershipFromJson = function (json) {
      return this.Membership.create(json);
    };

    SuiteMixin.prototype.membershipFromJsonAsync = function (json) {
      return this.Membership.asyncCreate(json);
    };

    SuiteMixin.prototype.jsonMembershipWithAllStudies = function () {
      var json = this.Factory.membership();
      json.studyData.allEntities = true;
      return json;
    };

    SuiteMixin.prototype.jsonMembershipWithStudy = function (id, name) {
      var json = this.Factory.membership();
      json.studyData.entityData = [{ id: id, name: name}];
      return json;
    };

    SuiteMixin.prototype.jsonMembershipWithAllCentres = function () {
      var json = this.Factory.membership();
      json.centreData.allEntities = true;
      return json;
    };

    SuiteMixin.prototype.jsonMembershipWithCentre = function (id, name) {
      var json = this.Factory.membership();
      json.centreData.entityData = [{ id: id, name: name}];
      return json;
    };

    SuiteMixin.prototype.jsonMembershipWithEntities = function () {
      var entityData = [ this.jsonEntityData() ];
      return this.jsonObjWithEntities(entityData, entityData);
    };

    SuiteMixin.prototype.fixtures = function (options) {
      var jsonMembership = this.Factory.membership(options),
          membership     = this.Membership.create(jsonMembership);
      return {
        jsonMembership: jsonMembership,
        membership:     membership
      };
    };

    return SuiteMixin;
  }

  beforeEach(() => {
    angular.mock.module('biobankApp', 'biobank.test');
    angular.mock.inject(function(MebershipSpecCommon, ServerReplyMixin) {
      _.extend(this, new SuiteMixinFactory(MebershipSpecCommon).prototype, ServerReplyMixin);

      this.injectDependencies('$rootScope',
                              '$httpBackend',
                              '$httpParamSerializer',
                              'Membership',
                              'EntitySet',
                              'EntityInfo',
                              'Factory',
                              'TestUtils');

      this.TestUtils.addCustomMatchers();

      // used by promise tests
      this.expectMembership = (entity) => {
        expect(entity).toEqual(jasmine.any(this.Membership));
      };

      this.url = url;

      //---

      function url() {
        const args = [ 'access/memberships' ].concat(_.toArray(arguments));
        return MebershipSpecCommon.prototype.url.apply(null, args);
      }
    });
  });

  afterEach(function() {
    this.$httpBackend.verifyNoOutstandingExpectation();
    this.$httpBackend.verifyNoOutstandingRequest();
  });

  describe('base class shared behaviour', function() {
    var context = {};
    beforeEach(function() {
      context.isForAllStudiesFunc  = this.Membership.prototype.isForAllStudies;
      context.isMemberOfStudyFunc  = this.Membership.prototype.isMemberOfStudy;
      context.isForAllCentresFunc  = this.Membership.prototype.isForAllCentres;
      context.isMemberOfCentreFunc = this.Membership.prototype.isMemberOfCentre;
    });
    membershipCommonBehaviour(context);
  });

  it('can be constructed with user data', function() {
    var json = this.Factory.membership({
      userData: [{
        id:   this.Factory.stringNext(),
        name: this.Factory.stringNext()
      }]
    }),
        membership = new this.Membership(json);
    expect(membership).toHaveArrayOfSize('userData', json.userData.length);
    expect(membership.userData[0]).toEqual(jasmine.any(this.EntityInfo));
  });

  describe('when getting a single membership', function() {

    it('can retrieve a single membership', function() {
      var id = this.Factory.stringNext(),
          json = this.Factory.membership();
      this.$httpBackend.whenGET(this.url(id)).respond(this.reply(json));
      this.Membership.get(id).then(this.expectMembership).catch(failTest);
      this.$httpBackend.flush();
    });

    it('fails when getting a membership and it has a bad format', function() {
      this.Membership.SCHEMA.required.forEach((property) => {
        var json = this.Factory.membership(),
            badJson = _.omit(json, property);
        this.$httpBackend.whenGET(this.url(json.id)).respond(this.reply(badJson));
        this.Membership.get(json.id).then(shouldNotFail).catch(shouldFail);
        this.$httpBackend.flush();
      });

      function shouldNotFail() {
        fail('function should not be called');
      }

      function shouldFail(error) {
        expect(error.message).toContain('Missing required property');
      }
    });

  });

  describe('when listing memberships', function() {

    it('can retrieve', function() {
      var memberships = [ this.Factory.membership() ],
          reply = this.Factory.pagedResult(memberships),
          testMembership = (pagedResult) => {
            expect(pagedResult.items).toBeArrayOfSize(1);
            expect(pagedResult.items[0]).toEqual(jasmine.any(this.Membership));
          };

      this.$httpBackend.whenGET(this.url()).respond(this.reply(reply));
      this.Membership.list().then(testMembership).catch(failTest);
      this.$httpBackend.flush();
    });

    it('can use options', function() {
      var optionList = [
            { filter: 'name::test' },
            { page: 2 },
            { limit: 10 }
          ],
          memberships = [ this.Factory.membership() ],
          reply = this.Factory.pagedResult(memberships);

      optionList.forEach((options) => {
        var url = this.url() + '?' + this.$httpParamSerializer(options),
            testMembership = (pagedResult) => {
              expect(pagedResult.items).toBeArrayOfSize(memberships.length);
              pagedResult.items.forEach((membership) => {
                expect(membership).toEqual(jasmine.any(this.Membership));
              });
            };

        this.$httpBackend.whenGET(url).respond(this.reply(reply));
        this.Membership.list(options).then(testMembership).catch(failTest);
        this.$httpBackend.flush();
      });
    });

    it('listing omits empty options', function() {
      var options = { filter: ''},
          memberships = [ this.Factory.membership() ],
          reply = this.Factory.pagedResult(memberships),
          testMembership = (pagedResult) => {
            expect(pagedResult.items).toBeArrayOfSize(memberships.length);
            pagedResult.items.forEach((membership) => {
              expect(membership).toEqual(jasmine.any(this.Membership));
            });
          };

      this.$httpBackend.whenGET(this.url()).respond(this.reply(reply));
      this.Membership.list(options).then(testMembership).catch(failTest);
      this.$httpBackend.flush();
    });

    it('fails when an invalid membership is returned', function() {
      var json = [ _.omit(this.Factory.membership(), 'id') ],
          reply = this.Factory.pagedResult(json);

      this.$httpBackend.whenGET(this.url()).respond(this.reply(reply));
      this.Membership.list().then(listFail).catch(shouldFail);
      this.$httpBackend.flush();

      function listFail() {
        fail('function should not be called');
      }

      function shouldFail(error) {
        expect(error).toStartWith('invalid memberships from server');
      }
    });

  });

  it('can add a membership', function() {
    var options =
        {
          userData: [ this.Factory.membershipEntityData() ],
          studyData: {
            allEntities: false,
            entityData: [ this.Factory.membershipEntityData() ]
          },
          centreData: {
            allEntities: false,
            entityData: [ this.Factory.membershipEntityData() ]
          }
        },
        f       = this.fixtures(options),
        reqJson = _.pick(f.jsonMembership, 'name', 'description');

    reqJson.userIds    = f.jsonMembership.userData.map(getEntityDataIds);
    reqJson.allStudies = f.jsonMembership.studyData.allEntities;
    reqJson.studyIds   = f.jsonMembership.studyData.entityData.map(getEntityDataIds);
    reqJson.allCentres = f.jsonMembership.centreData.allEntities;
    reqJson.centreIds  = f.jsonMembership.centreData.entityData.map(getEntityDataIds);

    this.$httpBackend.expectPOST(this.url(), reqJson).respond(this.reply(f.jsonMembership));
    f.membership.add().then(this.expectMembership).catch(failTest);
    this.$httpBackend.flush();

    function getEntityDataIds(entityInfo) {
      return entityInfo.id;
    }

  });

  describe('when removing a membership', function() {

    it('should remove a membership', function() {
      var membership = this.Membership.create(this.Factory.membership()),
          url = this.url(membership.id, membership.version);

      this.$httpBackend.expectDELETE(url).respond(this.reply(true));
      membership.remove();
      this.$httpBackend.flush();
    });

    it('cannot remove a new membership', function() {
      var membership = new this.Membership();
      expect(() => {
        membership.remove();
      }).toThrowError(/membership has not been persisted/);
    });

  });

  it('can update the name', function() {
    var f    = this.fixtures(),
        name = this.Factory.stringNext();

    this.updateEntity.call(this,
                           f.membership,
                           'updateName',
                           name,
                           this.url('name', f.membership.id),
                           { name: name },
                           f.jsonMembership,
                           this.expectMembership,
                           failTest);
  });

  it('can update the description', function() {
    var f           = this.fixtures(),
        description = this.Factory.stringNext();

    this.updateEntity.call(this,
                           f.membership,
                           'updateDescription',
                           undefined,
                           this.url('description', f.membership.id),
                           { },
                           f.jsonMembership,
                           this.expectMembership,
                           failTest);

    this.updateEntity.call(this,
                           f.membership,
                           'updateDescription',
                           description,
                           this.url('description', f.membership.id),
                           { description: description },
                           f.jsonMembership,
                           this.expectMembership,
                           failTest);
  });

  it('can add a user', function() {
    var f      = this.fixtures(),
        userId = this.Factory.stringNext();

    this.updateEntity.call(this,
                           f.membership,
                           'addUser',
                           userId,
                           this.url('user', f.membership.id),
                           { userId: userId },
                           f.jsonMembership,
                           this.expectMembership,
                           failTest);
  });

  describe('when removing a user', function() {

    it('can remove a user', function() {
      var f       = this.fixtures({ userData: [ this.Factory.membershipEntityData() ] }),
          userId  = f.jsonMembership.userData[0].id,
          url     = this.url('user', f.membership.id, f.membership.version, userId);

      this.$httpBackend.expectDELETE(url).respond(this.reply(f.jsonMembership));
      f.membership.removeUser(userId)
        .then((reply) => {
          expect(reply).toEqual(jasmine.any(this.Membership));
        })
        .catch(failTest);
      this.$httpBackend.flush();
    });

    it('cannot remove a user from a new membership', function() {
      var membership = new this.Membership();
      expect(() => {
        membership.removeUser();
      }).toThrowError(/membership has not been persisted/);
    });

  });

  it('can assign to be for all studies', function() {
    var f = this.fixtures();

    this.updateEntity.call(this,
                           f.membership,
                           'allStudies',
                           undefined,
                           this.url('allStudies', f.membership.id),
                           {},
                           f.jsonMembership,
                           this.expectMembership,
                           failTest);
  });

  it('can add a study', function() {
    var f       = this.fixtures(),
        studyId = this.Factory.stringNext();

    this.updateEntity.call(this,
                           f.membership,
                           'addStudy',
                           studyId,
                           this.url('study', f.membership.id),
                           { studyId: studyId },
                           f.jsonMembership,
                           this.expectMembership,
                           failTest);
  });

  it('can assign to be for all centres', function() {
    var f = this.fixtures();

    this.updateEntity.call(this,
                           f.membership,
                           'allCentres',
                           undefined,
                           this.url('allCentres', f.membership.id),
                           {},
                           f.jsonMembership,
                           this.expectMembership,
                           failTest);
  });

  it('can add a centre', function() {
    var f        = this.fixtures(),
        centreId = this.Factory.stringNext();

    this.updateEntity.call(this,
                           f.membership,
                           'addCentre',
                           centreId,
                           this.url('centre', f.membership.id),
                           { centreId: centreId },
                           f.jsonMembership,
                           this.expectMembership,
                           failTest);
  });

  describe('can remove a study', function() {
    var context = {};
    beforeEach(function () {
      context.entityName = 'study';
      context.jsonWithEntityFunc = this.jsonMembershipWithStudy;
      context.removeEntityFunc = this.Membership.prototype.removeStudy;
    });
    removeEntitySharedBehaviour(context);
  });

  describe('can remove a centre', function() {
    var context = {};
    beforeEach(function () {
      context.entityName = 'centre';
      context.jsonWithEntityFunc = this.jsonMembershipWithCentre;
      context.removeEntityFunc = this.Membership.prototype.removeCentre;
    });
    removeEntitySharedBehaviour(context);
  });

  function removeEntitySharedBehaviour(context) {

    describe('(shared) removes an entity', function () {

      it('makes the correct REST API call', function() {
        var entityId   = this.Factory.stringNext(),
            fieldName  = context.entityName + 'Data',
            json       = context.jsonWithEntityFunc.call(this, entityId, this.Factory.stringNext()),
            membership = this.membershipFromJson(json),
            url        = this.url(context.entityName, membership.id, membership.version, entityId);

        expect(membership[fieldName].allEntities).toBeFalse();
        expect(membership[fieldName].entityData).toBeNonEmptyArray();

        this.$httpBackend.expectDELETE(url).respond(this.reply(json));

        context.removeEntityFunc.call(membership, entityId)
          .then((reply) => {
            expect(reply).toEqual(jasmine.any(this.Membership));
          })
          .catch(failTest);
        this.$httpBackend.flush();
      });
    });

    it('throws a domain error if membership has not been persited yet', function() {
      var membership = this.membershipFromConstructor();

      expect(() => {
        context.removeEntityFunc.call(membership, this.Factory.stringNext());
      }).toThrowError(/membership has not been persisted/);
    });

  }

  // used by promise tests
  function failTest(error) {
    expect(error).toBeUndefined();
  }

});

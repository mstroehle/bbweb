/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */


import _ from 'lodash'

/*
 * Angular factory for Users.
 */
/* @ngInject */
function UserFactory($q,
                     $log,
                     biobankApi,
                     DomainEntity,
                     ConcurrencySafeEntity,
                     DomainError,
                     UserState,
                     RoleIds,
                     UserMembership) {

  /**
   * Information for a user of the system.
   * @extends domain.ConcurrencySafeEntity
   * @memberOf domain.users
   */
  class User extends ConcurrencySafeEntity {

    /**
     * Use this contructor to create a new User to be persited on the server. Use {@link
     * domain.users.User.create|create()} or {@link domain.users.User.asyncCreate|asyncCreate()} to create
     * objects returned by the server.
   *
   * @param {object} [obj={}] - An initialization object whose properties are the same as the members from
   * this class. Objects of this type are usually returned by the server's REST API.
   */
    constructor(obj = { state: UserState.REGISTERED }) {
      super(User.SCHEMA, obj)

      /**
       * The user's full name.
       *
       * @name domain.users.User#name
       * @type {string}
       */

      /**
       * The user's email address.
       *
       * @name domain.users.User#email
       * @type {string}
       */

      /**
       * The user's optional avatar URL.
       *
       * @name domain.users.User#avatarUrl
       * @type {string}
       */

      /**
       * Information for the roles this user has.
       *
       * @name domain.users.User#roleData
       * @type {Array<domain.EntityInfo>}
       */

      /**
       * The state can be one of: registered, active or locked.
       *
       * @name domain.users.User#state
       * @type {domain.users.UserState}
       */

      this.membership = new UserMembership(_.get(obj, 'membership', {}));
    }

    register(password) {
      var json = {
        name:      this.name,
        email:     this.email,
        password:  password,
        avatarUrl: this.avatarUrl
      };
      return biobankApi.post(User.url(), json).then(User.asyncCreate);
    }

    /** @protected */
    update(url, additionalJson) {
      return super.update(url, additionalJson).then(User.asyncCreate);
    }

    updateName(name) {
      return this.update(User.url('update', this.id), { property: 'name', value: name });
    }

    updateEmail(email) {
      return this.update(User.url('update', this.id), { property: 'email', value: email });
    }

    updatePassword(currentPassword, newPassword) {
      return this.update(User.url('update', this.id),
                         {
                           property: 'password',
                           value: {
                             currentPassword: currentPassword,
                             newPassword:     newPassword
                           }
                         });
    }

    updateAvatarUrl(avatarUrl) {
      return this.update(User.url('update', this.id), { property: 'avatarUrl', value: avatarUrl });
    }

    activate() {
      if (this.state !== UserState.REGISTERED) {
        throw new DomainError('user state is not registered: ' + this.state);
      }
      return this.update(User.url('update', this.id), { property: 'state', value: 'activate' });
    }

    lock() {
      if ((this.state !== UserState.REGISTERED) && (this.state !== UserState.ACTIVE))  {
        throw new DomainError('user state is not registered or active: ' + this.state);
      }
      return this.update(User.url('update', this.id), { property: 'state', value: 'lock' });
    }

    unlock() {
      if (this.state !== UserState.LOCKED) {
        throw new DomainError('user state is not locked: ' + this.state);
      }
      return this.update(User.url('update', this.id), { property: 'state', value: 'unlock' });
    }

    addRole(roleId) {
      if (this.roleData.find(role => role.id === roleId) !== undefined) {
        throw new DomainError('user already has role: ' + roleId);
      }
      return this.update(User.url('roles', this.id),
                         {
                           expectedVersion: this.version,
                           roleId:          roleId
                         });
    }

    removeRole(roleId) {
      if (this.roleData.find(role => role.id === roleId) === undefined) {
        throw new DomainError('user does not have role: ' + roleId);
      }
      return biobankApi.del(User.url('roles', this.id, this.version, roleId))
        .then(User.asyncCreate);
    }

    isRegistered() {
      return (this.state === UserState.REGISTERED);
    }

    isActive() {
      return (this.state === UserState.ACTIVE);
    }

    isLocked() {
      return (this.state === UserState.LOCKED);
    }

    hasRoles() {
      return (this.roleData.length > 0);
    }

    hasRole(roleSlug) {
      return this.roleData.find(role => role.slug === roleSlug) !== undefined;
    }

    hasAnyRoleOf(...roleIds) {
      var ids = _.map(this.roleData, 'ids');
      return roleIds.filter(id => ids.includes(id)).length > 0;
    }

    hasStudyAdminRole() {
      return this.hasRole(RoleIds.StudyAdministrator);
    }

    hasCentreAdminRole() {
      return this.hasRole(RoleIds.CentreAdministrator);
    }

    hasUserAdminRole() {
      return this.hasRole(RoleIds.UserAdministrator);
    }

    hasAdminRole() {
      return this.hasAnyRoleOf(RoleIds.StudyAdministrator,
                               RoleIds.CentreAdministrator,
                               RoleIds.UserAdministrator);
    }

    hasSpecimenCollectorRole() {
      return this.hasRole(RoleIds.SpecimenCollector);
    }


    hasShippingUserRole() {
      return this.hasRole(RoleIds.ShippingUser);
    }

    getRoleNames() {
      return this.roleData.map(role => role.name).join(', ');
    }

    static url (...paths){
      return DomainEntity.url.apply(null, [ 'users' ].concat(paths));
    }

    /** @protected */
    static isValid(obj) {
      return ConcurrencySafeEntity.isValid(User.SCHEMA, [UserMembership.SCHEMA], obj);
    }

    /**
     * Creates a User, but first it validates <code>obj</code> to ensure that it has a valid schema.
     *
     * @param {object} [obj={}] - An initialization object whose properties are the same as the members from
     * this class. Objects of this type are usually returned by the server's REST API.
     *
     * @returns {domain.users.User} A user created from the given object.
     *
     * @see {@link domain.users.User.asyncCreate|asyncCreate()} when you need to create
     * a user within asynchronous code.
     */
    static create(obj) {
      var validation = User.isValid(obj);
      if (!validation.valid) {
        $log.error(validation.message);
        throw new DomainError(validation.message);
      }
      return new User(obj);
    }

    /**
     * Creates a User from a server reply, but first validates that <tt>obj</tt> has a valid schema.
     * <i>Meant to be called from within promise code.</i>
     *
     * @param {object} [obj={}] - An initialization object whose properties are the same as the members from
     * this class. Objects of this type are usually returned by the server's REST API.
     *
     * @returns {Promise<domain.users.User>} A user wrapped in a promise.
     *
     * @see {@link domain.users.User.create|create()} when not creating a User within asynchronous code.
     */
    static asyncCreate(obj) {
      var result;

      try {
        result = User.create(obj);
        return $q.when(result);
      } catch (e) {
        return $q.reject(e);
      }
    }

    /**
     * Retrieves a User from the server.
     *
     * @param {string} slug the slug for the user to retrieve.
     *
     * @returns {Promise<domain.users.User>} The user within a promise.
     */
    static get(slug) {
      return biobankApi.get(User.url(slug)).then(User.asyncCreate);
    }

    /**
     * Used to list users.
     *
     * @param {object} options - The options to use.
     *
     * @param {string} options.filter The filter expression to use on user to refine the list.
     *
     * @param {string} options.sort Users can be sorted by 'name', 'email' or by 'state'. Values other
     * than these yield an error. Use a minus sign prefix to sort in descending order.
     *
     * @param {int} options.page If the total results are longer than limit, then page selects which
     * users should be returned. If an invalid value is used then the response is an error.
     *
     * @param {int} options.limit The total number of users to return per page. The maximum page size is
     * 10. If a value larger than 10 is used then the response is an error.
     *
     * @returns {Promise<biobank.domain.PagedResult>} A promise of {@link biobank.domain.PagedResult} with
     * items of type {@link domain.users.User}.
     */
    static list(options) {
      var validKeys = [ 'filter',
                        'sort',
                        'page',
                        'limit'
                      ],
          params;

      options = options || {};
      params = _.omitBy(_.pick(options, validKeys), function (value) {
        return value === '';
      });

      return biobankApi.get(User.url('search'), params).then(function(reply) {
        // reply is a paged result
        var deferred = $q.defer();
        try {
          reply.items = reply.items.map((obj) => User.create(obj));
          deferred.resolve(reply);
        } catch (e) {
          deferred.reject('invalid users from server');
        }
        return deferred.promise;
      });
    }
  }


  User.SCHEMA = ConcurrencySafeEntity.createDerivedSchema({
    id: 'User',
    properties: {
      'slug':         { 'type': 'string' },
      'name':         { 'type': 'string' },
      'email':        { 'type': 'string' },
      'avatarUrl':    { 'type': [ 'string', 'null' ] },
      'state':        { 'type': 'string' },
      'roleData':     { 'type': 'array', 'items': { '$ref': 'EntityInfo' } },
      'membership':   {
        'oneOf': [
          { 'type': 'null' },
          { 'type': 'object', '$ref': '#UserMembership' }
        ]
      }
    },
    required: [ 'slug', 'name', 'state', 'email', 'roleData' ]
  });

  return User;
}

export default ngModule => ngModule.factory('User', UserFactory)

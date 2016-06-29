(ns lupapalvelu.change-email
  (:require [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [lupapalvelu.vetuma :as vetuma]))

(defn- notify-init-email-change [user new-email]
  (let [token-id (token/make-token :change-email user {:new-email new-email}
                                   :auto-consume false
                                   :ttl ttl/change-email-token-ttl)
        token (token/get-token token-id)]
    (notifications/notify! :change-email
                           {:user (assoc user :email new-email)
                            :data {:old-email (:email user)
                                   :new-email new-email
                                   :token     token}})))

(defn init-email-change [user email]
  (let [email (usr/canonize-email email)
        dummy-user (usr/get-user-by-email email)]
    (if (or (not dummy-user) (usr/dummy? dummy-user))
      (notify-init-email-change user email)
      (fail :error.duplicate-email))))

(defn- remove-dummy-auths-where-user-already-has-auth [user-id new-email]
  (mongo/update-by-query :applications
                         {:auth.id user-id}
                         {$pull {:auth {:username         new-email
                                        :invite.user.role "dummy"}}}))

(defn- change-auths-dummy-id-to-user-id [{:keys [id username email] :as user} dummy-id]
  (mongo/update-by-query :applications
                         {:auth {$elemMatch {:id dummy-id
                                             :invite.user.role "dummy"}}}
                         {$set {:auth.$.id id
                                :auth.$.username username
                                :auth.$.invite.email email
                                :auth.$.invite.user (usr/summary user)}}))

(defn- change-email-with-token [token stamp]
  {:pre [(map? token)]}

  (let [vetuma-data (vetuma/get-user stamp)
        new-email (get-in token [:data :new-email])
        {hetu :personId old-email :email id :id :as user} (usr/get-user-by-id! (:user-id token))]
    (cond
      (not= (:token-type token) :change-email) (fail! :error.token-not-found)
      (not= hetu (:userid vetuma-data)) (fail! :error.personid-mismatch)
      (usr/email-in-use? new-email) (fail! :error.duplicate-email))

    (when-let [{dummy-id :id :as dummy-user} (usr/get-user-by-email new-email)]
      (when (usr/dummy? dummy-user)
        (remove-dummy-auths-where-user-already-has-auth id new-email)
        (change-auths-dummy-id-to-user-id user dummy-id)
        (usr/remove-dummy-user dummy-id)))

    ; Strictly this atomic update is enough.
    ; Access to applications is determined by user id.
    (usr/update-user-by-email old-email
                              {:personId hetu}
                              {$set {:username new-email :email new-email}})

    ; Update application.auth arrays.
    ; They might have duplicates due to old bugs, ensure everything is updated.
    (loop [n 1]
      (when (pos? n)
        ; loop exits when no applications with the old username were found
        (recur (mongo/update-by-query :applications
                                      {:auth {$elemMatch {:id (:id user)
                                                          :username old-email}}}
                                      {$set {:auth.$.username new-email}}))))

    ; Also update emails in invite auths
    (loop [n 1]
      (when (pos? n)
        (recur (mongo/update-by-query :applications
                                      {:auth {$elemMatch {:id id
                                                          :invite.email old-email}}}
                                      {$set {:auth.$.invite.email new-email
                                             :auth.$.invite.user.username new-email}}))))

    ; Cleanup tokens
    (vetuma/consume-user stamp)
    (token/get-token (:id token) :consume true)

    ; Send notifications
    (notifications/notify! :email-changed {:user user, :data {:new-email new-email}})

    (ok)))

(defn change-email [tokenId stamp]
  (if-let [token (token/get-token tokenId)]
    (change-email-with-token token stamp)
    (fail :error.token-not-found)))
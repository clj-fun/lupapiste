(ns lupapalvelu.authorization
  (:require [lupapalvelu.user :as usr]
            [lupapalvelu.application-schema :as aps]
            [schema.core :refer [defschema] :as sc]
            [sade.schemas :as ssc]
            [sade.strings :as ss]))

;;
;; Roles
;;

(def all-authenticated-user-roles #{:applicant :authority :oirAuthority :authorityAdmin :admin})
(def all-user-roles (conj all-authenticated-user-roles :anonymous :rest-api :trusted-etl))

(def default-authz-writer-roles #{:owner :writer :foreman})
(def default-authz-reader-roles (conj default-authz-writer-roles :reader :guest :guestAuthority))
(def all-authz-writer-roles (conj default-authz-writer-roles :statementGiver))
(def all-authz-roles (conj all-authz-writer-roles :reader :guest :guestAuthority))

(def default-org-authz-roles #{:authority :approver})
(def commenter-org-authz-roles (conj default-org-authz-roles :commenter))
(def reader-org-authz-roles (conj commenter-org-authz-roles :reader))
(def all-org-authz-roles (conj reader-org-authz-roles :authorityAdmin :tos-editor :tos-publisher :archivist))

(def default-user-authz {:query default-authz-reader-roles
                         :export default-authz-reader-roles
                         :command default-authz-writer-roles
                         :raw default-authz-writer-roles})

;;
;; Schema
;;

(defschema Invite
  {(sc/optional-key :role)           (apply sc/enum all-authz-roles)
   (sc/optional-key :path)           sc/Str
   :email                            ssc/Email
   (sc/optional-key :application)    aps/ApplicationId ; FIXME delete key after prod is migrated
   :created                          ssc/Timestamp
   :inviter                          usr/SummaryUser
   (sc/optional-key :documentName)   sc/Str
   (sc/optional-key :documentId)     ssc/ObjectIdStr
   :user                             usr/SummaryUser
   (sc/optional-key :title)          sc/Str
   (sc/optional-key :text)           sc/Str})

(defschema CompanyInvite
  {:user {:id ssc/ObjectIdStr}})

(defschema Auth
  {:id                               (sc/if ss/in-lower-case? ssc/Username ssc/ObjectIdStr)
   :username                         ssc/Username
   :firstName                        sc/Str
   :lastName                         sc/Str
   :role                             (apply sc/enum all-authz-roles)
   (sc/optional-key :type)           (sc/enum :company :owner)
   (sc/optional-key :name)           sc/Str
   (sc/optional-key :y)              ssc/FinnishY
   (sc/optional-key :unsubscribed)   sc/Bool
   (sc/optional-key :statementId)    ssc/ObjectIdStr
   (sc/optional-key :invite)         (sc/if :email Invite CompanyInvite)
   (sc/optional-key :inviteAccepted) ssc/Timestamp
   (sc/optional-key :inviter)        (sc/if map? usr/SummaryUser ssc/ObjectIdStr)})

;;
;; Auth utils
;;

(defn get-auths-by-roles
  "Returns sequence of all auth-entries in an application with the
  given roles. Each role can be keyword or string."
  [{auth :auth} roles]
  (let [role-set (->> roles (map name) set)]
    ;; Roles in auths can also be keywords or strings.
    ;; (name nil) causes NPE so default value is needed.
    (filter #(contains? role-set (name (get % :role ""))) auth)))

(defn get-auths-by-role
  [application role]
  (get-auths-by-roles application [role]))

(defn get-auths [{auth :auth} user-id]
  (filter #(= (:id %) user-id) auth))

(defn get-auth [application user-id]
  (first (get-auths application user-id)))

(defn has-auth? [{auth :auth} user-id]
  (or (some (partial = user-id) (map :id auth)) false))

(defn has-auth-role? [{auth :auth} user-id role]
  (has-auth? {:auth (get-auths-by-role {:auth auth} role)} user-id))

(defn create-invite-auth [inviter invited application-id role timestamp & [text document-name document-id path]]
  {:pre [(seq inviter) (seq invited) application-id role timestamp]}
  (let [invite (cond-> {:created      timestamp
                        :email        (:email invited)
                        :role         role
                        :user         (usr/summary invited)
                        :inviter      (usr/summary inviter)}
                 (not (nil? path))               (assoc :path path)
                 (not (ss/blank? text))          (assoc :text text)
                 (not (ss/blank? document-name)) (assoc :documentName document-name)
                 (not (ss/blank? document-id))   (assoc :documentId document-id))]
    (assoc (usr/user-in-role invited :reader) :invite invite)))

;;
;; Authz checkers
;;

(defn user-authz? [roles application user]
  {:pre [(set? roles)]}
  (let [roles-in-app  (map (comp keyword :role) (get-auths application (:id user)))]
    (some roles roles-in-app)))

(defn org-authz
  "Returns user's org authz in given organization, nil if not found"
  [organization user]
  (get-in user [:orgAuthz (keyword organization)]))

(defn has-organization-authz-roles?
  "Returns true if user has requested roles in organization"
  [requested-authz-roles {organization :organization} user]
  (let [user-org-authz (org-authz organization user)]
    (and (usr/authority? user) requested-authz-roles (some requested-authz-roles user-org-authz))))

(ns lupapalvelu.change-email-api
  (:require [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.action :refer [defquery defcommand defraw email-validator] :as action]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.user :as usr]
            [lupapalvelu.company :as com]
            [lupapalvelu.change-email :as change-email]))

(defn change-email-link [lang token]
  (str (env/value :host) "/app/" lang "/welcome#!/email/" token))

(notifications/defemail :change-email
  {:recipients-fn notifications/from-user
   :model-fn (fn [{data :data} conf recipient]
               (let [{:keys [id expires]} (:token data)]
                 (merge
                   (select-keys data [:old-email :new-email])
                   {:name    (:firstName recipient)
                    :expires (util/to-local-datetime expires)
                    :link-fi (change-email-link "fi" id)
                    :link-sv (change-email-link "sv" id)})))})

(notifications/defemail :email-changed
  {:recipients-fn (fn [{user :user}]
                    (if-let [company-id (get-in user [:company :id])]
                      (->> (com/find-company-admins company-id)
                           (remove #(= (:id %) (:id user)))
                           (cons user))
                      [user]))
   :model-fn (fn [{:keys [user data]} conf recipient]
               {:name      (:firstName recipient)
                :old-email (:email user)
                :new-email (:new-email data)})})

(defn- has-person-id? [user]
  (if-let [user-id (:id user)]
    (let [full-user (if (contains? user :personId) user (usr/get-user-by-id! user-id))]
      (not (ss/blank? (:personId full-user))))
    false))

(defn- validate-has-person-id [{user :user} _]
  (when-not (has-person-id? user)
    (fail :error.unauthorized)))

(defcommand change-email-init
  {:parameters [email]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :pre-checks [validate-has-person-id]
   :description "Starts the workflow for changing user password"}
  [{user :user}]
  (change-email/init-email-change user email))

(defcommand change-email
  {:parameters [tokenId stamp]
   :input-validators [(partial action/non-blank-parameters [:tokenId :stamp])]
   :user-roles #{:anonymous}}
  [_]
  (change-email/change-email tokenId stamp))

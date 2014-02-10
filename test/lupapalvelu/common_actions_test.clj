(ns lupapalvelu.common-actions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.test-util :refer :all]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.common-actions :as ca]))

(fact (executed "ping" {:action "ping"}) => {:ok true :text "pong"})
(fact (executed {:action "ping"}) => {:ok true :text "pong"})

(testable-privates lupapalvelu.action user-is-not-allowed-to-access?)

(facts "Allowed actions for statementGiver"
  (let [allowed-actions #{:give-statement
                          :application
                          :allowed-actions
                          :validate-doc
                          :add-comment
                          :attachment-types
                          :should-see-unsubmitted-statements
                          :view-attachment
                          :delete-attachment
                          :set-attachment-type
                          :upload-attachment
                          :download-all-attachments
                          :download-attachment
                          :delete-attachment-version}
        user {:id "user123" :organizations [] :role :applicant}
        application {:organization "999-R" :auth [{:id "user123" :role "statementGiver"}]}]
    (doseq [command (ca/foreach-action user {} application)
            :let [action (keyword (:action command))
                  result (doc-result (user-is-not-allowed-to-access? command application) action)]]
      (if (allowed-actions action)
        result => (doc-check nil?)
        result => (doc-check = {:ok false :text "error.unauthorized"})))))

(ns lupapalvelu.xml.asianhallinta.verdict
  (:require [sade.core :refer [ok fail fail!] :as core]
            [sade.xml :as xml]
            [pandect.core :as pandect]
            [taoensso.timbre :refer [error]]
            [me.raynes.fs :as fs]
            [monger.operators :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as org]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.state-machine :as sm]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [sade.common-reader :as cr]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.attachment :as attachment]))

(defn- error-and-fail! [error-msg fail-key]
  (error error-msg)
  (fail! fail-key))

(defn- unzip-file [path-to-zip target-dir]
  (if-not (and (fs/exists? path-to-zip) (fs/exists? target-dir))
    (error-and-fail! (str "Could not find file " path-to-zip) :error.integration.asianhallinta-file-not-found)
    (util/unzip path-to-zip target-dir)))

(defn- ensure-attachments-present! [unzipped-path attachments]
  (let [attachment-paths (->> attachments
                              (map :LinkkiLiitteeseen)
                              (map fs/base-name))]
    (doseq [filename attachment-paths]
      (when (empty? (fs/find-files unzipped-path (ss/escaped-re-pattern filename)))
        (error-and-fail! (str "Attachment referenced in XML was not present in zip: " filename) :error.integration.asianhallinta-missing-attachment)))))

(defn- build-verdict [{:keys [AsianPaatos]} timestamp]
  {:id              (mongo/create-id)
   :kuntalupatunnus (:AsianTunnus AsianPaatos)
   :timestamp timestamp
   :source    "ah"
   :paatokset [{:paatostunnus (:PaatoksenTunnus AsianPaatos)
                :paivamaarat  {:anto (cr/to-timestamp (:PaatoksenPvm AsianPaatos))}
                :poytakirjat  [{:paatoksentekija (:PaatoksenTekija AsianPaatos)
                                :paatospvm       (cr/to-timestamp (:PaatoksenPvm AsianPaatos))
                                :pykala          (:Pykala AsianPaatos)
                                :paatoskoodi     (or (:PaatosKoodi AsianPaatos) (:PaatoksenTunnus AsianPaatos)) ; PaatosKoodi is not required
                                :id              (mongo/create-id)}]}]})

(defn- insert-attachment! [application attachment unzipped-path verdict-id poytakirja-id timestamp]
  (let [filename      (fs/base-name (:LinkkiLiitteeseen attachment))
        file          (fs/file (s/join "/" [unzipped-path filename]))
        file-size     (.length file)
        orgs          (org/resolve-organizations
                        (:municipality application)
                        (:permitType application))
        batchrun-user (user/batchrun-user (map :id orgs))
        target        {:type "verdict" :id verdict-id :poytakirjaId poytakirja-id}
        attachment-id (mongo/create-id)]
    (attachment/attach-file! application
                             {:filename filename
                              :size file-size
                              :content file
                              :attachment-id attachment-id
                              :attachment-type {:type-group "muut" :type-id "muu"}
                              :target target
                              :required false
                              :locked true
                              :user batchrun-user
                              :created timestamp
                              :state :ok})))

(defn process-ah-verdict [path-to-zip ftp-user system-user]
  (let [tmp-dir (fs/temp-dir (str "ah"))]
    (try
      (let [unzipped-path (unzip-file path-to-zip tmp-dir)
            xmls (fs/find-files unzipped-path #".*xml$")
            timestamp (core/now)]
        ; path must contain exactly one xml
        (when-not (= (count xmls) 1)
          (error-and-fail! (str "Expected to find one xml, found " (count xmls) " for user " ftp-user) :error.integration.asianhallinta-wrong-number-of-xmls))

                                        ; parse XML
        (let [parsed-xml (-> (first xmls) slurp xml/parse cr/strip-xml-namespaces xml/xml->edn)
              attachments (-> (get-in parsed-xml [:AsianPaatos :Liitteet])
                              (util/ensure-sequential :Liite)
                              :Liite)
              application-id (get-in parsed-xml [:AsianPaatos :HakemusTunnus])]
                                        ; Check that all referenced attachments were included in zip
          (ensure-attachments-present! unzipped-path attachments)

          (when-not application-id
            (error-and-fail! (str "ah-verdict - Application id is nil for user " ftp-user) :error.integration.asianhallinta.no-application-id))

                                        ; Create verdict
                                        ; -> fetch application
          (let [application (domain/get-application-no-access-checking application-id)
                current-state (keyword (:state application))
                verdict-given-state (sm/verdict-given-state application)
                org-scope (org/resolve-organization-scope (:municipality application) (:permitType application))]

                                        ; -> check ftp-user has right to modify app
            (when-not (= ftp-user (get-in org-scope [:caseManagement :ftpUser]))
              (error-and-fail! (str "FTP user " ftp-user " is not allowed to make changes to application " application-id) :error.integration.asianhallinta.unauthorized))

                                        ; -> check that application is in correct state
            (when-not (#{:constructionStarted :sent verdict-given-state} current-state)
              (error-and-fail!
               (str "Application " application-id " in wrong state (" current-state ") for asianhallinta verdict") :error.integration.asianhallinta.wrong-state))

                                        ; -> build update clause
                                        ; -> update-application
            (let [new-verdict   (build-verdict parsed-xml timestamp)
                  command       (action/application->command application)
                  poytakirja-id (get-in new-verdict [:paatokset 0 :poytakirjat 0 :id])
                  update-clause (util/deep-merge
                                 {$push {:verdicts new-verdict}, $set  {:modified timestamp}}
                                 (when (= :sent (keyword (:state application)))
                                   (application/state-transition-update verdict-given-state timestamp system-user)))]

              (action/update-application command update-clause)
              (doseq [attachment attachments]
                (insert-attachment!
                 application
                 attachment
                 unzipped-path
                 (:id new-verdict)
                 poytakirja-id
                 timestamp))
              (notifications/notify! :application-verdict command)
              (ok)))))
      (catch Throwable e
        (if-let [error-key (some-> e ex-data :text)]
          (fail error-key)
          (throw e)))
      (finally
        (fs/delete-dir tmp-dir)))))

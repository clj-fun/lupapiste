(ns lupapalvelu.smoketest.application-smoke-tests
  (:require [lupapiste.mongocheck.core :refer [mongocheck]]
            [lupapiste.mongocheck.checks :as checks]
            [lupapalvelu.states :as states]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.application :as app]
            [lupapalvelu.server] ; ensure all namespaces are loaded
            [lupapalvelu.attachment :as att]
            [lupapalvelu.authorization :as auth]
            [sade.strings :as ss]
            [sade.schemas :as ssc])
  (:import [schema.utils.ErrorContainer]))

(defn- validate-doc [ignored-errors application {:keys [id schema-info data] :as doc}]
  (if (and (:name schema-info) (:version schema-info))
    (let [ignored (set ignored-errors)
          schema (model/get-document-schema doc)
          info (model/document-info doc schema)
          results (filter
                    (fn [{result :result}]
                      (and (= :err (first result)) (not (ignored (second result)))))
                    (flatten (model/validate-fields application info nil data [])))]
      (when (seq results)
        {:document-id id :schema-info schema-info :results results}))
    {:document-id id :schema-info schema-info :results "Schema name or version missing"}))

(defn- validate-documents [ignored-errors {documents :documents :as application}]
  (let [results (filter seq (map (partial validate-doc ignored-errors application) documents))]
    (when (seq results)
      results)))

(defn- validate-tasks [ignored-errors {tasks :tasks :as application}]
  (let [results (filter seq (map (partial validate-doc ignored-errors application) tasks))]
    (when (seq results)
      results)))

(defn- validate-state [{state :state :as application}]
  (when (ss/blank? state)
    {:result "Missing state"}))

;; Every document is valid.
(mongocheck :applications (partial validate-documents []) :documents :state :auth)

(mongocheck :submitted-applications (partial validate-documents ["illegal-hetu"]) :documents :state :auth)

;; Tasks are valid
(mongocheck :applications (partial validate-tasks []) :tasks :state :auth)

;; All applications have state
(mongocheck :applications validate-state :state)

(def coerce-auth (ssc/json-coercer auth/Auth))

(defn validate-auth-against-schema [{id :id :as auth}]
  (let [coercion-result (coerce-auth auth)]
    (when (instance? schema.utils.ErrorContainer coercion-result)
      {:auth-id id
       :error "Not valid auth"
       :coercion-result coercion-result})))

(defn validate-auth-array [{auths :auth}]
  (->> (map validate-auth-against-schema auths)
       (remove nil?)
       seq))

;; All auths are valid
(mongocheck :applications validate-auth-array :auth)

;; Latest attachment version and latestVersion match
(defn validate-latest-version [{id :id versions :versions latestVersion :latestVersion}]
  (when-not (or (empty? versions) (= latestVersion (last versions)))
    {:attachment-id id :error "latest version does not match last element of versions array"}))

(def coerce-attachment (ssc/json-coercer att/Attachment))

(defn validate-attachment-against-schema [{id :id :as attachment}]
  (let [{{{type-id :type-id type-group :type-group} :type} :error :as coercion-result} (coerce-attachment attachment)]
    (when (instance? schema.utils.ErrorContainer coercion-result)
      {:attachment-id id
       :error "Not valid attachment"
       :coercion-result  (cond-> coercion-result ; truncate long enum prints from type-group and type-id by taking only value
                           type-group (update-in [:error :type :type-group] #(list 'invalid-type-group (.value %)))
                           type-id    (update-in [:error :type :type-id]    #(list 'invalid-type-id (.value %))))})))

(defn validate-attachments [{attachments :attachments id :id}]
  (->> attachments
       (mapcat (juxt validate-attachment-against-schema validate-latest-version))
       (remove nil?)
       seq))

(mongocheck :applications validate-attachments :attachments)

;; Documents have operation information

(defn- application-schemas-have-ops [{documents :documents :as application}]
  (when-not (:infoRequest application)
    (let [operations (app/get-operations application)
          docs-with-op (count (filter #(get-in % [:schema-info :op]) documents))
          ops          (count operations)]
      (when-not (= docs-with-op ops)
        (format "Different number of operations and documents refering to an operation: %d != %d" docs-with-op ops)))))


(mongocheck :applications application-schemas-have-ops :documents :primaryOperation :secondaryOperations :infoRequest)

(mongocheck :submitted-applications application-schemas-have-ops :documents :primaryOperation :secondaryOperations :infoRequest)

(mongocheck :applications (checks/not-null-property :organization) :organization)

(mongocheck :applications (checks/not-null-property :propertyId) :propertyId)

(mongocheck :applications (checks/not-null-property :location) :location)

(mongocheck :applications (checks/not-null-property :municipality) :municipality)

(mongocheck :applications (checks/not-null-property :schema-version) :schema-version)

(defn timestamp-is-set [ts-key states]
  (fn [application]
    (when (and (states (keyword (:state application))) (nil? (ts-key application)))
      (format "Timestamp %s is null in state %s" (name ts-key) (:state application)))))

(mongocheck :applications (timestamp-is-set :opened (states/all-states-but [:draft :canceled])) :state :opened)

;;
;; Skips applications with operation "aiemmalla-luvalla-hakeminen" (previous permit aka paperilupa)
;;
(mongocheck :applications
  (fn [application]
    (when (and
            ((states/all-application-states-but [:canceled :draft :open]) (keyword (:state application)))
            (when-not (some #(= "aiemmalla-luvalla-hakeminen" %) (map :name (app/get-operations application)))
              (nil? (:submitted application))))
      "Submitted timestamp is null"))
  :submitted :state :primaryOperation :secondaryOperations)

(mongocheck :applications (timestamp-is-set :sent #{:sent :complementNeeded}) :state :sent)

(mongocheck :applications (timestamp-is-set :closed #{:closed}) :state :closed)

(ns lupapalvelu.organization
  (:import [org.geotools.data FileDataStoreFinder DataUtilities]
           [org.geotools.geojson.feature FeatureJSON]
           [org.geotools.feature.simple SimpleFeatureBuilder]
           [org.geotools.geojson.geom GeometryJSON]
           [org.geotools.geometry.jts JTS]
           [org.geotools.referencing CRS]
           [org.geotools.referencing.crs DefaultGeographicCRS]
           [org.opengis.feature.simple SimpleFeature]
           [java.util ArrayList])

  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [monger.operators :refer :all]
            [cheshire.core :as json]
            [hiccup.core :as hiccup]
            [clj-rss.core :as rss]
            [schema.core :as sc]
            [sade.core :refer [fail fail!]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.crypt :as crypt]
            [sade.http :as http]
            [sade.xml :as sxml]
            [sade.schemas :as ssc]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.geojson :as geo]
            [me.raynes.fs :as fs]
            [clojure.walk :refer [keywordize-keys]]))

(def scope-skeleton
  {:permitType nil
   :municipality nil
   :inforequest-enabled false
   :new-application-enabled false
   :open-inforequest false
   :open-inforequest-email ""
   :opening nil})

(sc/defschema Tag
  {:id ssc/ObjectIdStr
   :label sc/Str})

(sc/defschema Layer
  {:id sc/Str
   :base sc/Bool
   :name sc/Str})

(def permanent-archive-authority-roles [:tos-editor :tos-publisher :archivist])
(def authority-roles (concat [:authority :approver :commenter :reader] permanent-archive-authority-roles))

(defn- with-scope-defaults [org]
  (if (:scope org)
    (update-in org [:scope] #(map (fn [s] (util/deep-merge scope-skeleton s)) %))
    org))

(defn- remove-sensitive-data
  [org]
  (if (:krysp org)
    (update org :krysp #(into {} (map (fn [[permit-type config]] [permit-type (dissoc config :password :crypto-iv)]) %)))
    org))

(defn get-organizations
  ([]
    (get-organizations {}))
  ([query]
   (->> (mongo/select :organizations query)
        (map remove-sensitive-data)
        (map with-scope-defaults)))
  ([query projection]
   (->> (mongo/select :organizations query projection)
        (map remove-sensitive-data)
        (map with-scope-defaults))))

(defn get-organization [id]
  {:pre [(not (s/blank? id))]}
  (->> (mongo/by-id :organizations id)
       remove-sensitive-data
       with-scope-defaults))

(defn update-organization [id changes]
  {:pre [(not (s/blank? id))]}
  (mongo/update-by-id :organizations id changes))

(defn get-organization-attachments-for-operation [organization operation]
  (-> organization :operations-attachments ((-> operation :name keyword))))

(defn encode-credentials
  [username password]
  (when-not (s/blank? username)
    (let [crypto-key       (-> (env/value :backing-system :crypto-key) (crypt/str->bytes) (crypt/base64-decode))
          crypto-iv        (crypt/make-iv-128)
          crypted-password (->> password
                                (crypt/str->bytes)
                                (crypt/encrypt crypto-key crypto-iv :aes)
                                (crypt/base64-encode)
                                (crypt/bytes->str))
          crypto-iv        (-> crypto-iv (crypt/base64-encode) (crypt/bytes->str))]
      {:username username :password crypted-password :crypto-iv crypto-iv})))

(defn decode-credentials
  "Decode password that was originally generated (together with the init-vector )by encode-credentials.
   Arguments are base64 encoded."
  [password crypto-iv]
  (let [crypto-key   (-> (env/value :backing-system :crypto-key) (crypt/str->bytes) (crypt/base64-decode))
        crypto-iv (-> crypto-iv crypt/str->bytes crypt/base64-decode)]
    (->> password
                          (crypt/str->bytes)
                          (crypt/base64-decode)
                          (crypt/decrypt crypto-key crypto-iv :aes)
                          (crypt/bytes->str))))

(defn get-krysp-wfs
  "Returns a map containing :url and :version information for municipality's KRYSP WFS"
  ([{:keys [organization permitType] :as application}]
    (get-krysp-wfs {:_id organization} permitType))
  ([query permit-type]
   (let [organization (mongo/select-one :organizations query [:krysp])
         krysp-config (get-in organization [:krysp (keyword permit-type)])
         crypto-key   (-> (env/value :backing-system :crypto-key) (crypt/str->bytes) (crypt/base64-decode))
         crypto-iv    (:crypto-iv krysp-config)
         password     (when-let [password (and crypto-iv (:password krysp-config))]
                        (decode-credentials password crypto-iv))
         username     (:username krysp-config)]
     (when-not (s/blank? (:url krysp-config))
       (->> (when username {:credentials [username password]})
            (merge (select-keys krysp-config [:url :version])))))))

(defn municipality-address-endpoint [municipality]
  (when (and (not (ss/blank? municipality)) (re-matches #"\d{3}" municipality) )
    (get-krysp-wfs {:scope.municipality municipality, :krysp.osoitteet.url {"$regex" ".+"}} :osoitteet)))


(defn set-krysp-endpoint
  [id url username password endpoint-type version]
  {:pre [(mongo/valid-key? endpoint-type)]}
  (let [url (ss/trim url)
        updates (->> (encode-credentials username password)
                  (merge {:url url :version version})
                  (map (fn [[k v]] [(str "krysp." endpoint-type "." (name k)) v]))
                  (into {})
                  (hash-map $set))]
    (if (and (not (ss/blank? url)) (= "osoitteet" endpoint-type))
      (let [capabilities-xml (wfs/get-capabilities-xml url username password)
            osoite-feature-type (some->> (wfs/feature-types capabilities-xml)
                                         (map (comp :FeatureType sxml/xml->edn))
                                         (filter #(re-matches #"[a-z]*:?Osoite$" (:Name %))) first)
            address-updates (assoc-in updates [$set (str "krysp." endpoint-type "." "defaultSRS")] (:DefaultSRS osoite-feature-type))]
        (if-not osoite-feature-type
          (fail! :error.no-address-feature-type)
          (update-organization id address-updates)))
      (update-organization id updates))))

(defn get-organization-name [organization]
  (let [default (get-in organization [:name :fi] (str "???ORG:" (:id organization) "???"))]
    (get-in organization [:name i18n/*lang*] default)))

(defn resolve-organizations
  ([municipality]
    (resolve-organizations municipality nil))
  ([municipality permit-type]
    (get-organizations {:scope {$elemMatch (merge {:municipality municipality} (when permit-type {:permitType permit-type}))}})))

(defn resolve-organization [municipality permit-type]
  {:pre  [municipality (permit/valid-permit-type? permit-type)]}
  (when-let [organizations (resolve-organizations municipality permit-type)]
    (when (> (count organizations) 1)
      (errorf "*** multiple organizations in scope of - municipality=%s, permit-type=%s -> %s" municipality permit-type (count organizations)))
    (first organizations)))

(defn resolve-organization-scope
  ([municipality permit-type]
    {:pre  [municipality (permit/valid-permit-type? permit-type)]}
    (let [organization (resolve-organization municipality permit-type)]
      (resolve-organization-scope municipality permit-type organization)))
  ([municipality permit-type organization]
    {:pre  [municipality organization (permit/valid-permit-type? permit-type)]}
   (first (filter #(and (= municipality (:municipality %)) (= permit-type (:permitType %))) (:scope organization)))))

(defn with-organization [id function]
  (if-let [organization (get-organization id)]
    (function organization)
    (do
      (debugf "organization '%s' not found with id." id)
      (fail :error.organization-not-found))))

(defn has-ftp-user? [organization permit-type]
  (not (ss/blank? (get-in organization [:krysp (keyword permit-type) :ftpUser]))))

(defn allowed-roles-in-organization [organization]
  {:pre [(map? organization)]}
  (if-not (:permanent-archive-enabled organization)
    (remove #(% (set permanent-archive-authority-roles)) authority-roles)
    authority-roles))

(defn filter-valid-user-roles-in-organization [organization roles]
  (let [organization  (if (map? organization) organization (get-organization organization))
        allowed-roles (set (allowed-roles-in-organization organization))]
    (filter (comp allowed-roles keyword) roles)))

(defn create-tag-ids
  "Creates mongo id for tag if id is not present"
  [tags]
  (map
    #(if (:id %)
       %
       (assoc % :id (mongo/create-id)))
    tags))

(defn some-organization-has-archive-enabled? [organization-ids]
  (pos? (mongo/count :organizations {:_id {$in organization-ids} :permanent-archive-enabled true})))


;;
;; Organization/municipality provided map support.
;;

(defn query-organization-map-server
  [org-id params headers]
  (when-let [m (-> org-id get-organization :map-layers :server)]
    (let [{:keys [url username password crypto-iv]} m]
      (http/get url
                (merge {:query-params params}
                       (when-not (ss/blank? crypto-iv)
                         {:basic-auth [username (decode-credentials password crypto-iv)]})
                       {:headers (select-keys headers [:accept :accept-encoding])
                        :as :stream})))))

(defn organization-map-layers-data [org-id]
  (when-let [{:keys [server layers]} (-> org-id get-organization :map-layers)]
    (let [{:keys [url username password crypto-iv]} server]
      {:server {:url url
                :username username
                :password (if (ss/blank? crypto-iv)
                            password
                            (decode-credentials password crypto-iv))}
       :layers layers})))

(defn update-organization-map-server [org-id url username password]
  (let [credentials (if (ss/blank? password)
                      {:username username
                       :password password}
                      (encode-credentials username password))
        server      (assoc credentials :url url)]
   (update-organization org-id {$set {:map-layers.server server}})))

;;
;; Construction waste feeds
;;

(defmulti waste-ads (fn [org-id & [fmt lang]] fmt))

(defn max-modified
  "Returns the max (latest) modified value of the given document part
  or list of parts."
  [m]
  (cond
    (:modified m)   (:modified m)
    (map? m)        (max-modified (vals m))
    (sequential? m) (apply max (map max-modified (cons 0 m)))
    :default        0))

(def max-number-of-ads 100)

(defmethod waste-ads :default [ org-id & _]
  (->>
   ;; 1. Every application that maybe has available materials.
   (mongo/select
    :applications
    {:organization org-id
     :documents {$elemMatch {:data.availableMaterials {$exists true }
                             :data.contact {$nin ["" nil]}}}})
   ;; 2. Create materials, contact, modified map.
   (map (fn [{docs :documents}]
          (some #(when (= (-> % :schema-info :name) "rakennusjateselvitys")
                   (let [data (select-keys (:data %) [:contact :availableMaterials])
                         {:keys [contact availableMaterials]} (tools/unwrapped data)]
                     {:contact contact
                      ;; Material and amount information are mandatory. If the information
                      ;; is not present, the row is not included.
                      :materials (->> availableMaterials
                                      tools/rows-to-list
                                      (filter (fn [m]
                                                (->> (select-keys m [:aines :maara])
                                                       vals
                                                       (not-any? ss/blank?)))))
                      :modified (max-modified data)}))
                docs)))
   ;; 3. We only check the contact validity. Name and either phone or email
   ;;    must have been provided and (filtered) materials list cannot be empty.
   (filter (fn [{{:keys [name phone email]} :contact
                 materials                  :materials}]
             (letfn [(good [s] (-> s ss/blank? false?))]
               (and (good name) (or (good phone) (good email))
                    (not-empty materials)))))
   ;; 4. Sorted in the descending modification time order.
   (sort-by (comp - :modified))
   ;; 5. Cap the size of the final list
   (take max-number-of-ads)))


(defmethod waste-ads :rss [org-id _ lang]
  (let [ads         (waste-ads org-id)
        columns     (map :name schemas/availableMaterialsRow)
        loc         (fn [prefix term] (if (ss/blank? term)
                                        term
                                        (i18n/with-lang lang (i18n/loc (str prefix term)))))
        col-value   (fn [col-key col-data]
                      (let [k (keyword col-key)
                            v (k col-data)]
                        (case k
                          :yksikko (loc "jateyksikko." v)
                          v)))
        col-row-map (fn [fun]
                      (->> columns (map fun) (concat [:tr]) vec))
        items       (for [{:keys [contact materials]} ads
                          :let [{:keys [name phone email]}  contact
                                html (hiccup/html [:div [:span (ss/join " " [name phone email])]
                                                   [:table
                                                    (col-row-map #(vec [:th (loc "available-materials." %)]))
                                                    (for [m materials]
                                                      (col-row-map #(vec [:td (col-value % m)])))]])]]

                      {:title "Lupapiste"
                       :link "http://www.lupapiste.fi"
                       :author name
                       :description (str "<![CDATA[ " html " ]]>")})]
    (rss/channel-xml {:title (str "Lupapiste:" (i18n/with-lang lang (i18n/loc "available-materials.contact")))
                      :link "" :description ""}
                     items)))

(defmethod waste-ads :json [org-id & _]
  (json/generate-string (waste-ads org-id)))

;; Waste feed enpoint parameter validators

(defn valid-org [cmd]
  (when-not (-> cmd :data :org ss/upper-case get-organization)
    (fail :error.organization-not-found)))

(defn valid-feed-format [cmd]
  (when-not (->> cmd :data :fmt ss/lower-case keyword (contains? #{:rss :json}) )
    (fail :error.invalid-feed-format)))

(defn valid-language [cmd]
  (when-not  (->> cmd :data :lang ss/lower-case keyword (contains? (set i18n/supported-langs)) )
    (fail :error.unsupported-language)))

(defn-
  ^org.geotools.data.simple.SimpleFeatureCollection
  transform-crs-to-wgs84
  "Convert feature crs in collection to WGS84"
  [^org.geotools.feature.FeatureCollection collection]
  (let [iterator (.features collection)
        list (ArrayList.)
        _ (loop [feature (when (.hasNext iterator)
                           (.next iterator))]
            (when feature
              ; Set CRS to WGS84 to bypass problems when converting to GeoJSON (CRS detection is skipped with WGS84).
              ; Atm we assume only CRS EPSG:3067 is used.
              (let [feature-type (DataUtilities/createSubType (.getFeatureType feature) nil DefaultGeographicCRS/WGS84)
                    builder (SimpleFeatureBuilder. feature-type) ; build new feature with changed crs
                    _ (.init builder feature) ; init builder with original feature
                    transformed-feature (.buildFeature builder (mongo/create-id))]
                (.add list transformed-feature)))
            (when (.hasNext iterator)
              (recur (.next iterator))))]
    (.close iterator)
    (DataUtilities/collection list)))

(defn- transform-coordinates-to-wgs84 [collection]
  "Convert feature coordinates in collection to WGS84 which is supported by mongo 2dsphere index"
  (let [schema (.getSchema collection)
        crs (.getCoordinateReferenceSystem schema)
        transform (CRS/findMathTransform crs DefaultGeographicCRS/WGS84 true)
        iterator (.features collection)
        feature (when (.hasNext iterator)
                  (.next iterator))
        list (ArrayList.)
        _ (loop [feature (cast SimpleFeature feature)]
            (when feature
              (let [geometry (.getDefaultGeometry feature)
                    transformed-geometry (JTS/transform geometry transform)]
                (.setDefaultGeometry feature transformed-geometry)
                (.add list feature)))
            (when (.hasNext iterator)
              (recur (.next iterator))))]
    (.close iterator)
    (DataUtilities/collection list)))

(defn parse-shapefile-to-organization-areas [org-id tempfile tmpdir file-info]
  (when-not (= (:content-type file-info) "application/zip")
    (fail! :error.illegal-shapefile))
  (let [target-dir (util/unzip (.getPath tempfile) tmpdir)
        shape-file (first (util/get-files-by-regex (.getPath target-dir) #"^.+\.shp$"))
        data-store (FileDataStoreFinder/getDataStore shape-file)
        new-collection-wgs84 (some-> data-store
                                     .getFeatureSource
                                     .getFeatures
                                     transform-coordinates-to-wgs84
                                     transform-crs-to-wgs84)
        new-collection (some-> data-store
                               .getFeatureSource
                               .getFeatures
                               transform-crs-to-wgs84)
        precision      13 ; FeatureJSON shows only 4 decimals by default
        areas (keywordize-keys (json/parse-string (.toString (FeatureJSON. (GeometryJSON. precision)) new-collection)))
        areas-wgs84 (keywordize-keys (json/parse-string (.toString (FeatureJSON. (GeometryJSON. precision)) new-collection-wgs84)))
        ensured-areas (geo/ensure-features areas)
        ensured-areas-wgs84 (geo/ensure-features areas-wgs84)]
    (when (geo/validate-features (:features ensured-areas))
      (fail! :error.coordinates-not-epsg3067))
    (update-organization org-id {$set {:areas ensured-areas
                                         :areas-wgs84 ensured-areas-wgs84}})
    (.dispose data-store)
    ensured-areas))

(ns lupapalvelu.components.core
  (:use [clojure.string :only [join]]))

(defn get-component
  "Return component c from components. Throws exception if component c is missing"
  [components c]
  (or (components c) (throw (Exception. (format "Unknown component: %s" (name c))))))

(defn get-component-deps
  "Returns the dependencies of component c"
  [components c]
  (or (:depends (get-component components c)) []))

(defn exclude
  "Return a lazy-seq of elements that are in 'depends' excluding elements that are in 'found'"
  [found depends]
  (filter (comp not (set found)) depends))

(defn get-dependencies 
  "Return a lazy-seq of all dependencies of component 'c'. The returned seq contains the keywords of dependencies
   with the root dependency first."
  ([components c]
    (get-dependencies components [] c))
  ([components found c]
    (conj (reduce (partial get-dependencies components) found (exclude found (get-component-deps components c))) c)))

(defn component-resources
  "Returns a lazy-seq of resources names registered for specified component. The seq contains strings,
   each representing a resource of 'kind', where 'kind' is one of :js, :html or :css. Note that this
   does not include resources from dependencies."
  [components kind c]
  (let [path (or (:name (components c)) (name c))]
    (map (partial str path \/) (kind (get-component components c)))))

(defn get-resources
  "Returns a lazy-seq of resources names registered for specified component, including resources from
   dependencies. The seq contains strings, each representing a resource of 'kind', where 'kind' is one
   of :js, :html or :css."
  [components kind c]
  (mapcat (partial component-resources components kind) (get-dependencies components c)))

(defn path [& dir] (str "private/" (join "/" dir)))

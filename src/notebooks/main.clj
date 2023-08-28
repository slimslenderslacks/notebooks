(ns notebooks.main
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [nextjournal.markdown :as md]
   [cheshire.core :as json]))

(def awesome-compose "/Users/slim/slimslenderslacks/awesome-compose")

(defn code? [content]
  (and
   (map? content)
   (= :code (:type content))))

(def docker-supported-file? #{"dockercompose" "dockerfile"})

(defn shell-script-code? [content]
  (and
   (map? content)
   (= :code (:type content))
   (not (docker-supported-file? (:language content)))))

(defn compose-code? [content]
  (and
   (map? content)
   (= :code (:type content))
   (= "dockercompose" (:language content))))

(defn dockerfile-code? [content]
  (and
   (map? content)
   (= :code (:type content))
   (= "dockerfile" (:language content))))

(defmulti markdown-content :type)

(defn render-content [content & args]
  (->> (merge content (when-let [prefix (first args)] {:prefix prefix}))
       (map markdown-content)
       #_(interpose " ")
       (apply str)))

(defmethod markdown-content :link
  [{:keys [content] {href :href} :attrs}]
  (format
   "[%s](%s)"
   (render-content content)
   href))
(defmethod markdown-content :text
  [{:keys [text]}] text)
(defmethod markdown-content :image
  [{{:keys [src]} :attrs}]
  (format "<img src=\"%s\" width=\"500\"/>" src))

(defmethod markdown-content :softbreak
  [_] "\n")
(defmethod markdown-content :hardbreak
  [{:keys [prefix]}] (if prefix (str "\n" prefix) "\n"))

(defmethod markdown-content :heading
  [{:keys [content heading-level]}]
  (format "%s %s" (apply str (repeat heading-level "#")) (render-content content)))
(defmethod markdown-content :paragraph
  [{:keys [content prefix]}]
  (format "%s" (apply render-content (concat [content] (when prefix [prefix])))))
(defmethod markdown-content :blockquote
  [{:keys [content]}]
  (format "> %s" (render-content content)))
(defmethod markdown-content :bullet-list
  [{:keys [content]}]
  (render-content content))
(defmethod markdown-content :numbered-list
  [{:keys [content]}]
  (render-content content))

(defmethod markdown-content :monospace
  [{:keys [content]}]
  (format "`%s`" (render-content content)))
(defmethod markdown-content :em
  [{:keys [content]}]
  (format "*%s*" (render-content content)))
(defmethod markdown-content :strong
  [{:keys [content]}]
  (format "**%s**" (render-content content)))
(defmethod markdown-content :monospace
  [{:keys [content]}]
  (format "`%s`" (render-content content)))
(defmethod markdown-content :list-item
  [{:keys [content]}]
  (format "* %s\n" (render-content content)))
(defmethod markdown-content :plain
  [{:keys [content]}]
  (render-content content))

(defmethod markdown-content :default
  [x] (str x))

(defn markdown-string [coll]
  (->> coll
       (map markdown-content)
       (interpose "\n\n")
       (apply str)))

(defn notebook-cell [coll]
  (cond
    (-> coll first shell-script-code?)
    {:kind 2
     :language "shellscript"
     :value (->> (-> coll first :content first :text)
                 (string/split-lines)
                 (filter #(string/starts-with? % "$"))
                 (map #(string/replace % "$ " ""))
                 (string/join "\n"))}
    (-> coll first compose-code?)
    {:kind 2
     :language "dockercompose"
     :value ""}
    (-> coll first dockerfile-code?)
    {:kind 2
     :language "dockerfile"
     :value ""}
    :else
    {:kind 1
     :language "markdown"
     :value (markdown-string coll)}))

(defn is-project-structure? [m]
  (= m {:type :paragraph
        :content [{:type :text :text "Project structure:"}]}))

(defn is-compose-yaml-link? [m]
  (= "compose.yaml" (-> m :content first :content first :content first :text)))

(defn find-compose-yaml-link [coll]
  (->> (interleave (range) coll)
       (partition 2)
       (some (fn [[n m]]
               (when
                (and
                 (is-compose-yaml-link? m)
                 (code? (nth coll (inc n)))) n)))))

(defn find-project-structure-node [coll]
  (->> (interleave (range) coll)
       (partition 2)
       (some (fn [[n m]]
               (when
                (and
                 (is-project-structure? m)
                 (code? (nth coll (inc n)))) n)))))

(defn combine-into-paragraph [n coll]
  [(let [{c :content} (nth coll n) {c1 :content} (nth coll (inc n))]
     {:type :paragraph
      :content
      (concat
       c
       [{:type :text
         :text (format "\n\n```\n%s\n```\n\n" (-> c1 first :text))}])})])

(defn add-compose-snippet [n coll]
  (let [{c :content} (nth coll n)]
    [{:type :paragraph
      :content c}
     {:type :code
      :language "dockercompose"}]))

(defn edit-content-pairs [f g coll]
  (if-let [n (f coll)]
    (concat
     (take n coll)
     (g n coll)
     (drop (+ n 2) coll))
    coll))

(defn create-notebook [f]
  (->> (md/parse (slurp f))
       :content
       ((partial edit-content-pairs find-project-structure-node combine-into-paragraph))
       ((partial edit-content-pairs find-compose-yaml-link add-compose-snippet))
       (partition-by code?)
       (map notebook-cell)
       ((fn [coll] {:cells coll}))
       (json/generate-string)))

(defn write-notebook [f]
  (let [notebook (io/file (.getParentFile f) "notebook.idnb")
        notebook-content (create-notebook f)]
    (spit notebook notebook-content)))

(defn generate-notebooks []
  (->> (.listFiles (io/file awesome-compose))
       (filter #(.isDirectory %))
       (filter (complement #(= ".git" (.getName %))))
       (map #(io/file % "README.md"))
       (filter #(.exists %))
       (map write-notebook)))

(comment
  (def f (io/file awesome-compose "angular" "README.md"))
  (md/parse (slurp f))

  (write-notebook f)

  (slurp (io/file awesome-compose "angular" "notebook.idnb"))

  (spit
   (io/file awesome-compose "angular" "try.idnb")
   (create-notebook (io/file awesome-compose "angular" "README.md")))

  (generate-notebooks))

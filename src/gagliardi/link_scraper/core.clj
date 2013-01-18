(ns gagliardi.link-scraper.core
  (:require [net.cgrand.enlive-html :as e]
            [cemerick.url :refer [url]]
            [clojure.string :as s]
            [clj-http.client :as http])
  (:import [com.google.common.net InternetDomainName]
           [java.util.concurrent LinkedBlockingQueue]))

(def config {:num-workers 30
             :snapshot-delay 5000})

(defn root-domain [domain-str]
  (try
    (-> (InternetDomainName/from domain-str)
      (.topPrivateDomain)
      (.name))
    (catch java.lang.IllegalArgumentException e
      (println e))))

(defn str->url [href]
  (try
    (url href)
    (catch java.net.MalformedURLException e (println e))))

(defn clean-url 
  "Drop everything after the host name"
  [url-str]
  (try
    (let [url-map (url url-str)]
      (str (:protocol url-map) "://" (:host url-map)))
    (catch Exception e nil)))

(defn absolute-url? [url-str]
  (.startsWith url-str "http://"))

(defn html->domains [html-str]
  (let [html (e/html-resource (java.io.StringReader. html-str))
        links (e/select html [:a])
        raw-urls (remove nil? (map (comp :href :attrs) links))
        absolute-urls (filter absolute-url? raw-urls)
        clean-absolute-urls (map clean-url absolute-urls)
        hosts (map (comp :host str->url) clean-absolute-urls)]
    (set (map root-domain hosts))))

(defn get-body [url]
  (try
    (:body (http/get url {:conn-timeout 5000}))
    (catch Exception e (println e))))

(defn url->linked-domains [url]
  (let [html (get-body url)]
    (when html
      (html->domains html))))

(def url-queue (LinkedBlockingQueue.))
(def linked-domains (atom #{}))
(def urls-processed (atom #{}))
(def total-urls (atom 0))

(defn fill-queue! [filepath & [n]]
  (let [urls (s/split (slurp filepath) #"\n")
        urls (if n (take n urls) urls)]
    (doseq [url urls]
      (.put url-queue url))
    (swap! total-urls (fn [_] (.size url-queue)))))

(defn worker-fn []
  (when (> (.size url-queue) 0) 
    (when-let [url (.poll url-queue)] 
      (try
        (let [domains (url->linked-domains url)]
          (swap! linked-domains into domains))
        (catch Exception e (println "failed processing " url "\n" e))  
        (finally (swap! urls-processed conj url))))
    (recur)))

(def workers (doall
               (for [i (range (:num-workers config))]
                 (Thread. worker-fn))))

;; Recreating the domains-temp and processed-temp files every time is dumb,
;; but easily fast enough for my purposes
(def snapshot-thread
  (Thread. (fn []
             (loop [running? true] 
               (when running?
                 (let [processed @urls-processed
                       linked    @linked-domains
                       total     @total-urls]
                   (println "Domains processed: " (count processed))
                   (spit "linked-domains"   (s/join "\n" linked)) 
                   (spit "urls-processed" (s/join "\n" processed)) 
                   (when-let [run-again? (not= total (count processed))]
                     (Thread/sleep (:snapshot-delay config))
                     (recur run-again?))))))))

(defn start-workers! []
  (doseq [worker workers]
    (.start worker)))

;; TODO: shutdown when all the domains have been processed
(defn -main
  [& [filepath]]
  (fill-queue! filepath)
  (.start snapshot-thread)
  (start-workers!))

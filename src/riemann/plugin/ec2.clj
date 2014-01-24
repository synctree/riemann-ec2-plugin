(ns riemann.plugin.ec2
  (:require [amazonica.aws.ec2 :as ec2]
            [riemann.service :refer [thread-service]]
            [riemann.core :as core]
            [riemann.config :as config]
            [riemann.streams :refer :all]
            [clojure.string :as str]))

(defn- shorthost [host]
  (first (str/split host #"\.")))

(def cache (atom nil))

(defn- ec2-service [opts]
  (let [interval (long (* 1000 (get opts :interval 5)))
        creds (get opts :credentials {})]
    (thread-service
      ::ec2 [interval creds]
      (fn update-cache [core]
        (when-let [instances (:reservations (ec2/describe-instances creds))]
          (reset! cache (mapcat :instances instances)))
        (Thread/sleep interval)))))

(defn start-ec2 [opts]
  "Creates a thread-service which updates the cache used to retrieve information
  regarding EC2 instance status"
  (let [service (ec2-service opts)]
    (swap! config/next-core core/conj-service service :force)
    service))

(defn get-host-info [host]
  "Retrieve information regarding one host"
  (first
    (filter
      (comp (partial = (shorthost host)) shorthost :private-dns-name) @cache)))

(defn running-stream [& children]
  "Provides a stream which will only pass on events from hosts with state \"running\"
  to children.

  (ec2/running-stream (partial prn \"a running host sent me an event\"))

  Note: data regarding instance status is retrieved from a cache. This cache is updated by
  a background thread which you must start from your configuration using the start-ec2 function
  provided by this namespace."
  (fn [event]
    (when-let [host (:host event)]
      (when-let [ec2-info (get-host-info host)]
        (when (= (:name (:state ec2-info)) "running")
          (call-rescue event children))))))

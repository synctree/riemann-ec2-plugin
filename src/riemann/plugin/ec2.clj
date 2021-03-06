(ns riemann.plugin.ec2
  (:require [amazonica.aws.ec2 :as ec2]
            [riemann.service :refer [thread-service]]
            [riemann.core :as core]
            [riemann.config :as config]
            [riemann.streams :refer :all]
            [clojure.string :as str])
  (:import (org.cliffc.high_scale_lib NonBlockingHashMap)))

(defn- shorthost [host]
  (first (str/split host #"\.")))

(def ^:private cache (NonBlockingHashMap.))

(defn- ec2-service [opts]
  (let [interval (long (* 1000 (get opts :interval 5)))
        creds (get opts :credentials {})]
    (thread-service
      ::ec2 [interval creds]
      (fn update-cache [core]
        (when-let [instances (:reservations (ec2/describe-instances creds))]
          (doseq [instance (mapcat :instances instances)]
                  (.put cache (-> instance (get :private-dns-name) shorthost) instance)))
        (Thread/sleep interval)))))

(defn start-ec2
  "Creates a thread-service which updates the cache used to retrieve information
  regarding EC2 instance status"
  [opts]
  (let [service (ec2-service opts)]
    (swap! config/next-core core/conj-service service :force)
    service))

(defn get-host-info
  "Retrieve information regarding one host"
  [host]
  (.get cache (shorthost host)))

(defn running-stream
  "Provides a stream which will only pass on events from hosts with state \"running\"
  to children.

  (ec2/running-stream (partial prn \"a running host sent me an event\"))

  Note: data regarding instance status is retrieved from a cache. This cache is updated by
  a background thread which you must start from your configuration using the start-ec2 function
  provided by this namespace."

  [& children]
  (fn [event]
    (when-let [host (:host event)]
      (when-let [ec2-info (get-host-info host)]
        (when (= (:name (:state ec2-info)) "running")
          (call-rescue event children))))))

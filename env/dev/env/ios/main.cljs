(ns ^:figwheel-no-load env.ios.main
  (:require [om.next :as om]
            [game-log.ios.core :as core]
            [game-log.state :as state]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :heads-up-display true
  :jsload-callback #(om/add-root! core/reconciler core/MainView 1))

(core/init)

(def root-el (core/app-root))